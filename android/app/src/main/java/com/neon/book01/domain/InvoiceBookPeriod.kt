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

/** Invalid dates remain visible in ALL for data review; future-dated invoices are not part of closed periods. */
fun matchesInvoiceBookPeriod(
    invoiceDate: String,
    period: InvoiceBookPeriod,
    asOf: LocalDate,
): Boolean {
    val date = runCatching { LocalDate.parse(invoiceDate) }.getOrNull()
        ?: return period == InvoiceBookPeriod.ALL
    if (period != InvoiceBookPeriod.ALL && date.isAfter(asOf)) return false
    if (period == InvoiceBookPeriod.ALL) return true
    val month = YearMonth.from(asOf)
    return when (period) {
        InvoiceBookPeriod.ALL -> true
        InvoiceBookPeriod.THIS_MONTH -> YearMonth.from(date) == month
        InvoiceBookPeriod.LAST_MONTH -> YearMonth.from(date) == month.minusMonths(1)
        InvoiceBookPeriod.THIS_YEAR -> date.year == asOf.year
    }
}

/** True only when an invoice has a valid issue date on or before the requested cutoff. */
fun isInvoiceIssuedAsOf(invoiceDate: String, asOf: LocalDate): Boolean =
    runCatching { LocalDate.parse(invoiceDate) }.getOrNull()?.let { !it.isAfter(asOf) } ?: false
