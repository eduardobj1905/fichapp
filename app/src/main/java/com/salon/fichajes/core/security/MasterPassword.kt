package com.salon.fichajes.core.security

import java.security.SecureRandom

/** Reglas de la contraseña maestra de la dueña (más fuerte que un PIN de 4 cifras). */
object PasswordRules {
    const val MIN_LENGTH = 8

    /** Devuelve el error o null si es válida. */
    fun validate(password: String, repeat: String): String? = when {
        password.length < MIN_LENGTH -> "La contraseña debe tener al menos $MIN_LENGTH caracteres"
        password.none(Char::isLetter) || password.none(Char::isDigit) -> "Usa letras y números"
        password.isBlank() -> "La contraseña no puede estar en blanco"
        password != repeat -> "Las contraseñas no coinciden"
        else -> null
    }
}

/**
 * Código de recuperación de un solo uso (p. ej. "K7PM-2XQA-9HDT"). Se enseña UNA vez para apuntarlo
 * en papel y se guarda hasheado, igual que la contraseña. Sin letras confusas (0/O, 1/I/L).
 */
object RecoveryCode {
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private const val LENGTH = 12
    private val random = SecureRandom()

    fun generate(): String =
        (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }
            .chunked(4).joinToString("-") { it.joinToString("") }

    /** Ignora guiones, espacios y mayúsculas al comprobarlo. */
    fun normalize(code: String): String = code.uppercase().filter { it.isLetterOrDigit() }
}
