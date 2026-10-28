package com.neon.book01.domain

/**
 * Produces a pasteable RFC-4180-style CSV snapshot of a read-only statement slice.
 * Monetary amounts stay exact integer minor units, with an explicit currency column;
 * no accounting entry or persisted record is created.
 */
fun counterpartyStatementCsv(
    counterpartyLabel: String,
    currencyCode: String,
    period: CounterpartyStatementPeriod,
    settlementLabel: String = "Cobro/pago",
): String {
    require(counterpartyLabel.isNotBlank()) { "La contraparte debe estar identificada." }
    require(settlementLabel.isNotBlank()) { "El tipo de abono debe estar identificado." }
    require(currencyCode.isNotBlank() && currencyCode.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
        "El código de moneda no es válido."
    }
    val lines = mutableListOf(
        listOf("contraparte", "fecha", "tipo", "referencia", "cambio_minor", "saldo_minor", "moneda").joinToString(","),
        listOf(counterpartyLabel, "", "Saldo inicial", "", "0", period.openingBalanceMinor.toString(), currencyCode)
            .joinToString(",") { csvText(it) },
    )
    period.movements.forEach { movement ->
        lines += listOf(
            csvText(counterpartyLabel),
            csvText(movement.date),
            csvText(if (movement.kind == StatementMovementKind.INVOICE) "Factura" else settlementLabel),
            csvText(movement.reference),
            movement.changeMinor.toString(),
            movement.balanceAfterMinor.toString(),
            csvText(currencyCode),
        ).joinToString(",")
    }
    lines += listOf(counterpartyLabel, "", "Saldo final", "", "0", period.closingBalanceMinor.toString(), currencyCode)
        .joinToString(",") { csvText(it) }
    return lines.joinToString("\r\n")
}

/** Quotes CSV text and neutralizes spreadsheet formula prefixes in human-entered fields. */
private fun csvText(value: String): String {
    val firstMeaningful = value.dropWhile { it.isWhitespace() }.firstOrNull()
    val safe = if (firstMeaningful != null && firstMeaningful in setOf('=', '+', '-', '@')) "'$value" else value
    return "\"${safe.replace("\"", "\"\"")}\""
}
