package com.neon.book01.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoiceBookFilterEmptyStateTest {
    @Test fun distinguishesFutureOnlyPeriodsFromIssuedInvoicesWithoutStatusMatches() {
        assertFalse(shouldShowInvoiceBookFilterEmptyState(InvoiceBookFilter.PAID, 0, 0))
        assertFalse(shouldShowInvoiceBookFilterEmptyState(InvoiceBookFilter.PAID, 2, 0))
        assertTrue(shouldExplainFutureInvoicesExcludedFromStatusFilter(InvoiceBookFilter.PAID, 2, 0))
        assertFalse(shouldExplainFutureInvoicesExcludedFromStatusFilter(InvoiceBookFilter.ALL, 2, 0))

        assertTrue(shouldShowInvoiceBookFilterEmptyState(InvoiceBookFilter.PAID, 2, 0))
        assertFalse(shouldShowInvoiceBookFilterEmptyState(InvoiceBookFilter.PAID, 2, 1))
        assertFalse(shouldExplainFutureInvoicesExcludedFromStatusFilter(InvoiceBookFilter.PAID, 3, 2))
        assertFalse(shouldShowInvoiceBookFilterEmptyState(InvoiceBookFilter.ALL, 2, 0))
    }
}
