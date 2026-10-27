package com.neon.book01.domain

/** Conventional debit/credit nature labels used to explain how an account balance increases. */
object AccountNatureRules {
    private val debitIncreaseTypes = setOf("ASSET", "EXPENSE", "COST")
    private val creditIncreaseTypes = setOf("LIABILITY", "EQUITY", "INCOME")

    /** Returns the side that increases the account, or rejects an unknown account type. */
    fun increaseSideLabel(accountType: String): String = when (accountType) {
        in debitIncreaseTypes -> "Debe"
        in creditIncreaseTypes -> "Haber"
        else -> throw IllegalArgumentException("Tipo de cuenta desconocido: $accountType.")
    }

    fun decreaseSideLabel(accountType: String): String = when (increaseSideLabel(accountType)) {
        "Debe" -> "Haber"
        else -> "Debe"
    }
}
