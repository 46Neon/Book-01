package com.neon.book01.domain

/** Account information needed to validate a close plan without persistence or Android dependencies. */
data class PeriodCloseAccount(val code: String, val type: String, val active: Boolean = true)

data class PeriodClosePostingLine(
    val accountCode: String,
    val debitMinor: Long,
    val creditMinor: Long,
    val currencyCode: String,
)

data class PeriodClosePosting(
    val entryDate: String,
    val status: String,
    val currencyCode: String,
    val kind: String,
    val lines: List<PeriodClosePostingLine>,
)

data class PeriodCloseLinePlan(
    val accountCode: String,
    val debitMinor: Long,
    val creditMinor: Long,
    val currencyCode: String = UsdMoney.CURRENCY_CODE,
)

data class PeriodClosePlan(
    val startDate: String,
    val endDate: String,
    val retainedEarningsAccountCode: String,
    /** Net income is positive; a loss is negative. */
    val netResultMinor: Long,
    /** Empty only when there was no net P&L account activity to close. */
    val lines: List<PeriodCloseLinePlan>,
)

/** Pure, exact-cent close planning. Reversal lines are processed using their own entry posting date. */
object PeriodCloseCalculator {
    fun calculate(
        accounts: List<PeriodCloseAccount>,
        postings: List<PeriodClosePosting>,
        startDate: String,
        endDate: String,
        retainedEarningsAccountCode: String,
    ): PeriodClosePlan {
        val range = PostingDateRange.parse(startDate, endDate)
        require(range.startInclusive != null && range.endInclusive != null) {
            "Indica ambas fechas del periodo que se va a cerrar."
        }
        val start = range.startInclusive.toString()
        val end = range.endInclusive.toString()
        val accountByCode = accounts.associateBy { it.code }
        require(accountByCode.size == accounts.size) { "Hay códigos de cuenta duplicados." }
        val retained = accountByCode[retainedEarningsAccountCode]
            ?: throw IllegalArgumentException("No se encontró la cuenta de ganancias retenidas.")
        require(retained.active && retained.type == "EQUITY") {
            "Selecciona una cuenta activa de patrimonio para ganancias retenidas."
        }

        val inRange = postings.filter { range.includes(it.entryDate) }
        require(inRange.all { it.status == "POSTED" }) {
            "No se puede cerrar un periodo que contiene asientos sin contabilizar."
        }
        require(inRange.all { it.currencyCode == UsdMoney.CURRENCY_CODE }) {
            "No se puede cerrar un periodo con asientos que no estén en USD."
        }
        require(inRange.none { it.kind == "PERIOD_CLOSE" }) {
            "El intervalo contiene un asiento de cierre previo."
        }
        inRange.forEach { entry ->
            require(entry.lines.isNotEmpty()) { "El periodo contiene un asiento sin líneas contables." }
            var entryDebits = 0L
            var entryCredits = 0L
            entry.lines.forEach { line ->
                require(line.currencyCode == UsdMoney.CURRENCY_CODE) {
                    "No se puede cerrar un periodo con líneas que no estén en USD."
                }
                require(line.debitMinor >= 0L && line.creditMinor >= 0L && ((line.debitMinor == 0L) xor (line.creditMinor == 0L))) {
                    "Cada línea del periodo debe contener un débito o crédito positivo."
                }
                require(line.accountCode in accountByCode) { "El periodo contiene una cuenta inexistente." }
                entryDebits = Math.addExact(entryDebits, line.debitMinor)
                entryCredits = Math.addExact(entryCredits, line.creditMinor)
            }
            require(entryDebits == entryCredits) {
                "El periodo contiene un asiento no balanceado que no se puede cerrar."
            }
        }

        val debits = mutableMapOf<String, MutableList<Long>>()
        val credits = mutableMapOf<String, MutableList<Long>>()
        inRange.flatMap { it.lines }.forEach { line ->
            require(line.debitMinor >= 0 && line.creditMinor >= 0 && ((line.debitMinor == 0L) xor (line.creditMinor == 0L))) {
                "Cada línea del periodo debe contener un débito o crédito positivo."
            }
            val account = accountByCode[line.accountCode]
                ?: throw IllegalArgumentException("El periodo contiene una cuenta inexistente.")
            if (account.type == "INCOME" || account.type == "EXPENSE") {
                if (line.debitMinor > 0) debits.getOrPut(line.accountCode) { mutableListOf() }.add(line.debitMinor)
                if (line.creditMinor > 0) credits.getOrPut(line.accountCode) { mutableListOf() }.add(line.creditMinor)
            }
        }
        val pnlCodes = (debits.keys + credits.keys).toSortedSet()
        val closingLines = mutableListOf<PeriodCloseLinePlan>()
        var incomeTotal = 0L
        var expenseTotal = 0L
        pnlCodes.forEach { code ->
            val account = accountByCode.getValue(code)
            val debit = UsdMoney.sumMinorUnits(debits[code].orEmpty())
            val credit = UsdMoney.sumMinorUnits(credits[code].orEmpty())
            val normalBalance = if (account.type == "INCOME") Math.subtractExact(credit, debit)
                else Math.subtractExact(debit, credit)
            if (normalBalance != 0L) {
                val amount = absExact(normalBalance)
                val debitClose = if (account.type == "INCOME") normalBalance > 0 else normalBalance < 0
                closingLines += PeriodCloseLinePlan(code, if (debitClose) amount else 0L, if (debitClose) 0L else amount)
            }
            if (account.type == "INCOME") incomeTotal = Math.addExact(incomeTotal, normalBalance)
            else expenseTotal = Math.addExact(expenseTotal, normalBalance)
        }
        val net = Math.subtractExact(incomeTotal, expenseTotal)
        if (net != 0L) {
            val amount = absExact(net)
            // Profit credits retained equity; loss debits it.
            closingLines += PeriodCloseLinePlan(
                retained.code,
                debitMinor = if (net < 0) amount else 0L,
                creditMinor = if (net > 0) amount else 0L,
            )
        }
        val totalDebits = UsdMoney.sumMinorUnits(closingLines.map { it.debitMinor })
        val totalCredits = UsdMoney.sumMinorUnits(closingLines.map { it.creditMinor })
        require(totalDebits == totalCredits) { "El plan de cierre no está balanceado." }
        return PeriodClosePlan(start, end, retained.code, net, closingLines)
    }

    private fun absExact(value: Long): Long {
        require(value != Long.MIN_VALUE) { "El importe excede el rango exacto de centavos USD." }
        return kotlin.math.abs(value)
    }
}
