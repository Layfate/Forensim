package com.forensim.app.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordPolicyTest {
    @Test
    fun rejectsWeakPasswords() {
        for (p in listOf("", "password", "Password1", "password#123", "PASSWORD#123", "Pass#word", "Abcdefghij#")) {
            assertFalse("'$p' should be rejected", PasswordPolicy.isValid(p))
        }
    }

    @Test
    fun acceptsStrongPasswords() {
        for (p in listOf("Forensim#2026", "Ch@inOfCust0dy", "Zq9!rTuv#Lm")) {
            assertTrue("'$p' should be accepted", PasswordPolicy.isValid(p))
        }
    }

    @Test
    fun whitespaceIsNotASpecialCharacter() {
        assertFalse(PasswordPolicy.isValid("Forensim 2026"))
    }

    @Test
    fun unmetListsMissingRules() {
        val missing = PasswordPolicy.unmet("forensim").map { it.label }
        assertEquals(4, missing.size)
        assertTrue(missing.any { it.contains("special") })
    }

    @Test
    fun strengthGrows() {
        assertEquals(PasswordPolicy.Strength.WEAK, PasswordPolicy.strength("abc"))
        assertEquals(PasswordPolicy.Strength.GOOD, PasswordPolicy.strength("Forensim#26"))
        assertEquals(PasswordPolicy.Strength.STRONG, PasswordPolicy.strength("Forensim#2026Lab"))
    }
}
