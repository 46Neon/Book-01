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
                    reversalOfEntryId = null, counterparty = "Cliente — Café",
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
    fun legacyEncryptedV1BackupWithoutCounterpartyRestoresItAsNull() {
        // Captured legacy v1 file: valid AES-GCM authentication and no trailing counterparty extension.
        val encrypted = java.util.Base64.getDecoder().decode("QjAxRQAAAAEAAzRQAAECAwQFBgcICQoLDA0ODwABAgMEBQYHCAkKCwAAAuhe75i8rdIijatlXaVnFDUj3IOncwhlX21vrRJcpY33OOBdDVeiUNiL2+ON/avFYtAwglFEfVKxFJBSrn2XgyiKTfgSD9zZzXpUbTEmPNkx/eJb5raqIe/x4WXoHgXXo0DKmjhknahC9ajGGB5iqei8hDdM2YdrbCCO44sJa87ej/FXmzL81NP3y5ca+sKN5DFbkMgrI1WcFDzBfdaZZ91RBNk71GQgDleMpKoVfXmEYX0b3bFO/In96KA6o2233kghXL+Bt8t9fmKgP9FgkXjbWvo/AwTrkfOY4nGpOIPKZC9lSMV+5dLLuiSM7KssZhaYkCLjEKUGuF3sRqHkiU2zYkrdegcTOEFOAcKy+plPKYRC4ILI0TCX4BFgmAWwquAVPHD70QnQu6JBZdEoyRowcP7amN76xabjkuZ5GfmJdxk8bUPBck41rFS1EMBT30GiDfsJDfkASqhfvAmefbg17pDQbF+7IwEUC2t5v/nL3MLHKVoHX0hE9Isrd4P8UCxp6nLdGr9Xv3PEmCM7t3x9o+GvjL8VzPpAXVsK9LqAqH48dG3IBBXw3AKGIR977+A8DPWwA1aYB0b5kQUgTCW98YWLI+V7/alfDk/36S+xuUT9AEwssgqEx4YJgVBtqEbK+OaTKFS4JAWs6h8jOzVeMjmVraVxZRVNtneVe2vpgbGNmOEyVdLZ/jDW3B9Y1zsyDT/eVPw9Nni8IV24knkHQJU3vTiwNxz4VKP3AB4VRSMHpUtDyBpVMe+0Q5jtYYMR9jrQoJjSvF1h1ZnD2bbmHK4yA+GFMA2Fb1yzMLwJcsqb/tvOhAZJFZvaaPayBaciZS71CQ4xUX3AmSvnYmEUHgtJjwPta0OG4K5hKgLnTIawV0l+rqw6idSIPWj/mcqCrXRxTOlCepKhiNBmD586SWArReRL11pVa2/LBLlfPkVEeyLDH+0W+8fo/EFC7wlpXdIxkuoubf+ncqP5lMRLselRiKcxOAw=" )
        val decoded = LedgerBackupCodec.decrypt(encrypted, password)
        assertEquals(null, decoded.journalEntries.single().counterparty)
        assertEquals("Venta antigua", decoded.journalEntries.single().description)
        assertEquals(1, LedgerBackupValidator.validate(decoded).journalEntryCount)
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
