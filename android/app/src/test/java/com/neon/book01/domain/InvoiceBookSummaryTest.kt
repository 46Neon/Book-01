package com.neon.book01.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class InvoiceBookSummaryTest {
    @Test fun summarizesGrossInvoiceTotalsInExactMinorUnits() {
        assertEquals(
            InvoiceBookSummary(invoiceCount = 3, grossTotalMinor = 12_345L),
            summarizeInvoiceBookTotals(listOf(1_001L, 2_344L, 9_000L)),
        )
    }

    @Test fun emptyBookHasZeroCountAndTotal() {
        assertEquals(InvoiceBookSummary(0, 0L), summarizeInvoiceBookTotals(emptyList()))
    }

    @Test(expected = ArithmeticException::class)
    fun overflowIsRejectedRatherThanWrapping() {
        summarizeInvoiceBookTotals(listOf(Long.MAX_VALUE, 1L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeInvoiceTotalIsRejected() {
        summarizeInvoiceBookTotals(listOf(100L, -1L))
    }
}
