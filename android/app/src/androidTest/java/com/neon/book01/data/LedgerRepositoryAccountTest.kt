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
class LedgerRepositoryAccountTest {
    private lateinit var database: LedgerDatabase
    private lateinit var repository: LedgerRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            LedgerDatabase::class.java,
        ).addCallback(LedgerDatabase.integrityGuardsCallback()).allowMainThreadQueries().build()
        repository = LedgerRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun renameKeepsPostedJournalLabelsAndAuditRecordsTheChange() = runBlocking {
        repository.initializeBook()
        repository.recordSimpleEntry("2026-06-01", "Venta", EntryKind.INCOME, "4000", "1000", 2500)
        val oldLines = database.ledgerDao().getAllEntries().single().lines
        assertEquals(setOf("Ingresos", "Caja"), oldLines.map { it.accountNameSnapshot }.toSet())

        repository.updateAccountSettings("4000", "Ventas", active = true)

        val account = database.ledgerDao().getAccount("4000")!!
        assertEquals("Ventas", account.name)
        assertEquals("INCOME", account.type)
        assertEquals("4000", account.code)
        assertEquals(oldLines.associateBy { it.id }, database.ledgerDao().getLines(oldLines.first().entryId).associateBy { it.id })
        val audit = database.openHelper.readableDatabase.query("SELECT oldName, newName, oldActive, newActive FROM account_audit_events WHERE accountCode = '4000'").use {
            check(it.moveToFirst())
            listOf(it.getString(0), it.getString(1), it.getInt(2).toString(), it.getInt(3).toString())
        }
        assertEquals(listOf("Ingresos", "Ventas", "1", "1"), audit)

        repository.recordSimpleEntry("2026-06-02", "Otra venta", EntryKind.INCOME, "4000", "1000", 1000)
        val newLineSnapshot = database.ledgerDao().getAllEntries()
            .first { it.entry.entryDate == "2026-06-02" }.lines
            .first { it.accountCode == "4000" }.accountNameSnapshot
        assertEquals("Ventas", newLineSnapshot)
    }

    @Test
    fun deactivationIsAuditedAndBlocksNewPostingsWithoutRemovingAccountOrHistory() = runBlocking {
        repository.initializeBook()
        repository.recordSimpleEntry("2026-06-01", "Gasto", EntryKind.EXPENSE, "5100", "1000", 300)
        val original = database.ledgerDao().getAllEntries().single()

        repository.updateAccountSettings("5100", "Papelería", active = false)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.recordSimpleEntry("2026-06-02", "Otro gasto", EntryKind.EXPENSE, "5100", "1000", 100)
            }
        }

        assertEquals(1, database.ledgerDao().getAllEntries().size)
        assertEquals(original.lines.associateBy { it.id }, database.ledgerDao().getLines(original.entry.id).associateBy { it.id })
        assertEquals(false, database.ledgerDao().getAccount("5100")!!.active)
        assertEquals(1L, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM account_audit_events WHERE accountCode = '5100' AND oldActive = 1 AND newActive = 0").use { it.moveToFirst(); it.getLong(0) })
    }

    @Test
    fun accountAuditRecordsAreAppendOnly() = runBlocking {
        repository.initializeBook()
        repository.updateAccountSettings("1000", "Caja chica", active = true)
        val db = database.openHelper.writableDatabase

        assertThrows(android.database.SQLException::class.java) {
            db.execSQL("UPDATE account_audit_events SET newName = 'alterado'")
        }
        assertThrows(android.database.SQLException::class.java) {
            db.execSQL("DELETE FROM account_audit_events")
        }
        assertEquals(1L, db.query("SELECT COUNT(*) FROM account_audit_events").use { it.moveToFirst(); it.getLong(0) })
        assertThrows(android.database.SQLException::class.java) { db.execSQL("UPDATE accounts SET type = 'LIABILITY' WHERE code = '1000'") }
        assertThrows(android.database.SQLException::class.java) { db.execSQL("DELETE FROM accounts WHERE code = '1000'") }
        Unit
    }

    @Test
    fun postedEntriesCannotBeUpdatedOrDeleted() = runBlocking {
        repository.initializeBook()
        repository.recordSimpleEntry("2026-06-01", "Ingreso", EntryKind.INCOME, "4000", "1000", 500)
        val db = database.openHelper.writableDatabase
        assertThrows(android.database.SQLException::class.java) { db.execSQL("UPDATE journal_entries SET description = 'alterado'") }
        assertThrows(android.database.SQLException::class.java) { db.execSQL("DELETE FROM journal_entries") }
        assertEquals(1, database.ledgerDao().getAllEntries().size)
    }
}
