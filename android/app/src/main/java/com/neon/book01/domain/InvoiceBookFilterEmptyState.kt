package com.neon.book01.domain

/** True only when the period has invoices but the selected status has no matches. */
fun shouldShowInvoiceBookFilterEmptyState(
    filter: InvoiceBookFilter,
    periodInvoiceCount: Int,
    matchingStatusCount: Int,
): Boolean = filter != InvoiceBookFilter.ALL && periodInvoiceCount > 0 && matchingStatusCount == 0
