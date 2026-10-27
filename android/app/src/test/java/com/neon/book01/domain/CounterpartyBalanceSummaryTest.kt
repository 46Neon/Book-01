package com.neon.book01.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CounterpartyBalanceSummaryTest {
    private val today = LocalDate.parse("2026-10-27")

    @Test fun aggregatesOpenAndOverdueBalancesPerCounterpartyFromAppendOnlySettlements() {
        val actual = summarizeCounterpartyBalances(
            invoices = listOf(
                InvoiceBalanceInput("i1", "client-a", 10_000, "2026-10-01"),
                InvoiceBalanceInput("i2", "client-a", 5_000, "2026-11-01"),
                InvoiceBalanceInput("i3", "client-b", 2_000, "2026-10-26"),
                InvoiceBalanceInput("i4", "client-b", 3_000, null),
            ),
            settlements = listOf(SettlementBalanceInput("i1", 4_000), SettlementBalanceInput("i3", 2_000)),
            asOf = today,
        )
        assertEquals(
            listOf(
                CounterpartyBalanceSummary("client-a", 2, 11_000, 6_000, 6_000, 0, 0),
                CounterpartyBalanceSummary("client-b", 2, 3_000, 0, 0, 0, 0),
            ),
            actual,
        )
    }

    @Test fun agingBucketsUseElapsedDaysAfterDueDateAndExcludeTodayAndUnknownDates() {
        val actual = summarizeCounterpartyBalances(
            invoices = listOf(
                InvoiceBalanceInput("today", "p", 100, "2026-10-27"),
                InvoiceBalanceInput("day1", "p", 200, "2026-10-26"),
                InvoiceBalanceInput("day30", "p", 300, "2026-09-27"),
                InvoiceBalanceInput("day31", "p", 400, "2026-09-26"),
                InvoiceBalanceInput("day60", "p", 500, "2026-08-28"),
                InvoiceBalanceInput("day61", "p", 600, "2026-08-27"),
                InvoiceBalanceInput("invalid", "p", 700, "not-a-date"),
            ),
            settlements = listOf(SettlementBalanceInput("day1", 50)),
            asOf = today,
        ).single()
        assertEquals(2_750L, actual.outstandingMinor)
        assertEquals(1_950L, actual.overdueMinor)
        assertEquals(450L, actual.overdue1To30Minor)
        assertEquals(900L, actual.overdue31To60Minor)
        assertEquals(600L, actual.overdueOver60Minor)
    }

    @Test fun fullySettledAndUndatedInvoicesDoNotCreateOverdueBalances() {
        assertEquals(
            listOf(CounterpartyBalanceSummary("p", 2, 0, 0)),
            summarizeCounterpartyBalances(
                listOf(InvoiceBalanceInput("a", "p", 100, "2026-10-01"), InvoiceBalanceInput("b", "p", 50, null)),
                listOf(SettlementBalanceInput("a", 100), SettlementBalanceInput("b", 50)), today,
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSettlementGreaterThanInvoice() {
        summarizeCounterpartyBalances(listOf(InvoiceBalanceInput("a", "p", 100, null)), listOf(SettlementBalanceInput("a", 101)), today)
    }

    @Test(expected = ArithmeticException::class)
    fun rejectsOverflowRatherThanWrapping() {
        summarizeCounterpartyBalances(
            listOf(InvoiceBalanceInput("a", "p", Long.MAX_VALUE, null), InvoiceBalanceInput("b", "p", 1, null)), emptyList(), today,
        )
    }
}
