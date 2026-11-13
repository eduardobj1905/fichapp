package com.salon.fichajes.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hash de PIN con PBKDF2WithHmacSHA256 + salt aleatoria por empleada.
 *
 * Un PIN de 4 dígitos solo tiene 10.000 combinaciones: el hash evita que se lea en claro si alguien
 * copia la base de datos, y el bloqueo por intentos fallidos (LoginViewModel) frena la fuerza bruta
 * desde la propia tablet. Es costoso a propósito: llámalo siempre fuera del hilo principal.
 */
object PinHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 60_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    private val random = SecureRandom()

    fun newSalt(): String {
        val bytes = ByteArray(SALT_BYTES).also(random::nextBytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), Base64.getDecoder().decode(salt), ITERATIONS, KEY_LENGTH_BITS)
        try {
            val key = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
            return Base64.getEncoder().encodeToString(key)
        } finally {
            spec.clearPassword()
        }
    }

    /** Comparación en tiempo constante para no filtrar información por tiempos de respuesta. */
    fun verify(pin: String, salt: String, expectedHash: String): Boolean =
        MessageDigest.isEqual(
            hash(pin, salt).toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8),
        )

    fun isValidPin(pin: String): Boolean = pin.length == 4 && pin.all(Char::isDigit)
}
