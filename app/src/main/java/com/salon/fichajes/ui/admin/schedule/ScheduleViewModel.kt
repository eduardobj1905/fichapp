package com.salon.fichajes.ui.admin.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salon.fichajes.core.format.parseTimeOrNull
import com.salon.fichajes.core.time.Periods
import com.salon.fichajes.domain.auth.Permissions
import com.salon.fichajes.session.SessionManager
import com.salon.fichajes.domain.model.Employee
import com.salon.fichajes.domain.model.Shift
import com.salon.fichajes.domain.repository.EmployeeRepository
import com.salon.fichajes.domain.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject

data class ScheduleUiState(
    val isLoading: Boolean = true,
    val weekStart: LocalDate = LocalDate.MIN,
    val days: List<LocalDate> = emptyList(),
    val employees: List<Employee> = emptyList(),
    /** employeeId -> fecha -> turnos de ese día. */
    val shifts: Map<Long, Map<LocalDate, List<Shift>>> = emptyMap(),
) {
    fun shiftsFor(employeeId: Long, date: LocalDate): List<Shift> = shifts[employeeId]?.get(date).orEmpty()

    fun plannedHours(employeeId: Long): Duration =
        shifts[employeeId].orEmpty().values.flatten()
            .fold(Duration.ZERO) { acc, s -> acc + Duration.between(s.start, s.end) }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    employeeRepository: EmployeeRepository,
    sessionManager: SessionManager,
    clock: Clock,
) : ViewModel() {

    private val session = sessionManager.requireSession()

    private val weekStart = MutableStateFlow(Periods.weekStart(LocalDate.now(clock)))

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    val uiState: StateFlow<ScheduleUiState> = combine(
        weekStart,
        employeeRepository.observeActive(),
        weekStart.flatMapLatest { start -> scheduleRepository.observeBetween(start, start.plusDays(6)) },
    ) { start, employees, shifts ->
        ScheduleUiState(
            isLoading = false,
            weekStart = start,
            days = (0L..6L).map { start.plusDays(it) },
            employees = employees,
            shifts = shifts.groupBy { it.employeeId }.mapValues { (_, list) -> list.groupBy { it.date } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState())

    fun previousWeek() = weekStart.update { it.minusWeeks(1) }
    fun nextWeek() = weekStart.update { it.plusWeeks(1) }

    fun addShift(employeeId: Long, date: LocalDate, startText: String, endText: String) {
        val start = parseTimeOrNull(startText)
        val end = parseTimeOrNull(endText)
        if (start == null || end == null) {
            _messages.trySend("Horas no válidas. Usa el formato HH:mm")
            return
        }
        viewModelScope.launch {
            runCatching { scheduleRepository.save(session.employeeId, Shift(0, employeeId, date, start, end)) }
                .onFailure { _messages.send(it.message ?: "No se ha podido guardar el turno") }
        }
    }

    fun deleteShift(shift: Shift) {
        viewModelScope.launch {
            runCatching { scheduleRepository.delete(session.employeeId, shift.id) }
                .onFailure { _messages.send(it.message ?: "No se ha podido borrar el turno") }
        }
    }

    /** Una encargada no puede tocar su propio horario (la dueña sí). */
    fun canEdit(employeeId: Long): Boolean =
        Permissions.canManageStaffMember(session.role, session.employeeId, employeeId)

    private inline fun MutableStateFlow<LocalDate>.update(transform: (LocalDate) -> LocalDate) {
        value = transform(value)
    }
}
