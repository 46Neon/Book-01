package com.neon.book01.domain

import java.time.LocalDate

/** Persisted invoice input used to derive a counterparty's display-only activity statement. */
data class StatementInvoiceInput(
    val invoiceId: String,
    val counterpartyId: String,
    val invoiceNumber: String,
    val invoiceDate: String,
    val totalMinor: Long,
)

/** Persisted settlement input; settlements remain separate, append-only movements. */
data class StatementSettlementInput(
    val settlementId: String,
    val invoiceId: String,
    val settlementDate: String,
    val amountMinor: Long,
)

enum class StatementMovementKind { INVOICE, SETTLEMENT }

data class CounterpartyStatementMovement(
    val date: String,
    val kind: StatementMovementKind,
    val reference: String,
    /** Invoices increase outstanding balance; their collections/payments reduce it. */
    val changeMinor: Long,
    val balanceAfterMinor: Long,
)

/**
 * Builds a chronological, read-only statement from saved invoices and linked settlements.
 * It does not infer or create journal entries. Exact integer arithmetic rejects overflow.
 */
fun buildCounterpartyStatement(
    counterpartyId: String,
    invoices: List<StatementInvoiceInput>,
    settlements: List<StatementSettlementInput>,
): List<CounterpartyStatementMovement> {
    require(counterpartyId.isNotBlank()) { "La contraparte debe estar identificada." }
    val selected = invoices.filter { it.counterpartyId == counterpartyId }
    require(selected.map { it.invoiceId }.toSet().size == selected.size) { "Hay facturas duplicadas en el estado de cuenta." }
    selected.forEach {
        require(it.invoiceId.isNotBlank() && it.invoiceNumber.isNotBlank())
        require(it.totalMinor >= 0L) { "El total de la factura no puede ser negativo." }
        require(LocalDate.parse(it.invoiceDate).toString() == it.invoiceDate) { "La fecha de factura debe estar en formato AAAA-MM-DD." }
    }
    val invoiceById = selected.associateBy { it.invoiceId }
    val rows = mutableListOf<RawMovement>()
    selected.forEach { invoice ->
        rows += RawMovement(invoice.invoiceDate, StatementMovementKind.INVOICE, invoice.invoiceNumber, invoice.invoiceId, invoice.totalMinor)
    }
    val settledByInvoice = mutableMapOf<String, Long>()
    settlements.forEach { settlement ->
        val invoice = invoiceById[settlement.invoiceId]
            ?: if (invoices.any { it.invoiceId == settlement.invoiceId }) return@forEach
            else throw IllegalArgumentException("El abono no está vinculado a una factura del libro.")
        require(settlement.settlementId.isNotBlank())
        require(LocalDate.parse(settlement.settlementDate).toString() == settlement.settlementDate) { "La fecha del movimiento debe estar en formato AAAA-MM-DD." }
        require(settlement.amountMinor > 0L) { "El importe del abono debe ser positivo." }
        val settled = Math.addExact(settledByInvoice[settlement.invoiceId] ?: 0L, settlement.amountMinor)
        require(settled <= invoice.totalMinor) { "Los abonos superan el total de la factura." }
        settledByInvoice[settlement.invoiceId] = settled
        rows += RawMovement(settlement.settlementDate, StatementMovementKind.SETTLEMENT, invoice.invoiceNumber, settlement.settlementId, -settlement.amountMinor)
    }
    var balance = 0L
    return rows.sortedWith(
        compareBy<RawMovement> { it.date }
            .thenBy { if (it.kind == StatementMovementKind.INVOICE) 0 else 1 }
            .thenBy { it.reference }
            .thenBy { it.id },
    ).map { row ->
        balance = Math.addExact(balance, row.changeMinor)
        CounterpartyStatementMovement(row.date, row.kind, row.reference, row.changeMinor, balance)
    }
}

private data class RawMovement(
    val date: String,
    val kind: StatementMovementKind,
    val reference: String,
    val id: String,
    val changeMinor: Long,
)
