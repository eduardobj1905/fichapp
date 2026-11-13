package com.salon.fichajes.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterPasswordTest {

    @Test
    fun `contrasena valida con letras, numeros y 8 caracteres`() {
        assertNull(PasswordRules.validate("salon2026", "salon2026"))
    }

    @Test
    fun `rechaza contrasenas cortas, sin numeros, sin letras o que no coinciden`() {
        assertNotNull(PasswordRules.validate("abc123", "abc123"))
        assertNotNull(PasswordRules.validate("peluqueria", "peluqueria"))
        assertNotNull(PasswordRules.validate("12345678", "12345678"))
        assertNotNull(PasswordRules.validate("salon2026", "salon2027"))
    }

    @Test
    fun `codigo de recuperacion tiene formato XXXX-XXXX-XXXX sin caracteres confusos`() {
        val code = RecoveryCode.generate()
        assertTrue(code, Regex("^[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$").matches(code))
        assertFalse(code.any { it in "01OIL" })
        assertNotEquals(code, RecoveryCode.generate())
    }

    @Test
    fun `el codigo se compara sin guiones, espacios ni mayusculas`() {
        assertEquals("K7PM2XQA9HDT", RecoveryCode.normalize("k7pm-2xqa 9hdt"))
    }

    @Test
    fun `hash de la contrasena se verifica y usa salt distinta cada vez`() {
        val salt1 = PinHasher.newSalt()
        val salt2 = PinHasher.newSalt()
        val hash1 = PinHasher.hash("salon2026", salt1)
        assertTrue(PinHasher.verify("salon2026", salt1, hash1))
        assertFalse(PinHasher.verify("salon2027", salt1, hash1))
        assertNotEquals(hash1, PinHasher.hash("salon2026", salt2))
    }
}
