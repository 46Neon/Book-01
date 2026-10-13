package com.neon.book01.data

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerBackupDocumentTransferTest {
    @Test
    fun encryptedSnapshotRoundTripsThroughContentResolverDocumentUri() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val uri = Uri.parse("content://com.neon.book01.test.backups/saf-round-trip.b01")
        val passphrase = "saf test passphrase 2026".toCharArray()
        val marker = "SAF-URI-PLAINTEXT-MUST-NOT-LEAK"
        val original = LedgerBackupSnapshot(
            bookSettings = BookSettingsEntity(id = 1, bookName = marker, baseCurrency = "USD"),
            accounts = LedgerBookDefaults.accounts,
            journalEntries = emptyList(),
            journalLines = emptyList(),
            auditEvents = emptyList(),
            accountAuditEvents = emptyList(),
            periodCloses = emptyList(),
        )

        try {
            LedgerBackupDocumentTransfer.writeEncryptedSnapshot(resolver, uri, original, passphrase)
            val encrypted = LedgerBackupDocumentTransfer.readEncryptedFile(resolver, uri)
            try {
                assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains(marker))
                val restored = LedgerBackupCodec.decrypt(encrypted, passphrase)
                assertEquals(original, restored)
                assertEquals(LedgerBackupValidator.validate(original), LedgerBackupValidator.validate(restored))
            } finally {
                encrypted.fill(0)
            }
        } finally {
            passphrase.fill('\u0000')
            resolver.delete(uri, null, null)
        }
    }
}
