package com.neon.book01.domain

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoiceBookPeriodTest {
    private val today = LocalDate.parse("2026-10-27")

    @Test fun currentAndPreviousMonthsRespectCalendarBoundaries() {
        assertTrue(matchesInvoiceBookPeriod("2026-10-01", InvoiceBookPeriod.THIS_MONTH, today))
        assertTrue(matchesInvoiceBookPeriod("2026-09-30", InvoiceBookPeriod.LAST_MONTH, today))
        assertFalse(matchesInvoiceBookPeriod("2026-09-30", InvoiceBookPeriod.THIS_MONTH, today))
        assertFalse(matchesInvoiceBookPeriod("2026-08-31", InvoiceBookPeriod.LAST_MONTH, today))
    }

    @Test fun yearAndAllTimeAreDerivedWithoutChangingInvoiceData() {
        assertTrue(matchesInvoiceBookPeriod("2026-01-01", InvoiceBookPeriod.THIS_YEAR, today))
        assertFalse(matchesInvoiceBookPeriod("2025-12-31", InvoiceBookPeriod.THIS_YEAR, today))
        assertTrue(matchesInvoiceBookPeriod("invalid", InvoiceBookPeriod.ALL, today))
        assertFalse(matchesInvoiceBookPeriod("invalid", InvoiceBookPeriod.THIS_MONTH, today))
    }
    @Test fun futureDatedInvoicesStayVisibleOnlyInAllAndAreNotIssuedAsOf() {
        assertTrue(matchesInvoiceBookPeriod("2026-10-28", InvoiceBookPeriod.ALL, today))
        assertFalse(matchesInvoiceBookPeriod("2026-10-28", InvoiceBookPeriod.THIS_MONTH, today))
        assertFalse(matchesInvoiceBookPeriod("2026-10-28", InvoiceBookPeriod.THIS_YEAR, today))
        assertFalse(isInvoiceIssuedAsOf("2026-10-28", today))
        assertTrue(isInvoiceIssuedAsOf("2026-10-27", today))
        assertFalse(isInvoiceIssuedAsOf("invalid", today))
    }

}
