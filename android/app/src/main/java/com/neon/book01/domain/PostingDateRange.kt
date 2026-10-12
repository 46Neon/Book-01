package com.neon.book01.domain

import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Inclusive date bounds for reports. Inputs and stored dates must use strict ISO yyyy-MM-dd. */
data class PostingDateRange private constructor(
    val startInclusive: LocalDate?,
    val endInclusive: LocalDate?,
) {
    fun includes(isoDate: String): Boolean {
        val date = parseIsoDate(isoDate, "La fecha del movimiento")
        return (startInclusive == null || !date.isBefore(startInclusive)) &&
            (endInclusive == null || !date.isAfter(endInclusive))
    }

    companion object {
        fun parse(startText: String?, endText: String?): PostingDateRange {
            val start = parseBound(startText, "La fecha inicial")
            val end = parseBound(endText, "La fecha final")
            require(start == null || end == null || !start.isAfter(end)) {
                "La fecha inicial no puede ser posterior a la fecha final."
            }
            return PostingDateRange(start, end)
        }

        private fun parseBound(text: String?, label: String): LocalDate? {
            val value = text?.trim().orEmpty()
            return if (value.isEmpty()) null else parseIsoDate(value, label)
        }

        private fun parseIsoDate(value: String, label: String): LocalDate {
            require(ISO_DATE.matches(value)) {
                "$label debe tener el formato AAAA-MM-DD (por ejemplo, 2026-03-14)."
            }
            return try {
                LocalDate.parse(value)
            } catch (_: DateTimeParseException) {
                throw IllegalArgumentException("$label no es una fecha válida en formato AAAA-MM-DD.")
            }
        }

        private val ISO_DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
    }
}
