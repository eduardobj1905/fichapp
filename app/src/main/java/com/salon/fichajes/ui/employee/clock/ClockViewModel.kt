package com.salon.fichajes.ui.employee.clock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salon.fichajes.core.format.formatDateTime
import com.salon.fichajes.core.format.formatHoursMinutes
import com.salon.fichajes.core.format.formatTime
import com.salon.fichajes.core.time.Periods
import com.salon.fichajes.core.time.WorkedTime
import com.salon.fichajes.core.time.tickerFlow
import com.salon.fichajes.domain.hours.ApprovalStatus
import com.salon.fichajes.domain.hours.approvalStatus
import com.salon.fichajes.domain.hours.targetFor
import com.salon.fichajes.domain.model.Absence
import com.salon.fichajes.domain.model.ClockEntry
import com.salon.fichajes.domain.model.ClockResult
import com.salon.fichajes.domain.model.Employee
import com.salon.fichajes.domain.model.Shift
import com.salon.fichajes.domain.model.WeekApproval
import com.salon.fichajes.domain.model.WorkState
import com.salon.fichajes.domain.repository.AbsenceRepository
import com.salon.fichajes.domain.repository.ClockRepository
import com.salon.fichajes.domain.repository.EmployeeRepository
import com.salon.fichajes.domain.repository.ScheduleRepository
import com.salon.fichajes.domain.repository.WeekApprovalRepository
import java.time.LocalDate
import com.salon.fichajes.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

data class ClockUiState(
    val isLoading: Boolean = true,
    val employee: Employee? = null,
    val now: Instant = Instant.EPOCH,
    val zone: ZoneId = ZoneId.systemDefault(),
    /** Tramo abierto (null si no está trabajando). */
    val openEntry: ClockEntry? = null,
    val currentElapsed: Duration = Duration.ZERO,
    val today: Duration = Duration.ZERO,
    val week: Duration = Duration.ZERO,
    val weekTarget: Duration = Duration.ZERO,
    val recent: List<ClockEntry> = emptyList(),
    /** Bloquea el botón: fichaje en curso o recién hecho (anti doble toque). */
    val isProcessing: Boolean = false,
    /** Fichajes que la jefa ha modificado o añadido y la empleada aún no ha visto. */
    val unseenCorrections: List<ClockEntry> = emptyList(),
    val staffNames: Map<Long, String> = emptyMap(),
    /** Fuera / trabajando / en el descanso. */
    val workState: WorkState = WorkState.Off,
    val breakElapsed: Duration = Duration.ZERO,
    /** Turnos de hoy según el horario (con su descanso previsto). */
    val todayShifts: List<Shift> = emptyList(),
    /** Baja o ausencia que cubre hoy, si la hay. */
    val absenceToday: Absence? = null,
    val lastWeekStatus: ApprovalStatus = ApprovalStatus.PENDING,
    val lastWeekApprovedBy: String? = null,
) {
    val isWorking: Boolean get() = openEntry != null
    val isOnBreak: Boolean get() = workState is WorkState.OnBreak
    val weekProgress: Float
        get() = if (weekTarget.isZero) 0f else (week.toMinutes().toFloat() / weekTarget.toMinutes()).coerceIn(0f, 1f)
}

sealed interface ClockEvent {
    data class Message(val text: String) : ClockEvent
    /** Fichaje correcto: la UI hace vibrar el dispositivo. */
    data object ClockedSuccessfully : ClockEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ClockViewModel @Inject constructor(
    private val clockRepository: ClockRepository,
    employeeRepository: EmployeeRepository,
    scheduleRepository: ScheduleRepository,
    absenceRepository: AbsenceRepository,
    weekApprovalRepository: WeekApprovalRepository,
    private val sessionManager: SessionManager,
    private val clock: Clock,
) : ViewModel() {

    private val employeeId = sessionManager.requireSession().employeeId
    private val zone: ZoneId = clock.zone
    private val isProcessing = MutableStateFlow(false)
    private var autoLogoutJob: Job? = null

    private val _events = Channel<ClockEvent>(Channel.BUFFERED)
    val events: Flow<ClockEvent> = _events.receiveAsFlow()

    // Un único ticker compartido: alimenta el cronómetro y decide cuándo cambia la semana.
    private val ticker = tickerFlow(1.seconds, clock)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    // Si la pantalla sigue abierta al pasar de domingo a lunes, la consulta cambia sola de semana.
    private val weekEntries: Flow<List<ClockEntry>> = ticker
        .map { Periods.week(it.atZone(zone).toLocalDate(), zone) }
        .distinctUntilChanged()
        .flatMapLatest { range -> clockRepository.observeOverlapping(employeeId, range) }

    private data class Planning(
        val todayShifts: List<Shift>,
        val weekAbsences: List<Absence>,
        val lastWeekApproval: WeekApproval?,
        val lastWeekEntries: List<ClockEntry>,
    )

    // La sesión dura minutos: basta con fijar "hoy" y "esta semana" al entrar.
    private val todayDate = LocalDate.now(clock)
    private val monday = Periods.weekStart(todayDate)
    private val lastMonday = monday.minusWeeks(1)

    private val planning = combine(
        scheduleRepository.observeBetween(todayDate, todayDate).map { list -> list.filter { it.employeeId == employeeId } },
        absenceRepository.observeOverlapping(monday, monday.plusDays(6)).map { list -> list.filter { it.employeeId == employeeId } },
        weekApprovalRepository.observeForEmployee(employeeId, limit = 4).map { list -> list.firstOrNull { it.weekStart == lastMonday } },
        clockRepository.observeOverlapping(employeeId, Periods.week(lastMonday, zone)),
    ) { shifts, absences, approval, lastWeek -> Planning(shifts, absences, approval, lastWeek) }

    // Equipo (para poner nombre a quien corrigió) + cambios de la jefa aún no vistos + horario.
    private val context = combine(
        employeeRepository.observeActive(),
        clockRepository.observeUnseenCorrections(employeeId),
        planning,
    ) { staff, unseen, plan -> Triple(staff, unseen, plan) }

    val uiState: StateFlow<ClockUiState> = combine(
        context,
        clockRepository.observeRecent(employeeId, limit = 15),
        weekEntries,
        isProcessing,
        ticker,
    ) { (staff, unseen, plan), recent, week, processing, now ->
        val today = now.atZone(zone).toLocalDate()
        val open = recent.firstOrNull { it.isOpen }
        val employee = staff.firstOrNull { it.id == employeeId }
        val workState = WorkState.from(recent.firstOrNull(), now, zone)
        val lastWeekWorked = WorkedTime.between(plan.lastWeekEntries, Periods.week(lastMonday, zone), now)
        ClockUiState(
            isLoading = false,
            employee = employee,
            unseenCorrections = unseen,
            staffNames = staff.associate { it.id to it.name },
            now = now,
            zone = zone,
            openEntry = open,
            currentElapsed = open?.let { Duration.between(it.clockIn, now) } ?: Duration.ZERO,
            today = WorkedTime.between(week, Periods.day(today, zone), now),
            week = WorkedTime.between(week, Periods.week(today, zone), now),
            // Los días de baja no cuentan para el objetivo de horas.
            weekTarget = targetFor(employee?.weeklyContractHours ?: 0.0, monday, monday.plusDays(6), plan.weekAbsences),
            recent = recent,
            isProcessing = processing,
            workState = workState,
            breakElapsed = (workState as? WorkState.OnBreak)?.let { Duration.between(it.since, now) } ?: Duration.ZERO,
            todayShifts = plan.todayShifts,
            absenceToday = plan.weekAbsences.firstOrNull { it.covers(today) },
            lastWeekStatus = approvalStatus(plan.lastWeekApproval, lastWeekWorked),
            lastWeekApprovedBy = plan.lastWeekApproval?.let { staff.firstOrNull { e -> e.id == it.approvedBy }?.name },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClockUiState())

    /** Empieza el descanso: cierra el tramo actual marcándolo como salida a descanso. */
    fun onBreakButton() {
        if (isProcessing.value) return
        isProcessing.value = true
        viewModelScope.launch {
            runCatching { clockRepository.startBreak(employeeId) }
                .onSuccess { r -> handleResult(r) }
                .onFailure {
                    _events.send(ClockEvent.Message(it.message ?: "No se ha podido fichar el descanso"))
                    isProcessing.value = false
                }
        }
    }

    /** En el descanso, pero no vuelve hoy: esa salida pasa a ser el final de la jornada. */
    fun finishDayFromBreak() {
        if (isProcessing.value) return
        isProcessing.value = true
        viewModelScope.launch {
            clockRepository.finishDayFromBreak(employeeId)
            _events.send(ClockEvent.Message("Jornada terminada. ¡Hasta mañana!"))
            scheduleAutoLogout()
        }
    }

    private suspend fun handleResult(r: ClockResult) {
        when (r) {
            is ClockResult.ClockedIn -> {
                _events.send(ClockEvent.ClockedSuccessfully)
                _events.send(ClockEvent.Message("Entrada registrada a las ${r.at.formatTime(zone)}. ¡Buen día!"))
                scheduleAutoLogout()
            }
            is ClockResult.ClockedOut -> {
                _events.send(ClockEvent.ClockedSuccessfully)
                _events.send(ClockEvent.Message("Salida a las ${r.at.formatTime(zone)} · ${r.worked.formatHoursMinutes()} trabajadas"))
                scheduleAutoLogout()
            }
            is ClockResult.BreakStarted -> {
                _events.send(ClockEvent.ClockedSuccessfully)
                _events.send(ClockEvent.Message("Descanso desde las ${r.at.formatTime(zone)}. Ficha la vuelta al terminar."))
                scheduleAutoLogout()
            }
            is ClockResult.BreakEnded -> {
                _events.send(ClockEvent.ClockedSuccessfully)
                _events.send(
                    ClockEvent.Message("Vuelta del descanso a las ${r.at.formatTime(zone)} · descanso de ${r.breakDuration.formatHoursMinutes()}")
                )
                scheduleAutoLogout()
            }
            is ClockResult.NeedsAdminReview -> {
                _events.send(
                    ClockEvent.Message(
                        "Tienes una entrada abierta desde el ${r.openSince.formatDateTime(zone)}. " +
                            "Avisa a la encargada para que la corrija."
                    )
                )
                isProcessing.value = false
            }
        }
    }

    fun onClockButton() {
        // Anti doble toque: si ya hay un fichaje en marcha (o se acaba de fichar), se ignora.
        if (isProcessing.value) return
        isProcessing.value = true
        viewModelScope.launch {
            val result = runCatching { clockRepository.toggle(employeeId) }
            result.onSuccess { r -> handleResult(r) }.onFailure {
                _events.send(ClockEvent.Message("No se ha podido fichar. Inténtalo de nuevo."))
                isProcessing.value = false
            }
        }
    }

    /** Tablet compartida: tras fichar, la sesión se cierra sola a los 3 s (el botón sigue bloqueado). */
    private fun scheduleAutoLogout() {
        autoLogoutJob?.cancel()
        autoLogoutJob = viewModelScope.launch {
            delay(AUTO_LOGOUT_DELAY)
            sessionManager.logout()
        }
    }

    /** Si la empleada quiere ver sus vacaciones justo después de fichar, no la echamos. */
    fun cancelAutoLogout() {
        autoLogoutJob?.cancel()
        autoLogoutJob = null
        isProcessing.value = false
    }

    fun logout() = sessionManager.logout()

    /** La empleada pulsa "Entendido" en el aviso de cambios. */
    fun acknowledgeCorrections() {
        viewModelScope.launch { clockRepository.markCorrectionsSeen(employeeId) }
    }

    private companion object {
        val AUTO_LOGOUT_DELAY = 3.seconds
    }
}
