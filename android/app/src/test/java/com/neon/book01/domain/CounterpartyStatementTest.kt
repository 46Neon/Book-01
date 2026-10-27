package com.neon.book01.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CounterpartyStatementTest {
    @Test fun buildsExactChronologicalStatementFromInvoiceAndAppendOnlySettlements() {
        val actual = buildCounterpartyStatement(
            "customer",
            listOf(
                StatementInvoiceInput("i1", "customer", "INV-1", "2026-10-01", 10_000),
                StatementInvoiceInput("i2", "other", "INV-2", "2026-10-02", 8_000),
                StatementInvoiceInput("i3", "customer", "INV-3", "2026-10-05", 4_000),
            ),
            listOf(
                StatementSettlementInput("s1", "i1", "2026-10-03", 2_500),
                StatementSettlementInput("s2", "i1", "2026-10-04", 7_500),
            ),
        )
        assertEquals(listOf("2026-10-01", "2026-10-03", "2026-10-04", "2026-10-05"), actual.map { it.date })
        assertEquals(listOf(10_000L, 7_500L, 0L, 4_000L), actual.map { it.balanceAfterMinor })
        assertEquals(listOf(10_000L, -2_500L, -7_500L, 4_000L), actual.map { it.changeMinor })
        assertTrue(actual.all { it.kind == StatementMovementKind.INVOICE || it.kind == StatementMovementKind.SETTLEMENT })
    }

    @Test fun statementAsOfExcludesFutureInvoicesAndSettlements() {
        val asOf = java.time.LocalDate.parse("2026-10-27")
        val rows = buildCounterpartyStatement(
            counterpartyId = "customer",
            invoices = listOf(
                StatementInvoiceInput("current", "customer", "INV-1", "2026-10-01", 10_000),
                StatementInvoiceInput("future", "customer", "INV-2", "2026-10-28", 5_000),
            ),
            settlements = listOf(
                StatementSettlementInput("past", "current", "2026-10-27", 2_000),
                StatementSettlementInput("future-pay", "current", "2026-10-28", 3_000),
            ),
            asOf = asOf,
        )
        assertEquals(listOf("2026-10-01", "2026-10-27"), rows.map { it.date })
        assertEquals(8_000L, rows.last().balanceAfterMinor)
    }

    @Test fun periodUsesInclusiveBoundsAndRebasesRunningBalancesFromPriorMovements() {
        val statement = buildCounterpartyStatement(
            "customer",
            listOf(
                StatementInvoiceInput("before", "customer", "B-1", "2026-09-30", 1_000),
                StatementInvoiceInput("inside", "customer", "I-1", "2026-10-01", 500),
                StatementInvoiceInput("after", "customer", "A-1", "2026-10-04", 300),
            ),
            listOf(StatementSettlementInput("pay", "before", "2026-10-03", 250)),
        )
        val period = counterpartyStatementForPeriod(statement, "2026-10-01", "2026-10-03")
        assertEquals(1_000L, period.openingBalanceMinor)
        assertEquals(listOf("2026-10-01", "2026-10-03"), period.movements.map { it.date })
        assertEquals(listOf(1_500L, 1_250L), period.movements.map { it.balanceAfterMinor })
        assertEquals(1_250L, period.closingBalanceMinor)
    }

    @Test fun emptyPeriodKeepsOpeningAsClosingAndOpenBoundsAreSupported() {
        val statement = buildCounterpartyStatement(
            "c", listOf(StatementInvoiceInput("i", "c", "1", "2026-01-01", 1_000)), emptyList(),
        )
        val empty = counterpartyStatementForPeriod(statement, "2026-02-01", "2026-02-28")
        assertEquals(1_000L, empty.openingBalanceMinor)
        assertTrue(empty.movements.isEmpty())
        assertEquals(1_000L, empty.closingBalanceMinor)
        assertEquals(1_000L, counterpartyStatementForPeriod(statement, throughDate = "2026-01-01").closingBalanceMinor)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsReversedDateRange() {
        counterpartyStatementForPeriod(emptyList(), "2026-02-02", "2026-02-01")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonIsoDateRange() {
        counterpartyStatementForPeriod(emptyList(), "10/01/2026", null)
    }

    @Test fun sameDayInvoicePrecedesItsSettlementAndFinalBalanceIsOutstanding() {
        val actual = buildCounterpartyStatement(
            "vendor",
            listOf(StatementInvoiceInput("i", "vendor", "B-1", "2026-01-01", 3_000)),
            listOf(StatementSettlementInput("s", "i", "2026-01-01", 1_000)),
        )
        assertEquals(listOf(StatementMovementKind.INVOICE, StatementMovementKind.SETTLEMENT), actual.map { it.kind })
        assertEquals(2_000L, actual.last().balanceAfterMinor)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSettlementsAboveInvoiceAmount() {
        buildCounterpartyStatement(
            "c",
            listOf(StatementInvoiceInput("i", "c", "1", "2026-01-01", 100)),
            listOf(StatementSettlementInput("s", "i", "2026-01-02", 101)),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSettlementNotLinkedToAnySavedInvoice() {
        buildCounterpartyStatement("c", emptyList(), listOf(StatementSettlementInput("s", "missing", "2026-01-02", 10)))
    }

    @Test(expected = ArithmeticException::class)
    fun rejectsBalanceOverflowRatherThanWrapping() {
        buildCounterpartyStatement(
            "c",
            listOf(
                StatementInvoiceInput("i1", "c", "1", "2026-01-01", Long.MAX_VALUE),
                StatementInvoiceInput("i2", "c", "2", "2026-01-02", 1),
            ),
            emptyList(),
        )
    }
}
