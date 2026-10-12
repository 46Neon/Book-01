package com.neon.book01.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class OperatingSummaryCalculatorTest {
    @Test
    fun reversalLinesCancelIncomeAndExpenseActivityAndForeignCurrencyIsExcluded() {
        val summary = OperatingSummaryCalculator.calculate(
            accounts = listOf(
                ReportAccount("1000", "Caja", "ASSET"),
                ReportAccount("4000", "Ingresos", "INCOME"),
                ReportAccount("5100", "Papelería", "EXPENSE"),
            ),
            postings = listOf(
                ReportPostingLine("4000", debitMinor = 0, creditMinor = 10_000, currencyCode = "USD", entryDate = "2026-01-01"),
                ReportPostingLine("4000", debitMinor = 10_000, creditMinor = 0, currencyCode = "USD", entryDate = "2026-01-02"),
                ReportPostingLine("5100", debitMinor = 5_000, creditMinor = 0, currencyCode = "USD", entryDate = "2026-01-03"),
                ReportPostingLine("5100", debitMinor = 0, creditMinor = 1_250, currencyCode = "USD", entryDate = "2026-01-04"),
                ReportPostingLine("5100", debitMinor = 9_999, creditMinor = 0, currencyCode = "EUR", entryDate = "2026-01-05"),
            ),
        )

        assertEquals(0L, summary.incomeMinor)
        assertEquals(3_750L, summary.expenseMinor)
    }
}
