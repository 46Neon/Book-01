package com.neon.book01.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountHierarchyTest {
    @Test
    fun parentFirstOrderingIgnoresSerializedOrderAndKeepsNullParents() {
        val accounts = listOf(
            AccountEntity("5300", "Other expense", "EXPENSE", parentCode = "5200"),
            AccountEntity("5200", "Services", "EXPENSE", parentCode = "5100"),
            AccountEntity("5100", "Supplies", "EXPENSE"),
            AccountEntity("1000", "Cash", "ASSET"),
        )

        assertEquals(listOf("1000", "5100", "5200", "5300"), parentFirstAccounts(accounts).map { it.code })
    }

    @Test
    fun missingInactiveAndDifferentTypeParentsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            parentFirstAccounts(listOf(AccountEntity("5200", "Child", "EXPENSE", parentCode = "9999")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            parentFirstAccounts(listOf(
                AccountEntity("5100", "Inactive parent", "EXPENSE", active = false),
                AccountEntity("5200", "Child", "EXPENSE", parentCode = "5100"),
            ))
        }
        assertThrows(IllegalArgumentException::class.java) {
            parentFirstAccounts(listOf(
                AccountEntity("1000", "Asset parent", "ASSET"),
                AccountEntity("5200", "Expense child", "EXPENSE", parentCode = "1000"),
            ))
        }
    }

    @Test
    fun cyclesAndSelfReferencesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            parentFirstAccounts(listOf(
                AccountEntity("5100", "First", "EXPENSE", parentCode = "5200"),
                AccountEntity("5200", "Second", "EXPENSE", parentCode = "5100"),
            ))
        }
        assertThrows(IllegalArgumentException::class.java) {
            parentFirstAccounts(listOf(AccountEntity("5100", "Self", "EXPENSE", parentCode = "5100")))
        }
    }
}
