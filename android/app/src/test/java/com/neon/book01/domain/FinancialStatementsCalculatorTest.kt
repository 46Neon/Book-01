package com.neon.book01.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialStatementsCalculatorTest {
    private val accounts = listOf(
        ReportAccount("1000", "Cash", "ASSET"),
        ReportAccount("2000", "Payables", "LIABILITY"),
        ReportAccount("3000", "Capital", "EQUITY"),
        ReportAccount("4000", "Revenue", "INCOME"),
        ReportAccount("5000", "Expense", "EXPENSE"),
    )

    @Test fun incomeStatementIncludesBothDateBoundariesAndUsesExactCents() {
        val report = FinancialStatementsCalculator.incomeStatement(
            accounts,
            listOf(
                line("4000", credit = 10_001, date = "2026-03-01"),
                line("4000", credit = 20_009, date = "2026-03-31"),
                line("4000", credit = 50_000, date = "2026-04-01"),
                line("5000", debit = 1_201, date = "2026-03-31"),
            ),
            startDate = "2026-03-01",
            endDate = "2026-03-31",
        )
        assertEquals(30_010L, report.totalIncomeMinor)
        assertEquals(1_201L, report.totalExpensesMinor)
        assertEquals(28_809L, report.netIncomeMinor)
    }

    @Test fun postedReversalLinesOffsetTheOriginalOnTheirPostingDate() {
        val postings = listOf(
            line("4000", credit = 12_345, date = "2026-06-01"),
            line("4000", debit = 12_345, date = "2026-06-20"), // exact posted reversal
            line("5000", debit = 2_000, date = "2026-06-20"),
        )
        val wholeRange = FinancialStatementsCalculator.incomeStatement(accounts, postings, "2026-06-01", "2026-06-20")
        assertEquals(0L, wholeRange.totalIncomeMinor)
        assertEquals(-2_000L, wholeRange.netIncomeMinor)
        val beforeReversal = FinancialStatementsCalculator.incomeStatement(accounts, postings, null, "2026-06-19")
        assertEquals(12_345L, beforeReversal.netIncomeMinor)
    }

    @Test fun draftsAndNonUsdAreExplicitlyExcluded() {
        val report = FinancialStatementsCalculator.incomeStatement(
            accounts,
            listOf(
                line("4000", credit = 5_000),
                line("4000", credit = 99_000, status = "DRAFT"),
                line("4000", credit = 77_000, currency = "EUR"),
            ),
        )
        assertEquals(5_000L, report.totalIncomeMinor)
        assertEquals(1, report.excludedDraftLineCount)
        assertEquals(1, report.excludedOtherCurrencyLineCount)
    }

    @Test fun malformedOrReversedRangesAreRejected() {
        assertIllegalArgument { FinancialStatementsCalculator.incomeStatement(accounts, emptyList(), "2026-02-30", null) }
        assertIllegalArgument { FinancialStatementsCalculator.incomeStatement(accounts, emptyList(), "2026-03-02", "2026-03-01") }
        assertIllegalArgument { FinancialStatementsCalculator.incomeStatement(accounts, emptyList(), "2026/03/01", null) }
    }

    @Test fun balanceSheetIncludesCumulativeCurrentResultAndReconciles() {
        val report = FinancialStatementsCalculator.balanceSheet(
            accounts,
            listOf(
                line("1000", debit = 10_000, date = "2026-01-01"),
                line("3000", credit = 10_000, date = "2026-01-01"),
                line("1000", debit = 2_000, date = "2026-01-31"),
                line("4000", credit = 2_000, date = "2026-01-31"),
                line("5000", debit = 300, date = "2026-02-01"),
                line("1000", credit = 300, date = "2026-02-01"),
                line("1000", debit = 1_000, date = "2026-03-01"), // after as-of date
                line("4000", credit = 1_000, date = "2026-03-01"),
            ),
            asOfDate = "2026-02-01",
        )
        assertEquals(11_700L, report.totalAssetsMinor)
        assertEquals(0L, report.totalLiabilitiesMinor)
        assertEquals(10_000L, report.totalEquityMinor)
        assertEquals(1_700L, report.accumulatedResultMinor)
        assertEquals(0L, report.reconciliationDifferenceMinor)
        assertTrue(report.isReconciled)
    }

    @Test fun unbalancedPostingsAreReportedRatherThanSilentlyReclassified() {
        val report = FinancialStatementsCalculator.balanceSheet(
            accounts,
            listOf(line("1000", debit = 2_500, date = "2026-01-01")),
            asOfDate = "2026-01-01",
        )
        assertEquals(2_500L, report.totalAssetsMinor)
        assertEquals(0L, report.accumulatedResultMinor)
        assertEquals(2_500L, report.reconciliationDifferenceMinor)
        assertFalse(report.isReconciled)
    }

    @Test fun balanceSheetReversalAndExclusionsAreAppliedThroughInclusiveAsOfDate() {
        val report = FinancialStatementsCalculator.balanceSheet(
            accounts,
            listOf(
                line("1000", debit = 8_000, date = "2026-01-01"),
                line("3000", credit = 8_000, date = "2026-01-01"),
                line("1000", credit = 8_000, date = "2026-01-10"),
                line("3000", debit = 8_000, date = "2026-01-10"),
                line("1000", debit = 50_000, status = "DRAFT"),
                line("1000", debit = 60_000, currency = "EUR"),
            ),
            asOfDate = "2026-01-10",
        )
        assertEquals(0L, report.totalAssetsMinor)
        assertEquals(0L, report.totalEquityMinor)
        assertTrue(report.isReconciled)
        assertEquals(1, report.excludedDraftLineCount)
        assertEquals(1, report.excludedOtherCurrencyLineCount)
        assertIllegalArgument { FinancialStatementsCalculator.balanceSheet(accounts, emptyList(), "2026-02-30") }
    }

    @Test fun closedPeriodIncomeStatementStaysContinuousAndBalanceSheetDoesNotDoubleCountEarnings() {
        val postings = listOf(
            line("1000", debit = 10_000, date = "2026-01-01"),
            line("3000", credit = 10_000, date = "2026-01-01"),
            line("1000", debit = 5_000, date = "2026-03-10"),
            line("4000", credit = 5_000, date = "2026-03-10"),
            line("5000", debit = 1_000, date = "2026-03-20"),
            line("1000", credit = 1_000, date = "2026-03-20"),
            line("4000", debit = 5_000, date = "2026-03-31", kind = "PERIOD_CLOSE"),
            line("5000", credit = 1_000, date = "2026-03-31", kind = "PERIOD_CLOSE"),
            line("3000", credit = 4_000, date = "2026-03-31", kind = "PERIOD_CLOSE"),
        )
        val close = listOf(ClosedPeriodRange("2026-03-01", "2026-03-31"))
        val income = FinancialStatementsCalculator.incomeStatement(accounts, postings, "2026-03-01", "2026-03-31")
        assertEquals(5_000L, income.totalIncomeMinor)
        assertEquals(1_000L, income.totalExpensesMinor)
        assertEquals(4_000L, income.netIncomeMinor)
        val balance = FinancialStatementsCalculator.balanceSheet(accounts, postings, "2026-03-31", close)
        assertEquals(14_000L, balance.totalAssetsMinor)
        assertEquals(14_000L, balance.totalEquityMinor)
        assertEquals(0L, balance.accumulatedResultMinor)
        assertEquals(0L, balance.reconciliationDifferenceMinor)
        assertTrue(balance.isReconciled)
    }

    @Test fun closedPeriodWithLossTransfersDebitToEquityWithoutChangingStatementResult() {
        val postings = listOf(
            line("1000", debit = 10_000, date = "2026-01-01"),
            line("3000", credit = 10_000, date = "2026-01-01"),
            line("5000", debit = 2_500, date = "2026-05-05"),
            line("1000", credit = 2_500, date = "2026-05-05"),
            line("5000", credit = 2_500, date = "2026-05-31", kind = "PERIOD_CLOSE"),
            line("3000", debit = 2_500, date = "2026-05-31", kind = "PERIOD_CLOSE"),
        )
        val income = FinancialStatementsCalculator.incomeStatement(accounts, postings, "2026-05-01", "2026-05-31")
        assertEquals(-2_500L, income.netIncomeMinor)
        val balance = FinancialStatementsCalculator.balanceSheet(
            accounts, postings, "2026-05-31", listOf(ClosedPeriodRange("2026-05-01", "2026-05-31")),
        )
        assertEquals(7_500L, balance.totalAssetsMinor)
        assertEquals(7_500L, balance.totalEquityMinor)
        assertEquals(0L, balance.accumulatedResultMinor)
        assertEquals(0L, balance.reconciliationDifferenceMinor)
    }

    private fun line(
        account: String,
        debit: Long = 0,
        credit: Long = 0,
        date: String = "2026-01-01",
        status: String = "POSTED",
        currency: String = "USD",
        kind: String = "SIMPLE",
    ) = StatementPostingLine(account, debit, credit, currency, date, status, kind)

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
