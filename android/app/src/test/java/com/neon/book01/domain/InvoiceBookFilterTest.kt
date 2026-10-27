package com.neon.book01.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoiceBookFilterTest {
    @Test fun allIncludesEveryBalanceState() {
        InvoiceSettlementStatus.values().forEach { status ->
            assertTrue(matchesInvoiceBookFilter(InvoiceBookFilter.ALL, status))
        }
    }

    @Test fun eachFilterMatchesOnlyItsStatus() {
        val filters = mapOf(
            InvoiceBookFilter.OPEN to InvoiceSettlementStatus.OPEN,
            InvoiceBookFilter.PARTIALLY_PAID to InvoiceSettlementStatus.PARTIALLY_PAID,
            InvoiceBookFilter.OVERDUE to InvoiceSettlementStatus.OVERDUE,
            InvoiceBookFilter.PAID to InvoiceSettlementStatus.PAID,
        )
        filters.forEach { (filter, expected) ->
            InvoiceSettlementStatus.values().forEach { status ->
                if (status == expected) assertTrue(matchesInvoiceBookFilter(filter, status))
                else assertFalse(matchesInvoiceBookFilter(filter, status))
            }
        }
    }
}
