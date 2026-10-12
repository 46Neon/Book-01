package com.neon.book01.domain

import com.neon.book01.data.EntryWithLines
import com.neon.book01.data.JournalEntryEntity
import com.neon.book01.data.JournalLineEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GeneralLedgerCalculatorTest {
    @Test
    fun sortsPostingsAndCalculatesExactRunningBalance() {
        val report = GeneralLedgerCalculator.calculate(
            accountCode = "1000",
            entries = listOf(
                entry(id = "second", date = "2026-02-02", createdAt = "b", debit = 0, credit = 2_575),
                entry(id = "first", date = "2026-02-01", createdAt = "a", debit = 10_000, credit = 0),
            ),
        )

        assertEquals(listOf("first", "second"), report.rows.map { it.entryId })
        assertEquals(listOf(10_000L, 7_425L), report.rows.map { it.runningBalanceMinor })
        assertEquals(10_000L, report.totalDebitMinor)
        assertEquals(2_575L, report.totalCreditMinor)
        assertEquals(7_425L, report.closingBalanceMinor)
    }

    @Test
    fun excludesOtherCurrenciesAndDraftsFromUsdLedger() {
        val usd = entry(id = "usd", date = "2026-01-01", createdAt = "a", debit = 100, credit = 0)
        val eur = entry(id = "eur", date = "2026-01-02", createdAt = "b", debit = 9_999, credit = 0, currency = "EUR")
        val draft = entry(id = "draft", date = "2026-01-03", createdAt = "c", debit = 50, credit = 0, status = "DRAFT")

        val report = GeneralLedgerCalculator.calculate("1000", listOf(eur, draft, usd))

        assertEquals(listOf("usd"), report.rows.map { it.entryId })
        assertEquals(1, report.excludedOtherCurrencyLineCount)
        assertEquals(listOf("EUR"), report.excludedCurrencyCodes)
        assertEquals(100L, report.closingBalanceMinor)
    }

    @Test
    fun dateRangeIncludesBothBoundariesAndRecalculatesPeriodRunningBalance() {
        val report = GeneralLedgerCalculator.calculate(
            accountCode = "1000",
            entries = listOf(
                entry("before", "2026-01-09", "a", debit = 90, credit = 0),
                entry("before-eur", "2026-01-08", "a0", debit = 9_999, credit = 0, currency = "EUR"),
                entry("start", "2026-01-10", "b", debit = 100, credit = 0),
                entry("inside", "2026-01-11", "c", debit = 0, credit = 25),
                entry("end", "2026-01-12", "d", debit = 40, credit = 0),
                entry("after", "2026-01-13", "e", debit = 500, credit = 0),
            ),
            startDate = "2026-01-10",
            endDate = "2026-01-12",
        )

        assertEquals(listOf("start", "inside", "end"), report.rows.map { it.entryId })
        assertEquals(90L, report.openingBalanceMinor)
        assertEquals(listOf(190L, 165L, 205L), report.rows.map { it.runningBalanceMinor })
        assertEquals(140L, report.totalDebitMinor)
        assertEquals(25L, report.totalCreditMinor)
        assertEquals(205L, report.closingBalanceMinor)
    }

    @Test
    fun emptyOrUnboundedEndpointsIncludeTheRemainingPeriod() {
        val entries = listOf(
            entry("first", "2026-01-01", "a", debit = 1, credit = 0),
            entry("middle", "2026-01-02", "b", debit = 2, credit = 0),
            entry("last", "2026-01-03", "c", debit = 3, credit = 0),
        )

        val fromMiddle = GeneralLedgerCalculator.calculate("1000", entries, startDate = "2026-01-02", endDate = "")
        assertEquals(listOf("middle", "last"), fromMiddle.rows.map { it.entryId })
        assertEquals(1L, fromMiddle.openingBalanceMinor)
        assertEquals(listOf(3L, 6L), fromMiddle.rows.map { it.runningBalanceMinor })
        assertEquals(6L, fromMiddle.closingBalanceMinor)

        val throughMiddle = GeneralLedgerCalculator.calculate("1000", entries, startDate = " ", endDate = "2026-01-02")
        assertEquals(listOf("first", "middle"), throughMiddle.rows.map { it.entryId })
        assertEquals(0L, throughMiddle.openingBalanceMinor)
        assertEquals(3L, throughMiddle.closingBalanceMinor)

        val unbounded = GeneralLedgerCalculator.calculate("1000", entries, startDate = null, endDate = null)
        assertEquals(listOf("first", "middle", "last"), unbounded.rows.map { it.entryId })
        assertEquals(0L, unbounded.openingBalanceMinor)
        assertEquals(6L, unbounded.closingBalanceMinor)
    }

    @Test
    fun periodWithoutMovementsStillShowsPriorBalance() {
        val prior = entry("prior", "2026-01-01", "a", debit = 12_345, credit = 0)

        val report = GeneralLedgerCalculator.calculate(
            accountCode = "1000",
            entries = listOf(prior),
            startDate = "2026-02-01",
            endDate = "2026-02-28",
        )

        assertEquals(emptyList<String>(), report.rows.map { it.entryId })
        assertEquals(12_345L, report.openingBalanceMinor)
        assertEquals(0L, report.totalDebitMinor)
        assertEquals(0L, report.totalCreditMinor)
        assertEquals(12_345L, report.closingBalanceMinor)
    }

    @Test
    fun rejectsInvalidDatesAndReversedDateRange() {
        val entries = listOf(entry("one", "2026-01-01", "a", debit = 1, credit = 0))
        assertThrows(IllegalArgumentException::class.java) {
            GeneralLedgerCalculator.calculate("1000", entries, startDate = "01/01/2026")
        }
        assertThrows(IllegalArgumentException::class.java) {
            GeneralLedgerCalculator.calculate("1000", entries, endDate = "2026-02-30")
        }
        assertThrows(IllegalArgumentException::class.java) {
            GeneralLedgerCalculator.calculate("1000", entries, startDate = "2026-02-02", endDate = "2026-02-01")
        }
    }

    @Test
    fun rejectsUnrepresentableSignedBalanceMagnitude() {
        val firstCredit = entry(id = "first", date = "2026-01-01", createdAt = "a", debit = 0, credit = 1)
        val maxCredit = entry(id = "max", date = "2026-01-02", createdAt = "b", debit = 0, credit = Long.MAX_VALUE)

        assertThrows(ArithmeticException::class.java) {
            GeneralLedgerCalculator.calculate("1000", listOf(firstCredit, maxCredit))
        }
    }

    @Test
    fun throwsOnRunningBalanceOverflow() {
        val huge = entry(id = "huge", date = "2026-01-01", createdAt = "a", debit = Long.MAX_VALUE, credit = 0)
        val extra = entry(id = "extra", date = "2026-01-02", createdAt = "b", debit = 1, credit = 0)

        assertThrows(ArithmeticException::class.java) {
            GeneralLedgerCalculator.calculate("1000", listOf(huge, extra))
        }
    }

    private fun entry(
        id: String,
        date: String,
        createdAt: String,
        debit: Long,
        credit: Long,
        currency: String = "USD",
        status: String = "POSTED",
    ): EntryWithLines = EntryWithLines(
        entry = JournalEntryEntity(
            id = id,
            entryDate = date,
            description = "Movimiento $id",
            kind = "EXPENSE",
            categoryAccountCode = "5100",
            paymentAccountCode = "1000",
            currencyCode = currency,
            amountMinor = maxOf(debit, credit),
            status = status,
            createdAt = createdAt,
        ),
        lines = listOf(
            JournalLineEntity(
                id = "line-$id",
                entryId = id,
                accountCode = "1000",
                debitMinor = debit,
                creditMinor = credit,
                currencyCode = currency,
            ),
        ),
    )
}
