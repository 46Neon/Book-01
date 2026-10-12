package com.neon.book01.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountRulesTest {
    @Test
    fun trimsAndNormalizesNewAccount() {
        val account = AccountRules.validate(" 1200 ", "  Banco ahorro ", "ASSET", cashOrBank = true)
        assertEquals("1200", account.code)
        assertEquals("Banco ahorro", account.name)
        assertEquals("ASSET", account.type)
        assertEquals(true, account.cashOrBank)
    }

    @Test
    fun rejectsDuplicateShapeOrInvalidTypeInputs() {
        assertThrows(IllegalArgumentException::class.java) { AccountRules.validate("12A", "Cuenta", "ASSET", false) }
        assertThrows(IllegalArgumentException::class.java) { AccountRules.validate("123", "X", "ASSET", false) }
        assertThrows(IllegalArgumentException::class.java) { AccountRules.validate("123", "Cuenta", "UNKNOWN", false) }
    }

    @Test
    fun onlyAssetAccountsMayBeMarkedCashOrBank() {
        assertThrows(IllegalArgumentException::class.java) {
            AccountRules.validate("4000", "Ingresos", "INCOME", cashOrBank = true)
        }
    }
}
