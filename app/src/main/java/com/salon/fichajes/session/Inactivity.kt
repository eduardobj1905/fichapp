package com.salon.fichajes.session

import com.salon.fichajes.domain.model.Role
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Tiempos de cierre de sesión por inactividad en la tablet compartida. */
object InactivityPolicy {
    /** La jefa suele revisar con calma (horas, vacaciones…), pero no debe quedarse el panel abierto. */
    val ADMIN_TIMEOUT: Duration = 3.minutes

    /** Una empleada solo ficha o mira sus vacaciones. */
    val EMPLOYEE_TIMEOUT: Duration = 1.minutes

    /** Durante los últimos segundos se muestra "¿Sigues ahí?". */
    val WARNING: Duration = 15.seconds

    /** Tras escribir la contraseña maestra, la dueña no la vuelve a necesitar durante este tiempo. */
    val ELEVATION: Duration = 5.minutes

    fun timeoutFor(role: Role): Duration = if (role.canManage) ADMIN_TIMEOUT else EMPLOYEE_TIMEOUT

    /** Segundos que quedan hasta cerrar sesión (0 o negativo = cerrar ya). Reloj monótono en milisegundos. */
    fun secondsLeft(lastInteractionMs: Long, nowMs: Long, timeout: Duration): Long {
        val elapsedMs = (nowMs - lastInteractionMs).coerceAtLeast(0)
        val leftMs = timeout.inWholeMilliseconds - elapsedMs
        return if (leftMs <= 0) 0 else (leftMs + 999) / 1000 // redondeo hacia arriba
    }
}
