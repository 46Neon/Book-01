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
class LedgerRepositoryPeriodCloseTest {
    private lateinit var database: LedgerDatabase
    private lateinit var repository: LedgerRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, LedgerDatabase::class.java,
        ).addCallback(LedgerDatabase.integrityGuardsCallback()).allowMainThreadQueries().build()
        repository = LedgerRepository(database)
    }

    @After fun tearDown() = database.close()

    @Test fun profitClosePostsBalancedLinesAndImmutableMetadata() = runBlocking {
        repository.initializeBook()
        repository.recordSimpleEntry("2026-03-10", "Venta", EntryKind.INCOME, "4000", "1000", 5_000)
        repository.recordSimpleEntry("2026-03-20", "Gasto", EntryKind.EXPENSE, "5100", "1000", 1_000)
        val id = repository.closePeriod("2026-03-01", "2026-03-31", "3000") ?: error("Expected a closing journal entry")
        val close = database.ledgerDao().getAllEntries().single { it.entry.id == id }
        assertEquals("PERIOD_CLOSE", close.entry.kind)
        assertEquals(3, close.lines.size)
        assertEquals(5_000L, close.lines.filter { it.accountCode == "4000" }.single().debitMinor)
        assertEquals(1_000L, close.lines.filter { it.accountCode == "5100" }.single().creditMinor)
        assertEquals(4_000L, close.lines.filter { it.accountCode == "3000" }.single().creditMinor)
        assertEquals(
            database.openHelper.readableDatabase.query("SELECT SUM(debitMinor) FROM journal_lines").use { it.moveToFirst(); it.getLong(0) },
            database.openHelper.readableDatabase.query("SELECT SUM(creditMinor) FROM journal_lines").use { it.moveToFirst(); it.getLong(0) },
        )
        assertEquals(1L, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM period_closes").use { it.moveToFirst(); it.getLong(0) })
        assertEquals(3L, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM posted_entry_locks").use { it.moveToFirst(); it.getLong(0) })
        assertEquals(3L, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM audit_events").use { it.moveToFirst(); it.getLong(0) })
        assertThrows(android.database.SQLException::class.java) {
            database.openHelper.writableDatabase.execSQL("UPDATE period_closes SET netResultMinor = 0")
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.reversePostedEntry(id, "2026-04-01", "No revertir cierre") }
        }
        Unit
    }

    @Test fun lossCloseTransfersDebitAndClosedDatesRejectPostingAndReversal() = runBlocking {
        repository.initializeBook()
        repository.recordSimpleEntry("2026-05-04", "Gasto", EntryKind.EXPENSE, "5200", "1000", 2_500)
        repository.closePeriod("2026-05-01", "2026-05-31", "3000")
        val close = database.ledgerDao().getAllEntries().single { it.entry.kind == "PERIOD_CLOSE" }
        assertEquals(2_500L, close.lines.single { it.accountCode == "3000" }.debitMinor)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.recordSimpleEntry("2026-05-31", "Bloqueado", EntryKind.INCOME, "4000", "1000", 100) }
        }
        val original = database.ledgerDao().getAllEntries().single { it.entry.kind == EntryKind.EXPENSE.name }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.reversePostedEntry(original.entry.id, "2026-05-15", "Fecha bloqueada") }
        }
        // Reversal of a pre-close source is allowed when the correcting posting date is open.
        repository.reversePostedEntry(original.entry.id, "2026-06-01", "Corrección en periodo abierto")
        Unit
    }

    @Test fun futurePeriodsCannotBeClosedInAdvance() = runBlocking {
        repository.initializeBook()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.closePeriod("2099-01-01", "2099-12-31", "3100") }
        }
        assertEquals(0L, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM period_closes").use { it.moveToFirst(); it.getLong(0) })
    }

    @Test fun emptyPeriodClosesAsMetadataOnlyWithoutFakeEntryOrAudit() = runBlocking {
        repository.initializeBook()
        val result = repository.closePeriod("2026-06-01", "2026-06-30", "3000")
        assertEquals(null, result)
        val metadata = database.ledgerDao().getPeriodCloses().single()
        assertEquals(null, metadata.closingEntryId)
        assertEquals(0L, metadata.netResultMinor)
        assertEquals(0, database.ledgerDao().getAllEntries().size)
        assertEquals(0L, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM posted_entry_locks").use { it.moveToFirst(); it.getLong(0) })
        assertEquals(0L, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM audit_events").use { it.moveToFirst(); it.getLong(0) })
    }

    @Test fun overlappingAndDuplicateClosesAreRejectedWithoutPartialAudit() = runBlocking {
        repository.initializeBook()
        repository.recordSimpleEntry("2026-07-15", "Ingreso", EntryKind.INCOME, "4000", "1000", 500)
        repository.closePeriod("2026-07-01", "2026-07-31", "3000")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.closePeriod("2026-07-31", "2026-08-31", "3000") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.closePeriod("2026-07-01", "2026-07-31", "3000") }
        }
        assertEquals(1L, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM period_closes").use { it.moveToFirst(); it.getLong(0) })
        assertEquals(2L, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM journal_entries").use { it.moveToFirst(); it.getLong(0) })
        assertEquals(2L, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM audit_events").use { it.moveToFirst(); it.getLong(0) })
    }
}
