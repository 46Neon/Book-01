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

    @Query("SELECT * FROM audit_events ORDER BY id")
    suspend fun getAllAuditEvents(): List<AuditEventEntity>

    @Query("SELECT * FROM account_audit_events ORDER BY id")
    suspend fun getAllAccountAuditEvents(): List<AccountAuditEventEntity>

    @Query("SELECT * FROM period_closes ORDER BY id")
    suspend fun getAllPeriodCloses(): List<PeriodCloseEntity>

    @Query("SELECT * FROM audit_events ORDER BY occurredAt DESC")
    fun observeAuditEvents(): Flow<List<AuditEventEntity>>

    @Query("SELECT * FROM account_audit_events ORDER BY occurredAt DESC")
    fun observeAccountAuditEvents(): Flow<List<AccountAuditEventEntity>>

    @Query("SELECT * FROM period_closes ORDER BY startDate")
    fun observePeriodCloses(): Flow<List<PeriodCloseEntity>>

    @Query("SELECT * FROM period_closes ORDER BY startDate")
    suspend fun getPeriodCloses(): List<PeriodCloseEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM period_closes WHERE :dateIso BETWEEN startDate AND endDate)")
    suspend fun isDateClosed(dateIso: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM period_closes WHERE startDate <= :endDate AND endDate >= :startDate)")
    suspend fun hasOverlappingClose(startDate: String, endDate: String): Boolean

    @Query("SELECT * FROM journal_entries WHERE id = :entryId LIMIT 1")
    suspend fun getEntry(entryId: String): JournalEntryEntity?

    @Query("SELECT * FROM journal_lines WHERE entryId = :entryId ORDER BY id")
    suspend fun getLines(entryId: String): List<JournalLineEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM journal_entries WHERE reversalOfEntryId = :entryId)")
    suspend fun hasReversal(entryId: String): Boolean

    @Query("SELECT * FROM accounts ORDER BY code")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY code")
    suspend fun getAllAccounts(): List<AccountEntity>

    @Query("SELECT * FROM journal_lines ORDER BY id")
    suspend fun getAllJournalLines(): List<JournalLineEntity>

    @Query("SELECT * FROM book_settings ORDER BY id")
    suspend fun getAllBookSettings(): List<BookSettingsEntity>

    @Query("SELECT COUNT(*) FROM journal_lines")
    suspend fun countJournalLines(): Int

    @Query("SELECT COUNT(*) FROM journal_entries")
    suspend fun countJournalEntriesRaw(): Int

    @Query("SELECT COUNT(*) FROM audit_events")
    suspend fun countAuditEvents(): Int

    @Query("SELECT COUNT(*) FROM account_audit_events")
    suspend fun countAccountAuditEvents(): Int

    @Query("SELECT COUNT(*) FROM period_closes")
    suspend fun countPeriodCloses(): Int

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

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("SELECT COUNT(*) FROM journal_entries")
    suspend fun countEntries(): Int

    @Insert
    suspend fun insertEntry(entry: JournalEntryEntity)

    @Insert
    suspend fun insertLines(lines: List<JournalLineEntity>)

    @Insert
    suspend fun insertAuditEvent(event: AuditEventEntity)

    @Insert
    suspend fun insertAccountAuditEvent(event: AccountAuditEventEntity)

    @Insert
    suspend fun insertPeriodClose(periodClose: PeriodCloseEntity)
}
