package com.neon.book01.domain

/** Exact, display-only gross-total summary for invoices in a selected book/date period. */
data class InvoiceBookSummary(val invoiceCount: Int, val grossTotalMinor: Long)

/**
 * Summarizes invoice totals without mixing in settlements or applying tax/status assumptions.
 * Long arithmetic is checked so an unexpectedly large book cannot silently wrap the total.
 */
fun summarizeInvoiceBookTotals(totalsMinor: Iterable<Long>): InvoiceBookSummary {
    var count = 0
    var total = 0L
    for (amount in totalsMinor) {
        require(amount >= 0L) { "El total de una factura no puede ser negativo." }
        count = Math.addExact(count, 1)
        total = Math.addExact(total, amount)
    }
    return InvoiceBookSummary(count, total)
}
