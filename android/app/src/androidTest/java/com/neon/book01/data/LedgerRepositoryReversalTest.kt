package com.neon.book01.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.neon.book01.domain.EntryKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerRepositoryReversalTest {
    private lateinit var database: LedgerDatabase
    private lateinit var repository: LedgerRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            LedgerDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = LedgerRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun reversalPostsExactInverseAndPreservesOriginalAndAuditHistory() = runBlocking {
        repository.initializeBook()
        repository.recordSimpleEntry(
            dateIso = "2026-03-04",
            description = "Compra de papelería",
            kind = EntryKind.EXPENSE,
            categoryAccountCode = "5100",
            paymentAccountCode = "1010",
            amountMinor = 12345,
        )
        val originalBefore = database.ledgerDao().getEntryWithLinesForTest("2026-03-04")
        val original = originalBefore.single()
        val reversalId = repository.reversePostedEntry(original.entry.id, "2026-03-05", "Importe capturado por error")
        val reversal = database.ledgerDao().getEntryWithLinesForTest("2026-03-05").single()

        assertNotEquals(original.entry.id, reversalId)
        assertEquals(original.entry, database.ledgerDao().getEntry(original.entry.id))
        assertEquals(original.lines, database.ledgerDao().getLines(original.entry.id))
        assertEquals(original.entry.id, reversal.entry.reversalOfEntryId)
        val sourceByAccount = original.lines.associateBy { it.accountCode }
        val inverseByAccount = reversal.lines.associateBy { it.accountCode }
        assertEquals(sourceByAccount.keys, inverseByAccount.keys)
        sourceByAccount.forEach { (code, source) ->
            val inverse = inverseByAccount.getValue(code)
            assertEquals(source.debitMinor, inverse.creditMinor)
            assertEquals(source.creditMinor, inverse.debitMinor)
            assertEquals(source.currencyCode, inverse.currencyCode)
            assertEquals(source.accountNameSnapshot, inverse.accountNameSnapshot)
        }
        assertEquals(3, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM audit_events").use { it.moveToFirst(); it.getInt(0) })
    }

    @Test
    fun auditEventsRejectUpdatesAndDeletes() = runBlocking {
        repository.initializeBook()
        repository.recordSimpleEntry("2026-03-04", "Ingreso", EntryKind.INCOME, "4000", "1000", 500)
        val db = database.openHelper.writableDatabase

        assertThrows(android.database.SQLException::class.java) {
            db.execSQL("UPDATE audit_events SET reason = 'alterado'")
        }
        assertThrows(android.database.SQLException::class.java) {
            db.execSQL("DELETE FROM audit_events")
        }
        assertEquals(1L, db.query("SELECT COUNT(*) FROM audit_events").use { it.moveToFirst(); it.getLong(0) })
    }

    @Test
    fun duplicateAndReversalOfReversalAreRejected() = runBlocking {
        repository.initializeBook()
        repository.recordSimpleEntry("2026-03-04", "Ingreso", EntryKind.INCOME, "4000", "1000", 500)
        val original = database.ledgerDao().getEntryWithLinesForTest("2026-03-04").single()
        val reversalId = repository.reversePostedEntry(original.entry.id, "2026-03-05", "Corrección")

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.reversePostedEntry(original.entry.id, "2026-03-06", "Duplicada") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.reversePostedEntry(reversalId, "2026-03-06", "No se admite") }
        }
        assertEquals(2L, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM journal_entries").use { it.moveToFirst(); it.getLong(0) })
        assertEquals(3L, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM audit_events").use { it.moveToFirst(); it.getLong(0) })
    }

    private suspend fun LedgerDao.getEntryWithLinesForTest(date: String): List<EntryWithLines> =
        getAllEntries().filter { it.entry.entryDate == date }
}
