package com.neon.book01.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LedgerBackupCodecTest {
    private val password = "correct horse battery staple".toCharArray()

    @Test
    fun roundTripPreservesEveryFieldAndExactAmounts() {
        val original = LedgerBackupSnapshot(
            bookSettings = BookSettingsEntity(id = 1, bookName = "Café — caja", baseCurrency = "USD"),
            accounts = listOf(
                AccountEntity(code = "1000", name = "Caja", type = "ASSET", active = true, cashOrBank = true),
                AccountEntity(code = "5999", name = "Archivo", type = "EXPENSE", active = false, cashOrBank = false),
            ),
            journalEntries = listOf(
                JournalEntryEntity(
                    id = "entry-1", entryDate = "2026-10-12", description = "Venta exacta", kind = "INCOME",
                    categoryAccountCode = "4000", paymentAccountCode = "1000", currencyCode = "USD",
                    amountMinor = Long.MAX_VALUE - 17, status = "POSTED", createdAt = "2026-10-12T13:14:15Z",
                    reversalOfEntryId = null,
                ),
                JournalEntryEntity(
                    id = "entry-2", entryDate = "2026-10-13", description = "Reversa", kind = "REVERSAL",
                    categoryAccountCode = "4000", paymentAccountCode = "1000", currencyCode = "USD",
                    amountMinor = Long.MIN_VALUE + 23, status = "POSTED", createdAt = "2026-10-13T13:14:15Z",
                    reversalOfEntryId = "entry-1",
                ),
            ),
            journalLines = listOf(
                JournalLineEntity("line-1", "entry-1", "1000", Long.MAX_VALUE - 17, 0L, "USD", "Caja al momento"),
                JournalLineEntity("line-2", "entry-1", "4000", 0L, Long.MAX_VALUE - 17, "USD", "Ingresos antiguos"),
            ),
            auditEvents = listOf(
                AuditEventEntity("audit-1", "entry-1", "POSTED", "t1", "2026-10-12", "Registro inicial", null),
                AuditEventEntity("audit-2", "entry-2", "REVERSAL_POSTED", "t2", "2026-10-13", "Corrección", "entry-1"),
            ),
            accountAuditEvents = listOf(
                AccountAuditEventEntity("account-audit-1", "1000", "t3", "Caja vieja", "Caja", false, true),
            ),
            periodCloses = listOf(
                PeriodCloseEntity("close-1", "2026-01-01", "2026-06-30", "3100", -9876543210123L, "entry-3", "t4"),
                PeriodCloseEntity("close-2", "2026-07-01", "2026-07-31", "3100", 0L, null, "t5"),
            ),
        )

        val encrypted = LedgerBackupCodec.encrypt(original, password)
        val decoded = LedgerBackupCodec.decrypt(encrypted, password)

        assertEquals(original, decoded)
        assertArrayEquals("B01E".toByteArray(Charsets.US_ASCII), encrypted.copyOfRange(0, 4))
        assertFalseContains(encrypted, "Venta exacta".toByteArray(Charsets.UTF_8))
    }

    @Test
    fun wrongPasswordIsRejected() {
        val encrypted = LedgerBackupCodec.encrypt(sample(), password)
        assertThrows(BackupAuthenticationException::class.java) {
            LedgerBackupCodec.decrypt(encrypted, "incorrect password".toCharArray())
        }
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        val encrypted = LedgerBackupCodec.encrypt(sample(), password)
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        assertThrows(BackupAuthenticationException::class.java) {
            LedgerBackupCodec.decrypt(encrypted, password)
        }
    }

    @Test
    fun emptyBookRoundTripsIncludingMissingSettings() {
        val empty = LedgerBackupSnapshot(null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(empty, LedgerBackupCodec.decrypt(LedgerBackupCodec.encrypt(empty, password), password))
    }

    @Test
    fun independentExportsUseFreshSaltAndNonce() {
        val book = sample()
        val first = LedgerBackupCodec.encrypt(book, password)
        val second = LedgerBackupCodec.encrypt(book, password)
        assertFalse(first.contentEquals(second))
        assertEquals(book, LedgerBackupCodec.decrypt(first, password))
        assertEquals(book, LedgerBackupCodec.decrypt(second, password))
    }

    private fun sample() = LedgerBackupSnapshot(
        bookSettings = BookSettingsEntity(1, "Libro", "USD"),
        accounts = listOf(AccountEntity("1000", "Caja", "ASSET", true, true)),
        journalEntries = emptyList(),
        journalLines = emptyList(),
        auditEvents = emptyList(),
        accountAuditEvents = emptyList(),
        periodCloses = emptyList(),
    )

    private fun assertFalseContains(container: ByteArray, target: ByteArray) {
        assertFalse(container.toList().windowed(target.size).any { it == target.toList() })
    }

    private fun assertFalse(value: Boolean) {
        org.junit.Assert.assertFalse(value)
    }
}
