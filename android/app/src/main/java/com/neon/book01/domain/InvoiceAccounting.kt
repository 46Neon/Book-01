package com.neon.book01.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** Sale/purchase invoice posting proposal. Rates are supplied by the book, never hard-coded. */
enum class InvoiceKind { SALE, PURCHASE }
enum class PaymentCondition { CASH, CREDIT }
enum class PurchaseVatTreatment { INPUT_TAX_ASSET, INCLUDED_IN_PURCHASE_COST }

data class InvoiceAccounts(
    val cashOrBank: String,
    val receivable: String,
    val payable: String,
    val revenue: String,
    val purchaseCostOrExpense: String,
    val outputVat: String,
    val inputVat: String,
    val bankLoanLiability: String,
    /** This policy is deliberately required from configuration; have a qualified accountant approve it. */
    val purchaseVatTreatment: PurchaseVatTreatment,
)

data class InvoiceInput(
    val kind: InvoiceKind,
    val invoiceDate: String,
    val invoiceNumber: String,
    val controlNumber: String? = null,
    val registeredPartyId: String,
    val description: String,
    val taxableBaseMinor: Long,
    /** Tax rate in basis points: 1600 means 16%. It must be explicitly provided. */
    val vatRateBasisPoints: Int,
    val condition: PaymentCondition,
    val paidTodayMinor: Long,
    val cashAccountCode: String,
    val dueDate: String? = null,
)

data class PostingLineProposal(val accountCode: String, val debitMinor: Long, val creditMinor: Long)
data class InvoicePostingPreview(
    val baseMinor: Long,
    val vatMinor: Long,
    val totalMinor: Long,
    val paidTodayMinor: Long,
    val outstandingMinor: Long,
    val lines: List<PostingLineProposal>,
)

object InvoiceAccounting {
    /** Calculate the invoice tax per document to cents, using decimal arithmetic and HALF_UP. */
    fun preview(input: InvoiceInput, accounts: InvoiceAccounts): InvoicePostingPreview {
        validate(input, accounts)
        val vat = BigDecimal.valueOf(input.taxableBaseMinor)
            .multiply(BigDecimal.valueOf(input.vatRateBasisPoints.toLong()))
            .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP)
            .longValueExact()
        val total = Math.addExact(input.taxableBaseMinor, vat)
        val outstanding = Math.subtractExact(total, input.paidTodayMinor)
        val lines = when (input.kind) {
            InvoiceKind.SALE -> buildList {
                if (input.paidTodayMinor > 0) add(PostingLineProposal(input.cashAccountCode, input.paidTodayMinor, 0))
                if (outstanding > 0) add(PostingLineProposal(accounts.receivable, outstanding, 0))
                add(PostingLineProposal(accounts.revenue, 0, input.taxableBaseMinor))
                if (vat > 0) add(PostingLineProposal(accounts.outputVat, 0, vat))
            }
            InvoiceKind.PURCHASE -> buildList {
                val expenseOrCost = if (accounts.purchaseVatTreatment == PurchaseVatTreatment.INPUT_TAX_ASSET) input.taxableBaseMinor else total
                add(PostingLineProposal(accounts.purchaseCostOrExpense, expenseOrCost, 0))
                if (vat > 0 && accounts.purchaseVatTreatment == PurchaseVatTreatment.INPUT_TAX_ASSET) add(PostingLineProposal(accounts.inputVat, vat, 0))
                if (input.paidTodayMinor > 0) add(PostingLineProposal(input.cashAccountCode, 0, input.paidTodayMinor))
                if (outstanding > 0) add(PostingLineProposal(accounts.payable, 0, outstanding))
            }
        }
        requireBalanced(lines)
        return InvoicePostingPreview(input.taxableBaseMinor, vat, total, input.paidTodayMinor, outstanding, lines)
    }

    /** A due date is optional, but when entered it must be a real ISO date on/after the invoice date. */
    fun validateDueDate(invoiceDate: String, dueDate: String?) {
        if (dueDate == null) return
        val invoice = runCatching { java.time.LocalDate.parse(invoiceDate) }.getOrNull()
            ?: throw IllegalArgumentException("La fecha de factura no es válida.")
        val due = runCatching { java.time.LocalDate.parse(dueDate) }.getOrNull()
            ?: throw IllegalArgumentException("La fecha de vencimiento no es válida.")
        require(invoice.toString() == invoiceDate && due.toString() == dueDate) { "La fecha de vencimiento debe tener el formato AAAA-MM-DD." }
        require(!due.isBefore(invoice)) { "El vencimiento no puede ser anterior a la fecha de la factura." }
    }

    /** Later receipts/payments are independent balanced entries linked by the caller to an invoice. */
    fun settlement(kind: InvoiceKind, amountMinor: Long, cashAccountCode: String, accounts: InvoiceAccounts): List<PostingLineProposal> {
        require(amountMinor > 0) { "El cobro o pago debe ser mayor que cero." }
        val lines = when (kind) {
            InvoiceKind.SALE -> listOf(
                PostingLineProposal(cashAccountCode, amountMinor, 0),
                PostingLineProposal(accounts.receivable, 0, amountMinor),
            )
            InvoiceKind.PURCHASE -> listOf(
                PostingLineProposal(accounts.payable, amountMinor, 0),
                PostingLineProposal(cashAccountCode, 0, amountMinor),
            )
        }
        requireBalanced(lines)
        return lines
    }

    /** A collection/payment cannot predate the invoice it settles. Both dates must be strict ISO dates. */
    fun requireSettlementDateNotBeforeInvoice(invoiceDate: String, settlementDate: String) {
        val invoice = runCatching { java.time.LocalDate.parse(invoiceDate) }.getOrNull()
            ?: throw IllegalArgumentException("La fecha de factura no es válida.")
        val settlement = runCatching { java.time.LocalDate.parse(settlementDate) }.getOrNull()
            ?: throw IllegalArgumentException("La fecha del cobro o pago no es válida.")
        require(!settlement.isBefore(invoice)) { "El cobro o pago no puede ser anterior a la fecha de la factura." }
    }

    /** A loan receipt increases cash and a liability; it never credits sales. */
    fun loanReceipt(amountMinor: Long, cashAccountCode: String, loanLiabilityAccountCode: String): List<PostingLineProposal> {
        require(amountMinor > 0) { "El préstamo debe ser mayor que cero." }
        return listOf(
            PostingLineProposal(cashAccountCode, amountMinor, 0),
            PostingLineProposal(loanLiabilityAccountCode, 0, amountMinor),
        )
    }

    fun requireSufficientFunds(currentBalanceMinor: Long, paymentMinor: Long) {
        require(currentBalanceMinor >= 0 && paymentMinor >= 0) { "El saldo y el pago no pueden ser negativos." }
        require(paymentMinor <= currentBalanceMinor) { "Saldo insuficiente: el pago dejaría caja o banco en negativo." }
    }

    fun requireUniqueInvoice(existingKeys: Set<String>, kind: InvoiceKind, registeredPartyId: String, invoiceNumber: String) {
        val key = duplicateKey(kind, registeredPartyId, invoiceNumber)
        require(key !in existingKeys) { "Ya existe una factura con ese número para este cliente o proveedor." }
    }

    fun duplicateKey(kind: InvoiceKind, registeredPartyId: String, invoiceNumber: String): String =
        listOf(kind.name, registeredPartyId.trim().uppercase(), invoiceNumber.trim().uppercase()).joinToString("|")

    private fun validate(input: InvoiceInput, accounts: InvoiceAccounts) {
        require(input.invoiceDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && runCatching { java.time.LocalDate.parse(input.invoiceDate) }.isSuccess) { "La fecha de factura no es válida." }
        require(input.invoiceNumber.isNotBlank()) { "Escribe el número de factura." }
        require(input.kind != InvoiceKind.SALE || !input.controlNumber.isNullOrBlank()) { "La venta requiere número de control." }
        validateDueDate(input.invoiceDate, input.dueDate)
        require(input.registeredPartyId.isNotBlank()) { "Selecciona un cliente o proveedor registrado." }
        require(input.description.isNotBlank()) { "Escribe una descripción." }
        require(input.taxableBaseMinor > 0) { "La base imponible debe ser mayor que cero." }
        require(input.vatRateBasisPoints in 0..100_000) { "La tasa de IVA debe ser un parámetro válido." }
        require(input.kind == InvoiceKind.SALE || accounts.purchaseVatTreatment == PurchaseVatTreatment.INPUT_TAX_ASSET || accounts.purchaseVatTreatment == PurchaseVatTreatment.INCLUDED_IN_PURCHASE_COST) { "El tratamiento del IVA de compra no está configurado." }
        require(input.paidTodayMinor >= 0) { "El pago recibido hoy no puede ser negativo." }
        require(input.paidTodayMinor <= Math.addExact(input.taxableBaseMinor, BigDecimal.valueOf(input.taxableBaseMinor).multiply(BigDecimal.valueOf(input.vatRateBasisPoints.toLong())).divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP).longValueExact())) { "El pago de hoy no puede superar el total de la factura." }
        require(input.condition != PaymentCondition.CASH || input.paidTodayMinor == Math.addExact(input.taxableBaseMinor, BigDecimal.valueOf(input.taxableBaseMinor).multiply(BigDecimal.valueOf(input.vatRateBasisPoints.toLong())).divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP).longValueExact())) { "Una factura de contado debe quedar pagada en su totalidad." }
        require(input.cashAccountCode.isNotBlank()) { "Selecciona una cuenta de caja o banco." }
    }

    private fun requireBalanced(lines: List<PostingLineProposal>) {
        require(lines.isNotEmpty() && lines.all { it.debitMinor >= 0 && it.creditMinor >= 0 && ((it.debitMinor == 0L) xor (it.creditMinor == 0L)) }) { "Las líneas del asiento son inválidas." }
        require(UsdMoney.sumMinorUnits(lines.map { it.debitMinor }) == UsdMoney.sumMinorUnits(lines.map { it.creditMinor })) { "El asiento no está balanceado." }
    }
}
