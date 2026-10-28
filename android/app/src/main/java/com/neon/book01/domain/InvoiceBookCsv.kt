package com.neon.book01.domain

/** A saved invoice row for a read-only auxiliary-book CSV snapshot. Amounts are minor units. */
data class InvoiceBookCsvRow(
    val invoiceDate: String,
    val invoiceNumber: String,
    val counterparty: String,
    val description: String,
    val taxableBaseMinor: Long,
    val vatMinor: Long,
    val totalMinor: Long,
    val dueDate: String?,
)

/**
 * Creates a shareable CSV snapshot of the selected auxiliary-book issue period.
 * It exports saved invoice values only; it does not calculate tax, subtract settlements,
 * create a file, or imply a statutory filing format.
 */
fun invoiceBookCsv(currencyCode: String, rows: Iterable<InvoiceBookCsvRow>): String {
    require(currencyCode.isNotBlank() && currencyCode.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
        "El código de moneda no es válido."
    }
    val lines = mutableListOf(
        listOf("fecha_emision", "referencia", "contraparte", "descripcion", "base_minor", "impuesto_minor", "total_minor", "fecha_vencimiento", "moneda")
            .joinToString(","),
    )
    for (row in rows) {
        require(row.taxableBaseMinor >= 0 && row.vatMinor >= 0 && row.totalMinor >= 0) {
            "Los importes de factura no pueden ser negativos."
        }
        lines += listOf(
            row.invoiceDate, row.invoiceNumber, row.counterparty, row.description,
            row.taxableBaseMinor.toString(), row.vatMinor.toString(), row.totalMinor.toString(),
            row.dueDate.orEmpty(), currencyCode,
        ).joinToString(",") { csvText(it) }
    }
    return lines.joinToString("\r\n")
}

private fun csvText(value: String): String {
    val firstMeaningful = value.dropWhile { it.isWhitespace() }.firstOrNull()
    val safe = if (firstMeaningful != null && firstMeaningful in setOf('=', '+', '-', '@')) "'$value" else value
    return "\"${safe.replace("\"", "\"\"")}\""
}
