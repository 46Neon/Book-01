package com.neon.book01.domain

import com.neon.book01.data.EntryWithLines

data class GeneralLedgerRow(
    val entryId: String,
    val entryDate: String,
    val createdAt: String,
    val description: String,
    val debitMinor: Long,
    val creditMinor: Long,
    /** Positive means debit balance; negative means credit balance. */
    val runningBalanceMinor: Long,
)

data class GeneralLedgerReport(
    val accountCode: String,
    val currencyCode: String,
    val rows: List<GeneralLedgerRow>,
    val totalDebitMinor: Long,
    val totalCreditMinor: Long,
    /** Positive means debit balance; negative means credit balance. */
    val closingBalanceMinor: Long,
    val excludedOtherCurrencyLineCount: Int,
    val excludedCurrencyCodes: List<String>,
)

object GeneralLedgerCalculator {
    fun calculate(
        accountCode: String,
        entries: List<EntryWithLines>,
        currencyCode: String = UsdMoney.CURRENCY_CODE,
    ): GeneralLedgerReport {
        require(accountCode.isNotBlank()) { "Selecciona una cuenta para consultar el Mayor." }
        require(currencyCode.isNotBlank()) { "La moneda del libro no puede estar vacía." }

        val accountLines = entries.asSequence()
            .filter { it.entry.status == "POSTED" }
            .flatMap { entryWithLines ->
                entryWithLines.lines.asSequence()
                    .filter { it.accountCode == accountCode }
                    .map { line -> Triple(entryWithLines.entry, line, entryWithLines.entry.description) }
            }
            .toList()

        val selectedCurrencyLines = accountLines
            .filter { (_, line, _) -> line.currencyCode == currencyCode }
            .sortedWith(
                compareBy<Triple<com.neon.book01.data.JournalEntryEntity, com.neon.book01.data.JournalLineEntity, String>>(
                    { it.first.entryDate },
                    { it.first.createdAt },
                    { it.first.id },
                    { it.second.id },
                ),
            )
        val excludedLines = accountLines.filter { (_, line, _) -> line.currencyCode != currencyCode }

        var runningBalance = 0L
        val rows = selectedCurrencyLines.map { (entry, line, description) ->
            runningBalance = Math.addExact(runningBalance, line.debitMinor)
            runningBalance = Math.subtractExact(runningBalance, line.creditMinor)
            if (runningBalance == Long.MIN_VALUE) {
                throw ArithmeticException("El saldo supera el rango numérico seguro.")
            }
            GeneralLedgerRow(
                entryId = entry.id,
                entryDate = entry.entryDate,
                createdAt = entry.createdAt,
                description = description,
                debitMinor = line.debitMinor,
                creditMinor = line.creditMinor,
                runningBalanceMinor = runningBalance,
            )
        }
        val totalDebit = selectedCurrencyLines.fold(0L) { total, (_, line, _) ->
            Math.addExact(total, line.debitMinor)
        }
        val totalCredit = selectedCurrencyLines.fold(0L) { total, (_, line, _) ->
            Math.addExact(total, line.creditMinor)
        }

        return GeneralLedgerReport(
            accountCode = accountCode,
            currencyCode = currencyCode,
            rows = rows,
            totalDebitMinor = totalDebit,
            totalCreditMinor = totalCredit,
            closingBalanceMinor = runningBalance,
            excludedOtherCurrencyLineCount = excludedLines.size,
            excludedCurrencyCodes = excludedLines.map { it.second.currencyCode }.distinct().sorted(),
        )
    }
}
