package com.neon.book01.domain

import java.time.LocalDate

/** Derived invoice balance state; it does not change or post accounting entries. */
enum class InvoiceSettlementStatus(val displayLabel: String) {
    PAID("Pagada"),
    PARTIALLY_PAID("Parcial"),
    OPEN("Abierta"),
    OVERDUE("Vencida"),
}

/**
 * Derives status solely from persisted settlements and an explicitly recorded due date.
 * An absent/invalid due date never implies overdue. A past-due balance is overdue even
 * when partial payments have already been recorded.
 */
fun invoiceSettlementStatus(
    totalMinor: Long,
    settledMinor: Long,
    dueDate: String?,
    asOf: LocalDate,
): InvoiceSettlementStatus {
    require(totalMinor >= 0) { "El total no puede ser negativo." }
    require(settledMinor >= 0) { "Los pagos acumulados no pueden ser negativos." }

    val pending = (totalMinor - settledMinor).coerceAtLeast(0L)
    if (pending == 0L) return InvoiceSettlementStatus.PAID

    val due = dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    if (due != null && due.isBefore(asOf)) return InvoiceSettlementStatus.OVERDUE
    if (settledMinor > 0L) return InvoiceSettlementStatus.PARTIALLY_PAID
    return InvoiceSettlementStatus.OPEN
}
