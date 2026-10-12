package com.neon.book01.data

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Embedded
import androidx.room.Relation

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val code: String,
    val name: String,
    val type: String,
    val active: Boolean = true,
)

@Entity(tableName = "journal_entries")
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

@Database(
    entities = [AccountEntity::class, JournalEntryEntity::class, JournalLineEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    companion object {
        @Volatile
        private var instance: LedgerDatabase? = null

        fun getInstance(context: Context): LedgerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LedgerDatabase::class.java,
                    "book-01-ledger.db",
                ).build().also { instance = it }
            }
    }
}
