package com.neon.book01.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LedgerBackupValidatorTest {
    @Test fun acceptsLegacyNonUsdCurrencyWithoutConversion() {
        val legacy = validSnapshot().copy(
            bookSettings = BookSettingsEntity(1, "Libro heredado", "USD"),
            journalEntries = listOf(entry("entry-1", "VES").copy(amountMinor = 125L)),
            journalLines = listOf(
                JournalLineEntity("line-1", "entry-1", "1000", 125L, 0L, "VES", "Caja anterior"),
                JournalLineEntity("line-2", "entry-1", "4000", 0L, 125L, "VES", "Ingreso anterior"),
            ),
        )
        val preview = LedgerBackupValidator.validate(legacy)
        assertEquals("Libro heredado", preview.bookName)
    }

    @Test fun rejectsDuplicatePrimaryKeysAndReversalTargets() {
        val valid = validSnapshot()
        val duplicateEntryId = valid.copy(journalEntries = listOf(entry("entry-1"), entry("entry-1")))
        assertThrows(IllegalArgumentException::class.java) { LedgerBackupValidator.validate(duplicateEntryId) }

        val duplicates = valid.copy(
            journalEntries = listOf(entry("entry-1"), entry("reverse-1", reversal = "entry-1"), entry("reverse-2", reversal = "entry-1")),
        )
        assertThrows(IllegalArgumentException::class.java) { LedgerBackupValidator.validate(duplicates) }
    }

    @Test fun acceptsAnAuthenticatedNormalReversalWithExactSwappedLines() {
        LedgerBackupValidator.validate(authenticated(reversalSnapshot()))
    }

    @Test fun rejectsAuthenticatedReversalOfDraft() {
        assertRejected(reversalSnapshot(originalStatus = "DRAFT"))
    }

    @Test fun rejectsAuthenticatedReversalWithWrongInverseAmount() {
        val valid = reversalSnapshot()
        val wrongAmount = valid.copy(
            journalEntries = valid.journalEntries.map { if (it.id == "reverse-1") it.copy(amountMinor = 99L) else it },
            journalLines = valid.journalLines.map { line ->
                when (line.id) {
                    "reverse-cash-1" -> line.copy(creditMinor = 74L)
                    "reverse-income" -> line.copy(debitMinor = 99L)
                    else -> line
                }
            },
        )
        assertRejected(wrongAmount)
    }

    @Test fun rejectsAuthenticatedReversalWithWrongInverseAccount() {
        val valid = reversalSnapshot()
        assertRejected(valid.copy(journalLines = valid.journalLines.map { line ->
            if (line.id == "reverse-cash-2") line.copy(accountCode = "4000") else line
        }))
    }

    @Test fun rejectsAuthenticatedReversalWithWrongInverseCurrency() {
        val valid = reversalSnapshot()
        assertRejected(valid.copy(
            journalEntries = valid.journalEntries.map { if (it.id == "reverse-1") it.copy(currencyCode = "VES") else it },
            journalLines = valid.journalLines.map { line ->
                if (line.entryId == "reverse-1") line.copy(currencyCode = "VES") else line
            },
        ))
    }

    @Test fun acceptsAuthenticatedProfitAndLossPeriodClosesEvenWhenRetainedAccountIsNowInactive() {
        val profit = closeSnapshot(income = true)
        val historicalProfit = profit.copy(accounts = profit.accounts.map { if (it.code == "3100") it.copy(active = false) else it })
        LedgerBackupValidator.validate(authenticated(historicalProfit))
        LedgerBackupValidator.validate(authenticated(closeSnapshot(income = false)))
    }

    @Test fun acceptsAuthenticatedMetadataOnlyCloseWithZeroActivity() {
        val empty = validSnapshot().copy(journalEntries = emptyList(), journalLines = emptyList())
        val snapshot = empty.copy(periodCloses = listOf(
            PeriodCloseEntity("close-1", "2024-01-01", "2024-01-31", "3100", 0L, null, "t1"),
        ))
        LedgerBackupValidator.validate(authenticated(snapshot))
    }

    @Test fun rejectsAuthenticatedNonzeroCloseWithoutTransferEntry() {
        val valid = closeSnapshot(income = true)
        assertRejected(valid.copy(
            journalEntries = valid.journalEntries.filterNot { it.id == "close-entry" },
            journalLines = valid.journalLines.filterNot { it.entryId == "close-entry" },
            periodCloses = listOf(PeriodCloseEntity("close-1", "2024-01-01", "2024-01-31", "3100", 100L, null, "t1")),
        ))
    }

    @Test fun rejectsAuthenticatedCloseWithWrongTransferLines() {
        val valid = closeSnapshot(income = true)
        assertRejected(valid.copy(journalLines = valid.journalLines.map { line ->
            if (line.id == "close-income") line.copy(accountCode = "5000") else line
        }))
    }

    @Test fun rejectsAuthenticatedCloseWithWrongNetResult() {
        val valid = closeSnapshot(income = true)
        assertRejected(valid.copy(periodCloses = valid.periodCloses.map { it.copy(netResultMinor = 99L) }))
    }

    @Test fun rejectsDuplicateCloseLinksAndUnbalancedEntries() {
        val valid = validSnapshot()
        val duplicateLinks = valid.copy(
            journalEntries = listOf(entry("entry-1"), entry("close-entry", kind = "PERIOD_CLOSE", date = "2024-01-31")),
            journalLines = valid.journalLines + listOf(
                JournalLineEntity("close-line-1", "close-entry", "3100", 1L, 0L, "USD", "Retenidas"),
                JournalLineEntity("close-line-2", "close-entry", "4000", 0L, 1L, "USD", "Ingresos"),
            ),
            periodCloses = listOf(
                PeriodCloseEntity("close-1", "2024-01-01", "2024-01-31", "3100", 0, "close-entry", "t1"),
                PeriodCloseEntity("close-2", "2024-02-01", "2024-02-29", "3100", 0, "close-entry", "t2"),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) { LedgerBackupValidator.validate(duplicateLinks) }

        val unbalanced = valid.copy(journalLines = valid.journalLines.mapIndexed { index, line ->
            if (index == 0) line.copy(debitMinor = 2L) else line
        })
        assertThrows(IllegalArgumentException::class.java) { LedgerBackupValidator.validate(unbalanced) }
    }

    private fun assertRejected(snapshot: LedgerBackupSnapshot) {
        assertThrows(IllegalArgumentException::class.java) {
            LedgerBackupValidator.validate(authenticated(snapshot))
        }
    }

    /** Round-tripping through AES-GCM makes each malformed fixture an authenticated snapshot. */
    private fun authenticated(snapshot: LedgerBackupSnapshot): LedgerBackupSnapshot {
        val passphrase = "authenticated validator fixture".toCharArray()
        val encrypted = LedgerBackupCodec.encrypt(snapshot, passphrase)
        return try {
            LedgerBackupCodec.decrypt(encrypted, passphrase)
        } finally {
            passphrase.fill(Char.MIN_VALUE)
            encrypted.fill(0)
        }
    }

    private fun reversalSnapshot(originalStatus: String = "POSTED"): LedgerBackupSnapshot {
        val original = entry("original").copy(status = originalStatus, amountMinor = 100L)
        val reversal = entry("reverse-1", reversal = "original", kind = "REVERSAL").copy(amountMinor = 100L)
        return validSnapshot().copy(
            journalEntries = listOf(original, reversal),
            journalLines = listOf(
                JournalLineEntity("original-cash-1", "original", "1000", 75L, 0L, "USD", "Caja"),
                JournalLineEntity("original-cash-2", "original", "1000", 25L, 0L, "USD", "Caja"),
                JournalLineEntity("original-income", "original", "4000", 0L, 100L, "USD", "Ingresos"),
                JournalLineEntity("reverse-cash-1", "reverse-1", "1000", 0L, 75L, "USD", "Caja"),
                JournalLineEntity("reverse-cash-2", "reverse-1", "1000", 0L, 25L, "USD", "Caja"),
                JournalLineEntity("reverse-income", "reverse-1", "4000", 100L, 0L, "USD", "Ingresos"),
            ),
        )
    }

    private fun closeSnapshot(income: Boolean): LedgerBackupSnapshot {
        val source = if (income) {
            entry("source").copy(amountMinor = 100L)
        } else {
            entry("source").copy(kind = "EXPENSE", categoryAccountCode = "5000", amountMinor = 100L)
        }
        val sourceLines = if (income) listOf(
            JournalLineEntity("source-cash", "source", "1000", 100L, 0L, "USD", "Caja"),
            JournalLineEntity("source-income", "source", "4000", 0L, 100L, "USD", "Ingresos"),
        ) else listOf(
            JournalLineEntity("source-expense", "source", "5000", 100L, 0L, "USD", "Gastos"),
            JournalLineEntity("source-cash", "source", "1000", 0L, 100L, "USD", "Caja"),
        )
        val transferLines = if (income) listOf(
            JournalLineEntity("close-income", "close-entry", "4000", 100L, 0L, "USD", "Ingresos"),
            JournalLineEntity("close-equity", "close-entry", "3100", 0L, 100L, "USD", "Retenidas"),
        ) else listOf(
            JournalLineEntity("close-expense", "close-entry", "5000", 0L, 100L, "USD", "Gastos"),
            JournalLineEntity("close-equity", "close-entry", "3100", 100L, 0L, "USD", "Retenidas"),
        )
        val close = entry("close-entry", kind = "PERIOD_CLOSE", date = "2024-01-31").copy(
            categoryAccountCode = "3100",
            paymentAccountCode = "3100",
            amountMinor = 100L,
        )
        return validSnapshot().copy(
            journalEntries = listOf(source, close),
            journalLines = sourceLines + transferLines,
            periodCloses = listOf(
                PeriodCloseEntity("close-1", "2024-01-01", "2024-01-31", "3100", if (income) 100L else -100L, "close-entry", "t1"),
            ),
        )
    }

    private fun validSnapshot() = LedgerBackupSnapshot(
        bookSettings = LedgerBookDefaults.settings,
        accounts = LedgerBookDefaults.accounts,
        journalEntries = listOf(entry("entry-1")),
        journalLines = listOf(
            JournalLineEntity("line-1", "entry-1", "1000", 1L, 0L, "USD", "Caja"),
            JournalLineEntity("line-2", "entry-1", "4000", 0L, 1L, "USD", "Ingresos"),
        ),
        auditEvents = emptyList(),
        accountAuditEvents = emptyList(),
        periodCloses = emptyList(),
    )

    private fun entry(id: String, currency: String = "USD", reversal: String? = null, kind: String = "INCOME", date: String = "2024-01-15") =
        JournalEntryEntity(id, date, "Descripción", kind, "4000", "1000", currency, 1L, "POSTED", "created", reversal)
}
