package com.neon.book01.domain

import java.time.LocalDate
import java.time.YearMonth

/** Derived, display-only date ranges for the sales and purchase auxiliary books. */
enum class InvoiceBookPeriod(val displayLabel: String) {
    ALL("Todo el histórico"),
    THIS_MONTH("Este mes"),
    LAST_MONTH("Mes anterior"),
    THIS_YEAR("Este año"),
}

/** Invalid invoice dates are excluded from bounded periods, but remain visible in ALL. */
fun matchesInvoiceBookPeriod(
    invoiceDate: String,
    period: InvoiceBookPeriod,
    asOf: LocalDate,
): Boolean {
    if (period == InvoiceBookPeriod.ALL) return true
    val date = runCatching { LocalDate.parse(invoiceDate) }.getOrNull() ?: return false
    val month = YearMonth.from(asOf)
    return when (period) {
        InvoiceBookPeriod.ALL -> true
        InvoiceBookPeriod.THIS_MONTH -> YearMonth.from(date) == month
        InvoiceBookPeriod.LAST_MONTH -> YearMonth.from(date) == month.minusMonths(1)
        InvoiceBookPeriod.THIS_YEAR -> date.year == asOf.year
    }
}
