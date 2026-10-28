package com.neon.book01.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import com.neon.book01.domain.CounterpartyRules
import com.neon.book01.domain.SupportReferenceRules

/** A consistent, full-book snapshot. Amounts remain signed 64-bit minor-unit values. */
data class LedgerBackupSnapshot(
    val bookSettings: BookSettingsEntity?,
    val accounts: List<AccountEntity>,
    val journalEntries: List<JournalEntryEntity>,
    val journalLines: List<JournalLineEntity>,
    val auditEvents: List<AuditEventEntity>,
    val accountAuditEvents: List<AccountAuditEventEntity>,
    val periodCloses: List<PeriodCloseEntity>,
    val counterparties: List<CounterpartyEntity> = emptyList(),
    val invoices: List<InvoiceEntity> = emptyList(),
    val invoiceSettlements: List<InvoiceSettlementEntity> = emptyList(),
)

/** Versioned encrypted export format. This deliberately has no import/restore implementation. */
object LedgerBackupCodec {
    private val MAGIC = byteArrayOf(0x42, 0x30, 0x31, 0x45) // B01E
    private const val FORMAT_VERSION = 1
    private const val COUNTERPARTY_EXTENSION_MAGIC = 0x43505459 // CPTY
    private const val ACCOUNT_PARENT_EXTENSION_MAGIC = 0x5041524E // PARN
    private const val SUPPORT_REFERENCE_EXTENSION_MAGIC = 0x53524546 // SREF
    private const val INVOICE_EXTENSION_MAGIC = 0x494E5643 // INVC
    private const val PBKDF2_ITERATIONS = 210_000
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val TAG_BYTES = TAG_BITS / 8
    const val MAX_FILE_BYTES = 128 * 1024 * 1024
    private const val MAX_STRING_BYTES = 1 * 1024 * 1024
    private const val MAX_ROWS = 1_000_000
    private val random = SecureRandom()

    /** Passphrase characters are owned/cleared by the caller. */
    fun encrypt(snapshot: LedgerBackupSnapshot, passphrase: CharArray): ByteArray {
        require(passphrase.size >= 12) { "Use una frase de al menos 12 caracteres." }
        val plaintext = encodeSnapshot(snapshot)
        require(plaintext.size <= MAX_FILE_BYTES - 128) { "El libro es demasiado grande para exportarlo." }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val encryptedLength = plaintext.size + TAG_BYTES
        val header = header(salt, nonce, encryptedLength)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(header)
            val ciphertext = cipher.doFinal(plaintext)
            try {
                ByteArrayOutputStream(header.size + ciphertext.size).use { bytes ->
                    bytes.write(header)
                    bytes.write(ciphertext)
                    bytes.toByteArray()
                }.also { require(it.size <= MAX_FILE_BYTES) { "El libro es demasiado grande para exportarlo." } }
            } finally {
                ciphertext.fill(0)
                header.fill(0)
            }
        } finally {
            plaintext.fill(0)
            salt.fill(0)
            nonce.fill(0)
        }
    }

    /** Read a SAF stream incrementally, rejecting oversized input before growing its buffer further. */
    fun readBounded(input: InputStream): ByteArray {
        val output = WipingByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        try {
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() <= MAX_FILE_BYTES - count) { "El archivo de copia supera el tamaño máximo permitido." }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        } finally {
            buffer.fill(0)
            output.clear()
        }
    }

    /** Verifies the GCM tag before parsing any ledger content. */
    fun decrypt(file: ByteArray, passphrase: CharArray): LedgerBackupSnapshot {
        require(file.size in (MAGIC.size + 4 + 4 + SALT_BYTES + NONCE_BYTES + 4 + TAG_BYTES)..MAX_FILE_BYTES) {
            "El archivo de copia no es válido."
        }
        val input = DataInputStream(ByteArrayInputStream(file))
        val magic = ByteArray(MAGIC.size).also(input::readFully)
        require(magic.contentEquals(MAGIC)) { "Formato de copia no reconocido." }
        val version = input.readInt()
        require(version == FORMAT_VERSION) { "Esta versión del formato de copia no es compatible." }
        val iterations = input.readInt()
        require(iterations == PBKDF2_ITERATIONS) { "Parámetros de cifrado no compatibles." }
        val salt = ByteArray(SALT_BYTES).also(input::readFully)
        val nonce = ByteArray(NONCE_BYTES).also(input::readFully)
        val cipherLength = input.readInt()
        require(cipherLength >= TAG_BYTES && cipherLength <= MAX_FILE_BYTES && cipherLength == input.available()) {
            "El archivo de copia está incompleto o dañado."
        }
        val ciphertext = ByteArray(cipherLength).also(input::readFully)
        val authenticatedHeader = header(salt, nonce, cipherLength)
        var plaintext: ByteArray? = null
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(authenticatedHeader)
            val cleartext = try {
                cipher.doFinal(ciphertext)
            } catch (_: AEADBadTagException) {
                throw BackupAuthenticationException("No se pudo autenticar la copia. Verifique la frase y que el archivo no haya sido alterado.")
            }
            plaintext = cleartext
            return decodeSnapshot(cleartext)
        } finally {
            plaintext?.fill(0)
            ciphertext.fill(0)
            salt.fill(0)
            nonce.fill(0)
            authenticatedHeader.fill(0)
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, 256)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            try {
                SecretKeySpec(bytes, "AES")
            } finally {
                bytes.fill(0)
            }
        } finally {
            spec.clearPassword()
        }
    }

    private fun header(salt: ByteArray, nonce: ByteArray, cipherLength: Int): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { out ->
                out.write(MAGIC)
                out.writeInt(FORMAT_VERSION)
                out.writeInt(PBKDF2_ITERATIONS)
                out.write(salt)
                out.write(nonce)
                out.writeInt(cipherLength)
            }
            bytes.toByteArray()
        }

    private fun encodeSnapshot(book: LedgerBackupSnapshot): ByteArray = WipingByteArrayOutputStream().use { bytes ->
        val out = DataOutputStream(bytes)
            out.writeBoolean(book.bookSettings != null)
            book.bookSettings?.let { settings ->
                out.writeInt(settings.id)
                out.writeString(settings.bookName)
                out.writeString(settings.baseCurrency)
            }
            out.writeCount(book.accounts.size)
            book.accounts.forEach { account ->
                out.writeString(account.code)
                out.writeString(account.name)
                out.writeString(account.type)
                out.writeBoolean(account.active)
                out.writeBoolean(account.cashOrBank)
            }
            out.writeCount(book.journalEntries.size)
            book.journalEntries.forEach { entry ->
                out.writeString(entry.id)
                out.writeString(entry.entryDate)
                out.writeString(entry.description)
                out.writeString(entry.kind)
                out.writeString(entry.categoryAccountCode)
                out.writeString(entry.paymentAccountCode)
                out.writeString(entry.currencyCode)
                out.writeLong(entry.amountMinor)
                out.writeString(entry.status)
                out.writeString(entry.createdAt)
                out.writeNullableString(entry.reversalOfEntryId)
            }
            out.writeCount(book.journalLines.size)
            book.journalLines.forEach { line ->
                out.writeString(line.id)
                out.writeString(line.entryId)
                out.writeString(line.accountCode)
                out.writeLong(line.debitMinor)
                out.writeLong(line.creditMinor)
                out.writeString(line.currencyCode)
                out.writeString(line.accountNameSnapshot)
            }
            out.writeCount(book.auditEvents.size)
            book.auditEvents.forEach { event ->
                out.writeString(event.id)
                out.writeString(event.entryId)
                out.writeString(event.eventType)
                out.writeString(event.occurredAt)
                out.writeString(event.eventDate)
                out.writeString(event.reason)
                out.writeNullableString(event.relatedEntryId)
            }
            out.writeCount(book.accountAuditEvents.size)
            book.accountAuditEvents.forEach { event ->
                out.writeString(event.id)
                out.writeString(event.accountCode)
                out.writeString(event.occurredAt)
                out.writeString(event.oldName)
                out.writeString(event.newName)
                out.writeBoolean(event.oldActive)
                out.writeBoolean(event.newActive)
            }
            out.writeCount(book.periodCloses.size)
            book.periodCloses.forEach { close ->
                out.writeString(close.id)
                out.writeString(close.startDate)
                out.writeString(close.endDate)
                out.writeString(close.retainedEarningsAccountCode)
                out.writeLong(close.netResultMinor)
                out.writeNullableString(close.closingEntryId)
                out.writeString(close.closedAt)
            }
            // Preserve the legacy v1 record layout. Older authenticated v1 files end here.
            // This extension is appended inside the existing AES-GCM authenticated plaintext.
            val counterparties = book.journalEntries.filter { it.counterparty != null }
            out.writeInt(COUNTERPARTY_EXTENSION_MAGIC)
            out.writeCount(counterparties.size)
            counterparties.forEach { entry ->
                out.writeString(entry.id)
                require(CounterpartyRules.normalize(entry.counterparty) == entry.counterparty) { "La contraparte de la copia no es válida." }
                out.writeNullableString(entry.counterparty)
            }
            // Parent references are optional in older authenticated v1 snapshots.
            val parentLinks = book.accounts.filter { it.parentCode != null }
            out.writeInt(ACCOUNT_PARENT_EXTENSION_MAGIC)
            out.writeCount(parentLinks.size)
            parentLinks.forEach { account ->
                out.writeString(account.code)
                out.writeString(account.parentCode!!)
            }
            // Documentary references are a new authenticated v1 extension; prior files end before it.
            val supportReferences = book.journalEntries.filter { it.supportReference != null }
            out.writeInt(SUPPORT_REFERENCE_EXTENSION_MAGIC)
            out.writeCount(supportReferences.size)
            supportReferences.forEach { entry ->
                out.writeString(entry.id)
                val normalized = SupportReferenceRules.normalize(entry.supportReference)
                require(normalized == entry.supportReference) { "La referencia de soporte de la copia no es válida." }
                out.writeNullableString(entry.supportReference)
            }
            // Optional v1 extension. Older encrypted backups ending at the support-reference section remain valid.
            out.writeInt(INVOICE_EXTENSION_MAGIC)
            out.writeCount(book.counterparties.size)
            book.counterparties.forEach { party ->
                out.writeString(party.id); out.writeString(party.kind); out.writeString(party.displayName)
                out.writeNullableString(party.taxId); out.writeBoolean(party.active); out.writeNullableString(party.defaultAccountCode)
            }
            out.writeCount(book.invoices.size)
            book.invoices.forEach { invoice ->
                out.writeString(invoice.id); out.writeString(invoice.kind); out.writeString(invoice.invoiceDate); out.writeString(invoice.invoiceNumber)
                out.writeNullableString(invoice.controlNumber); out.writeString(invoice.registeredPartyId); out.writeString(invoice.description)
                out.writeLong(invoice.taxableBaseMinor); out.writeInt(invoice.vatRateBasisPoints); out.writeLong(invoice.vatMinor)
                out.writeLong(invoice.totalMinor); out.writeLong(invoice.paidMinor); out.writeString(invoice.paymentCondition)
                out.writeNullableString(invoice.dueDate); out.writeString(invoice.cashAccountCode); out.writeString(invoice.counterAccountCode)
                out.writeString(invoice.entryId); out.writeString(invoice.createdAt)
            }
            out.writeCount(book.invoiceSettlements.size)
            book.invoiceSettlements.forEach { settlement ->
                out.writeString(settlement.id); out.writeString(settlement.invoiceId); out.writeLong(settlement.amountMinor)
                out.writeString(settlement.settlementDate); out.writeString(settlement.cashAccountCode); out.writeString(settlement.entryId); out.writeString(settlement.createdAt)
            }
        out.flush()
        bytes.toByteArray()
    }

    internal fun decodeSnapshot(plaintext: ByteArray): LedgerBackupSnapshot {
        require(plaintext.size <= MAX_FILE_BYTES) { "El contenido de la copia es demasiado grande." }
        val input = DataInputStream(ByteArrayInputStream(plaintext))
        val settings = if (input.readBoolean()) {
            BookSettingsEntity(input.readInt(), input.readString(), input.readString())
        } else null
        val accounts = List(input.readCount(minimumRecordBytes = 14)) {
            AccountEntity(input.readString(), input.readString(), input.readString(), input.readBoolean(), input.readBoolean())
        }
        val entries = List(input.readCount(minimumRecordBytes = 45)) {
            JournalEntryEntity(
                id = input.readString(),
                entryDate = input.readString(),
                description = input.readString(),
                kind = input.readString(),
                categoryAccountCode = input.readString(),
                paymentAccountCode = input.readString(),
                currencyCode = input.readString(),
                amountMinor = input.readLong(),
                status = input.readString(),
                createdAt = input.readString(),
                reversalOfEntryId = input.readNullableString(),
            )
        }
        val lines = List(input.readCount(minimumRecordBytes = 36)) {
            JournalLineEntity(
                id = input.readString(),
                entryId = input.readString(),
                accountCode = input.readString(),
                debitMinor = input.readLong(),
                creditMinor = input.readLong(),
                currencyCode = input.readString(),
                accountNameSnapshot = input.readString(),
            )
        }
        val audits = List(input.readCount(minimumRecordBytes = 25)) {
            AuditEventEntity(
                id = input.readString(),
                entryId = input.readString(),
                eventType = input.readString(),
                occurredAt = input.readString(),
                eventDate = input.readString(),
                reason = input.readString(),
                relatedEntryId = input.readNullableString(),
            )
        }
        val accountAudits = List(input.readCount(minimumRecordBytes = 22)) {
            AccountAuditEventEntity(
                id = input.readString(),
                accountCode = input.readString(),
                occurredAt = input.readString(),
                oldName = input.readString(),
                newName = input.readString(),
                oldActive = input.readBoolean(),
                newActive = input.readBoolean(),
            )
        }
        val closes = List(input.readCount(minimumRecordBytes = 29)) {
            PeriodCloseEntity(
                id = input.readString(),
                startDate = input.readString(),
                endDate = input.readString(),
                retainedEarningsAccountCode = input.readString(),
                netResultMinor = input.readLong(),
                closingEntryId = input.readNullableString(),
                closedAt = input.readString(),
            )
        }
        var restoredEntries = entries // Legacy v1 snapshots end here and retain null optional references.
        var restoredAccounts = accounts // Legacy snapshots predate parentCode and retain null parents.
        if (input.available() > 0) {
            require(input.readInt() == COUNTERPARTY_EXTENSION_MAGIC) { "El contenido de la copia tiene datos sobrantes." }
            val count = input.readCount(minimumRecordBytes = 9)
            val knownIds = entries.map { it.id }.toSet()
            val byId = LinkedHashMap<String, String>()
            repeat(count) {
                val entryId = input.readString()
                val value = input.readNullableString()
                require(entryId in knownIds && value != null && byId.put(entryId, value) == null) {
                    "La extensión de contraparte no es válida."
                }
                require(CounterpartyRules.normalize(value) == value) { "La contraparte de la copia no es válida." }
            }
            restoredEntries = entries.map { entry -> entry.copy(counterparty = byId[entry.id]) }
        }
        if (input.available() > 0) {
            require(input.readInt() == ACCOUNT_PARENT_EXTENSION_MAGIC) { "El contenido de la copia tiene datos sobrantes." }
            val count = input.readCount(minimumRecordBytes = 10)
            val knownCodes = accounts.map { it.code }.toSet()
            val parents = LinkedHashMap<String, String>()
            repeat(count) {
                val code = input.readString()
                val parentCode = input.readString()
                require(code in knownCodes && parentCode in knownCodes && code != parentCode && parents.put(code, parentCode) == null) {
                    "La extensión de jerarquía de cuentas no es válida."
                }
            }
            restoredAccounts = accounts.map { account -> account.copy(parentCode = parents[account.code]) }
        }
        if (input.available() > 0) {
            require(input.readInt() == SUPPORT_REFERENCE_EXTENSION_MAGIC) { "El contenido de la copia tiene datos sobrantes." }
            val count = input.readCount(minimumRecordBytes = 9)
            val knownIds = entries.map { it.id }.toSet()
            val references = LinkedHashMap<String, String>()
            repeat(count) {
                val entryId = input.readString()
                val value = input.readNullableString()
                require(entryId in knownIds && value != null && references.put(entryId, value) == null) {
                    "La extensión de referencia de soporte no es válida."
                }
                require(SupportReferenceRules.normalize(value) == value) { "La referencia de soporte de la copia no es válida." }
            }
            restoredEntries = restoredEntries.map { entry -> entry.copy(supportReference = references[entry.id]) }
        }
        var counterparties = emptyList<CounterpartyEntity>()
        var invoices = emptyList<InvoiceEntity>()
        var invoiceSettlements = emptyList<InvoiceSettlementEntity>()
        if (input.available() > 0) {
            require(input.readInt() == INVOICE_EXTENSION_MAGIC) { "La extensión de facturas de la copia no es válida." }
            counterparties = List(input.readCount(minimumRecordBytes = 9)) {
                CounterpartyEntity(input.readString(), input.readString(), input.readString(), input.readNullableString(), input.readBoolean(), input.readNullableString())
            }
            invoices = List(input.readCount(minimumRecordBytes = 32)) {
                val id=input.readString(); val kind=input.readString(); val date=input.readString(); val number=input.readString()
                val control=input.readNullableString(); val party=input.readString(); val description=input.readString()
                val base=input.readLong(); val rate=input.readInt(); val vat=input.readLong(); val total=input.readLong(); val paid=input.readLong()
                val condition=input.readString(); val due=input.readNullableString(); val cash=input.readString(); val counter=input.readString(); val entry=input.readString(); val created=input.readString()
                InvoiceEntity(id,kind,date,number,control,party,description,base,rate,vat,total,paid,condition,due,cash,counter,entry,created)
            }
            invoiceSettlements = List(input.readCount(minimumRecordBytes = 32)) {
                InvoiceSettlementEntity(input.readString(),input.readString(),input.readLong(),input.readString(),input.readString(),input.readString(),input.readString())
            }
        }
        require(input.available() == 0) { "El contenido de la copia tiene datos sobrantes." }
        return LedgerBackupSnapshot(settings, restoredAccounts, restoredEntries, lines, audits, accountAudits, closes, counterparties, invoices, invoiceSettlements)
    }

    private fun DataOutputStream.writeCount(count: Int) {
        require(count in 0..MAX_ROWS) { "Demasiados registros para exportar." }
        writeInt(count)
    }

    private fun DataInputStream.readCount(minimumRecordBytes: Int): Int = readInt().also {
        require(it in 0..MAX_ROWS && (it == 0 || it <= available() / minimumRecordBytes)) {
            "Cantidad de registros no válida."
        }
    }

    private fun DataOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        try {
            require(bytes.size <= MAX_STRING_BYTES) { "Un campo excede el tamaño permitido." }
            writeInt(bytes.size)
            write(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun DataInputStream.readString(): String {
        val length = readInt()
        require(length in 0..MAX_STRING_BYTES && length <= available()) { "Campo de texto no válido." }
        val bytes = ByteArray(length).also(::readFully)
        return try {
            String(bytes, Charsets.UTF_8)
        } finally {
            bytes.fill(0)
        }
    }

    private fun DataOutputStream.writeNullableString(value: String?) {
        writeBoolean(value != null)
        value?.let { writeString(it) }
    }

    private fun DataInputStream.readNullableString(): String? = if (readBoolean()) readString() else null
}

private class WipingByteArrayOutputStream : ByteArrayOutputStream(8 * 1024) {
    fun clear() {
        buf.fill(0)
        reset()
    }

    override fun close() {
        clear()
    }
}

class BackupAuthenticationException(message: String) : SecurityException(message)
