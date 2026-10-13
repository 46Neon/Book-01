package com.neon.book01.data

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun pickerContractsUseCreateOpenActionsAndBackupMimeTypes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fileName = LedgerBackupDocumentPicker.defaultFileName(java.time.LocalDate.of(2026, 10, 13))
        val createIntent = LedgerBackupDocumentPicker.createContract().createIntent(context, fileName)
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, createIntent.action)
        assertEquals(LedgerBackupDocumentPicker.MIME_TYPE, createIntent.type)
        assertEquals(fileName, createIntent.getStringExtra(Intent.EXTRA_TITLE))
        assertTrue(createIntent.categories?.contains(Intent.CATEGORY_OPENABLE) == true)

        val mimeTypes = LedgerBackupDocumentPicker.openMimeTypes()
        val openIntent = LedgerBackupDocumentPicker.openContract().createIntent(context, mimeTypes)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, openIntent.action)
        assertArrayEquals(mimeTypes, openIntent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES))
        assertTrue(openIntent.categories?.contains(Intent.CATEGORY_OPENABLE) == true)
    }
}
