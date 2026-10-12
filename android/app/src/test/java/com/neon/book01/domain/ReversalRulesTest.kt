package com.neon.book01.domain

import com.neon.book01.data.EntryWithLines
import com.neon.book01.data.JournalEntryEntity
import com.neon.book01.data.JournalLineEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReversalRulesTest {
    @Test
    fun inverseLinesSwapDebitAndCreditWithoutChangingAccountOrCurrency() {
        val original = listOf(
            ReversalRules.JournalLine("5100", debitMinor = 12005, creditMinor = 0, currencyCode = "USD"),
            ReversalRules.JournalLine("1010", debitMinor = 0, creditMinor = 12005, currencyCode = "USD"),
        )
        assertEquals(
            listOf(
                ReversalRules.JournalLine("5100", debitMinor = 0, creditMinor = 12005, currencyCode = "USD"),
                ReversalRules.JournalLine("1010", debitMinor = 12005, creditMinor = 0, currencyCode = "USD"),
            ),
            ReversalRules.inverseLines(original),
        )
        assertEquals(12005L, ReversalRules.inverseLines(original).sumOf { it.debitMinor })
        assertEquals(12005L, ReversalRules.inverseLines(original).sumOf { it.creditMinor })
    }

    @Test
    fun rejectsUnbalancedSourceAndLinesThatCannotBeSafelyInverted() {
        assertThrows(IllegalArgumentException::class.java) {
            ReversalRules.inverseLines(
                listOf(ReversalRules.JournalLine("5100", 500, 0, "USD")),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReversalRules.inverseLines(
                listOf(
                    ReversalRules.JournalLine("5100", 500, 0, "USD"),
                    ReversalRules.JournalLine("1000", 0, 400, "USD"),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReversalRules.inverseLines(
                listOf(ReversalRules.JournalLine("5100", 500, 400, "USD")),
            )
        }
    }

    @Test
    fun rejectsUnpostedEntriesReversalsAndAlreadyReversedOriginals() {
        assertThrows(IllegalArgumentException::class.java) {
            ReversalRules.requireEligible("DRAFT", null, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReversalRules.requireEligible("POSTED", "original", false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReversalRules.requireEligible("POSTED", null, true)
        }
    }

    @Test
    fun reversalPostingsRemainInGeneralLedgerAndTrialBalanceNormally() {
        val original = reportEntry("original", "EXPENSE", null, "2026-01-01", debit = 200, credit = 0)
        val reversal = reportEntry("reversal", "REVERSAL", "original", "2026-01-02", debit = 0, credit = 200)
        val generalLedger = GeneralLedgerCalculator.calculate("1000", listOf(original, reversal))
        assertEquals(listOf("original", "reversal"), generalLedger.rows.map { it.entryId })
        assertEquals(0L, generalLedger.closingBalanceMinor)

        val balance = TrialBalanceCalculator.calculate(
            accounts = listOf(ReportAccount("1000", "Banco", "ASSET"), ReportAccount("5100", "Gasto", "EXPENSE")),
            postings = listOf(
                ReportPostingLine("5100", 200, 0, "USD", "2026-01-01"),
                ReportPostingLine("1000", 0, 200, "USD", "2026-01-01"),
                ReportPostingLine("5100", 0, 200, "USD", "2026-01-02"),
                ReportPostingLine("1000", 200, 0, "USD", "2026-01-02"),
            ),
        )
        assertEquals(400L, balance.totalDebitMinor)
        assertEquals(400L, balance.totalCreditMinor)
        assertEquals(0L, balance.lines.first { it.accountCode == "1000" }.signedBalanceMinor)
    }

    @Test
    fun rejectsInvalidDateAndBlankReason() {
        assertThrows(IllegalArgumentException::class.java) { ReversalRules.validateRequest("2026-02-30", "correction") }
        assertThrows(IllegalArgumentException::class.java) { ReversalRules.validateRequest("2026-02-01", "  ") }
    }

    private fun reportEntry(
        id: String,
        kind: String,
        reversalOf: String?,
        date: String,
        debit: Long,
        credit: Long,
    ) = EntryWithLines(
        entry = JournalEntryEntity(
            id = id,
            entryDate = date,
            description = "test $id",
            kind = kind,
            categoryAccountCode = "5100",
            paymentAccountCode = "1000",
            currencyCode = "USD",
            amountMinor = 200,
            status = "POSTED",
            createdAt = date,
            reversalOfEntryId = reversalOf,
        ),
        lines = listOf(
            JournalLineEntity("line-$id", id, "1000", debit, credit, "USD", "Caja"),
        ),
    )
}
