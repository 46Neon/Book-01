package com.neon.book01.data

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented runtime regression test for the full v1→v6 chain without losing postings. */
@RunWith(AndroidJUnit4::class)
class LedgerMigrationTest {
    @Test
    fun versionOneBookMigratesToUsdSettingsAndPreservesExistingJournal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "migration-v1-test.db"
        context.deleteDatabase(databaseName)
        createVersionOneDatabase(context, databaseName)

        val upgraded = Room.databaseBuilder(context, LedgerDatabase::class.java, databaseName)
            .addMigrations(LedgerDatabase.MIGRATION_1_2, LedgerDatabase.MIGRATION_2_3, LedgerDatabase.MIGRATION_3_4, LedgerDatabase.MIGRATION_4_5, LedgerDatabase.MIGRATION_5_6)
            .build()
        try {
            val db = upgraded.openHelper.writableDatabase
            assertEquals(6L, db.longValue("PRAGMA user_version"))
            assertEquals(1L, db.longValue("SELECT COUNT(*) FROM book_settings WHERE id = 1"))
            assertEquals("USD", db.stringValue("SELECT baseCurrency FROM book_settings WHERE id = 1"))
            assertEquals("Libro de prueba", db.stringValue("SELECT bookName FROM book_settings WHERE id = 1"))
            assertEquals(1L, db.longValue("SELECT COUNT(*) FROM journal_entries WHERE id = 'entry-v1'"))
            assertEquals(2L, db.longValue("SELECT COUNT(*) FROM journal_lines WHERE entryId = 'entry-v1'"))
            assertEquals(1L, db.longValue("SELECT COUNT(*) FROM journal_lines WHERE accountNameSnapshot = 'Caja'"))
            assertEquals(1L, db.longValue("SELECT COUNT(*) FROM journal_lines WHERE accountNameSnapshot = 'Ingresos'"))
            assertEquals(2L, db.longValue("SELECT COUNT(*) FROM journal_lines WHERE accountNameSnapshot != ''"))
            assertEquals(100L, db.longValue("SELECT amountMinor FROM journal_entries WHERE id = 'entry-v1'"))
            assertEquals("USD", db.stringValue("SELECT currencyCode FROM journal_entries WHERE id = 'entry-v1'"))
            assertEquals(1L, db.longValue("SELECT cashOrBank FROM accounts WHERE code = '1000'"))
            assertEquals(1L, db.longValue("SELECT cashOrBank FROM accounts WHERE code = '1010'"))
            assertEquals(0L, db.longValue("SELECT cashOrBank FROM accounts WHERE code = '4000'"))
            assertEquals(0L, db.longValue("SELECT COUNT(*) FROM audit_events"))
            assertEquals(1L, db.longValue("SELECT COUNT(*) FROM pragma_table_info('journal_entries') WHERE name = 'reversalOfEntryId'"))
            assertEquals(1L, db.longValue("SELECT COUNT(*) FROM journal_entries WHERE id = 'entry-v1' AND reversalOfEntryId IS NULL"))
            assertEquals(1L, db.longValue("SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'index_journal_entries_reversalOfEntryId'"))
            assertEquals(2L, db.longValue("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name LIKE 'audit_events_no_%'"))
            assertEquals(2L, db.longValue("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name LIKE 'account_audit_events_no_%'"))
            assertEquals(1L, db.longValue("SELECT COUNT(*) FROM pragma_table_info('audit_events') WHERE name = 'reason'"))
            assertEquals(1L, db.longValue("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'period_closes'"))
            assertEquals(0L, db.longValue("SELECT COUNT(*) FROM period_closes"))
            assertEquals(2L, db.longValue("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name LIKE 'period_closes_no_%'"))
        } finally {
            upgraded.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun createVersionOneDatabase(context: Context, name: String) {
        val db = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        try {
            db.execSQL("""CREATE TABLE IF NOT EXISTS accounts (
                code TEXT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL,
                active INTEGER NOT NULL, PRIMARY KEY(code))""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS journal_entries (
                id TEXT NOT NULL, entryDate TEXT NOT NULL, description TEXT NOT NULL,
                kind TEXT NOT NULL, categoryAccountCode TEXT NOT NULL,
                paymentAccountCode TEXT NOT NULL, currencyCode TEXT NOT NULL,
                amountMinor INTEGER NOT NULL, status TEXT NOT NULL, createdAt TEXT NOT NULL,
                PRIMARY KEY(id))""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS journal_lines (
                id TEXT NOT NULL, entryId TEXT NOT NULL, accountCode TEXT NOT NULL,
                debitMinor INTEGER NOT NULL, creditMinor INTEGER NOT NULL,
                currencyCode TEXT NOT NULL, PRIMARY KEY(id),
                FOREIGN KEY(entryId) REFERENCES journal_entries(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(accountCode) REFERENCES accounts(code) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_journal_lines_entryId ON journal_lines(entryId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_journal_lines_accountCode ON journal_lines(accountCode)")
            db.execSQL("INSERT INTO accounts VALUES ('1000', 'Caja', 'ASSET', 1)")
            db.execSQL("INSERT INTO accounts VALUES ('1010', 'Banco', 'ASSET', 1)")
            db.execSQL("INSERT INTO accounts VALUES ('4000', 'Ingresos', 'INCOME', 1)")
            db.execSQL("INSERT INTO journal_entries VALUES ('entry-v1', '2026-01-01', 'Ingreso de prueba', 'INCOME', '4000', '1000', 'USD', 100, 'POSTED', '2026-01-01T00:00:00Z')")
            db.execSQL("INSERT INTO journal_lines VALUES ('line-1', 'entry-v1', '1000', 100, 0, 'USD')")
            db.execSQL("INSERT INTO journal_lines VALUES ('line-2', 'entry-v1', '4000', 0, 100, 'USD')")
            db.version = 1
        } finally {
            db.close()
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.longValue(sql: String): Long =
        query(sql).use { cursor: Cursor ->
            check(cursor.moveToFirst()) { "Query returned no rows: $sql" }
            cursor.getLong(0)
        }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.stringValue(sql: String): String =
        query(sql).use { cursor: Cursor ->
            check(cursor.moveToFirst()) { "Query returned no rows: $sql" }
            cursor.getString(0)
        }
}
