package com.neon.book01.domain

/** Minimal persisted identity needed to warn about a likely duplicate before posting. */
data class InvoiceIdentity(
    val kind: String,
    val counterpartyId: String,
    val invoiceNumber: String,
)

/**
 * A convenience check for the entry form. The repository's transactional duplicate check remains
 * authoritative, so this read-only helper never replaces the final write-time validation.
 */
object InvoiceDuplicateRules {
    fun normalizeNumber(value: String): String = value.trim().uppercase()

    fun isDuplicate(
        kind: String,
        counterpartyId: String,
        invoiceNumber: String,
        existing: Iterable<InvoiceIdentity>,
    ): Boolean {
        val normalizedNumber = normalizeNumber(invoiceNumber)
        if (kind.isBlank() || counterpartyId.isBlank() || normalizedNumber.isBlank()) return false
        return existing.any {
            it.kind == kind &&
                it.counterpartyId == counterpartyId.trim() &&
                normalizeNumber(it.invoiceNumber) == normalizedNumber
        }
    }
}
