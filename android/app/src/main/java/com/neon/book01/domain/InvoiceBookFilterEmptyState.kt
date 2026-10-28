package com.neon.book01.domain

/** True only when issued invoices exist in the period but none match the selected status. */
fun shouldShowInvoiceBookFilterEmptyState(
    filter: InvoiceBookFilter,
    issuedPeriodInvoiceCount: Int,
    matchingStatusCount: Int,
): Boolean = filter != InvoiceBookFilter.ALL && issuedPeriodInvoiceCount > 0 && matchingStatusCount == 0

/** Future-dated invoices are visible only under ALL until their issue date. */
fun shouldExplainFutureInvoicesExcludedFromStatusFilter(
    filter: InvoiceBookFilter,
    periodInvoiceCount: Int,
    issuedPeriodInvoiceCount: Int,
): Boolean = filter != InvoiceBookFilter.ALL && periodInvoiceCount > 0 && issuedPeriodInvoiceCount == 0


/** The period summary excludes future-dated invoices, which are listed only by ALL. */
fun shouldExplainFutureInvoicesInPeriodSummary(
    periodInvoiceCount: Int,
    issuedPeriodInvoiceCount: Int,
): Boolean = periodInvoiceCount > issuedPeriodInvoiceCount
