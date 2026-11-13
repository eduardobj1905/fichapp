package com.salon.fichajes.ui.admin.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salon.fichajes.core.security.PasswordRules
import com.salon.fichajes.core.security.PinHasher
import com.salon.fichajes.domain.model.Employee
import com.salon.fichajes.domain.model.Role
import com.salon.fichajes.domain.repository.EmployeeRepository
import com.salon.fichajes.domain.repository.OwnerSecurityRepository
import com.salon.fichajes.session.SessionManager
import com.salon.fichajes.ui.theme.StaffPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Diálogo abierto en la pantalla Equipo. */
sealed interface TeamDialog {
    data object Add : TeamDialog
    data class ChangeRole(val employee: Employee) : TeamDialog
    data class ResetPin(val employee: Employee) : TeamDialog
    data class Deactivate(val employee: Employee) : TeamDialog
    data class Transfer(val employee: Employee) : TeamDialog
    data object ChangePassword : TeamDialog
}

data class TeamDialogState(
    val dialog: TeamDialog? = null,
    val error: String? = null,
    val busy: Boolean = false,
    /** Ceder el control: la contraseña actual ya se ha comprobado (paso 2). */
    val transferVerified: Boolean = false,
)

/** Todo lo de esta pantalla es solo de la dueña (además, el repositorio lo vuelve a comprobar). */
@HiltViewModel
class TeamViewModel @Inject constructor(
    private val employeeRepository: EmployeeRepository,
    private val securityRepository: OwnerSecurityRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val employees: StateFlow<List<Employee>> = employeeRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _dialog = MutableStateFlow(TeamDialogState())
    val dialog: StateFlow<TeamDialogState> = _dialog.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    val currentUserId: Long get() = sessionManager.requireSession().employeeId

    fun open(dialog: TeamDialog) {
        if (dialog is TeamDialog.Transfer && sessionManager.nowMs() < transferLockedUntilMs) {
            _messages.trySend("Demasiados intentos con la contraseña. Espera un minuto.")
            return
        }
        _dialog.value = TeamDialogState(dialog)
    }

    private var transferFailures = 0
    private var transferLockedUntilMs = 0L

    /**
     * Paso 1 de ceder el control: se comprueba la contraseña ANTES de pasar la tablet a la nueva dueña.
     * Siempre se pide, aunque la contraseña maestra se haya escrito hace poco, porque es lo más delicado.
     * Con 5 fallos seguidos se bloquea un minuto (igual que el diálogo de contraseña maestra).
     */
    fun verifyTransferPassword(currentPassword: String) {
        _dialog.value = _dialog.value.copy(busy = true, error = null)
        viewModelScope.launch {
            if (securityRepository.verifyMasterPassword(currentPassword)) {
                transferFailures = 0
                _dialog.value = _dialog.value.copy(busy = false, error = null, transferVerified = true)
            } else if (++transferFailures >= MAX_TRANSFER_FAILURES) {
                transferFailures = 0
                transferLockedUntilMs = sessionManager.nowMs() + TRANSFER_LOCK_MS
                close()
                _messages.send("Demasiados intentos con la contraseña. Espera un minuto.")
            } else {
                fail("La contraseña actual no es correcta (${MAX_TRANSFER_FAILURES - transferFailures} intentos)")
            }
        }
    }
    fun close() { _dialog.value = TeamDialogState() }

    private fun fail(message: String) { _dialog.value = _dialog.value.copy(error = message, busy = false) }

    /** Ejecuta la operación; si va bien cierra el diálogo y enseña [success]. */
    private fun perform(success: String, block: suspend () -> Unit) {
        _dialog.value = _dialog.value.copy(busy = true, error = null)
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess {
                    close()
                    _messages.send(success)
                }
                .onFailure { fail(it.message ?: "No se ha podido guardar") }
        }
    }

    fun add(name: String, pin: String, weeklyHours: String, vacationDays: String, role: Role) {
        val hours = weeklyHours.replace(',', '.').toDoubleOrNull()
        val days = vacationDays.toIntOrNull()
        when {
            name.isBlank() -> return fail("Falta el nombre")
            !PinHasher.isValidPin(pin) -> return fail("El PIN debe tener 4 números")
            hours == null || hours <= 0 || hours > 60 -> return fail("Horas semanales no válidas")
            days == null || days < 0 -> return fail("Días de vacaciones no válidos")
        }
        val color = StaffPalette[employees.value.size % StaffPalette.size]
        perform("${name.trim()} dada de alta como ${role.label.lowercase()}") {
            employeeRepository.create(currentUserId, name, role, pin, hours!!, days!!, color)
        }
    }

    fun changeRole(employee: Employee, role: Role) = perform(
        if (role == Role.MANAGER) "${employee.name} ahora es encargada" else "${employee.name} vuelve a ser empleada"
    ) { employeeRepository.changeRole(currentUserId, employee.id, role) }

    fun resetPin(employee: Employee, pin: String, repeat: String) {
        if (!PinHasher.isValidPin(pin)) return fail("El PIN debe tener 4 números")
        if (pin != repeat) return fail("Los PIN no coinciden")
        perform("PIN de ${employee.name} cambiado") { employeeRepository.resetPin(currentUserId, employee.id, pin) }
    }

    /** Baja lógica: deja de aparecer en la tablet, pero su registro de jornada se conserva. */
    fun deactivate(employee: Employee) = perform("${employee.name} dada de baja. Sus fichajes se conservan.") {
        employeeRepository.deactivate(currentUserId, employee.id)
    }

    fun changePassword(current: String, new: String, repeat: String) {
        PasswordRules.validate(new, repeat)?.let { return fail(it) }
        perform("Contraseña maestra cambiada") { securityRepository.changeMasterPassword(currentUserId, current, new) }
    }

    /**
     * Ceder el control: la nueva dueña escribe SU contraseña. Después se cierra la sesión, porque
     * quien estaba dentro ya no es la dueña, y se enseña el código de recuperación a la nueva.
     */
    fun transfer(employee: Employee, currentPassword: String, newPassword: String, repeat: String) {
        PasswordRules.validate(newPassword, repeat)?.let { return fail(it) }
        _dialog.value = _dialog.value.copy(busy = true, error = null)
        viewModelScope.launch {
            runCatching { securityRepository.transferOwnership(currentUserId, currentPassword, employee.id, newPassword) }
                .onSuccess { code ->
                    close()
                    sessionManager.showRecoveryCode(code)
                    sessionManager.logout()
                }
                .onFailure { fail(it.message ?: "No se ha podido ceder el control") }
        }
    }

    private companion object {
        const val MAX_TRANSFER_FAILURES = 5
        const val TRANSFER_LOCK_MS = 60_000L
    }
}
