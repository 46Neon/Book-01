package com.neon.book01.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountNatureRulesTest {
    @Test fun debitNatureTypesIncreaseByDebit() {
        listOf("ASSET", "EXPENSE", "COST").forEach { type ->
            assertEquals("Debe", AccountNatureRules.increaseSideLabel(type))
            assertEquals("Haber", AccountNatureRules.decreaseSideLabel(type))
        }
    }

    @Test fun creditNatureTypesIncreaseByCredit() {
        listOf("LIABILITY", "EQUITY", "INCOME").forEach { type ->
            assertEquals("Haber", AccountNatureRules.increaseSideLabel(type))
            assertEquals("Debe", AccountNatureRules.decreaseSideLabel(type))
        }
    }

    @Test fun rejectsUnknownType() {
        assertThrows(IllegalArgumentException::class.java) { AccountNatureRules.increaseSideLabel("UNKNOWN") }
        assertThrows(IllegalArgumentException::class.java) { AccountNatureRules.decreaseSideLabel("UNKNOWN") }
    }
}
