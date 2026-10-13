package com.neon.book01.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalFiltersTest {
    private val records = listOf(
        JournalFilterRecord(
            id = "posted-income",
            dateIso = "2026-03-01",
            description = "Venta de café",
            counterparty = "Mercado Central",
            kind = "INCOME",
            status = JournalFilterStatus.POSTED,
            accounts = listOf(JournalFilterAccount("4000", "Ventas"), JournalFilterAccount("1010", "Banco")),
        ),
        JournalFilterRecord(
            id = "draft-expense",
            dateIso = "2026-03-31",
            description = "Pago de Papelería",
            kind = "EXPENSE",
            status = JournalFilterStatus.DRAFT,
            accounts = listOf(JournalFilterAccount("5100", "Papelería"), JournalFilterAccount("1000", "Caja")),
        ),
        JournalFilterRecord(
            id = "other-month",
            dateIso = "2026-04-01",
            description = "Factura servicios",
            kind = "EXPENSE",
            status = JournalFilterStatus.POSTED,
            accounts = listOf(JournalFilterAccount("5200", "Servicios"), JournalFilterAccount("1010", "Banco")),
        ),
    )

    @Test
    fun dateRangeIncludesBothEndpoints() {
        val result = filterJournalRecords(records, JournalFilterCriteria(fromDate = "2026-03-01", toDate = "2026-03-31"))
        assertEquals(listOf("posted-income", "draft-expense"), result.map { it.id })
    }

    @Test
    fun searchMatchesDescriptionCaseInsensitively() {
        assertEquals(
            listOf("posted-income"),
            filterJournalRecords(records, JournalFilterCriteria(searchText = "CAFÉ")).map { it.id },
        )
    }

    @Test
    fun searchMatchesCounterpartyCaseInsensitivelyAndItsLabelIsDisplayable() {
        assertEquals(listOf("posted-income"), filterJournalRecords(records, JournalFilterCriteria(searchText = "MERCADO central")).map { it.id })
        assertEquals("Mercado Central", journalCounterpartyLabel(records.first().counterparty))
        assertEquals("—", journalCounterpartyLabel(null))
    }

    @Test
    fun searchMatchesAccountNameAndCodeCaseInsensitively() {
        assertEquals(
            listOf("draft-expense"),
            filterJournalRecords(records, JournalFilterCriteria(searchText = "papelerÍA")).map { it.id },
        )
        assertEquals(
            listOf("other-month"),
            filterJournalRecords(records, JournalFilterCriteria(searchText = "5200")).map { it.id },
        )
    }

    @Test
    fun kindAndStatusCanBeCombined() {
        assertEquals(
            listOf("draft-expense"),
            filterJournalRecords(
                records,
                JournalFilterCriteria(kind = "EXPENSE", status = JournalFilterStatus.DRAFT),
            ).map { it.id },
        )
        assertEquals(
            listOf("posted-income"),
            filterJournalRecords(
                records,
                JournalFilterCriteria(kind = "INCOME", status = JournalFilterStatus.POSTED),
            ).map { it.id },
        )
    }

    @Test
    fun accountFilterMatchesAnEntryAccount() {
        assertEquals(
            listOf("posted-income"),
            filterJournalRecords(records, JournalFilterCriteria(accountCode = "4000")).map { it.id },
        )
    }

    @Test
    fun noMatchReturnsAnEmptyList() {
        assertTrue(filterJournalRecords(records, JournalFilterCriteria(searchText = "no existe")).isEmpty())
    }

    @Test
    fun malformedAndImpossibleDatesAreRejectedStrictly() {
        listOf("2026-02-30", "2026-2-03", " 2026-02-03").forEach { invalid ->
            assertFalse(validateJournalDateRange(invalid, "").isValid)
            assertTrue(filterJournalRecords(records, JournalFilterCriteria(fromDate = invalid)).isEmpty())
        }
    }

    @Test
    fun invertedDateRangeIsRejected() {
        val validation = validateJournalDateRange("2026-04-01", "2026-03-01")
        assertFalse(validation.isValid)
        assertTrue(validation.error!!.contains("posterior"))
        assertTrue(
            filterJournalRecords(records, JournalFilterCriteria(fromDate = "2026-04-01", toDate = "2026-03-01")).isEmpty(),
        )
    }
}
