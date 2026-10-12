package com.neon.book01.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Query("SELECT * FROM journal_entries ORDER BY entryDate DESC, createdAt DESC")
    @Transaction
    fun observeEntries(): Flow<List<EntryWithLines>>

    @Query("SELECT * FROM accounts WHERE active = 1 ORDER BY code")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE code = :code LIMIT 1")
    suspend fun getAccount(code: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAccounts(accounts: List<AccountEntity>)

    @Insert
    suspend fun insertEntry(entry: JournalEntryEntity)

    @Insert
    suspend fun insertLines(lines: List<JournalLineEntity>)
}
