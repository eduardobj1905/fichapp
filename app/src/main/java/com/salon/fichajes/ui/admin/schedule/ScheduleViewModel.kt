package com.salon.fichajes.ui.admin.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salon.fichajes.core.format.formatShort
import com.salon.fichajes.core.format.parseTimeOrNull
import com.salon.fichajes.core.time.Periods
import com.salon.fichajes.domain.auth.Permissions
import com.salon.fichajes.domain.model.Absence
import com.salon.fichajes.domain.model.AbsenceType
import com.salon.fichajes.domain.model.Employee
import com.salon.fichajes.domain.model.Shift
import com.salon.fichajes.domain.model.VacationRequest
import com.salon.fichajes.domain.repository.AbsenceRepository
import com.salon.fichajes.domain.repository.EmployeeRepository
import com.salon.fichajes.domain.repository.ScheduleRepository
import com.salon.fichajes.domain.repository.VacationRepository
import com.salon.fichajes.session.SessionManager
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
    val absences: List<Absence> = emptyList(),
    val vacations: List<VacationRequest> = emptyList(),
) {
    fun shiftsFor(employeeId: Long, date: LocalDate): List<Shift> = shifts[employeeId]?.get(date).orEmpty()

    fun absenceOn(employeeId: Long, date: LocalDate): Absence? =
        absences.firstOrNull { it.employeeId == employeeId && it.covers(date) }

    fun onVacation(employeeId: Long, date: LocalDate): Boolean =
        vacations.any { it.employeeId == employeeId && date in it.startDate..it.endDate }

    /** Horas de trabajo planificadas (sin descansos y sin los días de baja). */
    fun plannedHours(employeeId: Long): Duration =
        shifts[employeeId].orEmpty()
            .filterKeys { absenceOn(employeeId, it) == null }
            .values.flatten()
            .fold(Duration.ZERO) { acc, s -> acc + s.plannedWork }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val absenceRepository: AbsenceRepository,
    vacationRepository: VacationRepository,
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
        weekStart.flatMapLatest { start -> absenceRepository.observeOverlapping(start, start.plusDays(6)) },
        weekStart.flatMapLatest { start -> vacationRepository.observeApprovedOverlapping(start, start.plusDays(6)) },
    ) { start, employees, shifts, absences, vacations ->
        ScheduleUiState(
            isLoading = false,
            weekStart = start,
            days = (0L..6L).map { start.plusDays(it) },
            employees = employees,
            shifts = shifts.groupBy { it.employeeId }.mapValues { (_, list) -> list.groupBy { it.date } },
            absences = absences,
            vacations = vacations,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState())

    fun previousWeek() { weekStart.value = weekStart.value.minusWeeks(1) }
    fun nextWeek() { weekStart.value = weekStart.value.plusWeeks(1) }

    /** El descanso es opcional: si se deja en blanco, el turno no tiene descanso. */
    fun addShift(employeeId: Long, date: LocalDate, startText: String, endText: String, breakStartText: String, breakEndText: String) {
        val start = parseTimeOrNull(startText)
        val end = parseTimeOrNull(endText)
        val hasBreak = breakStartText.isNotBlank() || breakEndText.isNotBlank()
        val breakStart = if (hasBreak) parseTimeOrNull(breakStartText) else null
        val breakEnd = if (hasBreak) parseTimeOrNull(breakEndText) else null
        if (start == null || end == null || (hasBreak && (breakStart == null || breakEnd == null))) {
            _messages.trySend("Horas no válidas. Usa el formato HH:mm")
            return
        }
        launchOrReport("No se ha podido guardar el turno") {
            scheduleRepository.save(session.employeeId, Shift(0, employeeId, date, start, end, breakStart, breakEnd))
        }
    }

    fun deleteShift(shift: Shift) = launchOrReport("No se ha podido borrar el turno") {
        scheduleRepository.delete(session.employeeId, shift.id)
    }

    /** Baja abierta desde [from]: sin fecha de alta hasta que se reincorpore. */
    fun startAbsence(employee: Employee, from: LocalDate, type: AbsenceType, note: String) =
        launchOrReport("No se ha podido guardar la baja", success = "${employee.name}: ${type.label.lowercase()} desde el ${from.formatShort()}") {
            absenceRepository.add(session.employeeId, employee.id, type, from, endDate = null, note = note)
        }

    /** Se reincorpora el día [returnDay]: el último día de baja es el anterior. */
    fun endAbsence(absence: Absence, returnDay: LocalDate) =
        launchOrReport("No se ha podido dar el alta", success = "Alta registrada: vuelve el ${returnDay.formatShort()}") {
            absenceRepository.end(session.employeeId, absence.id, returnDay.minusDays(1))
        }

    fun deleteAbsence(absence: Absence) = launchOrReport("No se ha podido borrar la baja", success = "Baja eliminada") {
        absenceRepository.delete(session.employeeId, absence.id)
    }

    /** Una encargada no puede tocar su propio horario ni sus bajas (la dueña sí). */
    fun canEdit(employeeId: Long): Boolean =
        Permissions.canManageStaffMember(session.role, session.employeeId, employeeId)

    private fun launchOrReport(error: String, success: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { success?.let { _messages.send(it) } }
                .onFailure { _messages.send(it.message ?: error) }
        }
    }
}
