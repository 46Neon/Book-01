package com.neon.book01.domain

data class OperatingSummary(
    val incomeMinor: Long,
    val expenseMinor: Long,
)

/** Summarizes income and expense activity from posted ledger lines, so reversals cancel correctly. */
object OperatingSummaryCalculator {
    fun calculate(
        accounts: List<ReportAccount>,
        postings: List<ReportPostingLine>,
        currencyCode: String = UsdMoney.CURRENCY_CODE,
    ): OperatingSummary {
        require(currencyCode == UsdMoney.CURRENCY_CODE) { "Esta versión calcula resultados únicamente en USD." }
        require(accounts.map { it.code }.distinct().size == accounts.size) { "Hay códigos de cuenta duplicados." }
        val accountByCode = accounts.associateBy { it.code }
        var income = 0L
        var expense = 0L

        postings.filter { it.currencyCode == currencyCode }.forEach { posting ->
            require(posting.debitMinor >= 0 && posting.creditMinor >= 0) { "El resumen contiene un importe negativo inválido." }
            require((posting.debitMinor == 0L) xor (posting.creditMinor == 0L)) {
                "Cada línea debe contener débito o crédito, pero no ambos."
            }
            val account = accountByCode[posting.accountCode]
                ?: throw IllegalArgumentException("El resumen contiene una cuenta inexistente.")
            when (account.type) {
                "INCOME" -> income = Math.addExact(income, Math.subtractExact(posting.creditMinor, posting.debitMinor))
                "EXPENSE" -> expense = Math.addExact(expense, Math.subtractExact(posting.debitMinor, posting.creditMinor))
                else -> Unit
            }
        }
        return OperatingSummary(incomeMinor = income, expenseMinor = expense)
    }
}
