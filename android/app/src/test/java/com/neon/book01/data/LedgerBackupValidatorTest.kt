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
