package com.neon.book01.domain

data class NewAccount(
    val code: String,
    val name: String,
    val type: String,
    val cashOrBank: Boolean,
)

object AccountRules {
    val supportedTypes = setOf("ASSET", "LIABILITY", "EQUITY", "INCOME", "EXPENSE")

    fun validate(code: String, name: String, type: String, cashOrBank: Boolean): NewAccount {
        val normalizedCode = code.trim()
        val normalizedName = name.trim()
        require(normalizedCode.matches(Regex("[0-9]{3,8}"))) { "El código debe tener entre 3 y 8 dígitos." }
        require(normalizedName.length in 2..80) { "El nombre debe tener entre 2 y 80 caracteres." }
        require(type in supportedTypes) { "Selecciona un tipo de cuenta válido." }
        require(!cashOrBank || type == "ASSET") { "Solo una cuenta de activo puede ser Caja/Banco." }
        return NewAccount(normalizedCode, normalizedName, type, cashOrBank)
    }
}
