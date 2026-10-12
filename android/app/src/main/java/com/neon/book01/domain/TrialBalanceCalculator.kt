package com.neon.book01.domain

data class ReportAccount(
    val code: String,
    val name: String,
    val type: String,
)

data class ReportPostingLine(
    val accountCode: String,
    val debitMinor: Long,
    val creditMinor: Long,
    val currencyCode: String,
    val entryDate: String,
)

data class TrialBalanceLine(
    val accountCode: String,
    val accountName: String,
    val accountType: String,
    val totalDebitMinor: Long,
    val totalCreditMinor: Long,
    /** Positive means normal balance; negative means balance on the opposite side. */
    val signedBalanceMinor: Long,
)

data class TrialBalanceReport(
    val lines: List<TrialBalanceLine>,
    val totalDebitMinor: Long,
    val totalCreditMinor: Long,
    val isBalanced: Boolean,
)

/** Deterministic trial-balance calculations; amounts remain integer USD cents. */
object TrialBalanceCalculator {
    private val debitNatureTypes = setOf("ASSET", "EXPENSE")
    private val creditNatureTypes = setOf("LIABILITY", "EQUITY", "INCOME")

    fun calculate(
        accounts: List<ReportAccount>,
        postings: List<ReportPostingLine>,
        currencyCode: String = UsdMoney.CURRENCY_CODE,
        startDate: String? = null,
        endDate: String? = null,
    ): TrialBalanceReport {
        require(currencyCode == UsdMoney.CURRENCY_CODE) { "Esta versión genera balances únicamente en USD." }
        val dateRange = PostingDateRange.parse(startDate, endDate)
        require(accounts.map { it.code }.distinct().size == accounts.size) { "Hay códigos de cuenta duplicados." }
        val accountByCode = accounts.associateBy { it.code }
        val debitAmounts = accounts.associate { it.code to mutableListOf<Long>() }
        val creditAmounts = accounts.associate { it.code to mutableListOf<Long>() }

        postings.filter { dateRange.includes(it.entryDate) }.forEach { posting ->
            require(posting.currencyCode == currencyCode) {
                "Hay movimientos en otra moneda; no se mezclarán con el balance USD."
            }
            require(posting.debitMinor >= 0 && posting.creditMinor >= 0) { "El balance contiene un importe negativo inválido." }
            require((posting.debitMinor == 0L) xor (posting.creditMinor == 0L)) {
                "Cada línea debe contener débito o crédito, pero no ambos."
            }
            require(accountByCode.containsKey(posting.accountCode)) { "El balance contiene una cuenta inexistente." }
            if (posting.debitMinor > 0) debitAmounts.getValue(posting.accountCode).add(posting.debitMinor)
            if (posting.creditMinor > 0) creditAmounts.getValue(posting.accountCode).add(posting.creditMinor)
        }

        val lines = accounts.map { account ->
            val debit = UsdMoney.sumMinorUnits(debitAmounts.getValue(account.code))
            val credit = UsdMoney.sumMinorUnits(creditAmounts.getValue(account.code))
            val signedBalance = when (account.type) {
                in debitNatureTypes -> Math.subtractExact(debit, credit)
                in creditNatureTypes -> Math.subtractExact(credit, debit)
                else -> throw IllegalArgumentException("Tipo contable desconocido: ${account.type}.")
            }
            TrialBalanceLine(
                accountCode = account.code,
                accountName = account.name,
                accountType = account.type,
                totalDebitMinor = debit,
                totalCreditMinor = credit,
                signedBalanceMinor = signedBalance,
            )
        }
        val totalDebits = UsdMoney.sumMinorUnits(lines.map { it.totalDebitMinor })
        val totalCredits = UsdMoney.sumMinorUnits(lines.map { it.totalCreditMinor })
        return TrialBalanceReport(lines, totalDebits, totalCredits, totalDebits == totalCredits)
    }
}
