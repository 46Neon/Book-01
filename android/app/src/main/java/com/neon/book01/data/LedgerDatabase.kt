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

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val code: String,
    val name: String,
    val type: String,
    val active: Boolean = true,
    @ColumnInfo(defaultValue = "0") val cashOrBank: Boolean = false,
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

@Database(
    entities = [AccountEntity::class, JournalEntryEntity::class, JournalLineEntity::class, BookSettingsEntity::class, AuditEventEntity::class, AccountAuditEventEntity::class],
    version = 5,
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
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `journal_entries_no_update` BEFORE UPDATE ON `journal_entries` BEGIN SELECT RAISE(ABORT, 'posted journal entries are immutable'); END")
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `journal_entries_no_delete` BEFORE DELETE ON `journal_entries` BEGIN SELECT RAISE(ABORT, 'journal entries cannot be deleted'); END")
        }

        @Volatile
        private var instance: LedgerDatabase? = null

        fun getInstance(context: Context): LedgerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LedgerDatabase::class.java,
                    "book-01-ledger.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            createAuditGuards(db)
                            createAccountAuditGuards(db)
                            createImmutableLedgerGuards(db)
                        }
                    }).build().also { instance = it }
            }
    }
}
