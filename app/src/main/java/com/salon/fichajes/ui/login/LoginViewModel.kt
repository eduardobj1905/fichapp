package com.salon.fichajes.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salon.fichajes.domain.model.Employee
import com.salon.fichajes.domain.repository.EmployeeRepository
import com.salon.fichajes.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PinEntryState(
    val pin: String = "",
    val error: String? = null,
    val isChecking: Boolean = false,
    val lockedSeconds: Int = 0,
)

data class LoginUiState(
    val employees: List<Employee> = emptyList(),
    val selected: Employee? = null,
    val pinEntry: PinEntryState = PinEntryState(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val employeeRepository: EmployeeRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val selectedId = MutableStateFlow<Long?>(null)
    private val pinEntry = MutableStateFlow(PinEntryState())
    private val failedAttempts = mutableMapOf<Long, Int>()

    val state: StateFlow<LoginUiState> =
        combine(employeeRepository.observeActive(), selectedId, pinEntry) { employees, id, entry ->
            LoginUiState(
                employees = employees,
                selected = employees.firstOrNull { it.id == id },
                pinEntry = entry,
                isLoading = false,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoginUiState())

    fun select(employee: Employee) {
        selectedId.value = employee.id
        pinEntry.value = PinEntryState()
    }

    fun cancel() {
        selectedId.value = null
        pinEntry.value = PinEntryState()
    }

    fun onDigit(digit: Char) {
        val entry = pinEntry.value
        if (entry.isChecking || entry.lockedSeconds > 0 || entry.pin.length >= PIN_LENGTH) return
        val newPin = entry.pin + digit
        pinEntry.update { it.copy(pin = newPin, error = null) }
        if (newPin.length == PIN_LENGTH) verify(newPin)
    }

    fun onDelete() {
        pinEntry.update { if (it.isChecking) it else it.copy(pin = it.pin.dropLast(1), error = null) }
    }

    private fun verify(pin: String) {
        val employee = state.value.selected ?: return
        pinEntry.update { it.copy(isChecking = true) }
        viewModelScope.launch {
            if (employeeRepository.verifyPin(employee.id, pin)) {
                failedAttempts.remove(employee.id)
                pinEntry.value = PinEntryState()
                selectedId.value = null
                sessionManager.login(employee.id, employee.role)
            } else {
                val attempts = (failedAttempts[employee.id] ?: 0) + 1
                failedAttempts[employee.id] = attempts
                if (attempts >= MAX_ATTEMPTS) {
                    failedAttempts.remove(employee.id)
                    lockFor(LOCK_SECONDS)
                } else {
                    pinEntry.value = PinEntryState(error = "PIN incorrecto (${MAX_ATTEMPTS - attempts} intentos)")
                }
            }
        }
    }

    /** Freno a la fuerza bruta: tras varios fallos seguidos el teclado se bloquea unos segundos. */
    private suspend fun lockFor(seconds: Int) {
        for (remaining in seconds downTo 1) {
            pinEntry.value = PinEntryState(error = "Demasiados intentos", lockedSeconds = remaining)
            delay(1_000)
        }
        pinEntry.value = PinEntryState()
    }

    private companion object {
        const val PIN_LENGTH = 4
        const val MAX_ATTEMPTS = 5
        const val LOCK_SECONDS = 30
    }
}
