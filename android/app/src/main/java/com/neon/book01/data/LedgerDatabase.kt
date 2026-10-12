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

@Database(
    entities = [AccountEntity::class, JournalEntryEntity::class, JournalLineEntity::class, BookSettingsEntity::class, AuditEventEntity::class],
    version = 4,
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

        @Volatile
        private var instance: LedgerDatabase? = null

        fun getInstance(context: Context): LedgerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LedgerDatabase::class.java,
                    "book-01-ledger.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            createAuditGuards(db)
                        }
                    }).build().also { instance = it }
            }
    }
}
