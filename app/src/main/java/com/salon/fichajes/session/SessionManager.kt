package com.salon.fichajes.session

import android.os.SystemClock
import com.salon.fichajes.domain.model.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class Session(val employeeId: Long, val role: Role)

/**
 * Sesión en memoria para el modo quiosco: la tablet es compartida, así que no se persiste nada.
 * Si la app se cierra o se reinicia, vuelve a la pantalla de PIN.
 *
 * Usa SystemClock.elapsedRealtime (reloj monótono), que no cambia aunque alguien toque la hora del
 * sistema, para:
 * - el último toque (cierre por inactividad);
 * - la "elevación": tras escribir la contraseña maestra, la dueña no la vuelve a necesitar durante
 *   unos minutos (como sudo).
 */
@Singleton
class SessionManager @Inject constructor() {
    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    @Volatile
    var lastInteractionMs: Long = SystemClock.elapsedRealtime()
        private set

    @Volatile
    private var elevatedUntilMs: Long = 0

    /** Código de recuperación recién generado, para enseñarlo UNA vez encima de cualquier pantalla. */
    private val _recoveryCodeToShow = MutableStateFlow<String?>(null)
    val recoveryCodeToShow: StateFlow<String?> = _recoveryCodeToShow.asStateFlow()

    fun login(employeeId: Long, role: Role) {
        registerInteraction()
        elevatedUntilMs = 0
        _session.value = Session(employeeId, role)
    }

    fun logout() {
        elevatedUntilMs = 0
        _session.value = null
    }

    /** Lo llama MainActivity.onUserInteraction() en cada toque o tecla. */
    fun registerInteraction() {
        lastInteractionMs = SystemClock.elapsedRealtime()
    }

    fun nowMs(): Long = SystemClock.elapsedRealtime()

    fun requireSession(): Session = checkNotNull(_session.value) { "No hay sesión iniciada" }

    fun elevate() {
        elevatedUntilMs = SystemClock.elapsedRealtime() + InactivityPolicy.ELEVATION.inWholeMilliseconds
    }

    fun isElevated(): Boolean =
        _session.value?.role == Role.OWNER && SystemClock.elapsedRealtime() < elevatedUntilMs

    fun showRecoveryCode(code: String) {
        _recoveryCodeToShow.value = code
    }

    fun dismissRecoveryCode() {
        _recoveryCodeToShow.value = null
    }
}
