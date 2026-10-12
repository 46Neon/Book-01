package com.neon.book01.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Query("SELECT * FROM journal_entries ORDER BY entryDate DESC, createdAt DESC")
    @Transaction
    fun observeEntries(): Flow<List<EntryWithLines>>

    @Query("SELECT * FROM journal_entries ORDER BY entryDate DESC, createdAt DESC")
    @Transaction
    suspend fun getAllEntries(): List<EntryWithLines>

    @Query("SELECT * FROM audit_events ORDER BY occurredAt DESC")
    fun observeAuditEvents(): Flow<List<AuditEventEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :entryId LIMIT 1")
    suspend fun getEntry(entryId: String): JournalEntryEntity?

    @Query("SELECT * FROM journal_lines WHERE entryId = :entryId ORDER BY id")
    suspend fun getLines(entryId: String): List<JournalLineEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM journal_entries WHERE reversalOfEntryId = :entryId)")
    suspend fun hasReversal(entryId: String): Boolean

    @Query("SELECT * FROM accounts ORDER BY code")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE code = :code LIMIT 1")
    suspend fun getAccount(code: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAccounts(accounts: List<AccountEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAccount(account: AccountEntity)

    @Query("SELECT * FROM book_settings WHERE id = 1 LIMIT 1")
    fun observeBookSettings(): Flow<BookSettingsEntity?>

    @Query("SELECT * FROM book_settings WHERE id = 1 LIMIT 1")
    suspend fun getBookSettings(): BookSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookSettings(settings: BookSettingsEntity)

    @Update
    suspend fun updateBookSettings(settings: BookSettingsEntity)

    @Query("SELECT COUNT(*) FROM journal_entries")
    suspend fun countEntries(): Int

    @Insert
    suspend fun insertEntry(entry: JournalEntryEntity)

    @Insert
    suspend fun insertLines(lines: List<JournalLineEntity>)

    @Insert
    suspend fun insertAuditEvent(event: AuditEventEntity)
}
