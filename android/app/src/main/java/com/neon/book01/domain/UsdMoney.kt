package com.neon.book01.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Exact USD money conversion for the MVP: one cent is one stored minor unit. */
object UsdMoney {
    const val CURRENCY_CODE = "USD"
    const val MINOR_UNITS_PER_UNIT = 100L

    /**
     * Parses USD without passing through binary floating point. Supported forms include
     * 1234.56, 1,234.56, 1234,56, 1.234,56, and an optional leading USD/US$/$. A
     * single separator followed by three digits is rejected because it is ambiguous.
     */
    fun parseMinorUnits(raw: String): Long {
        var input = raw.trim()
        require(input.isNotEmpty()) { "Escribe un importe en USD." }

        input = when {
            input.startsWith("USD", ignoreCase = true) -> input.substring(3).trim()
            input.startsWith("US$", ignoreCase = true) -> input.substring(3).trim()
            input.startsWith("$") -> input.substring(1).trim()
            else -> input
        }
        if (input.startsWith('.') || input.startsWith(',')) input = "0$input"
        require(input.isNotEmpty()) { "Escribe un importe en USD." }
        require(!input.startsWith('-') && !input.startsWith('+')) { "El importe debe ser positivo." }
        require(input.none { it.isWhitespace() }) { "No uses espacios dentro del importe." }
        require(input.all { it in '0'..'9' || it == '.' || it == ',' }) {
            "Usa solo cifras y separadores decimales."
        }

        val dotCount = input.count { it == '.' }
        val commaCount = input.count { it == ',' }
        val normalized = when {
            dotCount > 0 && commaCount > 0 -> {
                val decimalSeparator = if (input.lastIndexOf(',') > input.lastIndexOf('.')) ',' else '.'
                val groupingSeparator = if (decimalSeparator == ',') '.' else ','
                require(input.count { it == decimalSeparator } == 1) { "Revisa los separadores del importe." }
                val parts = input.split(decimalSeparator)
                require(parts.size == 2 && parts[1].length in 1..2) { "USD admite como máximo dos decimales." }
                require(validGroupedInteger(parts[0], groupingSeparator)) { "Revisa los separadores de miles." }
                parts[0].replace(groupingSeparator.toString(), "") + "." + parts[1]
            }
            dotCount + commaCount == 0 -> input
            dotCount + commaCount > 1 -> {
                val separator = if (dotCount > 0) '.' else ','
                require(validGroupedInteger(input, separator)) { "Revisa los separadores de miles." }
                input.replace(separator.toString(), "")
            }
            else -> {
                val separator = if (dotCount == 1) '.' else ','
                val parts = input.split(separator)
                require(parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                    "Revisa el importe. Ejemplo: 1250.75 o 1.250,75."
                }
                when {
                    parts[1].length <= 2 -> parts[0] + "." + parts[1]
                    parts[1].length == 3 -> throw IllegalArgumentException(
                        "El importe es ambiguo. Escribe, por ejemplo, 1234.00 o 1,234.00.",
                    )
                    else -> throw IllegalArgumentException("USD admite como máximo dos decimales.")
                }
            }
        }

        val minor = BigDecimal(normalized)
            .setScale(2, RoundingMode.UNNECESSARY)
            .movePointRight(2)
            .longValueExact()
        require(minor > 0) { "El importe debe ser mayor que cero." }
        return minor
    }

    fun sumMinorUnits(values: Iterable<Long>): Long =
        values.fold(0L) { total, value -> Math.addExact(total, value) }

    fun subtractMinorUnits(left: Long, right: Long): Long = Math.subtractExact(left, right)

    /** Formats integer cents as an exact Spanish-readable USD amount, e.g. US$ 1.234,56. */
    fun format(minorUnits: Long): String {
        val symbols = DecimalFormatSymbols(Locale.forLanguageTag("es-VE")).apply {
            decimalSeparator = ','
            groupingSeparator = '.'
        }
        val formatter = DecimalFormat("#,##0.00", symbols).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
            isGroupingUsed = true
            roundingMode = RoundingMode.UNNECESSARY
        }
        return "US$ ${formatter.format(BigDecimal.valueOf(minorUnits, 2))}"
    }

    private fun validGroupedInteger(value: String, separator: Char): Boolean {
        val groups = value.split(separator)
        if (groups.size < 2) return false
        return groups.first().length in 1..3 &&
            groups.first().all { it in '0'..'9' } &&
            groups.drop(1).all { group -> group.length == 3 && group.all { it in '0'..'9' } }
    }
}
