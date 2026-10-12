package com.neon.book01.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.neon.book01.domain.EntryKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerRepositoryBackupRestoreTest {
    private lateinit var destination: LedgerDatabase
    private lateinit var destinationRepository: LedgerRepository
    private lateinit var source: LedgerDatabase
    private lateinit var sourceRepository: LedgerRepository

    @Before fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        destination = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java)
            .addCallback(LedgerDatabase.integrityGuardsCallback()).allowMainThreadQueries().build()
        source = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java)
            .addCallback(LedgerDatabase.integrityGuardsCallback()).allowMainThreadQueries().build()
        destinationRepository = LedgerRepository(destination)
        sourceRepository = LedgerRepository(source)
    }

    @After fun tearDown() {
        destination.close()
        source.close()
    }

    @Test fun restoresFullAuthenticatedSnapshotExactlyIncludingAuditsSnapshotsAndCloses() = runBlocking {
        val backup = createHistoricalBook()
        destinationRepository.initializeBook()
        val preview = destinationRepository.previewBackupRestore(backup)
        assertEquals("Libro de prueba", preview.bookName)
        assertEquals(3, preview.journalEntryCount)
        assertEquals(1, preview.accountAuditEventCount)
        assertEquals(1, preview.periodCloseCount)

        destinationRepository.restoreBackup(backup)

        assertEquals(backup, destinationRepository.createBackupSnapshot())
        assertEquals(3, destination.ledgerDao().getAllEntries().size)
        assertEquals(1, destination.ledgerDao().getAllAccountAuditEvents().size)
        assertEquals(1, destination.ledgerDao().getAllPeriodCloses().size)
    }

    @Test fun restoresIntoUninitializedDatabase() = runBlocking {
        val backup = createHistoricalBook()
        destinationRepository.restoreBackup(backup)
        assertEquals(backup, destinationRepository.createBackupSnapshot())
    }

    @Test fun nonemptyAndModifiedBooksAreRefusedWithoutPartialWrites() = runBlocking {
        val backup = createHistoricalBook()
        destinationRepository.initializeBook()
        destinationRepository.recordSimpleEntry("2026-10-12", "Local", EntryKind.INCOME, "4000", "1000", 777)
        val before = destinationRepository.createBackupSnapshot()
        assertThrows(IllegalArgumentException::class.java) { runBlocking { destinationRepository.restoreBackup(backup) } }
        assertEquals(before, destinationRepository.createBackupSnapshot())

        val untouched = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, LedgerDatabase::class.java,
        ).addCallback(LedgerDatabase.integrityGuardsCallback()).allowMainThreadQueries().build()
        try {
            val customized = LedgerRepository(untouched)
            customized.initializeBook()
            customized.renameBook("Mi libro propio")
            val customizedBefore = customized.createBackupSnapshot()
            assertThrows(IllegalArgumentException::class.java) { runBlocking { customized.restoreBackup(backup) } }
            assertEquals(customizedBefore, customized.createBackupSnapshot())
        } finally {
            untouched.close()
        }
    }

    @Test fun foreignKeyAndUnbalancedSnapshotsAreRejectedWithoutPartialWrites() = runBlocking {
        val backup = createHistoricalBook()
        destinationRepository.initializeBook()
        val before = destinationRepository.createBackupSnapshot()
        val missingAccount = backup.copy(journalLines = backup.journalLines.mapIndexed { index, line ->
            if (index == 0) line.copy(accountCode = "does-not-exist") else line
        })
        assertThrows(IllegalArgumentException::class.java) { runBlocking { destinationRepository.restoreBackup(missingAccount) } }
        assertEquals(before, destinationRepository.createBackupSnapshot())

        val unbalanced = backup.copy(journalLines = backup.journalLines.mapIndexed { index, line ->
            if (index == 0) line.copy(debitMinor = line.debitMinor + 1L) else line
        })
        assertThrows(IllegalArgumentException::class.java) { runBlocking { destinationRepository.restoreBackup(unbalanced) } }
        assertEquals(before, destinationRepository.createBackupSnapshot())
    }

    @Test fun wrongPasswordAndTamperingNeverReachRestore() = runBlocking {
        val backup = createHistoricalBook()
        destinationRepository.initializeBook()
        val before = destinationRepository.createBackupSnapshot()
        val passphrase = "restore test phrase".toCharArray()
        val encrypted = LedgerBackupCodec.encrypt(backup, passphrase)
        assertThrows(BackupAuthenticationException::class.java) {
            LedgerBackupCodec.decrypt(encrypted, "wrong restore phrase".toCharArray())
        }
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        assertThrows(BackupAuthenticationException::class.java) { LedgerBackupCodec.decrypt(encrypted, passphrase) }
        assertEquals(before, destinationRepository.createBackupSnapshot())
        passphrase.fill('\u0000')
        encrypted.fill(0)
    }

    private suspend fun createHistoricalBook(): LedgerBackupSnapshot {
        sourceRepository.initializeBook()
        sourceRepository.recordSimpleEntry("2024-01-15", "Ingreso exacto", EntryKind.INCOME, "4000", "1000", 12_345L)
        sourceRepository.updateAccountSettings("1000", "Caja histórica", active = false)
        val original = source.ledgerDao().getAllEntries().single().entry
        sourceRepository.reversePostedEntry(original.id, "2024-02-01", "Corrección histórica")
        sourceRepository.closePeriod("2024-01-01", "2024-01-31", "3100")
        return sourceRepository.createBackupSnapshot()
    }
}
