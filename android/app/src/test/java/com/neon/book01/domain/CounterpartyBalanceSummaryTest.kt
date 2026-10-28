package com.neon.book01.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CounterpartyBalanceSummaryTest {
    private val today = LocalDate.parse("2026-10-27")

    @Test fun aggregatesOpenAndOverdueBalancesPerCounterpartyFromAppendOnlySettlements() {
        val actual = summarizeCounterpartyBalances(
            invoices = listOf(
                InvoiceBalanceInput("i1", "client-a", 10_000, "2026-09-01", "2026-10-01"),
                InvoiceBalanceInput("i2", "client-a", 5_000, "2026-10-01", "2026-11-01"),
                InvoiceBalanceInput("i3", "client-b", 2_000, "2026-09-01", "2026-10-26"),
                InvoiceBalanceInput("i4", "client-b", 3_000, "2026-10-01", null),
            ),
            settlements = listOf(SettlementBalanceInput("i1", 4_000, "2026-10-27"), SettlementBalanceInput("i3", 2_000, "2026-10-27")),
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
                InvoiceBalanceInput("today", "p", 100, "2026-10-01", "2026-10-27"),
                InvoiceBalanceInput("day1", "p", 200, "2026-10-01", "2026-10-26"),
                InvoiceBalanceInput("day30", "p", 300, "2026-10-01", "2026-09-27"),
                InvoiceBalanceInput("day31", "p", 400, "2026-10-01", "2026-09-26"),
                InvoiceBalanceInput("day60", "p", 500, "2026-10-01", "2026-08-28"),
                InvoiceBalanceInput("day61", "p", 600, "2026-10-01", "2026-08-27"),
                InvoiceBalanceInput("invalid", "p", 700, "2026-10-01", "not-a-date"),
            ),
            settlements = listOf(SettlementBalanceInput("day1", 50, "2026-10-27")),
            asOf = today,
        ).single()
        assertEquals(2_750L, actual.outstandingMinor)
        assertEquals(1_950L, actual.overdueMinor)
        assertEquals(450L, actual.overdue1To30Minor)
        assertEquals(900L, actual.overdue31To60Minor)
        assertEquals(600L, actual.overdueOver60Minor)
    }

    @Test fun futureDatedSettlementsDoNotReduceAsOfBalancesOrOverdueAging() {
        val actual = summarizeCounterpartyBalances(
            invoices = listOf(InvoiceBalanceInput("i", "client", 10_000, "2026-09-20", "2026-10-20")),
            settlements = listOf(
                SettlementBalanceInput("i", 2_000, "2026-10-27"),
                SettlementBalanceInput("i", 8_000, "2026-10-28"),
            ),
            asOf = today,
        ).single()
        assertEquals(8_000L, actual.outstandingMinor)
        assertEquals(8_000L, actual.overdueMinor)
    }

    @Test fun settledMinorAsOfExcludesFutureDatedAmounts() {
        assertEquals(2_000L, settledMinorAsOf(
            "i",
            listOf(SettlementBalanceInput("i", 2_000, "2026-10-27"), SettlementBalanceInput("i", 8_000, "2026-10-28")),
            today,
        ))
    }

    @Test fun futureDatedInvoicesAreNotIncludedInBalancesBeforeIssueDate() {
        val actual = summarizeCounterpartyBalances(
            invoices = listOf(
                InvoiceBalanceInput("issued", "p", 1_000, "2026-10-01", null),
                InvoiceBalanceInput("future", "p", 9_000, "2026-10-28", "2026-11-10"),
            ),
            settlements = emptyList(),
            asOf = today,
        )
        assertEquals(listOf(CounterpartyBalanceSummary("p", 1, 1_000, 0)), actual)
    }

    @Test fun fullySettledAndUndatedInvoicesDoNotCreateOverdueBalances() {
        assertEquals(
            listOf(CounterpartyBalanceSummary("p", 2, 0, 0)),
            summarizeCounterpartyBalances(
                listOf(InvoiceBalanceInput("a", "p", 100, "2026-10-01", "2026-10-01"), InvoiceBalanceInput("b", "p", 50, "2026-10-01", null)),
                listOf(SettlementBalanceInput("a", 100, "2026-10-27"), SettlementBalanceInput("b", 50, "2026-10-27")), today,
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSettlementGreaterThanInvoice() {
        summarizeCounterpartyBalances(listOf(InvoiceBalanceInput("a", "p", 100, "2026-10-01", null)), listOf(SettlementBalanceInput("a", 101, "2026-10-27")), today)
    }

    @Test(expected = ArithmeticException::class)
    fun rejectsOverflowRatherThanWrapping() {
        summarizeCounterpartyBalances(
            listOf(InvoiceBalanceInput("a", "p", Long.MAX_VALUE, "2026-10-01", null), InvoiceBalanceInput("b", "p", 1, "2026-10-01", null)), emptyList(), today,
        )
    }

    @Test fun prioritizesPastDueThenLargestBalanceAndUsesPartyNameForTies() {
        val summaries = listOf(
            CounterpartyBalanceSummary("z", 1, 500, 0),
            CounterpartyBalanceSummary("b", 1, 900, 300),
            CounterpartyBalanceSummary("a", 1, 900, 300),
            CounterpartyBalanceSummary("c", 1, 1_000, 100),
        )
        val ordered = sortCounterpartyBalanceSummaries(summaries) { id -> mapOf("a" to "Beta", "b" to "Alpha", "c" to "Gamma", "z" to "Zeta")[id]!! }
        assertEquals(listOf("b", "a", "c", "z"), ordered.map { it.counterpartyId })
    }
}
