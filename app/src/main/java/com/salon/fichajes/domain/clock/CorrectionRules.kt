package com.salon.fichajes.domain.clock

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Reglas para que la jefa modifique o añada fichajes. */
object CorrectionRules {

    /** Un tramo más largo que esto es casi seguro un error al escribir la hora. */
    val MAX_ENTRY: Duration = Duration.ofHours(16)

    /** Devuelve el mensaje de error, o null si la corrección es válida. */
    fun validate(clockIn: Instant, clockOut: Instant?, reason: String, now: Instant): String? = when {
        reason.isBlank() -> "Indica el motivo del cambio (lo verá la empleada)"
        clockIn.isAfter(now) -> "La entrada no puede estar en el futuro"
        clockOut != null && !clockOut.isAfter(clockIn) -> "La salida debe ser posterior a la entrada"
        clockOut != null && clockOut.isAfter(now) -> "La salida no puede estar en el futuro"
        clockOut != null && Duration.between(clockIn, clockOut) > MAX_ENTRY -> "Un tramo no puede durar más de 16 h"
        else -> null
    }

    /**
     * Pasa las horas locales que escribe la jefa a Instant. Si la salida es anterior a la entrada
     * (p. ej. 22:00 → 01:00) se entiende que es del día siguiente.
     */
    fun resolve(date: LocalDate, inTime: LocalTime, outTime: LocalTime?, zone: ZoneId): Pair<Instant, Instant?> {
        val clockIn = date.atTime(inTime).atZone(zone).toInstant()
        val clockOut = outTime?.let {
            val outDate = if (it.isAfter(inTime)) date else date.plusDays(1)
            outDate.atTime(it).atZone(zone).toInstant()
        }
        return clockIn to clockOut
    }
}
