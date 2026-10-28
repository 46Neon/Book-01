package com.neon.book01.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class InvoiceBookCsvTest {
    @Test fun exportsSavedPeriodInvoiceValuesWithExactMinorUnits() {
        assertEquals(
            "fecha_emision,referencia,contraparte,descripcion,base_minor,impuesto_minor,total_minor,fecha_vencimiento,moneda\r\n" +
                "\"2026-10-01\",\"F-1\",\"Cliente\",\"Consultoría\",\"10000\",\"1600\",\"11600\",\"2026-10-31\",\"USD\"",
            invoiceBookCsv("USD", listOf(InvoiceBookCsvRow("2026-10-01", "F-1", "Cliente", "Consultoría", 10_000, 1_600, 11_600, "2026-10-31"))),
        )
    }

    @Test fun quotesFieldsAndNeutralizesSpreadsheetFormulaPrefixes() {
        val csv = invoiceBookCsv("USD", listOf(InvoiceBookCsvRow("2026-10-01", "=1+1", " A\"B", "=SUM(A1)", 1, 0, 1, null)))
        assertEquals(
            "fecha_emision,referencia,contraparte,descripcion,base_minor,impuesto_minor,total_minor,fecha_vencimiento,moneda\r\n" +
                "\"2026-10-01\",\"'=1+1\",\" A\"\"B\",\"'=SUM(A1)\",\"1\",\"0\",\"1\",\"\",\"USD\"",
            csv,
        )
    }

    @Test fun emptyBookExportsHeaderOnly() {
        assertEquals(
            "fecha_emision,referencia,contraparte,descripcion,base_minor,impuesto_minor,total_minor,fecha_vencimiento,moneda",
            invoiceBookCsv("USD", emptyList()),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidCurrency() { invoiceBookCsv("USD!", emptyList()) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeSavedAmounts() {
        invoiceBookCsv("USD", listOf(InvoiceBookCsvRow("2026-10-01", "F-1", "C", "D", 1, -1, 0, null)))
    }
}
