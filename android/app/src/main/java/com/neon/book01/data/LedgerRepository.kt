package com.neon.book01.data

import androidx.room.withTransaction
import com.neon.book01.domain.AccountRules
import com.neon.book01.domain.AccountingRules
import com.neon.book01.domain.EntryKind
import com.neon.book01.domain.ReversalRules
import com.neon.book01.domain.UsdMoney
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class LedgerRepository(private val database: LedgerDatabase) {
    private val dao = database.ledgerDao()

    fun observeEntries(): Flow<List<EntryWithLines>> = dao.observeEntries()

    fun observeAuditEvents(): Flow<List<AuditEventEntity>> = dao.observeAuditEvents()

    fun observeAccountAuditEvents(): Flow<List<AccountAuditEventEntity>> = dao.observeAccountAuditEvents()

    fun observeAccounts(): Flow<List<AccountEntity>> = dao.observeAccounts()

    fun observeBookSettings(): Flow<BookSettingsEntity?> = dao.observeBookSettings()

    suspend fun initializeBook() {
        dao.insertAccounts(
            listOf(
                AccountEntity(code = "1000", name = "Caja", type = "ASSET", cashOrBank = true),
                AccountEntity(code = "1010", name = "Banco", type = "ASSET", cashOrBank = true),
                AccountEntity(code = "2000", name = "Cuentas por pagar", type = "LIABILITY"),
                AccountEntity(code = "3000", name = "Capital", type = "EQUITY"),
                AccountEntity(code = "4000", name = "Ingresos", type = "INCOME"),
                AccountEntity(code = "5100", name = "Papelería", type = "EXPENSE"),
                AccountEntity(code = "5200", name = "Servicios", type = "EXPENSE"),
                AccountEntity(code = "5300", name = "Otros gastos", type = "EXPENSE"),
            ),
        )
        dao.insertBookSettings(BookSettingsEntity(id = 1, bookName = "Libro de prueba", baseCurrency = "USD"))
    }

    suspend fun renameBook(newName: String) {
        val normalizedName = newName.trim()
        require(normalizedName.length in 2..60) { "El nombre debe tener entre 2 y 60 caracteres." }
        val current = dao.getBookSettings()
        if (current == null) {
            dao.insertBookSettings(BookSettingsEntity(id = 1, bookName = normalizedName, baseCurrency = UsdMoney.CURRENCY_CODE))
        } else {
            dao.updateBookSettings(current.copy(bookName = normalizedName))
        }
    }

    suspend fun createAccount(code: String, name: String, type: String, cashOrBank: Boolean) {
        val validated = AccountRules.validate(code, name, type, cashOrBank)
        require(dao.getAccount(validated.code) == null) { "Ya existe una cuenta con ese código." }
        database.withTransaction {
            require(dao.getAccount(validated.code) == null) { "Ya existe una cuenta con ese código." }
            dao.insertAccount(
                AccountEntity(
                    code = validated.code,
                    name = validated.name,
                    type = validated.type,
                    active = true,
                    cashOrBank = validated.cashOrBank,
                ),
            )
        }
    }

    /** Edit only the display label and selection status; account code/type are deliberately immutable. */
    suspend fun updateAccountSettings(accountCode: String, newName: String, active: Boolean) {
        val normalizedName = AccountRules.validateName(newName)
        database.withTransaction {
            val current = dao.getAccount(accountCode)
                ?: throw IllegalArgumentException("No se encontró la cuenta.")
            if (current.name == normalizedName && current.active == active) return@withTransaction
            dao.updateAccount(current.copy(name = normalizedName, active = active))
            dao.insertAccountAuditEvent(
                AccountAuditEventEntity(
                    id = UUID.randomUUID().toString(),
                    accountCode = current.code,
                    occurredAt = Instant.now().toString(),
                    oldName = current.name,
                    newName = normalizedName,
                    oldActive = current.active,
                    newActive = active,
                ),
            )
        }
    }

    suspend fun recordSimpleEntry(
        dateIso: String,
        description: String,
        kind: EntryKind,
        categoryAccountCode: String,
        paymentAccountCode: String,
        amountMinor: Long,
        currencyCode: String = UsdMoney.CURRENCY_CODE,
    ) {
        require(currencyCode == UsdMoney.CURRENCY_CODE) {
            "Esta versión registra únicamente en dólares estadounidenses (USD)."
        }
        val entryId = UUID.randomUUID().toString()
        database.withTransaction {
            val category = dao.getAccount(categoryAccountCode)
                ?: throw IllegalArgumentException("La categoría seleccionada no existe.")
            val payment = dao.getAccount(paymentAccountCode)
                ?: throw IllegalArgumentException("La cuenta de pago seleccionada no existe.")
            require(category.active) { "No se pueden registrar movimientos en una cuenta inactiva." }
            require(payment.active && payment.cashOrBank) { "Selecciona una cuenta activa de Caja o Banco." }

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
            val accountByCode = mapOf(category.code to category, payment.code to payment)
            val lines = postingLines.map { line ->
                JournalLineEntity(
                    id = UUID.randomUUID().toString(),
                    entryId = entryId,
                    accountCode = line.accountCode,
                    debitMinor = line.debitMinor,
                    creditMinor = line.creditMinor,
                    currencyCode = line.currencyCode,
                    accountNameSnapshot = accountByCode[line.accountCode]?.name
                        ?: throw IllegalArgumentException("No se encontró la cuenta de una línea contable."),
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
            dao.insertEntry(entry)
            dao.insertLines(lines)
            dao.insertAuditEvent(
                AuditEventEntity(
                    id = UUID.randomUUID().toString(),
                    entryId = entryId,
                    eventType = "POSTED",
                    occurredAt = Instant.now().toString(),
                    eventDate = dateIso,
                    reason = "Registro inicial del asiento.",
                ),
            )
        }
    }

    /** Add an exact counter-entry; the immutable original is never changed. */
    suspend fun reversePostedEntry(originalEntryId: String, reversalDateIso: String, reason: String): String {
        ReversalRules.validateRequest(reversalDateIso, reason)
        val normalizedReason = reason.trim()
        return database.withTransaction {
            val original = dao.getEntry(originalEntryId)
                ?: throw IllegalArgumentException("No se encontró el asiento original.")
            ReversalRules.requireEligible(
                status = original.status,
                reversalOfEntryId = original.reversalOfEntryId,
                alreadyReversed = dao.hasReversal(originalEntryId),
            )
            val sourceLines = dao.getLines(originalEntryId)
            require(sourceLines.isNotEmpty()) { "El asiento original no tiene líneas para reversar." }
            val inverseLines = ReversalRules.inverseLines(
                sourceLines.map { ReversalRules.JournalLine(it.accountCode, it.debitMinor, it.creditMinor, it.currencyCode) },
            )
            val reversalId = UUID.randomUUID().toString()
            val now = Instant.now().toString()
            val reversalEntry = original.copy(
                id = reversalId,
                entryDate = reversalDateIso,
                description = "Reversa de ${original.id}: $normalizedReason",
                kind = "REVERSAL",
                status = "POSTED",
                createdAt = now,
                reversalOfEntryId = original.id,
            )
            dao.insertEntry(reversalEntry)
            dao.insertLines(
                inverseLines.map { line ->
                    JournalLineEntity(
                        id = UUID.randomUUID().toString(),
                        entryId = reversalId,
                        accountCode = line.accountCode,
                        debitMinor = line.debitMinor,
                        creditMinor = line.creditMinor,
                        currencyCode = line.currencyCode,
                        accountNameSnapshot = sourceLines.first { it.accountCode == line.accountCode }.accountNameSnapshot,
                    )
                },
            )
            dao.insertAuditEvent(
                AuditEventEntity(
                    id = UUID.randomUUID().toString(),
                    entryId = reversalId,
                    eventType = "REVERSAL_POSTED",
                    occurredAt = now,
                    eventDate = reversalDateIso,
                    reason = normalizedReason,
                    relatedEntryId = original.id,
                ),
            )
            dao.insertAuditEvent(
                AuditEventEntity(
                    id = UUID.randomUUID().toString(),
                    entryId = original.id,
                    eventType = "REVERSED",
                    occurredAt = now,
                    eventDate = reversalDateIso,
                    reason = normalizedReason,
                    relatedEntryId = reversalId,
                ),
            )
            reversalId
        }
    }
}
