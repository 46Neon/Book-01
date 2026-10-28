package com.neon.book01.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Embedded
import androidx.room.Relation
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object LedgerBookDefaults {
    val accounts = listOf(
        AccountEntity(code = "1000", name = "Caja", type = "ASSET", cashOrBank = true),
        AccountEntity(code = "1010", name = "Banco", type = "ASSET", cashOrBank = true),
        AccountEntity(code = "2000", name = "Cuentas por pagar", type = "LIABILITY"),
        AccountEntity(code = "3000", name = "Capital", type = "EQUITY"),
        AccountEntity(code = "3100", name = "Utilidades retenidas", type = "EQUITY"),
        AccountEntity(code = "4000", name = "Ingresos", type = "INCOME"),
        AccountEntity(code = "5100", name = "Papelería", type = "EXPENSE"),
        AccountEntity(code = "5200", name = "Servicios", type = "EXPENSE"),
        AccountEntity(code = "5300", name = "Otros gastos", type = "EXPENSE"),
    )
    val settings = BookSettingsEntity(id = 1, bookName = "Libro de prueba", baseCurrency = "USD")
}

@Entity(
    tableName = "accounts",
    foreignKeys = [ForeignKey(entity = AccountEntity::class, parentColumns = ["code"], childColumns = ["parentCode"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("parentCode")],
)
data class AccountEntity(
    @PrimaryKey val code: String,
    val name: String,
    val type: String,
    val active: Boolean = true,
    @ColumnInfo(defaultValue = "0") val cashOrBank: Boolean = false,
    val parentCode: String? = null,
)

@Entity(tableName = "journal_entries", indices = [Index(value = ["reversalOfEntryId"], unique = true)])
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    val entryDate: String,
    val description: String,
    val kind: String,
    val categoryAccountCode: String,
    val paymentAccountCode: String,
    val currencyCode: String,
    val amountMinor: Long,
    val status: String,
    val createdAt: String,
    val reversalOfEntryId: String? = null,
    val counterparty: String? = null,
    val supportReference: String? = null,
)

@Entity(
    tableName = "journal_lines",
    foreignKeys = [
        ForeignKey(
            entity = JournalEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["code"],
            childColumns = ["accountCode"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("entryId"), Index("accountCode")],
)
data class JournalLineEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    val accountCode: String,
    val debitMinor: Long,
    val creditMinor: Long,
    val currencyCode: String,
    @ColumnInfo(defaultValue = "''") val accountNameSnapshot: String,
)

data class EntryWithLines(
    @Embedded val entry: JournalEntryEntity,
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val lines: List<JournalLineEntity>,
)

@Entity(tableName = "book_settings")
data class BookSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val bookName: String,
    val baseCurrency: String = "USD",
)

@Entity(
    tableName = "audit_events",
    foreignKeys = [
        ForeignKey(entity = JournalEntryEntity::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = JournalEntryEntity::class, parentColumns = ["id"], childColumns = ["relatedEntryId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("entryId"), Index("relatedEntryId")],
)
data class AuditEventEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    val eventType: String,
    val occurredAt: String,
    val eventDate: String,
    val reason: String,
    val relatedEntryId: String? = null,
)

@Entity(
    tableName = "account_audit_events",
    foreignKeys = [ForeignKey(entity = AccountEntity::class, parentColumns = ["code"], childColumns = ["accountCode"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("accountCode")],
)
data class AccountAuditEventEntity(
    @PrimaryKey val id: String,
    val accountCode: String,
    val occurredAt: String,
    val oldName: String,
    val newName: String,
    val oldActive: Boolean,
    val newActive: Boolean,
)

@Entity(
    tableName = "period_closes",
    foreignKeys = [
        ForeignKey(entity = JournalEntryEntity::class, parentColumns = ["id"], childColumns = ["closingEntryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["code"], childColumns = ["retainedEarningsAccountCode"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["closingEntryId"], unique = true), Index("retainedEarningsAccountCode"), Index("startDate"), Index("endDate")],
)
data class PeriodCloseEntity(
    @PrimaryKey val id: String,
    val startDate: String,
    val endDate: String,
    val retainedEarningsAccountCode: String,
    val netResultMinor: Long,
    val closingEntryId: String?,
    val closedAt: String,
)

/** Derived lock for a posted journal entry. It is deliberately excluded from backup snapshots. */
@Entity(
    tableName = "posted_entry_locks",
    foreignKeys = [ForeignKey(entity = JournalEntryEntity::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.RESTRICT)],
)
data class PostedEntryLockEntity(
    @PrimaryKey val entryId: String,
)

@Entity(
    tableName = "counterparties",
    foreignKeys = [ForeignKey(entity = AccountEntity::class, parentColumns = ["code"], childColumns = ["defaultAccountCode"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["kind", "displayName"], unique = true), Index("defaultAccountCode")],
)
data class CounterpartyEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val displayName: String,
    val taxId: String? = null,
    val active: Boolean = true,
    val defaultAccountCode: String? = null,
)

@Entity(
    tableName = "invoices",
    foreignKeys = [
        ForeignKey(entity = CounterpartyEntity::class, parentColumns = ["id"], childColumns = ["registeredPartyId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = JournalEntryEntity::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["code"], childColumns = ["cashAccountCode"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["code"], childColumns = ["counterAccountCode"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["kind", "registeredPartyId", "invoiceNumber"], unique = true), Index("entryId"), Index("cashAccountCode"), Index("counterAccountCode")],
)
data class InvoiceEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val invoiceDate: String,
    val invoiceNumber: String,
    val controlNumber: String?,
    val registeredPartyId: String,
    val description: String,
    val taxableBaseMinor: Long,
    val vatRateBasisPoints: Int,
    val vatMinor: Long,
    val totalMinor: Long,
    val paidMinor: Long,
    val paymentCondition: String,
    val dueDate: String?,
    val cashAccountCode: String,
    val counterAccountCode: String,
    val entryId: String,
    val createdAt: String,
)

@Entity(
    tableName = "invoice_settlements",
    foreignKeys = [
        ForeignKey(entity = InvoiceEntity::class, parentColumns = ["id"], childColumns = ["invoiceId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = JournalEntryEntity::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["code"], childColumns = ["cashAccountCode"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("invoiceId"), Index("entryId"), Index("cashAccountCode")],
)
data class InvoiceSettlementEntity(
    @PrimaryKey val id: String,
    val invoiceId: String,
    val amountMinor: Long,
    val settlementDate: String,
    val cashAccountCode: String,
    val entryId: String,
    val createdAt: String,
)

@Database(
    entities = [AccountEntity::class, JournalEntryEntity::class, JournalLineEntity::class, BookSettingsEntity::class, AuditEventEntity::class, AccountAuditEventEntity::class, PeriodCloseEntity::class, PostedEntryLockEntity::class, CounterpartyEntity::class, InvoiceEntity::class, InvoiceSettlementEntity::class],
    version = 12,
    exportSchema = true,
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    companion object {
        // Keep the migration visible to the on-device migration regression test.
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `book_settings` (" +
                        "`id` INTEGER NOT NULL, `bookName` TEXT NOT NULL, " +
                        "`baseCurrency` TEXT NOT NULL, PRIMARY KEY(`id`))",
                )
                database.execSQL(
                    "INSERT OR IGNORE INTO `book_settings` (`id`, `bookName`, `baseCurrency`) " +
                        "VALUES (1, 'Libro de prueba', 'USD')",
                )
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `accounts` ADD COLUMN `cashOrBank` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("UPDATE `accounts` SET `cashOrBank` = 1 WHERE `code` IN ('1000', '1010')")
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `journal_entries` ADD COLUMN `reversalOfEntryId` TEXT")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_journal_entries_reversalOfEntryId` ON `journal_entries` (`reversalOfEntryId`)")
                createAuditTable(database)
                createAuditGuards(database)
            }
        }

        private fun createAuditTable(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `audit_events` (" +
                    "`id` TEXT NOT NULL, `entryId` TEXT NOT NULL, `eventType` TEXT NOT NULL, " +
                    "`occurredAt` TEXT NOT NULL, `eventDate` TEXT NOT NULL, `reason` TEXT NOT NULL, " +
                    "`relatedEntryId` TEXT, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`entryId`) REFERENCES `journal_entries`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                    "FOREIGN KEY(`relatedEntryId`) REFERENCES `journal_entries`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)",
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_events_entryId` ON `audit_events` (`entryId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_events_relatedEntryId` ON `audit_events` (`relatedEntryId`)")
        }

        private fun createAuditGuards(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `audit_events_no_update` BEFORE UPDATE ON `audit_events` BEGIN SELECT RAISE(ABORT, 'audit_events is append-only'); END")
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `audit_events_no_delete` BEFORE DELETE ON `audit_events` BEGIN SELECT RAISE(ABORT, 'audit_events is append-only'); END")
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `journal_lines` ADD COLUMN `accountNameSnapshot` TEXT NOT NULL DEFAULT ''")
                database.execSQL("UPDATE `journal_lines` SET `accountNameSnapshot` = COALESCE((SELECT `name` FROM `accounts` WHERE `accounts`.`code` = `journal_lines`.`accountCode`), `accountCode`)")
                createAccountAuditTable(database)
                createAccountAuditGuards(database)
                createImmutableLedgerGuards(database)
            }
        }

        private fun createAccountAuditTable(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `account_audit_events` (" +
                    "`id` TEXT NOT NULL, `accountCode` TEXT NOT NULL, `occurredAt` TEXT NOT NULL, " +
                    "`oldName` TEXT NOT NULL, `newName` TEXT NOT NULL, `oldActive` INTEGER NOT NULL, `newActive` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`), FOREIGN KEY(`accountCode`) REFERENCES `accounts`(`code`) ON UPDATE NO ACTION ON DELETE RESTRICT)",
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_account_audit_events_accountCode` ON `account_audit_events` (`accountCode`)")
        }

        private fun createAccountAuditGuards(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `account_audit_events_no_update` BEFORE UPDATE ON `account_audit_events` BEGIN SELECT RAISE(ABORT, 'account_audit_events is append-only'); END")
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `account_audit_events_no_delete` BEFORE DELETE ON `account_audit_events` BEGIN SELECT RAISE(ABORT, 'account_audit_events is append-only'); END")
        }

        private fun createImmutableLedgerGuards(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `accounts_code_type_immutable` BEFORE UPDATE OF `code`, `type` ON `accounts` WHEN NEW.`code` != OLD.`code` OR NEW.`type` != OLD.`type` BEGIN SELECT RAISE(ABORT, 'account code and type are immutable'); END")
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `accounts_no_delete` BEFORE DELETE ON `accounts` BEGIN SELECT RAISE(ABORT, 'accounts cannot be deleted'); END")
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `journal_entries_no_update` BEFORE UPDATE ON `journal_entries` WHEN OLD.`status` = 'POSTED' BEGIN SELECT RAISE(ABORT, 'posted journal entries are immutable'); END")
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `journal_entries_no_delete` BEFORE DELETE ON `journal_entries` BEGIN SELECT RAISE(ABORT, 'journal entries cannot be deleted'); END")
        }

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `period_closes` (`id` TEXT NOT NULL, `startDate` TEXT NOT NULL, `endDate` TEXT NOT NULL, `retainedEarningsAccountCode` TEXT NOT NULL, `netResultMinor` INTEGER NOT NULL, `closingEntryId` TEXT, `closedAt` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`closingEntryId`) REFERENCES `journal_entries`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`retainedEarningsAccountCode`) REFERENCES `accounts`(`code`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_period_closes_closingEntryId` ON `period_closes` (`closingEntryId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_period_closes_retainedEarningsAccountCode` ON `period_closes` (`retainedEarningsAccountCode`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_period_closes_startDate` ON `period_closes` (`startDate`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_period_closes_endDate` ON `period_closes` (`endDate`)")
                createPeriodCloseGuards(database)
            }
        }

        private fun createPeriodCloseGuards(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `period_closes_no_update` BEFORE UPDATE ON `period_closes` BEGIN SELECT RAISE(ABORT, 'period_closes is append-only'); END")
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `period_closes_no_delete` BEFORE DELETE ON `period_closes` BEGIN SELECT RAISE(ABORT, 'period_closes is append-only'); END")
        }

        private fun createPostedEntryLocksTable(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `posted_entry_locks` (" +
                    "`entryId` TEXT NOT NULL, PRIMARY KEY(`entryId`), " +
                    "FOREIGN KEY(`entryId`) REFERENCES `journal_entries`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)",
            )
        }

        private fun createPostedEntryLockGuards(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `posted_entry_locks_insert_posted_only` BEFORE INSERT ON `posted_entry_locks` WHEN NOT EXISTS (SELECT 1 FROM `journal_entries` WHERE `id` = NEW.`entryId` AND `status` = 'POSTED') BEGIN SELECT RAISE(ABORT, 'only posted entries can be locked'); END")
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `posted_entry_locks_no_update` BEFORE UPDATE ON `posted_entry_locks` BEGIN SELECT RAISE(ABORT, 'posted entry locks are immutable'); END")
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `posted_entry_locks_no_delete` BEFORE DELETE ON `posted_entry_locks` BEGIN SELECT RAISE(ABORT, 'posted entry locks are immutable'); END")
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `journal_lines_no_insert_after_lock` BEFORE INSERT ON `journal_lines` WHEN EXISTS (SELECT 1 FROM `posted_entry_locks` WHERE `entryId` = NEW.`entryId`) BEGIN SELECT RAISE(ABORT, 'posted journal lines are immutable'); END")
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `journal_lines_no_update_after_lock` BEFORE UPDATE ON `journal_lines` WHEN EXISTS (SELECT 1 FROM `posted_entry_locks` WHERE `entryId` = OLD.`entryId`) OR EXISTS (SELECT 1 FROM `posted_entry_locks` WHERE `entryId` = NEW.`entryId`) OR EXISTS (SELECT 1 FROM `journal_entries` WHERE `id` = OLD.`entryId` AND `status` = 'POSTED') OR EXISTS (SELECT 1 FROM `journal_entries` WHERE `id` = NEW.`entryId` AND `status` = 'POSTED') BEGIN SELECT RAISE(ABORT, 'posted journal lines are immutable'); END")
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `journal_lines_no_delete_after_lock` BEFORE DELETE ON `journal_lines` WHEN EXISTS (SELECT 1 FROM `posted_entry_locks` WHERE `entryId` = OLD.`entryId`) OR EXISTS (SELECT 1 FROM `journal_entries` WHERE `id` = OLD.`entryId` AND `status` = 'POSTED') BEGIN SELECT RAISE(ABORT, 'posted journal lines are immutable'); END")
        }

        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                createPostedEntryLocksTable(database)
                database.execSQL("INSERT INTO `posted_entry_locks` (`entryId`) SELECT `id` FROM `journal_entries` WHERE `status` = 'POSTED'")
                createPostedEntryLockGuards(database)
            }
        }

        private fun createDraftEntryGuards(database: SupportSQLiteDatabase, includeCounterpartyValidation: Boolean = true) {
            database.execSQL("DROP TRIGGER IF EXISTS `journal_entries_no_update`")
            database.execSQL("DROP TRIGGER IF EXISTS `journal_lines_no_update_after_lock`")
            database.execSQL("DROP TRIGGER IF EXISTS `journal_lines_no_delete_after_lock`")
            database.execSQL("CREATE TRIGGER `journal_entries_no_update` BEFORE UPDATE ON `journal_entries` WHEN OLD.`status` = 'POSTED' BEGIN SELECT RAISE(ABORT, 'posted journal entries are immutable'); END")
            val counterpartyGuard = if (includeCounterpartyValidation) "OR (NEW.`counterparty` IS NOT NULL AND (length(trim(NEW.`counterparty`)) = 0 OR length(NEW.`counterparty`) > 80 OR NEW.`counterparty` != trim(NEW.`counterparty`)))" else ""
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `journal_entries_draft_transition_guard` BEFORE UPDATE OF `status` ON `journal_entries` WHEN OLD.`status` != NEW.`status` AND (OLD.`status` != 'DRAFT' OR NEW.`status` != 'POSTED' OR NEW.`kind` NOT IN ('INCOME','EXPENSE') OR NEW.`currencyCode` != 'USD' OR NEW.`amountMinor` <= 0 $counterpartyGuard OR length(NEW.`entryDate`) != 10 OR date(julianday(NEW.`entryDate`)) IS NULL OR date(julianday(NEW.`entryDate`)) != NEW.`entryDate` OR EXISTS (SELECT 1 FROM `period_closes` WHERE NEW.`entryDate` BETWEEN `startDate` AND `endDate`) OR NOT EXISTS (SELECT 1 FROM `accounts` WHERE `code` = NEW.`categoryAccountCode` AND `active` = 1 AND `type` = CASE NEW.`kind` WHEN 'INCOME' THEN 'INCOME' ELSE 'EXPENSE' END) OR NOT EXISTS (SELECT 1 FROM `accounts` WHERE `code` = NEW.`paymentAccountCode` AND `active` = 1 AND `cashOrBank` = 1 AND `type` = 'ASSET') OR (SELECT COUNT(*) FROM `journal_lines` WHERE `entryId` = NEW.`id`) != 2 OR NOT EXISTS (SELECT 1 FROM `journal_lines` WHERE `entryId` = NEW.`id` AND `accountCode` = NEW.`categoryAccountCode` AND `debitMinor` = CASE NEW.`kind` WHEN 'EXPENSE' THEN NEW.`amountMinor` ELSE 0 END AND `creditMinor` = CASE NEW.`kind` WHEN 'INCOME' THEN NEW.`amountMinor` ELSE 0 END) OR NOT EXISTS (SELECT 1 FROM `journal_lines` WHERE `entryId` = NEW.`id` AND `accountCode` = NEW.`paymentAccountCode` AND `debitMinor` = CASE NEW.`kind` WHEN 'INCOME' THEN NEW.`amountMinor` ELSE 0 END AND `creditMinor` = CASE NEW.`kind` WHEN 'EXPENSE' THEN NEW.`amountMinor` ELSE 0 END) OR (SELECT COALESCE(SUM(`debitMinor`),0) FROM `journal_lines` WHERE `entryId` = NEW.`id`) != NEW.`amountMinor` OR (SELECT COALESCE(SUM(`creditMinor`),0) FROM `journal_lines` WHERE `entryId` = NEW.`id`) != NEW.`amountMinor` OR EXISTS (SELECT 1 FROM `journal_lines` WHERE `entryId` = NEW.`id` AND (`currencyCode` != 'USD' OR `debitMinor` < 0 OR `creditMinor` < 0 OR (`debitMinor` = 0) = (`creditMinor` = 0)))) BEGIN SELECT RAISE(ABORT, 'draft failed posting validation'); END")
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `journal_entries_lock_after_post` AFTER UPDATE OF `status` ON `journal_entries` WHEN OLD.`status` = 'DRAFT' AND NEW.`status` = 'POSTED' BEGIN INSERT INTO `posted_entry_locks` (`entryId`) VALUES (NEW.`id`); INSERT INTO `audit_events` (`id`,`entryId`,`eventType`,`occurredAt`,`eventDate`,`reason`,`relatedEntryId`) VALUES (lower(hex(randomblob(16))),NEW.`id`,'DRAFT_POSTED',strftime('%Y-%m-%dT%H:%M:%fZ','now'),NEW.`entryDate`,'Borrador publicado tras validación.',NULL); END")
            createPostedEntryLockGuards(database)
        }

        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                createDraftEntryGuards(database, includeCounterpartyValidation = false)
            }
        }

        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Existing rows intentionally receive NULL; counterparty is optional.
                database.execSQL("ALTER TABLE `journal_entries` ADD COLUMN `counterparty` TEXT")
                createDraftEntryGuards(database)
            }
        }

        private fun createAccountHierarchyGuards(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TRIGGER IF EXISTS `accounts_parent_insert_guard`")
            database.execSQL("CREATE TRIGGER `accounts_parent_insert_guard` BEFORE INSERT ON `accounts` WHEN NEW.`parentCode` IS NOT NULL AND (NEW.`parentCode` = NEW.`code` OR NOT EXISTS (SELECT 1 FROM `accounts` WHERE `code` = NEW.`parentCode` AND `active` = 1 AND `type` = NEW.`type`)) BEGIN SELECT RAISE(ABORT, 'account parent must exist, be active, and have the same type'); END")
            database.execSQL("DROP TRIGGER IF EXISTS `accounts_parent_update_guard`")
            database.execSQL("CREATE TRIGGER `accounts_parent_update_guard` BEFORE UPDATE OF `parentCode` ON `accounts` WHEN NEW.`parentCode` IS NOT OLD.`parentCode` BEGIN SELECT RAISE(ABORT, 'account parent is immutable after creation'); END")
            database.execSQL("DROP TRIGGER IF EXISTS `accounts_parent_active_guard`")
            database.execSQL("CREATE TRIGGER `accounts_parent_active_guard` BEFORE UPDATE OF `active` ON `accounts` WHEN OLD.`active` = 1 AND NEW.`active` = 0 AND EXISTS (SELECT 1 FROM `accounts` WHERE `parentCode` = OLD.`code`) BEGIN SELECT RAISE(ABORT, 'an account with child accounts must remain active'); END")
        }

        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `accounts` ADD COLUMN `parentCode` TEXT REFERENCES `accounts`(`code`) ON UPDATE NO ACTION ON DELETE RESTRICT")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_parentCode` ON `accounts` (`parentCode`)")
                createAccountHierarchyGuards(database)
            }
        }

        private fun supportReferenceInvalidCondition(): String {
            val controls = (0..31).plus(127).joinToString(" OR ") {
                "instr(NEW.`supportReference`, char($it)) > 0"
            }
            return "length(trim(NEW.`supportReference`)) = 0 OR length(NEW.`supportReference`) > 120 OR NEW.`supportReference` != trim(NEW.`supportReference`) OR ($controls)"
        }

        private fun createSupportReferenceGuards(database: SupportSQLiteDatabase) {
            val invalid = supportReferenceInvalidCondition()
            database.execSQL("DROP TRIGGER IF EXISTS `journal_entries_support_reference_insert_guard`")
            database.execSQL("CREATE TRIGGER `journal_entries_support_reference_insert_guard` BEFORE INSERT ON `journal_entries` WHEN NEW.`supportReference` IS NOT NULL AND ($invalid) BEGIN SELECT RAISE(ABORT, 'support reference is invalid'); END")
            database.execSQL("DROP TRIGGER IF EXISTS `journal_entries_support_reference_update_guard`")
            database.execSQL("CREATE TRIGGER `journal_entries_support_reference_update_guard` BEFORE UPDATE OF `supportReference` ON `journal_entries` WHEN NEW.`supportReference` IS NOT NULL AND ($invalid) BEGIN SELECT RAISE(ABORT, 'support reference is invalid'); END")
        }

        internal val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `journal_entries` ADD COLUMN `supportReference` TEXT")
                createSupportReferenceGuards(database)
            }
        }

        internal val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("INSERT OR IGNORE INTO `accounts` (`code`,`name`,`type`,`active`,`cashOrBank`,`parentCode`) VALUES ('1100','Cuentas por cobrar','ASSET',1,0,NULL),('1105','IVA crédito fiscal (provisional)','ASSET',1,0,NULL),('2102','Préstamos bancarios por pagar','LIABILITY',1,0,NULL),('2103','IVA débito fiscal (provisional)','LIABILITY',1,0,NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `counterparties` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `displayName` TEXT NOT NULL, `taxId` TEXT, `active` INTEGER NOT NULL, `defaultAccountCode` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`defaultAccountCode`) REFERENCES `accounts`(`code`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_counterparties_defaultAccountCode` ON `counterparties` (`defaultAccountCode`)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_counterparties_kind_displayName` ON `counterparties` (`kind`, `displayName`)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `invoices` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `invoiceDate` TEXT NOT NULL, `invoiceNumber` TEXT NOT NULL, `controlNumber` TEXT, `registeredPartyId` TEXT NOT NULL, `description` TEXT NOT NULL, `taxableBaseMinor` INTEGER NOT NULL, `vatRateBasisPoints` INTEGER NOT NULL, `vatMinor` INTEGER NOT NULL, `totalMinor` INTEGER NOT NULL, `paidMinor` INTEGER NOT NULL, `paymentCondition` TEXT NOT NULL, `dueDate` TEXT, `cashAccountCode` TEXT NOT NULL, `counterAccountCode` TEXT NOT NULL, `entryId` TEXT NOT NULL, `createdAt` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`registeredPartyId`) REFERENCES `counterparties`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`entryId`) REFERENCES `journal_entries`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`cashAccountCode`) REFERENCES `accounts`(`code`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`counterAccountCode`) REFERENCES `accounts`(`code`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_invoices_kind_registeredPartyId_invoiceNumber` ON `invoices` (`kind`, `registeredPartyId`, `invoiceNumber`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_invoices_entryId` ON `invoices` (`entryId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_invoices_cashAccountCode` ON `invoices` (`cashAccountCode`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_invoices_counterAccountCode` ON `invoices` (`counterAccountCode`)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `invoice_settlements` (`id` TEXT NOT NULL, `invoiceId` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `settlementDate` TEXT NOT NULL, `cashAccountCode` TEXT NOT NULL, `entryId` TEXT NOT NULL, `createdAt` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`invoiceId`) REFERENCES `invoices`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`entryId`) REFERENCES `journal_entries`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`cashAccountCode`) REFERENCES `accounts`(`code`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_invoice_settlements_invoiceId` ON `invoice_settlements` (`invoiceId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_invoice_settlements_entryId` ON `invoice_settlements` (`entryId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_invoice_settlements_cashAccountCode` ON `invoice_settlements` (`cashAccountCode`)")
                createInvoiceGuards(database)
            }
        }

        private fun createInvoiceGuards(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `invoices_insert_guard` BEFORE INSERT ON `invoices` WHEN NEW.`kind` NOT IN ('SALE','PURCHASE') OR NEW.`invoiceDate` != date(NEW.`invoiceDate`) OR NEW.`invoiceNumber` != trim(NEW.`invoiceNumber`) OR length(NEW.`invoiceNumber`) = 0 OR NEW.`registeredPartyId` != trim(NEW.`registeredPartyId`) OR NEW.`taxableBaseMinor` <= 0 OR NEW.`vatRateBasisPoints` < 0 OR NEW.`vatRateBasisPoints` > 100000 OR NEW.`vatMinor` < 0 OR NEW.`totalMinor` != NEW.`taxableBaseMinor` + NEW.`vatMinor` OR NEW.`paidMinor` < 0 OR NEW.`paidMinor` > NEW.`totalMinor` OR NEW.`paymentCondition` NOT IN ('CASH','CREDIT') OR (NEW.`kind` = 'SALE' AND (NEW.`controlNumber` IS NULL OR length(trim(NEW.`controlNumber`)) = 0)) OR NOT EXISTS (SELECT 1 FROM `counterparties` WHERE `id`=NEW.`registeredPartyId` AND `active`=1 AND `kind`=CASE NEW.`kind` WHEN 'SALE' THEN 'CLIENT' ELSE 'SUPPLIER' END) OR NOT EXISTS (SELECT 1 FROM `journal_entries` WHERE `id`=NEW.`entryId` AND `status`='POSTED' AND `entryDate`=NEW.`invoiceDate`) BEGIN SELECT RAISE(ABORT, 'invoice validation failed'); END")
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `invoice_settlement_insert_guard` BEFORE INSERT ON `invoice_settlements` WHEN NEW.`amountMinor` <= 0 OR NEW.`settlementDate` != date(NEW.`settlementDate`) OR NOT EXISTS (SELECT 1 FROM `invoices` WHERE `id`=NEW.`invoiceId`) OR NOT EXISTS (SELECT 1 FROM `journal_entries` WHERE `id`=NEW.`entryId` AND `status`='POSTED' AND `entryDate`=NEW.`settlementDate`) OR COALESCE((SELECT SUM(`amountMinor`) FROM `invoice_settlements` WHERE `invoiceId`=NEW.`invoiceId`),0) + NEW.`amountMinor` > (SELECT `totalMinor` FROM `invoices` WHERE `id`=NEW.`invoiceId`) BEGIN SELECT RAISE(ABORT, 'invoice settlement validation failed'); END")
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `counterparties_no_update` BEFORE UPDATE ON `counterparties` BEGIN SELECT RAISE(ABORT, 'counterparties are immutable; correction history required'); END")
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `counterparties_no_delete` BEFORE DELETE ON `counterparties` BEGIN SELECT RAISE(ABORT, 'counterparties cannot be deleted'); END")
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `invoices_no_update` BEFORE UPDATE ON `invoices` BEGIN SELECT RAISE(ABORT, 'invoices are immutable; use correction history'); END")
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `invoices_no_delete` BEFORE DELETE ON `invoices` BEGIN SELECT RAISE(ABORT, 'invoices cannot be deleted'); END")
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `invoice_settlements_no_update` BEFORE UPDATE ON `invoice_settlements` BEGIN SELECT RAISE(ABORT, 'settlements are immutable'); END")
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `invoice_settlements_no_delete` BEFORE DELETE ON `invoice_settlements` BEGIN SELECT RAISE(ABORT, 'settlements are immutable'); END")
        }

        internal fun integrityGuardsCallback(): RoomDatabase.Callback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                createAuditGuards(db)
                createAccountAuditGuards(db)
                createImmutableLedgerGuards(db)
                createPeriodCloseGuards(db)
                createPostedEntryLockGuards(db)
                createDraftEntryGuards(db)
                createAccountHierarchyGuards(db)
                createSupportReferenceGuards(db)
                createInvoiceGuards(db)
            }
        }

        @Volatile
        private var instance: LedgerDatabase? = null

        fun getInstance(context: Context): LedgerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LedgerDatabase::class.java,
                    "book-01-ledger.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                    .addCallback(integrityGuardsCallback())
                    .build().also { instance = it }
            }
    }
}
