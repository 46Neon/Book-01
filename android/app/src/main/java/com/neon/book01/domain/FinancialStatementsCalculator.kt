package com.neon.book01.domain

/** Input line carries its entry status so drafts can never leak into statements. Amounts are USD cents. */
data class StatementPostingLine(
    val accountCode: String,
    val debitMinor: Long,
    val creditMinor: Long,
    val currencyCode: String,
    val entryDate: String,
    val entryStatus: String,
)

data class StatementAccountBalance(
    val accountCode: String,
    val accountName: String,
    val accountType: String,
    /** Positive is the account's normal balance; contra balances are negative. */
    val balanceMinor: Long,
)

data class IncomeStatementReport(
    val startDate: String?,
    val endDate: String?,
    val incomeLines: List<StatementAccountBalance>,
    val expenseLines: List<StatementAccountBalance>,
    val totalIncomeMinor: Long,
    val totalExpensesMinor: Long,
    val netIncomeMinor: Long,
    val excludedDraftLineCount: Int,
    val excludedOtherCurrencyLineCount: Int,
)

data class BalanceSheetReport(
    val asOfDate: String,
    val assetLines: List<StatementAccountBalance>,
    val liabilityLines: List<StatementAccountBalance>,
    val equityLines: List<StatementAccountBalance>,
    val totalAssetsMinor: Long,
    val totalLiabilitiesMinor: Long,
    val totalEquityMinor: Long,
    /** Net income accumulated from all posted activity through the as-of date; this book has no close workflow. */
    val accumulatedResultMinor: Long,
    /** Assets minus liabilities, equity and accumulated result; zero means the sheet reconciles. */
    val reconciliationDifferenceMinor: Long,
    val isReconciled: Boolean,
    val excludedDraftLineCount: Int,
    val excludedOtherCurrencyLineCount: Int,
)

/**
 * Financial statement calculators for this unclosed, USD-only book. Reversals are ordinary posted
 * counter-lines and therefore offset the original only according to their own posting date.
 * Account types are respected, never inferred or silently reclassified.
 */
object FinancialStatementsCalculator {
    private val allowedTypes = setOf("ASSET", "LIABILITY", "EQUITY", "INCOME", "EXPENSE")

    fun incomeStatement(
        accounts: List<ReportAccount>,
        postings: List<StatementPostingLine>,
        startDate: String? = null,
        endDate: String? = null,
    ): IncomeStatementReport {
        val dates = PostingDateRange.parse(startDate, endDate)
        val accountByCode = validateAccounts(accounts)
        val included = selectUsdPosted(postings).filter { dates.includes(it.entryDate) }
        val (income, expenses) = accountBalances(accountByCode, included)
        val incomeLines = income.filter { it.accountType == "INCOME" }
        val expenseLines = expenses.filter { it.accountType == "EXPENSE" }
        val totalIncome = UsdMoney.sumMinorUnits(incomeLines.map { it.balanceMinor })
        val totalExpenses = UsdMoney.sumMinorUnits(expenseLines.map { it.balanceMinor })
        return IncomeStatementReport(
            startDate = dates.startInclusive?.toString(),
            endDate = dates.endInclusive?.toString(),
            incomeLines = incomeLines,
            expenseLines = expenseLines,
            totalIncomeMinor = totalIncome,
            totalExpensesMinor = totalExpenses,
            netIncomeMinor = Math.subtractExact(totalIncome, totalExpenses),
            excludedDraftLineCount = postings.count { it.entryStatus != "POSTED" && safelyIncludes(dates, it.entryDate) },
            excludedOtherCurrencyLineCount = postings.count {
                it.entryStatus == "POSTED" && it.currencyCode != UsdMoney.CURRENCY_CODE && safelyIncludes(dates, it.entryDate)
            },
        )
    }

    fun balanceSheet(
        accounts: List<ReportAccount>,
        postings: List<StatementPostingLine>,
        asOfDate: String,
    ): BalanceSheetReport {
        val throughDate = PostingDateRange.parse(null, asOfDate).endInclusive!!
        val accountByCode = validateAccounts(accounts)
        val included = selectUsdPosted(postings).filter { !parsePostingDate(it.entryDate).isAfter(throughDate) }
        val balances = accountBalances(accountByCode, included)
        val assets = balances.first.filter { it.accountType == "ASSET" }
        val liabilities = balances.first.filter { it.accountType == "LIABILITY" }
        val equity = balances.first.filter { it.accountType == "EQUITY" }
        val totalAssets = UsdMoney.sumMinorUnits(assets.map { it.balanceMinor })
        val totalLiabilities = UsdMoney.sumMinorUnits(liabilities.map { it.balanceMinor })
        val totalEquity = UsdMoney.sumMinorUnits(equity.map { it.balanceMinor })
        val income = UsdMoney.sumMinorUnits(balances.second.filter { it.accountType == "INCOME" }.map { it.balanceMinor })
        val expenses = UsdMoney.sumMinorUnits(balances.second.filter { it.accountType == "EXPENSE" }.map { it.balanceMinor })
        val accumulatedResult = Math.subtractExact(income, expenses)
        val rightSide = Math.addExact(Math.addExact(totalLiabilities, totalEquity), accumulatedResult)
        val difference = Math.subtractExact(totalAssets, rightSide)
        return BalanceSheetReport(
            asOfDate = throughDate.toString(),
            assetLines = assets,
            liabilityLines = liabilities,
            equityLines = equity,
            totalAssetsMinor = totalAssets,
            totalLiabilitiesMinor = totalLiabilities,
            totalEquityMinor = totalEquity,
            accumulatedResultMinor = accumulatedResult,
            reconciliationDifferenceMinor = difference,
            isReconciled = difference == 0L,
            excludedDraftLineCount = postings.count { it.entryStatus != "POSTED" && safelyOnOrBefore(it.entryDate, throughDate) },
            excludedOtherCurrencyLineCount = postings.count {
                it.entryStatus == "POSTED" && it.currencyCode != UsdMoney.CURRENCY_CODE && safelyOnOrBefore(it.entryDate, throughDate)
            },
        )
    }

    private fun validateAccounts(accounts: List<ReportAccount>): Map<String, ReportAccount> {
        require(accounts.map { it.code }.distinct().size == accounts.size) { "Hay códigos de cuenta duplicados." }
        accounts.forEach { require(it.type in allowedTypes) { "Tipo contable desconocido: ${it.type}." } }
        return accounts.associateBy { it.code }
    }

    private fun selectUsdPosted(postings: List<StatementPostingLine>): List<StatementPostingLine> =
        postings.asSequence()
            .filter { it.entryStatus == "POSTED" }
            .filter { it.currencyCode == UsdMoney.CURRENCY_CODE }
            .toList()

    private fun accountBalances(
        accountByCode: Map<String, ReportAccount>,
        postings: List<StatementPostingLine>,
    ): Pair<List<StatementAccountBalance>, List<StatementAccountBalance>> {
        val debits = accountByCode.keys.associateWith { mutableListOf<Long>() }
        val credits = accountByCode.keys.associateWith { mutableListOf<Long>() }
        postings.forEach { line ->
            require(line.debitMinor >= 0L && line.creditMinor >= 0L) { "El estado financiero contiene un importe negativo inválido." }
            require((line.debitMinor == 0L) xor (line.creditMinor == 0L)) {
                "Cada línea debe contener débito o crédito, pero no ambos."
            }
            require(line.accountCode in accountByCode) { "El estado financiero contiene una cuenta inexistente." }
            if (line.debitMinor > 0) debits.getValue(line.accountCode).add(line.debitMinor)
            if (line.creditMinor > 0) credits.getValue(line.accountCode).add(line.creditMinor)
        }
        val debitNature = setOf("ASSET", "EXPENSE")
        val balances = accountByCode.values.sortedBy { it.code }.map { account ->
            val debit = UsdMoney.sumMinorUnits(debits.getValue(account.code))
            val credit = UsdMoney.sumMinorUnits(credits.getValue(account.code))
            val normal = if (account.type in debitNature) Math.subtractExact(debit, credit) else Math.subtractExact(credit, debit)
            StatementAccountBalance(account.code, account.name, account.type, normal)
        }
        return balances.filter { it.accountType in setOf("ASSET", "LIABILITY", "EQUITY") } to
            balances.filter { it.accountType in setOf("INCOME", "EXPENSE") }
    }

    private fun safelyIncludes(range: PostingDateRange, value: String): Boolean =
        runCatching { range.includes(value) }.getOrDefault(false)

    private fun safelyOnOrBefore(value: String, date: java.time.LocalDate): Boolean =
        runCatching { !parsePostingDate(value).isAfter(date) }.getOrDefault(false)

    private fun parsePostingDate(value: String): java.time.LocalDate =
        PostingDateRange.parse(value, value).startInclusive!!
}
