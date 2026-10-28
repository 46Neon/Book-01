package com.neon.book01.data

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented runtime regression test for the full v1→v12 chain without losing or unlocking postings. */
@RunWith(AndroidJUnit4::class)
class LedgerMigrationTest {
    @Test
    fun versionOneBookMigratesToUsdSettingsAndPreservesExistingJournal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "migration-v1-test.db"
        context.deleteDatabase(databaseName)
        createVersionOneDatabase(context, databaseName)

        val upgraded = Room.databaseBuilder(context, LedgerDatabase::class.java, databaseName)
            .addMigrations(LedgerDatabase.MIGRATION_1_2, LedgerDatabase.MIGRATION_2_3, LedgerDatabase.MIGRATION_3_4, LedgerDatabase.MIGRATION_4_5, LedgerDatabase.MIGRATION_5_6, LedgerDatabase.MIGRATION_6_7, LedgerDatabase.MIGRATION_7_8, LedgerDatabase.MIGRATION_8_9, LedgerDatabase.MIGRATION_9_10, LedgerDatabase.MIGRATION_10_11, LedgerDatabase.MIGRATION_11_12)
            .build()
        try {
            val db = upgraded.openHelper.writableDatabase
            assertEquals(12L, db.longValue("PRAGMA user_version"))
            assertEquals(1L, db.longValue("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='counterparties'"))
            assertEquals(1L, db.longValue("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='invoices'"))
            assertEquals(1L, db.longValue("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='invoice_settlements'"))
            assertEquals(1L, db.longValue("SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND name='invoices_no_update'"))
            assertEquals(1L, db.longValue("SELECT COUNT(*) FROM book_settings WHERE id = 1"))
            assertEquals("USD", db.stringValue("SELECT baseCurrency FROM book_settings WHERE id = 1"))
            assertEquals("Libro de prueba", db.stringValue("SELECT bookName FROM book_settings WHERE id = 1"))
            assertEquals(1L, db.longValue("SELECT COUNT(*) FROM journal_entries WHERE id = 'entry-v1'"))
            assertEquals(1L, db.longValue("SELECT COUNT(*) FROM pragma_table_info('journal_entries') WHERE name = 'counterparty'"))
            assertEquals(2L, db.longValue("SELECT COUNT(*) FROM journal_entries WHERE counterparty IS NULL"))
            assertEquals(1L, db.longValue("SELECT COUNT(*) FROM pragma_table_info('journal_entries') WHERE name = 'supportReference'"))
            assertEquals(2L, db.longValue("SELECT COUNT(*) FROM journal_entries WHERE supportReference IS NULL"))
            assertEquals(2L, db.longValue("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name LIKE 'journal_entries_support_reference_%_guard'"))
            db.execSQL("UPDATE journal_entries SET supportReference = 'REC-2026-001' WHERE id = 'draft-v1'")
            assertEquals("REC-2026-001", db.stringValue("SELECT supportReference FROM journal_entries WHERE id = 'draft-v1'"))
            assertThrows(android.database.SQLException::class.java) {
                db.execSQL("UPDATE journal_entries SET supportReference = ' REC-2026-001 ' WHERE id = 'draft-v1'")
            }
            assertThrows(android.database.SQLException::class.java) {
                db.execSQL("UPDATE journal_entries SET supportReference = 'BAD\nREF' WHERE id = 'draft-v1'")
            }
            assertThrows(android.database.SQLException::class.java) {
                db.execSQL("UPDATE journal_entries SET supportReference = ? WHERE id = 'draft-v1'", arrayOf("X".repeat(121)))
            }
            assertEquals(2L, db.longValue("SELECT COUNT(*) FROM journal_lines WHERE entryId = 'entry-v1'"))
            assertEquals(1L, db.longValue("SELECT COUNT(*) FROM posted_entry_locks WHERE entryId = 'entry-v1'"))
            assertEquals("DRAFT", db.stringValue("SELECT status FROM journal_entries WHERE id = 'draft-v1'"))
            assertEquals(0L, db.longValue("SELECT COUNT(*) FROM posted_entry_locks WHERE entryId = 'draft-v1'"))
            assertEquals(1L, db.longValue("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name = 'journal_lines_no_insert_after_lock'"))
            assertEquals(1L, db.longValue("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name = 'journal_entries_draft_transition_guard'"))
            assertEquals(2L, db.longValue("SELECT COUNT(*) FROM journal_lines WHERE accountNameSnapshot = 'Caja'"))
            assertEquals(2L, db.longValue("SELECT COUNT(*) FROM journal_lines WHERE accountNameSnapshot = 'Ingresos'"))
            assertEquals(4L, db.longValue("SELECT COUNT(*) FROM journal_lines WHERE accountNameSnapshot != ''"))
            assertEquals(100L, db.longValue("SELECT amountMinor FROM journal_entries WHERE id = 'entry-v1'"))
            assertEquals("USD", db.stringValue("SELECT currencyCode FROM journal_entries WHERE id = 'entry-v1'"))
            assertEquals(1L, db.longValue("SELECT cashOrBank FROM accounts WHERE code = '1000'"))
            assertEquals(1L, db.longValue("SELECT cashOrBank FROM accounts WHERE code = '1010'"))
            assertEquals(0L, db.longValue("SELECT cashOrBank FROM accounts WHERE code = '4000'"))
            assertEquals(7L, db.longValue("SELECT COUNT(*) FROM accounts WHERE parentCode IS NULL"))
            assertEquals(4L, db.longValue("SELECT COUNT(*) FROM accounts WHERE code IN ('1100','1105','2102','2103')"))
            assertEquals(1L, db.longValue("SELECT COUNT(*) FROM pragma_table_info('accounts') WHERE name = 'parentCode'"))
            assertEquals(1L, db.longValue("SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'index_accounts_parentCode'"))
            assertEquals(3L, db.longValue("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name LIKE 'accounts_parent_%'"))
            assertEquals(0L, db.longValue("SELECT COUNT(*) FROM audit_events"))
            assertEquals(1L, db.longValue("SELECT COUNT(*) FROM pragma_table_info('journal_entries') WHERE name = 'reversalOfEntryId'"))
            assertEquals(1L, db.longValue("SELECT COUNT(*) FROM journal_entries WHERE id = 'entry-v1' AND reversalOfEntryId IS NULL"))
            assertEquals(1L, db.longValue("SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'index_journal_entries_reversalOfEntryId'"))
            assertEquals(2L, db.longValue("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name LIKE 'audit_events_no_%'"))
            assertEquals(2L, db.longValue("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name LIKE 'account_audit_events_no_%'"))
            assertEquals(1L, db.longValue("SELECT COUNT(*) FROM pragma_table_info('audit_events') WHERE name = 'reason'"))
            assertEquals(1L, db.longValue("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'period_closes'"))
            assertEquals(0L, db.longValue("SELECT COUNT(*) FROM period_closes"))
            assertEquals(0L, db.longValue("SELECT \"notnull\" FROM pragma_table_info('period_closes') WHERE name = 'closingEntryId'"))
            assertEquals(2L, db.longValue("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name LIKE 'period_closes_no_%'"))
            assertThrows(android.database.SQLException::class.java) {
                db.execSQL("UPDATE journal_lines SET debitMinor = debitMinor + 1 WHERE id = 'line-1'")
            }
            assertThrows(android.database.SQLException::class.java) {
                db.execSQL("DELETE FROM journal_lines WHERE id = 'line-1'")
            }
            assertThrows(android.database.SQLException::class.java) {
                db.execSQL("INSERT INTO journal_lines (id, entryId, accountCode, debitMinor, creditMinor, currencyCode, accountNameSnapshot) VALUES ('late-line', 'entry-v1', '1000', 1, 0, 'USD', 'Caja')")
            }
            db.execSQL("INSERT INTO accounts (code,name,type,active,cashOrBank,parentCode) VALUES ('5000','Gastos','EXPENSE',1,0,NULL)")
            db.execSQL("INSERT INTO accounts (code,name,type,active,cashOrBank,parentCode) VALUES ('5001','Servicios','EXPENSE',1,0,'5000')")
            assertEquals("5000", db.stringValue("SELECT parentCode FROM accounts WHERE code = '5001'"))
            assertThrows(android.database.SQLException::class.java) {
                db.execSQL("INSERT INTO accounts (code,name,type,active,cashOrBank,parentCode) VALUES ('6000','Banco','ASSET',1,0,'5000')")
            }
            assertThrows(android.database.SQLException::class.java) {
                db.execSQL("UPDATE accounts SET parentCode = NULL WHERE code = '5001'")
            }
            assertThrows(android.database.SQLException::class.java) {
                db.execSQL("UPDATE accounts SET active = 0 WHERE code = '5000'")
            }
            assertThrows(android.database.SQLException::class.java) { db.execSQL("DELETE FROM accounts WHERE code = '5000'") }
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
            db.execSQL("INSERT INTO journal_entries VALUES ('draft-v1', '2026-01-02', 'Borrador de prueba', 'INCOME', '4000', '1000', 'USD', 50, 'DRAFT', '2026-01-02T00:00:00Z')")
            db.execSQL("INSERT INTO journal_lines VALUES ('draft-line-1', 'draft-v1', '1000', 50, 0, 'USD')")
            db.execSQL("INSERT INTO journal_lines VALUES ('draft-line-2', 'draft-v1', '4000', 0, 50, 'USD')")
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
