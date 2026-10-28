package com.neon.book01.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CounterpartyStatementCsvTest {
    @Test fun exportsExactIntegerBalancesAndInclusivePeriodRows() {
        val period = CounterpartyStatementPeriod(
            openingBalanceMinor = 12_345,
            movements = listOf(
                CounterpartyStatementMovement("2026-10-02", StatementMovementKind.INVOICE, "INV-7", 500, 12_845),
                CounterpartyStatementMovement("2026-10-03", StatementMovementKind.SETTLEMENT, "PAY-1", -200, 12_645),
            ),
            closingBalanceMinor = 12_645,
        )
        assertEquals(
            listOf(
                "contraparte,fecha,tipo,referencia,cambio_minor,saldo_minor,moneda",
                "\"Cliente\",\"\",\"Saldo inicial\",\"\",\"0\",\"12345\",\"USD\"",
                "\"Cliente\",\"2026-10-02\",\"Factura\",\"INV-7\",500,12845,\"USD\"",
                "\"Cliente\",\"2026-10-03\",\"Cobro/pago\",\"PAY-1\",-200,12645,\"USD\"",
                "\"Cliente\",\"\",\"Saldo final\",\"\",\"0\",\"12645\",\"USD\"",
            ).joinToString("\r\n"),
            counterpartyStatementCsv("Cliente", "USD", period),
        )
    }

    @Test fun quotesCommasAndQuotesAndNeutralizesFormulaLikeReferences() {
        val period = CounterpartyStatementPeriod(
            0,
            listOf(CounterpartyStatementMovement("2026-10-02", StatementMovementKind.INVOICE, "=1+1, \"x\"", 1, 1)),
            1,
        )
        val csv = counterpartyStatementCsv("=Cliente", "USD", period)
        assertEquals(true, csv.contains("\"'=Cliente\""))
        assertEquals(true, csv.contains("\"'=1+1, \"\"x\"\"\""))
    }

    @Test fun usesBookSpecificSettlementLabelWhenRequested() {
        val period = CounterpartyStatementPeriod(
            0,
            listOf(CounterpartyStatementMovement("2026-10-02", StatementMovementKind.SETTLEMENT, "PAY-1", -200, 0)),
            0,
        )
        val csv = counterpartyStatementCsv("Proveedor", "USD", period, settlementLabel = "Pago")
        assertEquals(true, csv.contains("\"Pago\""))
        assertEquals(false, csv.contains("Cobro/pago"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsafeCurrencyCode() {
        counterpartyStatementCsv("Cliente", "USD,EUR", CounterpartyStatementPeriod(0, emptyList(), 0))
    }
}
