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
import com.neon.book01.domain.CounterpartyRules
import com.neon.book01.domain.SupportReferenceRules
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class LedgerRepository(private val database: LedgerDatabase) {
    private val dao = database.ledgerDao()

    private val invoiceControlAccounts = listOf(
        AccountEntity("1100", "Cuentas por cobrar", "ASSET"),
        AccountEntity("1105", "IVA crédito fiscal (provisional)", "ASSET"),
        AccountEntity("2102", "Préstamos bancarios por pagar", "LIABILITY"),
        AccountEntity("2103", "IVA débito fiscal (provisional)", "LIABILITY"),
    )

    fun observeEntries(): Flow<List<EntryWithLines>> = dao.observeEntries()

    fun observeHasReversal(entryId: String): Flow<Boolean> = dao.observeHasReversal(entryId)

    fun observeDraftEntries(): Flow<List<EntryWithLines>> = dao.observeDraftEntries()

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

        val orderedAccounts = parentFirstAccounts(snapshot.accounts)
        val seededCodes = (LedgerBookDefaults.accounts + invoiceControlAccounts).map { it.code }.toSet()
        val currentAccounts = dao.getAllAccounts().associateBy { it.code }
        if (currentAccounts.isNotEmpty()) {
            LedgerBookDefaults.accounts.forEach { default ->
                val imported = snapshot.accounts.firstOrNull { it.code == default.code } ?: return@forEach
                val current = currentAccounts[default.code] ?: return@forEach
                if (current != imported) dao.updateAccount(imported)
            }
            orderedAccounts.filter { it.code !in seededCodes }.forEach { imported ->
                val current = currentAccounts[imported.code]
                if (current == null) dao.insertAccount(imported) else if (current != imported) dao.updateAccount(imported)
            }
        } else {
            orderedAccounts.forEach { dao.insertAccount(it) }
        }
        (LedgerBookDefaults.accounts + invoiceControlAccounts).filter { dao.getAccount(it.code) == null }.forEach { dao.insertAccount(it) }

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
        snapshot.counterparties.forEach { dao.insertCounterparty(it) }
        snapshot.invoices.forEach { dao.insertInvoice(it) }
        snapshot.invoiceSettlements.forEach { dao.insertInvoiceSettlement(it) }
        preview
    }

    private suspend fun requirePristineRestoreTarget() {
        val accounts = dao.getAllAccounts()
        val settings = dao.getAllBookSettings()
        val ledgerIsEmpty = dao.countJournalEntriesRaw() == 0 && dao.countJournalLines() == 0 &&
            dao.countAuditEvents() == 0 && dao.countAccountAuditEvents() == 0 && dao.countPeriodCloses() == 0 &&
            dao.countInvoices() == 0 && dao.countInvoiceSettlements() == 0 && dao.countCounterparties() == 0
        require(ledgerIsEmpty) {
            "Por seguridad, restaura solo en un libro nuevo y sin movimientos, auditoría, facturas, terceros ni cierres."
        }
        val neverInitialized = accounts.isEmpty() && settings.isEmpty()
        val exactStarterBook = accounts == (LedgerBookDefaults.accounts + invoiceControlAccounts).distinctBy { it.code }.sortedBy { it.code } &&
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
            counterparties = dao.getAllCounterparties(),
            invoices = dao.getAllInvoices(),
            invoiceSettlements = dao.getAllInvoiceSettlements(),
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
        dao.insertAccounts(invoiceControlAccounts)
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

    suspend fun createAccount(code: String, name: String, type: String, cashOrBank: Boolean, parentCode: String? = null) {
        val validated = AccountRules.validate(code, name, type, cashOrBank)
        require(dao.getAccount(validated.code) == null) { "Ya existe una cuenta con ese código." }
        database.withTransaction {
            require(dao.getAccount(validated.code) == null) { "Ya existe una cuenta con ese código." }
            val parent = parentCode?.let { dao.getAccount(it) ?: throw IllegalArgumentException("La cuenta padre no existe.") }
            require(parent == null || (parent.active && parent.type == validated.type && parent.code != validated.code)) {
                "La cuenta padre debe existir, estar activa y tener la misma clasificación."
            }
            dao.insertAccount(
                AccountEntity(
                    code = validated.code,
                    name = validated.name,
                    type = validated.type,
                    active = true,
                    cashOrBank = validated.cashOrBank,
                    parentCode = parent?.code,
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
            require(current.active.not() || active || !dao.hasChildAccounts(current.code)) {
                "Mantén activa esta cuenta mientras tenga cuentas dependientes."
            }
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

    fun observeInvoices(): Flow<List<InvoiceEntity>> = dao.observeInvoices()

    fun observeCounterparties(kind: String): Flow<List<CounterpartyEntity>> = dao.observeCounterparties(kind)

    fun observeInvoice(invoiceId: String): Flow<InvoiceEntity?> = dao.observeInvoice(invoiceId)

    fun observeInvoiceSettlements(invoiceId: String): Flow<List<InvoiceSettlementEntity>> = dao.observeInvoiceSettlements(invoiceId)

    fun observeAllInvoiceSettlements(): Flow<List<InvoiceSettlementEntity>> = dao.observeAllInvoiceSettlements()

    suspend fun hasAvailableCash(accountCode: String, amountMinor: Long): Boolean =
        dao.accountBalanceMinor(accountCode) >= amountMinor

    /** Preview with the same calculation rules used by the atomic posting operation. */
    fun previewInvoice(input: com.neon.book01.domain.InvoiceInput, accounts: com.neon.book01.domain.InvoiceAccounts): com.neon.book01.domain.InvoicePostingPreview =
        com.neon.book01.domain.InvoiceAccounting.preview(input, accounts)

    /** Save invoice + automatic journal + initial settlement atomically. Account codes are supplied by the chart configuration. */
    suspend fun createCounterparty(id: String, kind: String, displayName: String, taxId: String? = null, defaultAccountCode: String? = null) {
        val name = displayName.trim()
        require(kind in setOf("CLIENT", "SUPPLIER")) { "El tercero debe ser cliente o proveedor." }
        require(id.isNotBlank() && name.length in 2..120) { "Registra un identificador y nombre válidos." }
        require(taxId == null || taxId.trim().length <= 32) { "La identificación fiscal es demasiado larga." }
        val defaultAccount = defaultAccountCode?.let { dao.getAccount(it) ?: throw IllegalArgumentException("La cuenta predeterminada no existe.") }
        require(defaultAccount == null || (defaultAccount.active && if (kind == "SUPPLIER") defaultAccount.type in setOf("EXPENSE", "ASSET") else defaultAccount.type == "ASSET")) { "La cuenta predeterminada no corresponde al tipo de tercero." }
        database.withTransaction {
            require(dao.getCounterparty(id.trim()) == null) { "Ese identificador de tercero ya está registrado." }
            dao.insertCounterparty(CounterpartyEntity(id.trim(), kind, name, taxId?.trim()?.takeIf { it.isNotEmpty() }, true, defaultAccount?.code))
        }
    }

    suspend fun createInvoice(input: com.neon.book01.domain.InvoiceInput, accounts: com.neon.book01.domain.InvoiceAccounts): String {
        val preview = com.neon.book01.domain.InvoiceAccounting.preview(input, accounts)
        val postingDate = PostingDateRange.parse(input.invoiceDate, input.invoiceDate).startInclusive!!.toString()
        require(postingDate == input.invoiceDate) { "La fecha debe tener el formato AAAA-MM-DD." }
        val normalizedPartyId = input.registeredPartyId.trim()
        val normalizedInvoiceNumber = input.invoiceNumber.trim().uppercase()
        val normalizedControlNumber = input.controlNumber?.trim()?.takeIf { it.isNotEmpty() }
        require(input.kind != com.neon.book01.domain.InvoiceKind.SALE || normalizedControlNumber != null) { "La venta requiere número de control." }
        require(normalizedPartyId.isNotEmpty() && normalizedInvoiceNumber.isNotEmpty()) { "La factura requiere un tercero registrado y un número." }
        val invoiceId = UUID.randomUUID().toString()
        val entryId = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        val kind = input.kind.name
        return database.withTransaction {
            require(!dao.isDateClosed(postingDate)) { "La fecha pertenece a un periodo cerrado; elige una fecha abierta." }
            require(dao.findInvoice(kind, normalizedPartyId, normalizedInvoiceNumber) == null) { "Ya existe una factura con ese número para este cliente o proveedor." }
            val registeredParty = dao.getCounterparty(normalizedPartyId)
                ?: throw IllegalArgumentException("El cliente o proveedor debe estar registrado.")
            require(registeredParty.active && registeredParty.kind == if (input.kind == com.neon.book01.domain.InvoiceKind.SALE) "CLIENT" else "SUPPLIER") {
                "El tercero registrado no corresponde al tipo de factura o está inactivo."
            }
            require(input.condition != com.neon.book01.domain.PaymentCondition.CREDIT || input.dueDate == null || input.dueDate >= input.invoiceDate) {
                "El vencimiento no puede ser anterior a la fecha de la factura."
            }
            val effectiveAccounts = if (input.kind == com.neon.book01.domain.InvoiceKind.PURCHASE && registeredParty.defaultAccountCode != null) {
                val default = dao.getAccount(registeredParty.defaultAccountCode)
                    ?: throw IllegalArgumentException("La cuenta predeterminada del proveedor ya no existe.")
                require(default.active) { "La cuenta predeterminada del proveedor está inactiva." }
                accounts.copy(purchaseCostOrExpense = default.code)
            } else accounts
            val effectivePreview = com.neon.book01.domain.InvoiceAccounting.preview(input, effectiveAccounts)
            val cash = dao.getAccount(input.cashAccountCode) ?: throw IllegalArgumentException("No se encontró la cuenta de caja o banco.")
            require(cash.active && cash.cashOrBank && cash.type == "ASSET") { "Selecciona una cuenta activa de Caja o Banco." }
            suspend fun requireAccount(code: String, type: String) {
                // Validation stays inside the transaction so an inactive/missing account cannot race the posting.
                val account = runBlockingAccountLookup(code)
                require(account.active && account.type == type) { "La cuenta $code debe estar activa y ser de tipo $type." }
            }
            requireAccount(effectiveAccounts.receivable, "ASSET")
            requireAccount(effectiveAccounts.payable, "LIABILITY")
            requireAccount(effectiveAccounts.revenue, "INCOME")
            val purchaseDestination = runBlockingAccountLookup(effectiveAccounts.purchaseCostOrExpense)
            require(purchaseDestination.active && purchaseDestination.type in setOf("EXPENSE", "ASSET")) {
                "La cuenta de destino de compra debe ser costo/gasto o activo según la clasificación del proveedor."
            }
            if (input.kind == com.neon.book01.domain.InvoiceKind.SALE && effectivePreview.vatMinor > 0) requireAccount(effectiveAccounts.outputVat, "LIABILITY")
            if (input.kind == com.neon.book01.domain.InvoiceKind.PURCHASE && effectivePreview.vatMinor > 0 && effectiveAccounts.purchaseVatTreatment == com.neon.book01.domain.PurchaseVatTreatment.INPUT_TAX_ASSET) {
                requireAccount(effectiveAccounts.inputVat, "ASSET")
            }
            if (input.kind == com.neon.book01.domain.InvoiceKind.PURCHASE && input.paidTodayMinor > 0) {
                com.neon.book01.domain.InvoiceAccounting.requireSufficientFunds(dao.accountBalanceMinor(cash.code), input.paidTodayMinor)
            }
            val lines = effectivePreview.lines.map { line ->
                val account = dao.getAccount(line.accountCode) ?: throw IllegalArgumentException("No se encontró la cuenta ${line.accountCode}.")
                JournalLineEntity(UUID.randomUUID().toString(), entryId, account.code, line.debitMinor, line.creditMinor, UsdMoney.CURRENCY_CODE, account.name)
            }
            val counterpartCode = if (input.kind == com.neon.book01.domain.InvoiceKind.SALE) effectiveAccounts.revenue else effectiveAccounts.purchaseCostOrExpense
            dao.insertEntry(JournalEntryEntity(
                id = entryId, entryDate = input.invoiceDate,
                description = "${if (input.kind == com.neon.book01.domain.InvoiceKind.SALE) "Venta" else "Compra"} $normalizedInvoiceNumber: ${input.description.trim()}",
                kind = if (input.kind == com.neon.book01.domain.InvoiceKind.SALE) "SALE_INVOICE" else "PURCHASE_INVOICE",
                categoryAccountCode = counterpartCode, paymentAccountCode = cash.code, currencyCode = UsdMoney.CURRENCY_CODE,
                amountMinor = effectivePreview.totalMinor, status = "POSTED", createdAt = now, counterparty = normalizedPartyId,
                supportReference = normalizedInvoiceNumber,
            ))
            dao.insertLines(lines)
            dao.insertPostedEntryLock(PostedEntryLockEntity(entryId))
            dao.insertAuditEvent(AuditEventEntity(UUID.randomUUID().toString(), entryId, "INVOICE_POSTED", now, input.invoiceDate, "Factura $normalizedInvoiceNumber guardada con asiento automático."))
            dao.insertInvoice(InvoiceEntity(
                id = invoiceId, kind = kind, invoiceDate = input.invoiceDate, invoiceNumber = normalizedInvoiceNumber,
                controlNumber = normalizedControlNumber, registeredPartyId = normalizedPartyId,
                description = input.description.trim(), taxableBaseMinor = effectivePreview.baseMinor,
                vatRateBasisPoints = input.vatRateBasisPoints, vatMinor = effectivePreview.vatMinor, totalMinor = effectivePreview.totalMinor,
                paidMinor = input.paidTodayMinor, paymentCondition = input.condition.name, dueDate = input.dueDate, cashAccountCode = cash.code,
                counterAccountCode = if (input.kind == com.neon.book01.domain.InvoiceKind.SALE) effectiveAccounts.receivable else effectiveAccounts.payable,
                entryId = entryId, createdAt = now,
            ))
            if (input.paidTodayMinor > 0) {
                val initialSettlementId = UUID.randomUUID().toString()
                dao.insertInvoiceSettlement(InvoiceSettlementEntity(initialSettlementId, invoiceId, input.paidTodayMinor, input.invoiceDate, cash.code, entryId, now))
            }
            invoiceId
        }
    }

    /** Record a later partial/full collection or payment as a new balanced journal entry linked to the invoice. */
    suspend fun settleInvoice(invoiceId: String, amountMinor: Long, dateIso: String, cashAccountCode: String, accounts: com.neon.book01.domain.InvoiceAccounts): String {
        val postingDate = PostingDateRange.parse(dateIso, dateIso).startInclusive!!.toString()
        require(postingDate == dateIso) { "La fecha del cobro o pago no es válida." }
        require(amountMinor > 0) { "El cobro o pago debe ser mayor que cero." }
        val entryId = UUID.randomUUID().toString()
        val settlementId = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        return database.withTransaction {
            require(!dao.isDateClosed(postingDate)) { "La fecha pertenece a un periodo cerrado; elige una fecha abierta." }
            val invoice = dao.getInvoice(invoiceId) ?: throw IllegalArgumentException("No se encontró la factura.")
            com.neon.book01.domain.InvoiceAccounting.requireSettlementDateNotBeforeInvoice(invoice.invoiceDate, postingDate)
            val cash = dao.getAccount(cashAccountCode) ?: throw IllegalArgumentException("No se encontró la cuenta de caja o banco.")
            require(cash.active && cash.cashOrBank && cash.type == "ASSET") { "Selecciona una cuenta activa de Caja o Banco." }
            val remaining = Math.subtractExact(invoice.totalMinor, dao.totalSettled(invoiceId))
            require(amountMinor <= remaining) { "El importe supera el saldo pendiente de la factura." }
            val invoiceParty = dao.getCounterparty(invoice.registeredPartyId)
                ?: throw IllegalArgumentException("El tercero de la factura ya no existe.")
            require(invoiceParty.active) { "El tercero asociado a la factura está inactivo." }
            val kind = com.neon.book01.domain.InvoiceKind.valueOf(invoice.kind)
            val counterAccount = dao.getAccount(invoice.counterAccountCode)
                ?: throw IllegalArgumentException("No se encontró la cuenta por cobrar/pagar de la factura.")
            val expectedCounterType = if (kind == com.neon.book01.domain.InvoiceKind.SALE) "ASSET" else "LIABILITY"
            require(counterAccount.type == expectedCounterType) { "La cuenta asociada a la factura ya no tiene la clasificación contable esperada." }
            val effectiveAccounts = when (kind) {
                com.neon.book01.domain.InvoiceKind.SALE -> accounts.copy(receivable = counterAccount.code)
                com.neon.book01.domain.InvoiceKind.PURCHASE -> accounts.copy(payable = counterAccount.code)
            }
            if (kind == com.neon.book01.domain.InvoiceKind.PURCHASE) com.neon.book01.domain.InvoiceAccounting.requireSufficientFunds(dao.accountBalanceMinor(cash.code), amountMinor)
            val postings = com.neon.book01.domain.InvoiceAccounting.settlement(kind, amountMinor, cash.code, effectiveAccounts)
            val lines = postings.map { line ->
                val account = dao.getAccount(line.accountCode) ?: throw IllegalArgumentException("No se encontró la cuenta ${line.accountCode}.")
                require(account.active || account.code == counterAccount.code) { "La cuenta ${line.accountCode} está inactiva." }
                JournalLineEntity(UUID.randomUUID().toString(), entryId, account.code, line.debitMinor, line.creditMinor, UsdMoney.CURRENCY_CODE, account.name)
            }
            dao.insertEntry(JournalEntryEntity(
                id = entryId, entryDate = dateIso, description = "${if (kind == com.neon.book01.domain.InvoiceKind.SALE) "Cobro" else "Pago"} de factura ${invoice.invoiceNumber}",
                kind = if (kind == com.neon.book01.domain.InvoiceKind.SALE) "INVOICE_COLLECTION" else "INVOICE_PAYMENT",
                categoryAccountCode = invoice.counterAccountCode,
                paymentAccountCode = cash.code, currencyCode = UsdMoney.CURRENCY_CODE, amountMinor = amountMinor,
                status = "POSTED", createdAt = now, counterparty = invoice.registeredPartyId, supportReference = invoice.invoiceNumber,
            ))
            dao.insertLines(lines)
            dao.insertPostedEntryLock(PostedEntryLockEntity(entryId))
            dao.insertAuditEvent(AuditEventEntity(UUID.randomUUID().toString(), entryId, "INVOICE_SETTLEMENT_POSTED", now, dateIso, "Movimiento vinculado a factura ${invoice.invoiceNumber}."))
            dao.insertInvoiceSettlement(InvoiceSettlementEntity(settlementId, invoiceId, amountMinor, dateIso, cash.code, entryId, now))
            val initialMinor = dao.initialSettlementTotal(invoiceId, invoice.entryId)
            // The invoice record is immutable. Its initial payment is stored once; future receipts/payments are separate append-only settlements.
            require(initialMinor == invoice.paidMinor) { "El abono inicial no coincide con la factura." }
            entryId
        }
    }

    private suspend fun runBlockingAccountLookup(code: String): AccountEntity =
        dao.getAccount(code) ?: throw IllegalArgumentException("No se encontró la cuenta $code en el plan de cuentas.")

    suspend fun recordSimpleEntry(
        dateIso: String,
        description: String,
        kind: EntryKind,
        categoryAccountCode: String,
        paymentAccountCode: String,
        amountMinor: Long,
        currencyCode: String = UsdMoney.CURRENCY_CODE,
        counterparty: String? = null,
        supportReference: String? = null,
    ) {
        val normalizedCounterparty = CounterpartyRules.normalize(counterparty)
        val normalizedSupportReference = SupportReferenceRules.normalize(supportReference)
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
                counterparty = normalizedCounterparty,
                supportReference = normalizedSupportReference,
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

    /** Save or edit a simple income/expense locally without including it in posted accounting. */
    suspend fun saveSimpleDraft(
        dateIso: String,
        description: String,
        kind: EntryKind,
        categoryAccountCode: String,
        paymentAccountCode: String,
        amountMinor: Long,
        currencyCode: String = UsdMoney.CURRENCY_CODE,
        draftId: String? = null,
        counterparty: String? = null,
        supportReference: String? = null,
    ): String {
        val normalizedCounterparty = CounterpartyRules.normalize(counterparty)
        val normalizedSupportReference = SupportReferenceRules.normalize(supportReference)
        require(currencyCode == UsdMoney.CURRENCY_CODE) { "Esta versión registra únicamente en dólares estadounidenses (USD)." }
        val postingDate = PostingDateRange.parse(dateIso, dateIso).startInclusive!!.toString()
        require(postingDate == dateIso) { "La fecha debe tener el formato AAAA-MM-DD." }
        val id = draftId ?: UUID.randomUUID().toString()
        return database.withTransaction {
            val current = draftId?.let { dao.getEntry(it) }
            if (draftId != null) require(current?.status == "DRAFT") { "Solo se pueden editar asientos en borrador." }
            val descriptionClean = description.trim()
            val lines = makeSimpleEntryLines(id, descriptionClean, kind, categoryAccountCode, paymentAccountCode, amountMinor, currencyCode)
            val entry = JournalEntryEntity(
                id = id,
                entryDate = dateIso,
                description = descriptionClean,
                kind = kind.name,
                categoryAccountCode = categoryAccountCode,
                paymentAccountCode = paymentAccountCode,
                currencyCode = currencyCode,
                amountMinor = amountMinor,
                status = "DRAFT",
                createdAt = current?.createdAt ?: Instant.now().toString(),
                counterparty = normalizedCounterparty,
                supportReference = normalizedSupportReference,
            )
            if (current == null) dao.insertEntry(entry) else dao.updateEntry(entry)
            if (current != null) dao.deleteLinesForDraft(id)
            dao.insertLines(lines)
            dao.insertAuditEvent(
                AuditEventEntity(
                    id = UUID.randomUUID().toString(),
                    entryId = id,
                    eventType = if (current == null) "DRAFT_CREATED" else "DRAFT_EDITED",
                    occurredAt = Instant.now().toString(),
                    eventDate = dateIso,
                    reason = if (current == null) "Borrador guardado en este dispositivo." else "Borrador editado antes de publicar.",
                ),
            )
            id
        }
    }

    /** Final validation is repeated transactionally; the database trigger locks lines and audits the one-way post. */
    suspend fun postDraft(draftId: String) = database.withTransaction {
        val entry = dao.getEntry(draftId) ?: throw IllegalArgumentException("No se encontró el borrador.")
        require(entry.status == "DRAFT") { "Solo se pueden publicar asientos en borrador." }
        require(CounterpartyRules.normalize(entry.counterparty) == entry.counterparty) { "La contraparte del borrador no es válida." }
        require(SupportReferenceRules.normalize(entry.supportReference) == entry.supportReference) { "La referencia de soporte del borrador no es válida." }
        require(entry.kind == EntryKind.INCOME.name || entry.kind == EntryKind.EXPENSE.name) { "Este borrador no es un ingreso o gasto simple." }
        val postingDate = PostingDateRange.parse(entry.entryDate, entry.entryDate).startInclusive!!.toString()
        require(postingDate == entry.entryDate) { "La fecha del borrador no es válida." }
        require(!dao.isDateClosed(postingDate)) { "La fecha pertenece a un periodo cerrado; no se puede publicar." }
        val expected = makeSimpleEntryLines(
            entry.id,
            entry.description,
            EntryKind.valueOf(entry.kind),
            entry.categoryAccountCode,
            entry.paymentAccountCode,
            entry.amountMinor,
            entry.currencyCode,
        )
        val actual = dao.getLines(entry.id)
        require(actual.size == expected.size && actual.map { listOf(it.accountCode, it.debitMinor, it.creditMinor, it.currencyCode) }.toSet() ==
            expected.map { listOf(it.accountCode, it.debitMinor, it.creditMinor, it.currencyCode) }.toSet()) {
            "Las líneas del borrador no coinciden con el asiento balanceado."
        }
        dao.updateEntry(entry.copy(status = "POSTED"))
    }

    private suspend fun makeSimpleEntryLines(
        entryId: String,
        description: String,
        kind: EntryKind,
        categoryAccountCode: String,
        paymentAccountCode: String,
        amountMinor: Long,
        currencyCode: String,
    ): List<JournalLineEntity> {
        val category = dao.getAccount(categoryAccountCode) ?: throw IllegalArgumentException("La categoría seleccionada no existe.")
        val payment = dao.getAccount(paymentAccountCode) ?: throw IllegalArgumentException("La cuenta de pago seleccionada no existe.")
        require(category.active) { "No se pueden registrar movimientos en una cuenta inactiva." }
        require(payment.active && payment.cashOrBank) { "Selecciona una cuenta activa de Caja o Banco." }
        val postings = AccountingRules.simpleCashEntry(
            kind, description, category.code, category.type, payment.code, payment.type, amountMinor, currencyCode,
        )
        val names = mapOf(category.code to category.name, payment.code to payment.name)
        return postings.map { line ->
            JournalLineEntity(
                id = UUID.randomUUID().toString(), entryId = entryId, accountCode = line.accountCode,
                debitMinor = line.debitMinor, creditMinor = line.creditMinor, currencyCode = line.currencyCode,
                accountNameSnapshot = names.getValue(line.accountCode),
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
                postings = allEntries.filter { it.entry.status == "POSTED" }.map { entry ->
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
