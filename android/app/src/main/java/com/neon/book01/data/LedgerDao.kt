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

    @Query("SELECT * FROM journal_entries WHERE status = 'DRAFT' ORDER BY entryDate DESC, createdAt DESC")
    @Transaction
    fun observeDraftEntries(): Flow<List<EntryWithLines>>

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

    @Query("SELECT EXISTS(SELECT 1 FROM journal_entries WHERE reversalOfEntryId = :entryId)")
    fun observeHasReversal(entryId: String): Flow<Boolean>

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

    @Query("SELECT COUNT(*) FROM invoices")
    suspend fun countInvoices(): Int

    @Query("SELECT COUNT(*) FROM invoice_settlements")
    suspend fun countInvoiceSettlements(): Int

    @Query("SELECT COUNT(*) FROM counterparties")
    suspend fun countCounterparties(): Int

    @Query("SELECT * FROM counterparties ORDER BY kind, displayName")
    suspend fun getAllCounterparties(): List<CounterpartyEntity>

    @Query("SELECT * FROM invoices ORDER BY invoiceDate, invoiceNumber")
    suspend fun getAllInvoices(): List<InvoiceEntity>

    @Query("SELECT * FROM invoice_settlements ORDER BY settlementDate, createdAt")
    suspend fun getAllInvoiceSettlements(): List<InvoiceSettlementEntity>

    @Query("SELECT * FROM accounts WHERE code = :code LIMIT 1")
    suspend fun getAccount(code: String): AccountEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM accounts WHERE parentCode = :code)")
    suspend fun hasChildAccounts(code: String): Boolean

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

    @Update
    suspend fun updateEntry(entry: JournalEntryEntity)

    @Query("DELETE FROM journal_lines WHERE entryId = :entryId")
    suspend fun deleteLinesForDraft(entryId: String)

    @Insert
    suspend fun insertLines(lines: List<JournalLineEntity>)

    @Insert
    suspend fun insertPostedEntryLock(lock: PostedEntryLockEntity)

    @Insert
    suspend fun insertAuditEvent(event: AuditEventEntity)

    @Insert
    suspend fun insertAccountAuditEvent(event: AccountAuditEventEntity)

    @Insert
    suspend fun insertPeriodClose(periodClose: PeriodCloseEntity)

    @Insert
    suspend fun insertCounterparty(counterparty: CounterpartyEntity)

    @Query("SELECT * FROM counterparties WHERE id = :id LIMIT 1")
    suspend fun getCounterparty(id: String): CounterpartyEntity?

    @Query("SELECT * FROM counterparties WHERE kind = :kind ORDER BY displayName")
    fun observeCounterparties(kind: String): Flow<List<CounterpartyEntity>>

    @Insert
    suspend fun insertInvoice(invoice: InvoiceEntity)

    @Query("SELECT * FROM invoices WHERE id = :invoiceId LIMIT 1")
    suspend fun getInvoice(invoiceId: String): InvoiceEntity?

    @Query("SELECT * FROM invoices WHERE kind = :kind AND registeredPartyId = :partyId AND invoiceNumber = :invoiceNumber LIMIT 1")
    suspend fun findInvoice(kind: String, partyId: String, invoiceNumber: String): InvoiceEntity?

    @Query("SELECT * FROM invoices ORDER BY invoiceDate DESC, createdAt DESC")
    fun observeInvoices(): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE id = :invoiceId LIMIT 1")
    fun observeInvoice(invoiceId: String): Flow<InvoiceEntity?>

    @Insert
    suspend fun insertInvoiceSettlement(settlement: InvoiceSettlementEntity)

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM invoice_settlements WHERE invoiceId = :invoiceId")
    suspend fun totalSettled(invoiceId: String): Long

    @Query("SELECT * FROM invoice_settlements WHERE invoiceId = :invoiceId ORDER BY settlementDate, createdAt")
    fun observeInvoiceSettlements(invoiceId: String): Flow<List<InvoiceSettlementEntity>>

    @Query("SELECT COALESCE(SUM(debitMinor - creditMinor), 0) FROM journal_lines INNER JOIN journal_entries ON journal_entries.id = journal_lines.entryId WHERE journal_entries.status = 'POSTED' AND journal_lines.accountCode = :accountCode")
    suspend fun accountBalanceMinor(accountCode: String): Long
}
