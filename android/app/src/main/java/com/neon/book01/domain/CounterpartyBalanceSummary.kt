package com.neon.book01.domain

import java.time.LocalDate

data class InvoiceBalanceInput(
    val invoiceId: String,
    val counterpartyId: String,
    val totalMinor: Long,
    val dueDate: String?,
)

data class SettlementBalanceInput(
    val invoiceId: String,
    val amountMinor: Long,
)

data class CounterpartyBalanceSummary(
    val counterpartyId: String,
    val invoiceCount: Int,
    val outstandingMinor: Long,
    val overdueMinor: Long,
)

/** Summarizes outstanding balances from saved invoices and appended settlement records. */
fun summarizeCounterpartyBalances(
    invoices: List<InvoiceBalanceInput>,
    settlements: List<SettlementBalanceInput>,
    asOf: LocalDate,
): List<CounterpartyBalanceSummary> {
    val settledByInvoice = mutableMapOf<String, Long>()
    settlements.forEach { settlement ->
        require(settlement.amountMinor > 0L) { "El importe del abono debe ser positivo." }
        settledByInvoice[settlement.invoiceId] = Math.addExact(settledByInvoice[settlement.invoiceId] ?: 0L, settlement.amountMinor)
    }
    data class MutableBalance(var count: Int = 0, var outstanding: Long = 0L, var overdue: Long = 0L)
    val balances = linkedMapOf<String, MutableBalance>()
    invoices.forEach { invoice ->
        require(invoice.invoiceId.isNotBlank() && invoice.counterpartyId.isNotBlank())
        require(invoice.totalMinor >= 0L) { "El total de la factura no puede ser negativo." }
        val settled = settledByInvoice[invoice.invoiceId] ?: 0L
        require(settled <= invoice.totalMinor) { "Los abonos superan el total de la factura." }
        val pending = Math.subtractExact(invoice.totalMinor, settled)
        val balance = balances.getOrPut(invoice.counterpartyId) { MutableBalance() }
        balance.count = Math.addExact(balance.count, 1)
        balance.outstanding = Math.addExact(balance.outstanding, pending)
        val overdue = invoice.dueDate?.let { runCatching { LocalDate.parse(it).isBefore(asOf) }.getOrDefault(false) } == true
        if (overdue) balance.overdue = Math.addExact(balance.overdue, pending)
    }
    return balances.map { (partyId, balance) ->
        CounterpartyBalanceSummary(partyId, balance.count, balance.outstanding, balance.overdue)
    }.sortedBy { it.counterpartyId }
}
