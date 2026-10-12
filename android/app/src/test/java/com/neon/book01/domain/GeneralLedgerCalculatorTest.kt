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
