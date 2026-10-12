package com.neon.book01.data

import androidx.room.withTransaction
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
        require(description.isNotBlank()) { "Escribe una descripción." }
        require(amountMinor > 0) { "El importe debe ser mayor que cero." }

        val category = dao.getAccount(categoryAccountCode)
            ?: throw IllegalArgumentException("La categoría seleccionada no existe.")
        val payment = dao.getAccount(paymentAccountCode)
            ?: throw IllegalArgumentException("La cuenta de pago seleccionada no existe.")

        require(payment.type == "ASSET") { "Selecciona una cuenta de caja o banco." }
        require(
            (kind == EntryKind.EXPENSE && category.type == "EXPENSE") ||
                (kind == EntryKind.INCOME && category.type == "INCOME"),
        ) { "La categoría no corresponde al tipo de movimiento." }

        val entryId = UUID.randomUUID().toString()
        val categoryLine = if (kind == EntryKind.EXPENSE) {
            JournalLineEntity(
                id = UUID.randomUUID().toString(),
                entryId = entryId,
                accountCode = categoryAccountCode,
                debitMinor = amountMinor,
                creditMinor = 0,
                currencyCode = currencyCode,
            )
        } else {
            JournalLineEntity(
                id = UUID.randomUUID().toString(),
                entryId = entryId,
                accountCode = categoryAccountCode,
                debitMinor = 0,
                creditMinor = amountMinor,
                currencyCode = currencyCode,
            )
        }
        val paymentLine = if (kind == EntryKind.EXPENSE) {
            JournalLineEntity(
                id = UUID.randomUUID().toString(),
                entryId = entryId,
                accountCode = paymentAccountCode,
                debitMinor = 0,
                creditMinor = amountMinor,
                currencyCode = currencyCode,
            )
        } else {
            JournalLineEntity(
                id = UUID.randomUUID().toString(),
                entryId = entryId,
                accountCode = paymentAccountCode,
                debitMinor = amountMinor,
                creditMinor = 0,
                currencyCode = currencyCode,
            )
        }
        val lines = listOf(categoryLine, paymentLine)

        require(lines.sumOf { it.debitMinor } == lines.sumOf { it.creditMinor }) {
            "El asiento no está balanceado."
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

enum class EntryKind {
    INCOME,
    EXPENSE,
}
