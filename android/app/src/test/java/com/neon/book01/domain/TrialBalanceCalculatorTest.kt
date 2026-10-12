package com.neon.book01.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class TrialBalanceCalculatorTest {
    private val accounts = listOf(
        ReportAccount("1000", "Caja", "ASSET"),
        ReportAccount("4000", "Ingresos", "INCOME"),
        ReportAccount("5100", "Papelería", "EXPENSE"),
    )

    @Test
    fun reportBalancesAndUsesNormalSideForIncomeAndExpense() {
        val report = TrialBalanceCalculator.calculate(
            accounts,
            listOf(
                ReportPostingLine("5100", debitMinor = 2_850, creditMinor = 0, currencyCode = "USD"),
                ReportPostingLine("1000", debitMinor = 0, creditMinor = 2_850, currencyCode = "USD"),
                ReportPostingLine("1000", debitMinor = 15_000, creditMinor = 0, currencyCode = "USD"),
                ReportPostingLine("4000", debitMinor = 0, creditMinor = 15_000, currencyCode = "USD"),
            ),
        )

        assertTrue(report.isBalanced)
        assertEquals(17_850L, report.totalDebitMinor)
        assertEquals(17_850L, report.totalCreditMinor)
        assertEquals(2_850L, report.lines.first { it.accountCode == "5100" }.signedBalanceMinor)
        assertEquals(15_000L, report.lines.first { it.accountCode == "4000" }.signedBalanceMinor)
        assertEquals(12_150L, report.lines.first { it.accountCode == "1000" }.signedBalanceMinor)
    }

    @Test
    fun refusesToMixForeignCurrencyIntoUsdReport() {
        assertThrows(IllegalArgumentException::class.java) {
            TrialBalanceCalculator.calculate(
                accounts,
                listOf(ReportPostingLine("1000", 100, 0, "EUR")),
            )
        }
    }

    @Test
    fun rejectsAnUnbalancedJournalLine() {
        assertThrows(IllegalArgumentException::class.java) {
            TrialBalanceCalculator.calculate(
                accounts,
                listOf(ReportPostingLine("1000", 100, 100, "USD")),
            )
        }
    }
}
