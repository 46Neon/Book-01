package com.neon.book01.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.database.SQLException
import com.neon.book01.domain.EntryKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerPostedLineImmutabilityTest {
    private lateinit var database: LedgerDatabase
    private lateinit var repository: LedgerRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            LedgerDatabase::class.java,
        ).addCallback(LedgerDatabase.integrityGuardsCallback()).allowMainThreadQueries().build()
        repository = LedgerRepository(database)
    }

    @After fun tearDown() = database.close()

    @Test fun postingSucceedsThenDirectLineAndLockMutationsAreBlocked() = runBlocking {
        repository.initializeBook()
        repository.recordSimpleEntry("2026-03-04", "Ingreso", EntryKind.INCOME, "4000", "1000", 500)

        val db = database.openHelper.writableDatabase
        val entry = database.ledgerDao().getAllEntries().single()
        assertEquals("POSTED", entry.entry.status)
        assertEquals(2, entry.lines.size)
        assertEquals(1L, db.query("SELECT COUNT(*) FROM posted_entry_locks WHERE entryId = '${entry.entry.id}'").use {
            it.moveToFirst(); it.getLong(0)
        })

        assertThrows(SQLException::class.java) {
            db.execSQL("UPDATE journal_lines SET debitMinor = debitMinor + 1 WHERE entryId = ?", arrayOf(entry.entry.id))
        }
        assertThrows(SQLException::class.java) {
            db.execSQL("DELETE FROM journal_lines WHERE entryId = ?", arrayOf(entry.entry.id))
        }
        assertThrows(SQLException::class.java) {
            db.execSQL(
                "INSERT INTO journal_lines (id, entryId, accountCode, debitMinor, creditMinor, currencyCode, accountNameSnapshot) VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf("late-line", entry.entry.id, "1000", 1L, 0L, "USD", "Caja"),
            )
        }
        assertThrows(SQLException::class.java) {
            db.execSQL("UPDATE posted_entry_locks SET entryId = 'other' WHERE entryId = ?", arrayOf(entry.entry.id))
        }
        assertThrows(SQLException::class.java) {
            db.execSQL("DELETE FROM posted_entry_locks WHERE entryId = ?", arrayOf(entry.entry.id))
        }
        assertEquals(2L, db.query("SELECT COUNT(*) FROM journal_lines WHERE entryId = '${entry.entry.id}'").use {
            it.moveToFirst(); it.getLong(0)
        })
    }
}
