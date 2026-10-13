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
        val historical = createHistoricalBook()
        val backup = historical.copy(accounts = historical.accounts.reversed())
        destinationRepository.initializeBook()
        val preview = destinationRepository.previewBackupRestore(backup)
        assertEquals("Libro de prueba", preview.bookName)
        assertEquals(3, preview.journalEntryCount)
        assertEquals("REC-2024-001", backup.journalEntries.first { it.description == "Ingreso exacto" }.supportReference)
        assertEquals(1, preview.accountAuditEventCount)
        assertEquals(1, preview.periodCloseCount)

        destinationRepository.restoreBackup(backup)

        assertEquals(backup.copy(accounts = backup.accounts.sortedBy { it.code }), destinationRepository.createBackupSnapshot())
        assertEquals(3, destination.ledgerDao().getAllEntries().size)
        assertEquals(3L, destination.openHelper.readableDatabase.query("SELECT COUNT(*) FROM posted_entry_locks").use { it.moveToFirst(); it.getLong(0) })
        assertEquals(1, destination.ledgerDao().getAllAccountAuditEvents().size)
        assertEquals(1, destination.ledgerDao().getAllPeriodCloses().size)
    }

    @Test fun restoresIntoUninitializedDatabase() = runBlocking {
        val backup = createHistoricalBook()
        destinationRepository.restoreBackup(backup)
        assertEquals(backup, destinationRepository.createBackupSnapshot())
    }

    @Test fun repositoryEnforcesParentEligibilityAndPreventsReparentingOrDeactivation() = runBlocking {
        destinationRepository.initializeBook()
        destinationRepository.createAccount("5800", "Gastos principales", "EXPENSE", cashOrBank = false, parentCode = "5100")
        destinationRepository.createAccount("5810", "Gastos menores", "EXPENSE", cashOrBank = false, parentCode = "5800")
        assertEquals("5800", destination.ledgerDao().getAccount("5810")?.parentCode)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { destinationRepository.createAccount("5820", "Otra cuenta", "EXPENSE", cashOrBank = false, parentCode = "4000") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { destinationRepository.updateAccountSettings("5800", "Gastos principales", active = false) }
        }
        destinationRepository.createAccount("5900", "Padre inactivo", "EXPENSE", cashOrBank = false)
        destinationRepository.updateAccountSettings("5900", "Padre inactivo", active = false)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { destinationRepository.createAccount("5910", "Hijo inválido", "EXPENSE", cashOrBank = false, parentCode = "5900") }
        }
        assertThrows(android.database.SQLException::class.java) {
            runBlocking { destination.ledgerDao().updateAccount(destination.ledgerDao().getAccount("5810")!!.copy(parentCode = null)) }
        }
        Unit
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

    @Test fun authenticatedHierarchyWithMissingWrongTypeInactiveParentOrCycleIsRejected() = runBlocking {
        val valid = createHistoricalBook()
        val customParent = valid.accounts.first { it.code == "5400" }
        val customChild = valid.accounts.first { it.code == "5410" }
        val invalidSnapshots = listOf(
            valid.copy(accounts = valid.accounts.map { if (it.code == "5400") it.copy(parentCode = "9999") else it }),
            valid.copy(accounts = valid.accounts.map { if (it.code == "5400") it.copy(parentCode = "4000") else it }),
            valid.copy(accounts = valid.accounts.map { if (it.code == "5100") it.copy(active = false) else it }),
            valid.copy(accounts = valid.accounts.map {
                when (it.code) {
                    customParent.code -> it.copy(parentCode = customChild.code)
                    customChild.code -> it.copy(parentCode = customParent.code)
                    else -> it
                }
            }),
        )
        val before = destinationRepository.createBackupSnapshot()
        invalidSnapshots.forEach { invalid ->
            val passphrase = "authenticated hierarchy fixture".toCharArray()
            val encrypted = LedgerBackupCodec.encrypt(invalid, passphrase)
            try {
                assertThrows(IllegalArgumentException::class.java) {
                    val authenticated = LedgerBackupCodec.decrypt(encrypted, passphrase)
                    runBlocking { destinationRepository.previewBackupRestore(authenticated) }
                }
            } finally {
                passphrase.fill(Char.MIN_VALUE)
                encrypted.fill(0)
            }
        }
        assertEquals(before, destinationRepository.createBackupSnapshot())
    }

    @Test fun authenticatedInvalidCloseIsRejectedBeforeAnyPristineRestoreWrites() = runBlocking {
        val historical = createHistoricalBook()
        val invalid = historical.copy(
            periodCloses = historical.periodCloses.map { it.copy(netResultMinor = it.netResultMinor + 1L) },
        )
        val before = destinationRepository.createBackupSnapshot()
        val passphrase = "authenticated invalid close fixture".toCharArray()
        val encrypted = LedgerBackupCodec.encrypt(invalid, passphrase)
        val authenticatedSnapshot = try {
            LedgerBackupCodec.decrypt(encrypted, passphrase)
        } finally {
            passphrase.fill(Char.MIN_VALUE)
            encrypted.fill(0)
        }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { destinationRepository.restoreBackup(authenticatedSnapshot) }
        }
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
        sourceRepository.createAccount("5400", "Gastos operativos", "EXPENSE", cashOrBank = false, parentCode = "5100")
        sourceRepository.createAccount("5410", "Suministros", "EXPENSE", cashOrBank = false, parentCode = "5400")
        sourceRepository.recordSimpleEntry("2024-01-15", "Ingreso exacto", EntryKind.INCOME, "4000", "1000", 12_345L, supportReference = "REC-2024-001")
        sourceRepository.updateAccountSettings("1000", "Caja histórica", active = false)
        val original = source.ledgerDao().getAllEntries().single().entry
        sourceRepository.reversePostedEntry(original.id, "2024-02-01", "Corrección histórica")
        sourceRepository.closePeriod("2024-01-01", "2024-01-31", "3100")
        return sourceRepository.createBackupSnapshot()
    }
}
