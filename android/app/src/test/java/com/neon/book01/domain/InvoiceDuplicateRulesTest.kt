package com.neon.book01.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoiceDuplicateRulesTest {
    private val existing = listOf(
        InvoiceIdentity("SALE", "customer-1", "INV-204"),
        InvoiceIdentity("PURCHASE", "supplier-1", "ref-7"),
    )

    @Test fun duplicateMatchUsesKindCounterpartyAndTrimmedCaseInsensitiveNumber() {
        assertTrue(InvoiceDuplicateRules.isDuplicate("SALE", "customer-1", "  inv-204 ", existing))
    }

    @Test fun sameNumberForAnotherPartyOrDocumentKindIsNotDuplicate() {
        assertFalse(InvoiceDuplicateRules.isDuplicate("SALE", "customer-2", "INV-204", existing))
        assertFalse(InvoiceDuplicateRules.isDuplicate("PURCHASE", "customer-1", "INV-204", existing))
    }

    @Test fun blankIdentityDoesNotWarn() {
        assertFalse(InvoiceDuplicateRules.isDuplicate("SALE", "customer-1", "   ", existing))
        assertFalse(InvoiceDuplicateRules.isDuplicate("", "customer-1", "INV-204", existing))
    }
}
