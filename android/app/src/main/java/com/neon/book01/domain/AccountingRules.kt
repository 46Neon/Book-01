package com.neon.book01.domain

enum class EntryKind {
    INCOME,
    EXPENSE,
}

data class PostingLine(
    val accountCode: String,
    val debitMinor: Long,
    val creditMinor: Long,
    val currencyCode: String,
)

/** Deterministic rules for the first simple cash/bank income and expense workflow. */
object AccountingRules {
    fun simpleCashEntry(
        kind: EntryKind,
        description: String,
        categoryAccountCode: String,
        categoryAccountType: String,
        paymentAccountCode: String,
        paymentAccountType: String,
        amountMinor: Long,
        currencyCode: String,
    ): List<PostingLine> {
        require(description.isNotBlank()) { "Escribe una descripción." }
        require(amountMinor > 0) { "El importe debe ser mayor que cero." }
        require(currencyCode == UsdMoney.CURRENCY_CODE) {
            "Esta versión registra únicamente en dólares estadounidenses (USD)."
        }
        require(paymentAccountType == "ASSET") { "Selecciona una cuenta de caja o banco." }
        require(
            (kind == EntryKind.EXPENSE && categoryAccountType == "EXPENSE") ||
                (kind == EntryKind.INCOME && categoryAccountType == "INCOME"),
        ) { "La categoría no corresponde al tipo de movimiento." }

        val lines = when (kind) {
            EntryKind.EXPENSE -> listOf(
                PostingLine(categoryAccountCode, debitMinor = amountMinor, creditMinor = 0, currencyCode = currencyCode),
                PostingLine(paymentAccountCode, debitMinor = 0, creditMinor = amountMinor, currencyCode = currencyCode),
            )
            EntryKind.INCOME -> listOf(
                PostingLine(paymentAccountCode, debitMinor = amountMinor, creditMinor = 0, currencyCode = currencyCode),
                PostingLine(categoryAccountCode, debitMinor = 0, creditMinor = amountMinor, currencyCode = currencyCode),
            )
        }
        requireBalanced(lines)
        return lines
    }

    fun requireBalanced(lines: List<PostingLine>) {
        require(lines.isNotEmpty()) { "El asiento debe tener al menos una línea." }
        require(lines.all { it.debitMinor >= 0 && it.creditMinor >= 0 }) { "Los importes no pueden ser negativos." }
        require(lines.all { it.currencyCode == UsdMoney.CURRENCY_CODE }) {
            "Esta versión admite únicamente líneas en USD."
        }
        require(lines.all { (it.debitMinor == 0L) xor (it.creditMinor == 0L) }) {
            "Cada línea debe ser débito o crédito, no ambos."
        }
        require(lines.map { it.currencyCode }.distinct().size == 1) {
            "Las líneas del asiento deben usar la misma moneda en esta versión."
        }
        require(
            UsdMoney.sumMinorUnits(lines.map { it.debitMinor }) ==
                UsdMoney.sumMinorUnits(lines.map { it.creditMinor }),
        ) {
            "El asiento no está balanceado."
        }
    }
}
