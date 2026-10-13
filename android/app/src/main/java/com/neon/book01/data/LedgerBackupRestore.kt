package com.neon.book01.data

import com.neon.book01.domain.CounterpartyRules
import com.neon.book01.domain.SupportReferenceRules
import com.neon.book01.domain.PeriodCloseAccount
import com.neon.book01.domain.PeriodCloseCalculator
import com.neon.book01.domain.PeriodCloseLinePlan
import com.neon.book01.domain.PeriodClosePosting
import com.neon.book01.domain.PeriodClosePostingLine
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

/** Parent-first deterministic ordering used by restore even when serialized rows are shuffled. */
internal fun parentFirstAccounts(accounts: List<AccountEntity>): List<AccountEntity> {
    val byCode = accounts.associateBy { it.code }
    require(byCode.size == accounts.size) { "Hay códigos de cuenta duplicados." }
    accounts.forEach { account ->
        account.parentCode?.let { parentCode ->
            val parent = byCode[parentCode]
                ?: throw IllegalArgumentException("La cuenta ${account.code} hace referencia a un padre que no existe.")
            require(parentCode != account.code) { "Una cuenta no puede ser su propio padre." }
            require(parent.active) { "La cuenta padre ${parent.code} debe estar activa." }
            require(parent.type == account.type) { "La cuenta padre debe tener la misma clasificación." }
        }
    }
    val childrenByParent = accounts.filter { it.parentCode != null }.groupBy { it.parentCode!! }
    val ready = java.util.PriorityQueue<String>()
    accounts.filter { it.parentCode == null }.forEach { ready.add(it.code) }
    val ordered = mutableListOf<AccountEntity>()
    while (ready.isNotEmpty()) {
        val code = ready.remove()
        ordered += byCode.getValue(code)
        childrenByParent[code].orEmpty().forEach { child -> ready.add(child.code) }
    }
    require(ordered.size == accounts.size) { "La jerarquía de cuentas contiene un ciclo." }
    return ordered
}

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
            require(accounts.getValue(seeded.code).parentCode == null) {
                "Las cuentas predeterminadas deben conservar su jerarquía original."
            }
        }
        parentFirstAccounts(snapshot.accounts)

        val entries = snapshot.journalEntries.associateBy { it.id }
        val reversedIds = snapshot.journalEntries.mapNotNull { it.reversalOfEntryId }
        requireUnique(reversedIds, "vínculos de reversa")
        snapshot.journalEntries.forEach { entry ->
            require(entry.id.isNotBlank() && entry.createdAt.isNotBlank()) {
                "Un asiento de la copia tiene datos incompletos."
            }
            parseDate(entry.entryDate)
            require(entry.amountMinor >= 0L) { "Un asiento contiene un importe negativo no válido." }
            require(CounterpartyRules.normalize(entry.counterparty) == entry.counterparty) { "La contraparte de un asiento no es válida." }
            require(SupportReferenceRules.normalize(entry.supportReference) == entry.supportReference) { "La referencia de soporte de un asiento no es válida." }
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
                require(entry.kind == "REVERSAL" && entry.status == "POSTED" &&
                    original.status == "POSTED" && original.kind != "REVERSAL" &&
                    original.kind != "PERIOD_CLOSE" && original.reversalOfEntryId == null &&
                    snapshot.journalEntries.count { it.reversalOfEntryId == original.id } == 1) {
                    "El vínculo de reversa no coincide con un asiento contabilizado y reversible."
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

        snapshot.journalEntries.filter { it.kind == "REVERSAL" }.forEach { reversal ->
            val original = entries.getValue(reversal.reversalOfEntryId!!)
            require(lineMultiset(linesByEntry.getValue(reversal.id)) ==
                lineMultiset(linesByEntry.getValue(original.id), swapDebitAndCredit = true)) {
                "Las líneas de la reversa no son la inversión exacta del asiento original."
            }
        }

        val closes = snapshot.periodCloses
        val closeEntryIds = closes.mapNotNull { it.closingEntryId }
        requireUnique(closeEntryIds, "vínculos de asiento de cierre")
        closes.forEach { close ->
            val start = parseDate(close.startDate)
            val end = parseDate(close.endDate)
            require(!end.isBefore(start)) { "Un cierre tiene un rango de fechas inválido." }
            require(close.retainedEarningsAccountCode in accounts) { "Un cierre hace referencia a una cuenta que no existe." }

            // Activity is recomputed from the authenticated journal, not from the saved close.
            // Historical retained-earnings (or P&L) accounts may since have been deactivated;
            // activity is irrelevant to arithmetic validation of a completed close.
            val planningAccounts = snapshot.accounts.map { PeriodCloseAccount(it.code, it.type, active = true) }
            val planningPostings = snapshot.journalEntries.asSequence()
                .filter { entry ->
                    entry.status == "POSTED" && entry.kind != "PERIOD_CLOSE" &&
                        !parseDate(entry.entryDate).isBefore(start) && !parseDate(entry.entryDate).isAfter(end)
                }
                .map { entry ->
                    PeriodClosePosting(
                        entryDate = entry.entryDate,
                        status = entry.status,
                        currencyCode = entry.currencyCode,
                        kind = entry.kind,
                        lines = linesByEntry.getValue(entry.id).map { line ->
                            PeriodClosePostingLine(line.accountCode, line.debitMinor, line.creditMinor, line.currencyCode)
                        },
                    )
                }
                .toList()
            val plan = PeriodCloseCalculator.calculate(
                accounts = planningAccounts,
                postings = planningPostings,
                startDate = close.startDate,
                endDate = close.endDate,
                retainedEarningsAccountCode = close.retainedEarningsAccountCode,
            )
            require(close.netResultMinor == plan.netResultMinor) {
                "El resultado neto guardado no coincide con la actividad del periodo."
            }
            val shouldHaveEntry = plan.lines.isNotEmpty() || plan.netResultMinor != 0L
            require((close.closingEntryId != null) == shouldHaveEntry) {
                "La presencia del asiento de cierre no coincide con el plan calculado."
            }
            close.closingEntryId?.let { closingId ->
                val closing = entries[closingId]
                    ?: throw IllegalArgumentException("Un cierre hace referencia a un asiento que no existe.")
                val expectedAmount = plan.lines.fold(0L) { sum, line -> Math.addExact(sum, line.debitMinor) }
                require(closing.status == "POSTED" && closing.kind == "PERIOD_CLOSE" &&
                    closing.entryDate == plan.endDate &&
                    closing.categoryAccountCode == plan.retainedEarningsAccountCode &&
                    closing.paymentAccountCode == plan.retainedEarningsAccountCode &&
                    closing.currencyCode == "USD" && closing.amountMinor == expectedAmount &&
                    lineMultiset(linesByEntry.getValue(closingId)) == plannedLineMultiset(plan.lines)) {
                    "Los datos y las líneas del asiento de cierre no coinciden con el plan calculado."
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

    private data class LedgerLineSignature(
        val accountCode: String,
        val currencyCode: String,
        val debitMinor: Long,
        val creditMinor: Long,
    )

    /** A counted multiset keeps duplicate lines significant instead of overwriting by account. */
    private fun lineMultiset(
        lines: List<JournalLineEntity>,
        swapDebitAndCredit: Boolean = false,
    ): Map<LedgerLineSignature, Int> = lines.groupingBy { line ->
        LedgerLineSignature(
            accountCode = line.accountCode,
            currencyCode = line.currencyCode,
            debitMinor = if (swapDebitAndCredit) line.creditMinor else line.debitMinor,
            creditMinor = if (swapDebitAndCredit) line.debitMinor else line.creditMinor,
        )
    }.eachCount()

    private fun plannedLineMultiset(lines: List<PeriodCloseLinePlan>): Map<LedgerLineSignature, Int> =
        lines.groupingBy { line ->
            LedgerLineSignature(line.accountCode, line.currencyCode, line.debitMinor, line.creditMinor)
        }.eachCount()

    private fun requireUnique(values: List<String>, description: String) {
        require(values.size == values.toSet().size) { "La copia contiene $description duplicados." }
    }

    private fun parseDate(value: String): LocalDate = try {
        LocalDate.parse(value)
    } catch (_: Exception) {
        throw IllegalArgumentException("La copia contiene una fecha inválida.")
    }
}
