package com.neon.book01.domain

/** Canonical optional counterparty text for simple local income/expense entries. */
object CounterpartyRules {
    const val MAX_LENGTH = 80

    /** Blank is represented as null; nonblank names are trimmed and must be short, printable text. */
    fun normalize(value: String?): String? {
        val normalized = value?.trim().orEmpty()
        if (normalized.isEmpty()) return null
        require(normalized.length <= MAX_LENGTH) { "La contraparte no puede superar $MAX_LENGTH caracteres." }
        require(normalized.none { it.isISOControl() }) { "La contraparte no puede contener caracteres de control." }
        return normalized
    }
}
