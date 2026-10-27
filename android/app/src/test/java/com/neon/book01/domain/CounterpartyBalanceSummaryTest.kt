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
                CounterpartyBalanceSummary("client-a", 2, 11_000, 6_000),
                CounterpartyBalanceSummary("client-b", 2, 3_000, 0),
            ),
            actual,
        )
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
