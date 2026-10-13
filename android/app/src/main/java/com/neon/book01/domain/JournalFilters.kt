package com.neon.book01.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

/** Account identity available to journal filtering; no counterparty field is modeled today. */
data class JournalFilterAccount(val code: String, val name: String)

data class JournalFilterRecord(
    val id: String,
    val dateIso: String,
    val description: String,
    val kind: String,
    val status: JournalFilterStatus,
    val accounts: List<JournalFilterAccount>,
)

enum class JournalFilterStatus {
    ALL,
    POSTED,
    DRAFT,
}

data class JournalFilterCriteria(
    val fromDate: String = "",
    val toDate: String = "",
    val searchText: String = "",
    val accountCode: String? = null,
    /** `INCOME`, `EXPENSE`, or null for both. */
    val kind: String? = null,
    val status: JournalFilterStatus = JournalFilterStatus.ALL,
)

data class JournalDateRangeValidation(
    val fromDate: LocalDate?,
    val toDate: LocalDate?,
    val error: String? = null,
) {
    val isValid: Boolean get() = error == null
}

private val journalDateFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd")
    .withResolverStyle(ResolverStyle.STRICT)

/** Empty endpoints are allowed; any populated endpoint must be a real, exact ISO date. */
fun validateJournalDateRange(fromText: String, toText: String): JournalDateRangeValidation {
    val from = parseJournalDate(fromText)
        ?: if (fromText.isNotEmpty()) {
            return JournalDateRangeValidation(null, null, "La fecha desde debe ser una fecha válida con formato AAAA-MM-DD.")
        } else null
    val to = parseJournalDate(toText)
        ?: if (toText.isNotEmpty()) {
            return JournalDateRangeValidation(from, null, "La fecha hasta debe ser una fecha válida con formato AAAA-MM-DD.")
        } else null
    if (from != null && to != null && from.isAfter(to)) {
        return JournalDateRangeValidation(from, to, "La fecha desde no puede ser posterior a la fecha hasta.")
    }
    return JournalDateRangeValidation(from, to)
}

/** Pure entry-level filter: a match retains the whole entry and therefore all its debit/credit lines. */
fun filterJournalRecords(
    records: List<JournalFilterRecord>,
    criteria: JournalFilterCriteria,
): List<JournalFilterRecord> {
    val dateRange = validateJournalDateRange(criteria.fromDate, criteria.toDate)
    if (!dateRange.isValid) return emptyList()
    val query = criteria.searchText.trim()
    return records.filter { record ->
        val recordDate = parseJournalDate(record.dateIso) ?: return@filter false
        (dateRange.fromDate == null || !recordDate.isBefore(dateRange.fromDate)) &&
            (dateRange.toDate == null || !recordDate.isAfter(dateRange.toDate)) &&
            (criteria.kind == null || record.kind.equals(criteria.kind, ignoreCase = true)) &&
            (criteria.status == JournalFilterStatus.ALL || record.status == criteria.status) &&
            (criteria.accountCode == null || record.accounts.any { it.code == criteria.accountCode }) &&
            (query.isEmpty() || record.description.contains(query, ignoreCase = true) ||
                record.accounts.any { it.code.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true) })
    }
}

private fun parseJournalDate(value: String): LocalDate? {
    if (value.isEmpty()) return null
    return try {
        LocalDate.parse(value, journalDateFormatter)
    } catch (_: DateTimeParseException) {
        null
    }
}
