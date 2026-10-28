package com.neon.book01.domain

/** A derived, display-only filter for the sales/purchase auxiliary books. */
enum class InvoiceBookFilter(val displayLabel: String) {
    ALL("Todas"),
    OPEN("Abiertas"),
    PARTIALLY_PAID("Parciales"),
    OVERDUE("Vencidas"),
    PAID("Pagadas"),
}

/** Does not mutate invoices, settlements, or posted accounting entries. */
fun matchesInvoiceBookFilter(filter: InvoiceBookFilter, status: InvoiceSettlementStatus): Boolean =
    filter == InvoiceBookFilter.ALL || when (filter) {
        InvoiceBookFilter.ALL -> true
        InvoiceBookFilter.OPEN -> status == InvoiceSettlementStatus.OPEN
        InvoiceBookFilter.PARTIALLY_PAID -> status == InvoiceSettlementStatus.PARTIALLY_PAID
        InvoiceBookFilter.OVERDUE -> status == InvoiceSettlementStatus.OVERDUE
        InvoiceBookFilter.PAID -> status == InvoiceSettlementStatus.PAID
    }
