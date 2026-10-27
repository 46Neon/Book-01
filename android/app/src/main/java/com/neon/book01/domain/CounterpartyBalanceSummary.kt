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
    val overdue1To30Minor: Long = 0L,
    val overdue31To60Minor: Long = 0L,
    val overdueOver60Minor: Long = 0L,
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
    data class MutableBalance(
        var count: Int = 0,
        var outstanding: Long = 0L,
        var overdue: Long = 0L,
        var overdue1To30: Long = 0L,
        var overdue31To60: Long = 0L,
        var overdueOver60: Long = 0L,
    )
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
        val daysPastDue = invoice.dueDate?.let {
            runCatching { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(it), asOf) }.getOrNull()
        }
        if (daysPastDue != null && daysPastDue > 0L) {
            balance.overdue = Math.addExact(balance.overdue, pending)
            when {
                daysPastDue <= 30L -> balance.overdue1To30 = Math.addExact(balance.overdue1To30, pending)
                daysPastDue <= 60L -> balance.overdue31To60 = Math.addExact(balance.overdue31To60, pending)
                else -> balance.overdueOver60 = Math.addExact(balance.overdueOver60, pending)
            }
        }
    }
    return balances.map { (partyId, balance) ->
        CounterpartyBalanceSummary(partyId, balance.count, balance.outstanding, balance.overdue, balance.overdue1To30, balance.overdue31To60, balance.overdueOver60)
    }.sortedBy { it.counterpartyId }
}
