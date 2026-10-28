package com.neon.book01.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.fail

class InvoiceAccountingTest {
    private val accounts = InvoiceAccounts(
        cashOrBank = "1010", receivable = "1100", payable = "2000", revenue = "4000",
        purchaseCostOrExpense = "5100", outputVat = "2103", inputVat = "1105", bankLoanLiability = "2102",
        purchaseVatTreatment = PurchaseVatTreatment.INPUT_TAX_ASSET,
    )

    private fun input(kind: InvoiceKind, base: Long, rate: Int = 1600, paid: Long = 0, condition: PaymentCondition = PaymentCondition.CREDIT) =
        InvoiceInput(kind, "2026-01-10", "F-001", if (kind == InvoiceKind.SALE) "C-001" else null,
            "party-1", "Factura de prueba", base, rate, condition, paid, accounts.cashOrBank)

    @Test fun sale100PlusVat16_posts116() {
        val p = InvoiceAccounting.preview(input(InvoiceKind.SALE, 10_000, paid = 11_600, condition = PaymentCondition.CASH), accounts)
        assertEquals(11_600L, p.totalMinor)
        assertEquals(1_600L, p.vatMinor)
        assertEquals(0L, p.outstandingMinor)
        assertBalanced(p.lines)
        assertLine(p.lines, "1010", 11_600, 0)
        assertLine(p.lines, "4000", 0, 10_000)
        assertLine(p.lines, "2103", 0, 1_600)
    }

    @Test fun purchase50PlusVat8_posts58() {
        val p = InvoiceAccounting.preview(input(InvoiceKind.PURCHASE, 5_000, paid = 5_800, condition = PaymentCondition.CASH).copy(invoiceNumber="C-001"), accounts)
        assertEquals(5_800L, p.totalMinor)
        assertEquals(800L, p.vatMinor)
        assertBalanced(p.lines)
        assertLine(p.lines, "5100", 5_000, 0)
        assertLine(p.lines, "1105", 800, 0)
        assertLine(p.lines, "1010", 0, 5_800)
    }

    @Test fun partialSaleOf50LeavesReceivable66() {
        val p = InvoiceAccounting.preview(input(InvoiceKind.SALE, 10_000, paid = 5_000), accounts)
        assertEquals(11_600L, p.totalMinor)
        assertEquals(6_600L, p.outstandingMinor)
        assertLine(p.lines, "1010", 5_000, 0)
        assertLine(p.lines, "1100", 6_600, 0)
        assertBalanced(p.lines)
    }

    @Test fun purchase30Pay10LeavesPayable20() {
        val p = InvoiceAccounting.preview(input(InvoiceKind.PURCHASE, 3_000, rate = 0, paid = 1_000), accounts)
        assertEquals(2_000L, p.outstandingMinor)
        assertLine(p.lines, "5100", 3_000, 0)
        assertLine(p.lines, "1010", 0, 1_000)
        assertLine(p.lines, "2000", 0, 2_000)
        assertBalanced(p.lines)
    }

    @Test fun laterCollectionAndPaymentAreBalancedAndLinkedByApplication() {
        val collection = InvoiceAccounting.settlement(InvoiceKind.SALE, 6_600, accounts.cashOrBank, accounts)
        assertLine(collection, "1010", 6_600, 0)
        assertLine(collection, "1100", 0, 6_600)
        assertBalanced(collection)
        val payment = InvoiceAccounting.settlement(InvoiceKind.PURCHASE, 2_000, accounts.cashOrBank, accounts)
        assertLine(payment, "2000", 2_000, 0)
        assertLine(payment, "1010", 0, 2_000)
        assertBalanced(payment)
    }

    @Test fun dueDateIsOptionalButMustBeValidAndNotPrecedeInvoice() {
        InvoiceAccounting.validateDueDate("2026-01-10", null)
        InvoiceAccounting.validateDueDate("2026-01-10", "2026-01-10")
        InvoiceAccounting.validateDueDate("2026-01-10", "2026-02-01")
        expectFailure { InvoiceAccounting.validateDueDate("2026-01-10", "2026-01-09") }
        expectFailure { InvoiceAccounting.validateDueDate("2026-01-10", "2026-02-30") }
        expectFailure { InvoiceAccounting.validateDueDate("2026-1-10", "2026-02-01") }
    }

    @Test fun settlementCannotPrecedeInvoiceDate() {
        InvoiceAccounting.requireSettlementDateNotBeforeInvoice("2026-01-10", "2026-01-10")
        InvoiceAccounting.requireSettlementDateNotBeforeInvoice("2026-01-10", "2026-01-11")
        expectFailure { InvoiceAccounting.requireSettlementDateNotBeforeInvoice("2026-01-10", "2026-01-09") }
        expectFailure { InvoiceAccounting.requireSettlementDateNotBeforeInvoice("2026-02-30", "2026-03-01") }
    }

    @Test fun loanReceiptIsLiabilityNotIncome() {
        val lines = InvoiceAccounting.loanReceipt(500_000, accounts.cashOrBank, accounts.bankLoanLiability)
        assertLine(lines, "1010", 500_000, 0)
        assertLine(lines, "2102", 0, 500_000)
        assertTrue(lines.none { it.accountCode == accounts.revenue })
        assertBalanced(lines)
    }

    @Test fun insufficientFundsAreRejected() {
        expectFailure { InvoiceAccounting.requireSufficientFunds(999, 1_000) }
    }

    @Test fun configurableVatRateUsesExactCents() {
        val p = InvoiceAccounting.preview(input(InvoiceKind.SALE, 10_000, rate = 825, paid = 10_825), accounts)
        assertEquals(825L, p.vatMinor)
        assertEquals(10_825L, p.totalMinor)
        assertBalanced(p.lines)
    }

    @Test fun saleRequiresControlNumberButPurchaseDoesNot() {
        expectFailure { InvoiceAccounting.preview(input(InvoiceKind.SALE, 10_000).copy(controlNumber = null), accounts) }
        val purchase = InvoiceAccounting.preview(input(InvoiceKind.PURCHASE, 10_000, rate = 0), accounts)
        assertEquals(10_000L, purchase.totalMinor)
    }

    @Test fun cashInvoiceMustBeFullySettledToday() {
        expectFailure { InvoiceAccounting.preview(input(InvoiceKind.SALE, 10_000, paid = 5_000, condition = PaymentCondition.CASH), accounts) }
    }

    @Test fun duplicateIsScopedByKindAndRegisteredParty() {
        val existing = setOf(InvoiceAccounting.duplicateKey(InvoiceKind.SALE, "client-1", "F-001"))
        expectFailure { InvoiceAccounting.requireUniqueInvoice(existing, InvoiceKind.SALE, "CLIENT-1", "f-001") }
        InvoiceAccounting.requireUniqueInvoice(existing, InvoiceKind.SALE, "client-2", "F-001")
        InvoiceAccounting.requireUniqueInvoice(existing, InvoiceKind.PURCHASE, "client-1", "F-001")
    }

    private fun assertLine(lines: List<PostingLineProposal>, account: String, debit: Long, credit: Long) {
        val line = lines.single { it.accountCode == account }
        assertEquals(debit, line.debitMinor)
        assertEquals(credit, line.creditMinor)
    }

    private fun assertBalanced(lines: List<PostingLineProposal>) =
        assertEquals(lines.sumOf { it.debitMinor }, lines.sumOf { it.creditMinor })

    private fun expectFailure(block: () -> Unit) {
        try { block(); fail("Se esperaba una validación fallida") } catch (_: IllegalArgumentException) { }
    }
}
