package com.salon.fichajes.ui.employee.vacations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salon.fichajes.domain.model.VacationRequest
import com.salon.fichajes.domain.repository.EmployeeRepository
import com.salon.fichajes.domain.repository.VacationRepository
import com.salon.fichajes.domain.vacation.VacationBalance
import com.salon.fichajes.domain.vacation.vacationBalance
import com.salon.fichajes.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class EmployeeVacationsUiState(
    val isLoading: Boolean = true,
    val year: Int = LocalDate.now().year,
    val balance: VacationBalance = VacationBalance(0, 0, 0),
    val requests: List<VacationRequest> = emptyList(),
    val isSubmitting: Boolean = false,
)

@HiltViewModel
class EmployeeVacationsViewModel @Inject constructor(
    private val vacationRepository: VacationRepository,
    employeeRepository: EmployeeRepository,
    sessionManager: SessionManager,
    private val clock: Clock,
) : ViewModel() {

    private val employeeId = sessionManager.requireSession().employeeId
    private val isSubmitting = MutableStateFlow(false)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    val today: LocalDate get() = LocalDate.now(clock)

    val uiState: StateFlow<EmployeeVacationsUiState> = combine(
        employeeRepository.observe(employeeId),
        vacationRepository.observeForEmployee(employeeId),
        isSubmitting,
    ) { employee, requests, submitting ->
        val year = today.year
        EmployeeVacationsUiState(
            isLoading = false,
            year = year,
            balance = vacationBalance(employee?.annualVacationDays ?: 0, requests, year),
            requests = requests,
            isSubmitting = submitting,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EmployeeVacationsUiState())

    fun request(start: LocalDate, end: LocalDate) {
        val state = uiState.value
        val days = (end.toEpochDay() - start.toEpochDay() + 1).toInt()
        val error = when {
            end.isBefore(start) -> "La fecha de fin es anterior al inicio"
            start.isBefore(today) -> "No puedes pedir días que ya han pasado"
            start.year == state.year && days > state.balance.remaining ->
                "Pides $days días y te quedan ${state.balance.remaining}"
            else -> null
        }
        if (error != null) {
            _messages.trySend(error)
            return
        }
        isSubmitting.value = true
        viewModelScope.launch {
            runCatching { vacationRepository.request(employeeId, start, end) }
                .onSuccess { _messages.send("Solicitud enviada: $days días. La encargada la revisará.") }
                .onFailure { _messages.send(it.message ?: "No se ha podido enviar la solicitud") }
            isSubmitting.value = false
        }
    }
}
