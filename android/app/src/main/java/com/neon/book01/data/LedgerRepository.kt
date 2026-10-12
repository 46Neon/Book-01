package com.neon.book01.data

import androidx.room.withTransaction
import com.neon.book01.domain.AccountingRules
import com.neon.book01.domain.EntryKind
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class LedgerRepository(private val database: LedgerDatabase) {
    private val dao = database.ledgerDao()

    fun observeEntries(): Flow<List<EntryWithLines>> = dao.observeEntries()

    fun observeAccounts(): Flow<List<AccountEntity>> = dao.observeAccounts()

    suspend fun initializeBook() {
        dao.insertAccounts(
            listOf(
                AccountEntity(code = "1000", name = "Caja", type = "ASSET"),
                AccountEntity(code = "1010", name = "Banco", type = "ASSET"),
                AccountEntity(code = "4000", name = "Ingresos", type = "INCOME"),
                AccountEntity(code = "5100", name = "Papelería", type = "EXPENSE"),
                AccountEntity(code = "5200", name = "Servicios", type = "EXPENSE"),
                AccountEntity(code = "5300", name = "Otros gastos", type = "EXPENSE"),
            ),
        )
    }

    suspend fun recordSimpleEntry(
        dateIso: String,
        description: String,
        kind: EntryKind,
        categoryAccountCode: String,
        paymentAccountCode: String,
        amountMinor: Long,
        currencyCode: String = "USD",
    ) {
        val category = dao.getAccount(categoryAccountCode)
            ?: throw IllegalArgumentException("La categoría seleccionada no existe.")
        val payment = dao.getAccount(paymentAccountCode)
            ?: throw IllegalArgumentException("La cuenta de pago seleccionada no existe.")

        val entryId = UUID.randomUUID().toString()
        val postingLines = AccountingRules.simpleCashEntry(
            kind = kind,
            description = description,
            categoryAccountCode = category.code,
            categoryAccountType = category.type,
            paymentAccountCode = payment.code,
            paymentAccountType = payment.type,
            amountMinor = amountMinor,
            currencyCode = currencyCode,
        )
        val lines = postingLines.map { line ->
            JournalLineEntity(
                id = UUID.randomUUID().toString(),
                entryId = entryId,
                accountCode = line.accountCode,
                debitMinor = line.debitMinor,
                creditMinor = line.creditMinor,
                currencyCode = line.currencyCode,
            )
        }

        val entry = JournalEntryEntity(
            id = entryId,
            entryDate = dateIso,
            description = description.trim(),
            kind = kind.name,
            categoryAccountCode = categoryAccountCode,
            paymentAccountCode = paymentAccountCode,
            currencyCode = currencyCode,
            amountMinor = amountMinor,
            status = "POSTED",
            createdAt = Instant.now().toString(),
        )

        database.withTransaction {
            dao.insertEntry(entry)
            dao.insertLines(lines)
        }
    }
}
