package com.neon.book01.data

import java.math.BigInteger
import java.time.LocalDate

/** Counts shown to the user after decryption and before the separate confirmation step. */
data class LedgerBackupPreview(
    val bookName: String,
    val accountCount: Int,
    val journalEntryCount: Int,
    val journalLineCount: Int,
    val auditEventCount: Int,
    val accountAuditEventCount: Int,
    val periodCloseCount: Int,
)

/** Structural and accounting consistency checks for authenticated v1 snapshots. */
object LedgerBackupValidator {
    fun validate(snapshot: LedgerBackupSnapshot): LedgerBackupPreview {
        val settings = snapshot.bookSettings
            ?: throw IllegalArgumentException("La copia no contiene la configuración del libro.")
        require(settings.id == 1 && settings.bookName.trim().length in 2..60 && settings.baseCurrency.isNotBlank()) {
            "La configuración del libro no es válida."
        }

        requireUnique(snapshot.accounts.map { it.code }, "códigos de cuenta")
        requireUnique(snapshot.journalEntries.map { it.id }, "asientos")
        requireUnique(snapshot.journalLines.map { it.id }, "líneas contables")
        requireUnique(snapshot.auditEvents.map { it.id }, "eventos de auditoría")
        requireUnique(snapshot.accountAuditEvents.map { it.id }, "eventos de cuenta")
        requireUnique(snapshot.periodCloses.map { it.id }, "cierres de periodo")

        val accounts = snapshot.accounts.associateBy { it.code }
        require(accounts.size == snapshot.accounts.size) { "Hay códigos de cuenta duplicados." }
        require(snapshot.accounts.all { it.code.isNotBlank() && it.name.isNotBlank() && it.type.isNotBlank() }) {
            "Una cuenta de la copia tiene datos incompletos."
        }
        val accountTypes = accounts.mapValues { it.value.type }
        LedgerBookDefaults.accounts.forEach { seeded ->
            require(accountTypes[seeded.code] == seeded.type) {
                "La copia no conserva la clasificación inmutable de las cuentas predeterminadas."
            }
        }

        val entries = snapshot.journalEntries.associateBy { it.id }
        val reversedIds = snapshot.journalEntries.mapNotNull { it.reversalOfEntryId }
        requireUnique(reversedIds, "vínculos de reversa")
        snapshot.journalEntries.forEach { entry ->
            require(entry.id.isNotBlank() && entry.createdAt.isNotBlank()) {
                "Un asiento de la copia tiene datos incompletos."
            }
            parseDate(entry.entryDate)
            require(entry.amountMinor >= 0L) { "Un asiento contiene un importe negativo no válido." }
            require(entry.status == "DRAFT" || entry.status == "POSTED") { "La copia contiene un estado de asiento no válido." }
            require(entry.status != "DRAFT" || entry.kind == "INCOME" || entry.kind == "EXPENSE") {
                "La copia contiene un borrador que no es un ingreso o gasto simple."
            }
            require(entry.categoryAccountCode in accounts && entry.paymentAccountCode in accounts) {
                "Un asiento hace referencia a una cuenta que no existe."
            }
            entry.reversalOfEntryId?.let { originalId ->
                val original = entries[originalId]
                require(originalId != entry.id && original != null) { "Un vínculo de reversa no apunta a un asiento existente." }
                require(entry.kind == "REVERSAL" && original.kind != "REVERSAL" && original.kind != "PERIOD_CLOSE") {
                    "El vínculo de reversa no coincide con un asiento reversible."
                }
            } ?: require(entry.kind != "REVERSAL") { "Una reversa no conserva su vínculo al asiento original." }
        }

        requireUnique(snapshot.journalLines.map { it.id }, "identificadores de línea")
        val linesByEntry = snapshot.journalLines.groupBy { it.entryId }
        snapshot.journalLines.forEach { line ->
            val entry = entries[line.entryId]
                ?: throw IllegalArgumentException("Una línea hace referencia a un asiento que no existe.")
            require(line.accountCode in accounts) { "Una línea hace referencia a una cuenta que no existe." }
            require(line.debitMinor >= 0L && line.creditMinor >= 0L && (line.debitMinor == 0L) != (line.creditMinor == 0L)) {
                "Una línea debe tener un débito o un crédito positivo, pero no ambos."
            }
            require(line.currencyCode.isNotBlank() && line.currencyCode == entry.currencyCode) {
                "La moneda de una línea no coincide con la de su asiento."
            }
            require(line.accountNameSnapshot.isNotEmpty()) { "Una línea no conserva el nombre histórico de su cuenta." }
        }
        snapshot.journalEntries.forEach { entry ->
            val lines = linesByEntry[entry.id].orEmpty()
            require(lines.size >= 2) { "El asiento ${entry.id} no tiene suficientes líneas contables." }
            val debit = lines.fold(BigInteger.ZERO) { sum, line -> sum + BigInteger.valueOf(line.debitMinor) }
            val credit = lines.fold(BigInteger.ZERO) { sum, line -> sum + BigInteger.valueOf(line.creditMinor) }
            require(debit.signum() > 0 && debit == credit) { "El asiento ${entry.id} no está balanceado." }
            require(debit == BigInteger.valueOf(entry.amountMinor)) { "El importe declarado del asiento ${entry.id} no coincide con sus líneas." }
        }

        snapshot.auditEvents.forEach { event ->
            parseDate(event.eventDate)
            require(event.entryId in entries && (event.relatedEntryId == null || event.relatedEntryId in entries)) {
                "Un evento de auditoría hace referencia a un asiento que no existe."
            }
        }
        snapshot.accountAuditEvents.forEach { event ->
            require(event.accountCode in accounts) { "Un evento de cuenta hace referencia a una cuenta que no existe." }
        }

        val closes = snapshot.periodCloses
        val closeEntryIds = closes.mapNotNull { it.closingEntryId }
        requireUnique(closeEntryIds, "vínculos de asiento de cierre")
        closes.forEach { close ->
            val start = parseDate(close.startDate)
            val end = parseDate(close.endDate)
            require(!end.isBefore(start)) { "Un cierre tiene un rango de fechas inválido." }
            require(close.retainedEarningsAccountCode in accounts) { "Un cierre hace referencia a una cuenta que no existe." }
            close.closingEntryId?.let { closingId ->
                val closing = entries[closingId]
                    ?: throw IllegalArgumentException("Un cierre hace referencia a un asiento que no existe.")
                require(closing.kind == "PERIOD_CLOSE" && closing.entryDate == close.endDate &&
                    closing.categoryAccountCode == close.retainedEarningsAccountCode) {
                    "Los metadatos del cierre no coinciden con su asiento de cierre."
                }
            }
        }
        closes.forEachIndexed { index, first ->
            val firstStart = parseDate(first.startDate)
            val firstEnd = parseDate(first.endDate)
            closes.drop(index + 1).forEach { second ->
                val secondStart = parseDate(second.startDate)
                val secondEnd = parseDate(second.endDate)
                require(firstEnd.isBefore(secondStart) || secondEnd.isBefore(firstStart)) {
                    "La copia contiene periodos cerrados superpuestos."
                }
            }
        }
        snapshot.journalEntries.filter { it.kind == "PERIOD_CLOSE" }.forEach { entry ->
            require(entry.id in closeEntryIds) { "Un asiento de cierre no tiene metadatos de periodo asociados." }
        }

        return LedgerBackupPreview(
            bookName = settings.bookName,
            accountCount = snapshot.accounts.size,
            journalEntryCount = snapshot.journalEntries.size,
            journalLineCount = snapshot.journalLines.size,
            auditEventCount = snapshot.auditEvents.size,
            accountAuditEventCount = snapshot.accountAuditEvents.size,
            periodCloseCount = snapshot.periodCloses.size,
        )
    }

    private fun requireUnique(values: List<String>, description: String) {
        require(values.size == values.toSet().size) { "La copia contiene $description duplicados." }
    }

    private fun parseDate(value: String): LocalDate = try {
        LocalDate.parse(value)
    } catch (_: Exception) {
        throw IllegalArgumentException("La copia contiene una fecha inválida.")
    }
}
