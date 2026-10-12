package com.neon.book01.domain

/** Pure rules for creating a correcting counter-entry without changing the posted original. */
object ReversalRules {
    data class JournalLine(
        val accountCode: String,
        val debitMinor: Long,
        val creditMinor: Long,
        val currencyCode: String,
    )

    fun inverseLines(originalLines: List<JournalLine>): List<JournalLine> {
        require(originalLines.isNotEmpty()) { "El asiento original no tiene líneas." }
        val totalsByCurrency = mutableMapOf<String, Pair<Long, Long>>()
        originalLines.forEach { line ->
            require(line.accountCode.isNotBlank() && line.currencyCode.isNotBlank()) { "Cada línea debe identificar una cuenta y una moneda." }
            require(line.debitMinor >= 0 && line.creditMinor >= 0) { "Los importes no pueden ser negativos." }
            require((line.debitMinor == 0L) xor (line.creditMinor == 0L)) {
                "Cada línea original debe ser débito o crédito, no ambos."
            }
            val current = totalsByCurrency[line.currencyCode] ?: (0L to 0L)
            totalsByCurrency[line.currencyCode] =
                Math.addExact(current.first, line.debitMinor) to Math.addExact(current.second, line.creditMinor)
        }
        require(totalsByCurrency.values.all { (debit, credit) -> debit == credit }) {
            "El asiento original no cuadra por moneda y no se puede reversar automáticamente."
        }
        return originalLines.map { line ->
            line.copy(debitMinor = line.creditMinor, creditMinor = line.debitMinor)
        }
    }

    fun requireEligible(status: String, reversalOfEntryId: String?, alreadyReversed: Boolean) {
        require(status == "POSTED") { "Solo se pueden reversar asientos contabilizados." }
        require(reversalOfEntryId == null) { "No se puede reversar una reversa." }
        require(!alreadyReversed) { "Este asiento ya tiene una reversa." }
    }

    fun validateRequest(dateIso: String, reason: String) {
        require(runCatching { java.time.LocalDate.parse(dateIso) }.isSuccess) {
            "La fecha de reversa debe ser una fecha válida en formato AAAA-MM-DD."
        }
        require(reason.isNotBlank()) { "Escribe el motivo de la reversa." }
    }
}
