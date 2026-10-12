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
        assertEquals(
            UsdMoney.sumMinorUnits(lines.map { it.debitMinor }),
            UsdMoney.sumMinorUnits(lines.map { it.creditMinor }),
        )
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
    fun usdParserAcceptsUsAndSpanishThousandsAndDecimalSeparators() {
        assertEquals(123_456L, UsdMoney.parseMinorUnits("1,234.56"))
        assertEquals(123_456L, UsdMoney.parseMinorUnits("1.234,56"))
        assertEquals(2_850L, UsdMoney.parseMinorUnits("28,50"))
        assertEquals(2_850L, UsdMoney.parseMinorUnits("USD 28.50"))
    }

    @Test
    fun usdParserRejectsZeroNegativeAndMoreThanTwoFractionDigits() {
        assertThrows(IllegalArgumentException::class.java) { UsdMoney.parseMinorUnits("0") }
        assertThrows(IllegalArgumentException::class.java) { UsdMoney.parseMinorUnits("-1.00") }
        assertThrows(IllegalArgumentException::class.java) { UsdMoney.parseMinorUnits("1.005") }
        assertThrows(IllegalArgumentException::class.java) { UsdMoney.parseMinorUnits("1,234") }
    }

    @Test
    fun parserRejectsMalformedCurrencyPrefixesAndEmbeddedSpaces() {
        assertThrows(IllegalArgumentException::class.java) { UsdMoney.parseMinorUnits("USD USD 10.00") }
        assertThrows(IllegalArgumentException::class.java) { UsdMoney.parseMinorUnits("1 234.56") }
        assertThrows(IllegalArgumentException::class.java) { UsdMoney.parseMinorUnits("10.00 USD") }
    }

    @Test
    fun parserDoesNotOverflowLongMinorUnits() {
        assertThrows(ArithmeticException::class.java) {
            UsdMoney.parseMinorUnits("92233720368547758.08")
        }
    }

    @Test
    fun exactSumAndSubtractionRejectOverflow() {
        assertThrows(ArithmeticException::class.java) {
            UsdMoney.sumMinorUnits(listOf(Long.MAX_VALUE, 1L))
        }
        assertThrows(ArithmeticException::class.java) {
            UsdMoney.subtractMinorUnits(Long.MIN_VALUE, 1L)
        }
    }

    @Test
    fun accountingRulesRejectForeignAndMixedCurrencies() {
        assertThrows(IllegalArgumentException::class.java) {
            AccountingRules.simpleCashEntry(
                kind = EntryKind.EXPENSE,
                description = "Pago",
                categoryAccountCode = "5100",
                categoryAccountType = "EXPENSE",
                paymentAccountCode = "1000",
                paymentAccountType = "ASSET",
                amountMinor = 100,
                currencyCode = "VES",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AccountingRules.requireBalanced(
                listOf(
                    PostingLine("1000", debitMinor = 100, creditMinor = 0, currencyCode = "USD"),
                    PostingLine("4000", debitMinor = 0, creditMinor = 100, currencyCode = "VES"),
                ),
            )
        }
    }

    @Test
    fun usdFormattingIsExplicitAndKeepsTwoDecimals() {
        assertEquals("US$ 1.234,56", UsdMoney.format(123_456L))
        assertEquals("US$ 0,05", UsdMoney.format(5L))
    }
}
