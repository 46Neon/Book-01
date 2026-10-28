package com.neon.book01.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoiceBookFilterEmptyStateTest {
    @Test fun showsEmptyStateOnlyWhenPeriodHasInvoicesButFilterHasNoMatches() {
        assertTrue(shouldShowInvoiceBookFilterEmptyState(InvoiceBookFilter.PAID, 3, 0))
        assertFalse(shouldShowInvoiceBookFilterEmptyState(InvoiceBookFilter.PAID, 0, 0))
        assertFalse(shouldShowInvoiceBookFilterEmptyState(InvoiceBookFilter.PAID, 3, 1))
        assertFalse(shouldShowInvoiceBookFilterEmptyState(InvoiceBookFilter.ALL, 3, 0))
    }
}
