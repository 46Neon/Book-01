package com.neon.book01.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountingRulesTest {
    @Test
    fun expensePostsDebitToExpenseAndCreditToCash() {
        val lines = AccountingRules.simpleCashEntry(
            kind = EntryKind.EXPENSE,
            description = "Papelería",
            categoryAccountCode = "5100",
            categoryAccountType = "EXPENSE",
            paymentAccountCode = "1000",
            paymentAccountType = "ASSET",
            amountMinor = 2_850,
            currencyCode = "USD",
        )

        assertEquals(2, lines.size)
        assertEquals(2_850L, lines[0].debitMinor)
        assertEquals(0L, lines[0].creditMinor)
        assertEquals(0L, lines[1].debitMinor)
        assertEquals(2_850L, lines[1].creditMinor)
    }

    @Test
    fun incomePostsDebitToBankAndCreditToIncome() {
        val lines = AccountingRules.simpleCashEntry(
            kind = EntryKind.INCOME,
            description = "Cobro de cliente",
            categoryAccountCode = "4000",
            categoryAccountType = "INCOME",
            paymentAccountCode = "1010",
            paymentAccountType = "ASSET",
            amountMinor = 15_000,
            currencyCode = "USD",
        )

        assertEquals(15_000L, lines[0].debitMinor)
        assertEquals(15_000L, lines[1].creditMinor)
        assertEquals(lines.sumOf { it.debitMinor }, lines.sumOf { it.creditMinor })
    }

    @Test
    fun rejectsCategoryWithWrongAccountingType() {
        assertThrows(IllegalArgumentException::class.java) {
            AccountingRules.simpleCashEntry(
                kind = EntryKind.EXPENSE,
                description = "Pago",
                categoryAccountCode = "4000",
                categoryAccountType = "INCOME",
                paymentAccountCode = "1000",
                paymentAccountType = "ASSET",
                amountMinor = 100,
                currencyCode = "USD",
            )
        }
    }

    @Test
    fun amountParserAcceptsCommaAndDotWithTwoDecimals() {
        assertEquals(2_850L, AccountingRules.parseMinorUnits("28,50"))
        assertEquals(2_850L, AccountingRules.parseMinorUnits("28.50"))
    }

    @Test
    fun amountParserRejectsZeroAndMoreThanTwoDecimals() {
        assertThrows(IllegalArgumentException::class.java) { AccountingRules.parseMinorUnits("0") }
        assertThrows(ArithmeticException::class.java) { AccountingRules.parseMinorUnits("1.005") }
    }
}
