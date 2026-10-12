package com.neon.book01.domain

import com.neon.book01.data.EntryWithLines
import com.neon.book01.data.JournalEntryEntity
import com.neon.book01.data.JournalLineEntity

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
    /** Balance immediately before the first included date; zero when no start date is set. */
    val openingBalanceMinor: Long,
    /** Positive means debit balance; negative means credit balance. */
    val closingBalanceMinor: Long,
    val excludedOtherCurrencyLineCount: Int,
    val excludedCurrencyCodes: List<String>,
)

object GeneralLedgerCalculator {
    private data class Candidate(val entry: JournalEntryEntity, val line: JournalLineEntity)

    private val chronologicalOrder = compareBy<Candidate>(
        { it.entry.entryDate },
        { it.entry.createdAt },
        { it.entry.id },
        { it.line.id },
    )

    fun calculate(
        accountCode: String,
        entries: List<EntryWithLines>,
        currencyCode: String = UsdMoney.CURRENCY_CODE,
        startDate: String? = null,
        endDate: String? = null,
    ): GeneralLedgerReport {
        require(accountCode.isNotBlank()) { "Selecciona una cuenta para consultar el Mayor." }
        require(currencyCode.isNotBlank()) { "La moneda del libro no puede estar vacía." }
        val dateRange = PostingDateRange.parse(startDate, endDate)

        val datedAccountLines = entries.asSequence()
            .filter { it.entry.status == "POSTED" }
            .flatMap { entryWithLines ->
                entryWithLines.lines.asSequence()
                    .filter { it.accountCode == accountCode }
                    .map { Candidate(entryWithLines.entry, it) }
            }
            .map { candidate -> candidate to dateRange.includes(candidate.entry.entryDate) }
            .toList()

        val selectedCurrencyLines = datedAccountLines
            .filter { (candidate, isInRange) -> candidate.line.currencyCode == currencyCode && isInRange }
            .map { it.first }
            .sortedWith(chronologicalOrder)
        val openingCandidates = dateRange.startInclusive?.toString()?.let { startIso ->
            datedAccountLines
                .filter { (candidate, _) ->
                    candidate.line.currencyCode == currencyCode && candidate.entry.entryDate < startIso
                }
                .map { it.first }
                .sortedWith(chronologicalOrder)
        }.orEmpty()
        val excludedLines = datedAccountLines.filter { (candidate, isInRange) ->
            candidate.line.currencyCode != currencyCode && isInRange
        }

        val openingBalance = balanceAfter(openingCandidates)
        var runningBalance = openingBalance
        val rows = selectedCurrencyLines.map { candidate ->
            runningBalance = applyLine(runningBalance, candidate.line)
            GeneralLedgerRow(
                entryId = candidate.entry.id,
                entryDate = candidate.entry.entryDate,
                createdAt = candidate.entry.createdAt,
                description = candidate.entry.description,
                debitMinor = candidate.line.debitMinor,
                creditMinor = candidate.line.creditMinor,
                runningBalanceMinor = runningBalance,
            )
        }
        val totalDebit = selectedCurrencyLines.fold(0L) { total, candidate ->
            Math.addExact(total, candidate.line.debitMinor)
        }
        val totalCredit = selectedCurrencyLines.fold(0L) { total, candidate ->
            Math.addExact(total, candidate.line.creditMinor)
        }

        return GeneralLedgerReport(
            accountCode = accountCode,
            currencyCode = currencyCode,
            rows = rows,
            totalDebitMinor = totalDebit,
            totalCreditMinor = totalCredit,
            openingBalanceMinor = openingBalance,
            closingBalanceMinor = runningBalance,
            excludedOtherCurrencyLineCount = excludedLines.size,
            excludedCurrencyCodes = excludedLines.map { it.first.line.currencyCode }.distinct().sorted(),
        )
    }

    private fun balanceAfter(lines: List<Candidate>): Long =
        lines.fold(0L) { balance, candidate -> applyLine(balance, candidate.line) }

    private fun applyLine(balance: Long, line: JournalLineEntity): Long {
        val updated = Math.subtractExact(Math.addExact(balance, line.debitMinor), line.creditMinor)
        if (updated == Long.MIN_VALUE) {
            throw ArithmeticException("El saldo supera el rango numérico seguro.")
        }
        return updated
    }
}
