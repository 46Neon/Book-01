package com.neon.book01.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SupportReferenceRulesTest {
    @Test fun trimsTextAndTreatsBlankAsAbsent() {
        assertEquals("REC-2026-001", SupportReferenceRules.normalize("  REC-2026-001  "))
        assertNull(SupportReferenceRules.normalize("  \t "))
        assertNull(SupportReferenceRules.normalize(null))
    }

    @Test fun rejectsTooLongOrControlCharacters() {
        assertThrows(IllegalArgumentException::class.java) {
            SupportReferenceRules.normalize("x".repeat(SupportReferenceRules.MAX_LENGTH + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SupportReferenceRules.normalize("REC-2026\n001")
        }
    }
}
