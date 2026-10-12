package com.neon.book01.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodCloseCalculatorTest {
    private val accounts = listOf(
        PeriodCloseAccount("3000", "EQUITY"),
        PeriodCloseAccount("4000", "INCOME"),
        PeriodCloseAccount("5100", "EXPENSE"),
        PeriodCloseAccount("1000", "ASSET"),
    )

    @Test fun profitAndLossPlanIsBalancedAndUsesExactCents() {
        val profit = PeriodCloseCalculator.calculate(
            accounts,
            listOf(posting("2026-01-01", line("4000", credit = 10_001)), posting("2026-01-31", line("5100", debit = 2_001))),
            "2026-01-01", "2026-01-31", "3000",
        )
        assertEquals(8_000L, profit.netResultMinor)
        assertEquals(8_000L, profit.lines.single { it.accountCode == "3000" }.creditMinor)
        assertEquals(10_001L, profit.lines.sumOf { it.debitMinor })
        assertEquals(10_001L, profit.lines.sumOf { it.creditMinor })

        val loss = PeriodCloseCalculator.calculate(
            accounts,
            listOf(posting("2026-02-01", line("5100", debit = 4_000))),
            "2026-02-01", "2026-02-28", "3000",
        )
        assertEquals(-4_000L, loss.netResultMinor)
        assertEquals(4_000L, loss.lines.single { it.accountCode == "3000" }.debitMinor)
        assertEquals(4_000L, loss.lines.sumOf { it.debitMinor })
        assertEquals(4_000L, loss.lines.sumOf { it.creditMinor })
    }

    @Test fun emptyAndZeroNetPlansDoNotInventZeroLines() {
        val empty = PeriodCloseCalculator.calculate(accounts, emptyList(), "2026-03-01", "2026-03-31", "3000")
        assertEquals(0L, empty.netResultMinor)
        assertTrue(empty.lines.isEmpty())

        val zeroNet = PeriodCloseCalculator.calculate(
            accounts,
            listOf(posting("2026-03-03", line("4000", credit = 3_500)), posting("2026-03-04", line("5100", debit = 3_500))),
            "2026-03-01", "2026-03-31", "3000",
        )
        assertEquals(0L, zeroNet.netResultMinor)
        assertEquals(2, zeroNet.lines.size)
        assertTrue(zeroNet.lines.none { it.debitMinor == 0L && it.creditMinor == 0L })
        assertEquals(zeroNet.lines.sumOf { it.debitMinor }, zeroNet.lines.sumOf { it.creditMinor })
    }

    @Test fun inclusiveDatesAndPostingDateOfReversalAreRespected() {
        val plan = PeriodCloseCalculator.calculate(
            accounts,
            listOf(
                posting("2026-04-01", line("4000", credit = 1_000)),
                posting("2026-04-30", line("4000", debit = 250), kind = "REVERSAL"),
                posting("2026-05-01", line("5100", debit = 500)),
            ),
            "2026-04-01", "2026-04-30", "3000",
        )
        assertEquals(750L, plan.netResultMinor)
    }

    @Test fun rejectsUnbalancedPostedEntriesInThePeriod() {
        val unbalanced = PeriodClosePosting(
            entryDate = "2026-01-10",
            status = "POSTED",
            currencyCode = "USD",
            kind = "SIMPLE",
            lines = listOf(line("4000", credit = 100)),
        )
        assertThrows(IllegalArgumentException::class.java) {
            PeriodCloseCalculator.calculate(accounts, listOf(unbalanced), "2026-01-01", "2026-01-31", "3000")
        }
    }

    @Test fun rejectsInvalidRangesDraftsNonUsdPriorCloseAndNonEquityTarget() {
        val valid = posting("2026-01-01", line("4000", credit = 100))
        fun rejected(entries: List<PeriodClosePosting>, start: String = "2026-01-01", end: String = "2026-01-31", target: String = "3000") =
            runCatching { PeriodCloseCalculator.calculate(accounts, entries, start, end, target) }.isFailure
        assertTrue(rejected(listOf(valid.copy(status = "DRAFT"))))
        assertTrue(rejected(listOf(valid.copy(currencyCode = "VES"))))
        assertTrue(rejected(listOf(valid.copy(kind = "PERIOD_CLOSE"))))
        assertTrue(rejected(listOf(valid), "2026-02-01", "2026-01-31"))
        assertTrue(rejected(listOf(valid), target = "1000"))
        assertTrue(rejected(listOf(valid.copy(lines = listOf(line("4000", credit = 100, currency = "VES"))))))
    }

    private fun posting(
        date: String,
        line: PeriodClosePostingLine,
        kind: String = "INCOME",
        status: String = "POSTED",
    ): PeriodClosePosting {
        val counterLine = PeriodClosePostingLine(
            accountCode = "1000",
            debitMinor = line.creditMinor,
            creditMinor = line.debitMinor,
            currencyCode = line.currencyCode,
        )
        return PeriodClosePosting(date, status, line.currencyCode, kind, listOf(line, counterLine))
    }

    private fun line(code: String, debit: Long = 0, credit: Long = 0, currency: String = "USD") =
        PeriodClosePostingLine(code, debit, credit, currency)
}
