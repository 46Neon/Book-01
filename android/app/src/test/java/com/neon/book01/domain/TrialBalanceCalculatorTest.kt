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
                ReportPostingLine("5100", debitMinor = 2_850, creditMinor = 0, currencyCode = "USD", entryDate = "2026-01-01"),
                ReportPostingLine("1000", debitMinor = 0, creditMinor = 2_850, currencyCode = "USD", entryDate = "2026-01-01"),
                ReportPostingLine("1000", debitMinor = 15_000, creditMinor = 0, currencyCode = "USD", entryDate = "2026-01-02"),
                ReportPostingLine("4000", debitMinor = 0, creditMinor = 15_000, currencyCode = "USD", entryDate = "2026-01-02"),
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
    fun dateRangeIncludesBoundariesAndRecomputesOnlySelectedPeriodTotals() {
        val report = TrialBalanceCalculator.calculate(
            accounts = accounts,
            postings = listOf(
                ReportPostingLine("1000", 9_000, 0, "USD", "2026-01-09"),
                ReportPostingLine("1000", 100, 0, "USD", "2026-01-10"),
                ReportPostingLine("4000", 0, 100, "USD", "2026-01-10"),
                ReportPostingLine("5100", 40, 0, "USD", "2026-01-12"),
                ReportPostingLine("1000", 0, 40, "USD", "2026-01-12"),
                ReportPostingLine("1000", 5_000, 0, "USD", "2026-01-13"),
                // A foreign-currency posting outside the selected dates is not mixed into this report.
                ReportPostingLine("1000", 1, 0, "EUR", "2026-01-13"),
            ),
            startDate = "2026-01-10",
            endDate = "2026-01-12",
        )

        assertTrue(report.isBalanced)
        assertEquals(140L, report.totalDebitMinor)
        assertEquals(140L, report.totalCreditMinor)
        assertEquals(100L, report.lines.first { it.accountCode == "1000" }.totalDebitMinor)
        assertEquals(40L, report.lines.first { it.accountCode == "1000" }.totalCreditMinor)
    }

    @Test
    fun emptyAndUnboundedEndpointsWorkForTrialBalance() {
        val postings = listOf(
            ReportPostingLine("1000", 1, 0, "USD", "2026-01-01"),
            ReportPostingLine("4000", 0, 1, "USD", "2026-01-01"),
            ReportPostingLine("1000", 2, 0, "USD", "2026-01-02"),
            ReportPostingLine("4000", 0, 2, "USD", "2026-01-02"),
            ReportPostingLine("1000", 3, 0, "USD", "2026-01-03"),
            ReportPostingLine("4000", 0, 3, "USD", "2026-01-03"),
        )
        assertEquals(5L, TrialBalanceCalculator.calculate(accounts, postings, startDate = "2026-01-02", endDate = "").totalDebitMinor)
        assertEquals(3L, TrialBalanceCalculator.calculate(accounts, postings, startDate = " ", endDate = "2026-01-02").totalDebitMinor)
        assertEquals(6L, TrialBalanceCalculator.calculate(accounts, postings, startDate = null, endDate = null).totalDebitMinor)
    }

    @Test
    fun rejectsInvalidAndReversedTrialBalanceDateRanges() {
        val postings = listOf(ReportPostingLine("1000", 100, 0, "USD", "2026-01-01"))
        assertThrows(IllegalArgumentException::class.java) {
            TrialBalanceCalculator.calculate(accounts, postings, startDate = "1/1/2026")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TrialBalanceCalculator.calculate(accounts, postings, endDate = "2026-13-01")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TrialBalanceCalculator.calculate(accounts, postings, startDate = "2026-02-02", endDate = "2026-02-01")
        }
    }

    @Test
    fun refusesToMixForeignCurrencyIntoUsdReport() {
        assertThrows(IllegalArgumentException::class.java) {
            TrialBalanceCalculator.calculate(
                accounts,
                listOf(ReportPostingLine("1000", 100, 0, "EUR", "2026-01-01")), 
            )
        }
    }

    @Test
    fun rejectsAnUnbalancedJournalLine() {
        assertThrows(IllegalArgumentException::class.java) {
            TrialBalanceCalculator.calculate(
                accounts,
                listOf(ReportPostingLine("1000", 100, 100, "USD", "2026-01-01")), 
            )
        }
    }
}
