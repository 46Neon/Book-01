package com.neon.book01.data

import androidx.room.withTransaction
import com.neon.book01.domain.AccountRules
import com.neon.book01.domain.AccountingRules
import com.neon.book01.domain.EntryKind
import com.neon.book01.domain.ReversalRules
import com.neon.book01.domain.PostingDateRange
import com.neon.book01.domain.PeriodCloseAccount
import com.neon.book01.domain.PeriodCloseCalculator
import com.neon.book01.domain.PeriodClosePosting
import com.neon.book01.domain.PeriodClosePostingLine
import com.neon.book01.domain.UsdMoney
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class LedgerRepository(private val database: LedgerDatabase) {
    private val dao = database.ledgerDao()

    fun observeEntries(): Flow<List<EntryWithLines>> = dao.observeEntries()

    fun observeAuditEvents(): Flow<List<AuditEventEntity>> = dao.observeAuditEvents()

    fun observeAccountAuditEvents(): Flow<List<AccountAuditEventEntity>> = dao.observeAccountAuditEvents()

    fun observePeriodCloses(): Flow<List<PeriodCloseEntity>> = dao.observePeriodCloses()

    suspend fun getPeriodCloses(): List<PeriodCloseEntity> = dao.getPeriodCloses()

    /** Validate a decrypted backup and check the destination before presenting the confirmation screen. */
    suspend fun previewBackupRestore(snapshot: LedgerBackupSnapshot): LedgerBackupPreview = database.withTransaction {
        val preview = LedgerBackupValidator.validate(snapshot)
        requirePristineRestoreTarget()
        preview
    }

    /** Restore only into the untouched starter book; all checks and writes share one Room transaction. */
    suspend fun restoreBackup(snapshot: LedgerBackupSnapshot): LedgerBackupPreview = database.withTransaction {
        val preview = LedgerBackupValidator.validate(snapshot)
        requirePristineRestoreTarget()

        val importedByCode = snapshot.accounts.associateBy { it.code }
        val seededCodes = LedgerBookDefaults.accounts.map { it.code }.toSet()
        val currentAccounts = dao.getAllAccounts().associateBy { it.code }
        if (currentAccounts.isNotEmpty()) {
            // Code and type are immutable; validation above has already enforced each default type.
            LedgerBookDefaults.accounts.forEach { default ->
                val imported = importedByCode.getValue(default.code)
                val current = currentAccounts.getValue(default.code)
                if (current != imported) dao.updateAccount(imported)
            }
            snapshot.accounts.filter { it.code !in seededCodes }.forEach { dao.insertAccount(it) }
        } else {
            // An uninitialized database has no seed rows, so restore the complete chart of accounts.
            snapshot.accounts.forEach { dao.insertAccount(it) }
        }

        val importedSettings = snapshot.bookSettings!!
        val currentSettings = dao.getBookSettings()
        if (currentSettings == null) dao.insertBookSettings(importedSettings)
        else if (currentSettings != importedSettings) dao.updateBookSettings(importedSettings)

        snapshot.journalEntries.forEach { dao.insertEntry(it) }
        if (snapshot.journalLines.isNotEmpty()) dao.insertLines(snapshot.journalLines)
        snapshot.journalEntries.filter { it.status == "POSTED" }.forEach { entry ->
            dao.insertPostedEntryLock(PostedEntryLockEntity(entryId = entry.id))
        }
        snapshot.auditEvents.forEach { dao.insertAuditEvent(it) }
        snapshot.accountAuditEvents.forEach { dao.insertAccountAuditEvent(it) }
        snapshot.periodCloses.forEach { dao.insertPeriodClose(it) }
        preview
    }

    private suspend fun requirePristineRestoreTarget() {
        val accounts = dao.getAllAccounts()
        val settings = dao.getAllBookSettings()
        val ledgerIsEmpty = dao.countJournalEntriesRaw() == 0 && dao.countJournalLines() == 0 &&
            dao.countAuditEvents() == 0 && dao.countAccountAuditEvents() == 0 && dao.countPeriodCloses() == 0
        require(ledgerIsEmpty) {
            "Por seguridad, restaura solo en un libro nuevo y sin movimientos, auditoría ni cierres."
        }
        val neverInitialized = accounts.isEmpty() && settings.isEmpty()
        val exactStarterBook = accounts == LedgerBookDefaults.accounts.sortedBy { it.code } &&
            settings == listOf(LedgerBookDefaults.settings)
        require(neverInitialized || exactStarterBook) {
            "El libro ya fue personalizado; no se puede reemplazar mediante una restauración."
        }
    }

    /** Collect every table under one Room transaction so the encrypted file is a coherent snapshot. */
    suspend fun createBackupSnapshot(): LedgerBackupSnapshot = database.withTransaction {
        val entriesWithLines = dao.getAllEntries()
        LedgerBackupSnapshot(
            bookSettings = dao.getBookSettings(),
            accounts = dao.getAllAccounts(),
            journalEntries = entriesWithLines.map { it.entry },
            journalLines = entriesWithLines.flatMap { it.lines },
            auditEvents = dao.getAllAuditEvents(),
            accountAuditEvents = dao.getAllAccountAuditEvents(),
            periodCloses = dao.getAllPeriodCloses(),
        )
    }

    fun observeAccounts(): Flow<List<AccountEntity>> = dao.observeAccounts()

    fun observeBookSettings(): Flow<BookSettingsEntity?> = dao.observeBookSettings()

    suspend fun initializeBook() {
        dao.insertAccounts(LedgerBookDefaults.accounts)
        dao.insertBookSettings(LedgerBookDefaults.settings)
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
        val postingDate = PostingDateRange.parse(dateIso, dateIso).startInclusive!!.toString()
        database.withTransaction {
            require(!dao.isDateClosed(postingDate)) { "La fecha pertenece a un periodo cerrado; elige una fecha abierta." }
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
            dao.insertPostedEntryLock(PostedEntryLockEntity(entryId = entryId))
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
            require(original.kind != "PERIOD_CLOSE") { "Un cierre de periodo no se puede reversar desde la reversa ordinaria." }
            val reversalDate = PostingDateRange.parse(reversalDateIso, reversalDateIso).startInclusive!!.toString()
            require(!dao.isDateClosed(reversalDate)) { "La fecha de reversa pertenece a un periodo cerrado." }
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
            dao.insertPostedEntryLock(PostedEntryLockEntity(entryId = reversalId))
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

    /** Plans, optionally posts, audits, and stores an immutable close in one serialized transaction. */
    suspend fun closePeriod(startDateIso: String, endDateIso: String, retainedEarningsAccountCode: String): String? {
        val range = PostingDateRange.parse(startDateIso, endDateIso)
        val startDate = range.startInclusive?.toString()
            ?: throw IllegalArgumentException("Indica la fecha inicial del periodo.")
        val endDate = range.endInclusive?.toString()
            ?: throw IllegalArgumentException("Indica la fecha final del periodo.")
        require(endDate <= LocalDate.now().toString()) { "No se puede cerrar un periodo que todavía no ha terminado." }
        val closeId = UUID.randomUUID().toString()
        return database.withTransaction {
            require(!dao.hasOverlappingClose(startDate, endDate)) { "El periodo se superpone con otro periodo cerrado." }
            val allAccounts = dao.getAllAccounts()
            val allEntries = dao.getAllEntries()
            val plan = PeriodCloseCalculator.calculate(
                accounts = allAccounts.map { PeriodCloseAccount(it.code, it.type, it.active) },
                postings = allEntries.map { entry ->
                    PeriodClosePosting(
                        entryDate = entry.entry.entryDate,
                        status = entry.entry.status,
                        currencyCode = entry.entry.currencyCode,
                        kind = entry.entry.kind,
                        lines = entry.lines.map { line ->
                            PeriodClosePostingLine(line.accountCode, line.debitMinor, line.creditMinor, line.currencyCode)
                        },
                    )
                },
                startDate = startDate,
                endDate = endDate,
                retainedEarningsAccountCode = retainedEarningsAccountCode,
            )
            val retained = allAccounts.first { it.code == plan.retainedEarningsAccountCode }
            val now = Instant.now().toString()
            val entryId = if (plan.lines.isEmpty()) null else UUID.randomUUID().toString()
            if (entryId != null) {
                val names = allAccounts.associate { it.code to it.name }
                val lines = plan.lines.map { line ->
                    JournalLineEntity(
                        id = UUID.randomUUID().toString(),
                        entryId = entryId,
                        accountCode = line.accountCode,
                        debitMinor = line.debitMinor,
                        creditMinor = line.creditMinor,
                        currencyCode = line.currencyCode,
                        accountNameSnapshot = names[line.accountCode]
                            ?: throw IllegalArgumentException("No se encontró la cuenta de una línea de cierre."),
                    )
                }
                dao.insertEntry(
                    JournalEntryEntity(
                        id = entryId,
                        entryDate = endDate,
                        description = "Cierre contable $startDate a $endDate",
                        kind = "PERIOD_CLOSE",
                        categoryAccountCode = retained.code,
                        paymentAccountCode = retained.code,
                        currencyCode = UsdMoney.CURRENCY_CODE,
                        amountMinor = UsdMoney.sumMinorUnits(plan.lines.map { it.debitMinor }),
                        status = "POSTED",
                        createdAt = now,
                    ),
                )
                dao.insertLines(lines)
                dao.insertPostedEntryLock(PostedEntryLockEntity(entryId = entryId))
                dao.insertAuditEvent(
                    AuditEventEntity(
                        id = UUID.randomUUID().toString(),
                        entryId = entryId,
                        eventType = "PERIOD_CLOSED",
                        occurredAt = now,
                        eventDate = endDate,
                        reason = "Cierre $startDate a $endDate; resultado neto ${plan.netResultMinor} centavos USD; transferencia a patrimonio ${retained.code}.",
                    ),
                )
            }
            dao.insertPeriodClose(
                PeriodCloseEntity(
                    id = closeId,
                    startDate = plan.startDate,
                    endDate = plan.endDate,
                    retainedEarningsAccountCode = retained.code,
                    netResultMinor = plan.netResultMinor,
                    closingEntryId = entryId,
                    closedAt = now,
                ),
            )
            entryId
        }
    }

}
