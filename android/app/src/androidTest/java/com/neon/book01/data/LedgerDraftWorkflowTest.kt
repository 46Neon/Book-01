package com.neon.book01.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.neon.book01.domain.EntryKind
import com.neon.book01.domain.OperatingSummaryCalculator
import com.neon.book01.domain.ReportAccount
import com.neon.book01.domain.ReportPostingLine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerDraftWorkflowTest {
    private lateinit var database: LedgerDatabase
    private lateinit var repository: LedgerRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, LedgerDatabase::class.java,
        ).addCallback(LedgerDatabase.integrityGuardsCallback()).allowMainThreadQueries().build()
        repository = LedgerRepository(database)
    }

    @After fun tearDown() = database.close()

    @Test fun draftCanBeEditedThenPostedOnceWithAppendOnlyAuditAndSqlLocks() = runBlocking {
        repository.initializeBook()
        val id = repository.saveSimpleDraft("2026-07-15", "Ingreso temporal", EntryKind.INCOME, "4000", "1000", 12_500)
        repository.saveSimpleDraft("2026-07-16", "Venta revisada", EntryKind.INCOME, "4000", "1010", 15_000, draftId = id)
        val draft = database.ledgerDao().getEntry(id)!!
        assertEquals("DRAFT", draft.status)
        assertEquals("Venta revisada", draft.description)
        assertEquals(2, database.ledgerDao().getLines(id).size)

        val allBeforePosting = database.ledgerDao().getAllEntries()
        val summaryBeforePosting = summarizePosted(allBeforePosting)
        assertEquals(0L, summaryBeforePosting.incomeMinor)
        assertEquals(0L, summaryBeforePosting.expenseMinor)

        repository.postDraft(id)
        assertEquals("POSTED", database.ledgerDao().getEntry(id)!!.status)
        assertEquals(1L, sqlLong("SELECT COUNT(*) FROM posted_entry_locks WHERE entryId = '$id'"))
        assertEquals(1L, sqlLong("SELECT COUNT(*) FROM audit_events WHERE entryId = '$id' AND eventType = 'DRAFT_POSTED'"))
        val summaryAfterPosting = summarizePosted(database.ledgerDao().getAllEntries())
        assertEquals(15_000L, summaryAfterPosting.incomeMinor)
        assertEquals(0L, summaryAfterPosting.expenseMinor)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.postDraft(id) } }

        val sql = database.openHelper.writableDatabase
        assertThrows(android.database.SQLException::class.java) { sql.execSQL("UPDATE journal_entries SET description = 'tampered' WHERE id = '$id'") }
        assertThrows(android.database.SQLException::class.java) { sql.execSQL("UPDATE journal_entries SET status = 'DRAFT' WHERE id = '$id'") }
        assertThrows(android.database.SQLException::class.java) { sql.execSQL("UPDATE journal_lines SET debitMinor = debitMinor + 1 WHERE entryId = '$id'") }
        assertThrows(android.database.SQLException::class.java) { sql.execSQL("DELETE FROM journal_lines WHERE entryId = '$id'") }
        assertThrows(android.database.SQLException::class.java) {
            sql.execSQL("INSERT INTO journal_lines (id,entryId,accountCode,debitMinor,creditMinor,currencyCode,accountNameSnapshot) VALUES ('late-$id','$id','1000',1,0,'USD','Caja')")
        }

        assertEquals(setOf("DRAFT_CREATED", "DRAFT_EDITED", "DRAFT_POSTED"), database.ledgerDao().getAllAuditEvents().filter { it.entryId == id }.map { it.eventType }.toSet())
        assertEquals(3, database.ledgerDao().getAllAuditEvents().count { it.entryId == id })
        assertThrows(android.database.SQLException::class.java) { sql.execSQL("UPDATE audit_events SET reason = 'changed' WHERE entryId = '$id'") }
        Unit
    }

    @Test fun draftsDoNotAffectPeriodCloseAndCannotPostIntoClosedPeriod() = runBlocking {
        repository.initializeBook()
        val id = repository.saveSimpleDraft("2026-08-14", "Ingreso pendiente", EntryKind.INCOME, "4000", "1000", 99_999)
        val closeEntryId = repository.closePeriod("2026-08-01", "2026-08-31", "3000")
        assertEquals(null, closeEntryId)
        assertEquals(0L, sqlLong("SELECT netResultMinor FROM period_closes"))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.postDraft(id) } }
        assertEquals("DRAFT", database.ledgerDao().getEntry(id)!!.status)
        assertEquals(0L, sqlLong("SELECT COUNT(*) FROM posted_entry_locks WHERE entryId = '$id'"))
    }

    @Test fun backupAndRestorePreserveDraftAndPostedStatusesAndLocks() = runBlocking {
        repository.initializeBook()
        val draftId = repository.saveSimpleDraft("2026-09-10", "Aún pendiente", EntryKind.EXPENSE, "5100", "1000", 2_345)
        val postedId = repository.saveSimpleDraft("2026-09-11", "Aprobado por la persona", EntryKind.INCOME, "4000", "1000", 4_567)
        repository.postDraft(postedId)
        val secret = "correct horse battery staple".toCharArray()
        val decoded = try {
            LedgerBackupCodec.decrypt(LedgerBackupCodec.encrypt(repository.createBackupSnapshot(), secret), secret)
        } finally { secret.fill('\u0000') }
        assertEquals(mapOf(draftId to "DRAFT", postedId to "POSTED"), decoded.journalEntries.associate { it.id to it.status })
        LedgerBackupValidator.validate(decoded)

        val restoredDb = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, LedgerDatabase::class.java,
        ).addCallback(LedgerDatabase.integrityGuardsCallback()).allowMainThreadQueries().build()
        try {
            val restoredRepository = LedgerRepository(restoredDb)
            restoredRepository.initializeBook()
            restoredRepository.restoreBackup(decoded)
            assertEquals(mapOf(draftId to "DRAFT", postedId to "POSTED"), restoredDb.ledgerDao().getAllEntries().associate { it.entry.id to it.entry.status })
            assertEquals(1L, restoredDb.openHelper.readableDatabase.query("SELECT COUNT(*) FROM posted_entry_locks WHERE entryId = '$postedId'").use { it.moveToFirst(); it.getLong(0) })
            assertEquals(0L, restoredDb.openHelper.readableDatabase.query("SELECT COUNT(*) FROM posted_entry_locks WHERE entryId = '$draftId'").use { it.moveToFirst(); it.getLong(0) })
        } finally { restoredDb.close() }
    }

    @Test fun sqlCannotPostUnbalancedDraftAndValidSqlPostStillLocksLines() = runBlocking {
        repository.initializeBook()
        val badId = repository.saveSimpleDraft("2026-09-20", "Prueba", EntryKind.INCOME, "4000", "1000", 1_000)
        val sql = database.openHelper.writableDatabase
        sql.execSQL("UPDATE journal_entries SET amountMinor = 2, status = 'POSTED' WHERE id = '$badId'")
        assertEquals("DRAFT", database.ledgerDao().getEntry(badId)!!.status)
        assertEquals(1_000L, database.ledgerDao().getEntry(badId)!!.amountMinor)
        assertThrows(android.database.SQLException::class.java) {
            sql.execSQL("UPDATE journal_entries SET entryDate = '2026-02-30', status = 'POSTED' WHERE id = '$badId'")
        }
        assertEquals("DRAFT", database.ledgerDao().getEntry(badId)!!.status)

        val validId = repository.saveSimpleDraft("2026-09-21", "SQL validado", EntryKind.EXPENSE, "5100", "1000", 500)
        sql.execSQL("UPDATE journal_entries SET status = 'POSTED' WHERE id = '$validId'")
        assertEquals("POSTED", database.ledgerDao().getEntry(validId)!!.status)
        assertEquals(1L, sqlLong("SELECT COUNT(*) FROM posted_entry_locks WHERE entryId = '$validId'"))
        assertThrows(android.database.SQLException::class.java) { sql.execSQL("DELETE FROM journal_lines WHERE entryId = '$validId'") }
        assertEquals(1L, sqlLong("SELECT COUNT(*) FROM audit_events WHERE entryId = '$validId' AND eventType = 'DRAFT_POSTED'"))
    }

    private suspend fun summarizePosted(entries: List<EntryWithLines>) = OperatingSummaryCalculator.calculate(
        accounts = LedgerBookDefaults.accounts.map { ReportAccount(it.code, it.name, it.type) },
        postings = entries.filter { it.entry.status == "POSTED" }.flatMap { item ->
            item.lines.map { ReportPostingLine(it.accountCode, it.debitMinor, it.creditMinor, it.currencyCode, item.entry.entryDate) }
        },
    )

    private fun sqlLong(sql: String): Long = database.openHelper.readableDatabase.query(sql).use { it.moveToFirst(); it.getLong(0) }
}
