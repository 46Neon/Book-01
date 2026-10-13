package com.neon.book01.domain

/** Optional text identifier for documentary support; files themselves are not attached in this MVP. */
object SupportReferenceRules {
    const val MAX_LENGTH = 120

    /** Blank is represented as null; references are trimmed, bounded, and free of control characters. */
    fun normalize(value: String?): String? {
        val normalized = value?.trim().orEmpty()
        if (normalized.isEmpty()) return null
        require(normalized.length <= MAX_LENGTH) { "La referencia de soporte no puede superar $MAX_LENGTH caracteres." }
        require(normalized.none { it.isISOControl() }) { "La referencia de soporte no puede contener caracteres de control." }
        return normalized
    }
}
