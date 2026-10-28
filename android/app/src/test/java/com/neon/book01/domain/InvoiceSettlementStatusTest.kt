package com.neon.book01.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class InvoiceSettlementStatusTest {
    private val today = LocalDate.parse("2026-10-27")

    @Test fun unpaidInvoiceWithoutDueDateRemainsOpen() {
        assertEquals(InvoiceSettlementStatus.OPEN, invoiceSettlementStatus(10_000, 0, null, today))
    }

    @Test fun fullySettledInvoiceIsPaidEvenIfDueDatePassed() {
        assertEquals(InvoiceSettlementStatus.PAID, invoiceSettlementStatus(10_000, 10_000, "2026-10-01", today))
    }

    @Test fun partialPaymentWithFutureDueDateIsPartial() {
        assertEquals(InvoiceSettlementStatus.PARTIALLY_PAID, invoiceSettlementStatus(10_000, 2_500, "2026-11-01", today))
    }

    @Test fun outstandingPastDueInvoiceIsOverdue() {
        assertEquals(InvoiceSettlementStatus.OVERDUE, invoiceSettlementStatus(10_000, 0, "2026-10-26", today))
    }

    @Test fun partialPaymentDoesNotHideOverdueBalance() {
        assertEquals(InvoiceSettlementStatus.OVERDUE, invoiceSettlementStatus(10_000, 2_500, "2026-10-26", today))
    }

    @Test fun dueTodayIsNotOverdue() {
        assertEquals(InvoiceSettlementStatus.OPEN, invoiceSettlementStatus(10_000, 0, "2026-10-27", today))
    }

    @Test fun malformedDueDateDoesNotCreateOverdueStatus() {
        assertEquals(InvoiceSettlementStatus.OPEN, invoiceSettlementStatus(10_000, 0, "not-a-date", today))
    }

    @Test fun overpaymentCannotCreateNegativePendingBalance() {
        assertEquals(InvoiceSettlementStatus.PAID, invoiceSettlementStatus(10_000, 12_000, null, today))
    }
}
