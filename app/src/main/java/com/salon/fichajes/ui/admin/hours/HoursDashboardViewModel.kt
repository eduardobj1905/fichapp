package com.salon.fichajes.ui.admin.hours

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salon.fichajes.core.format.formatMonthYear
import com.salon.fichajes.core.format.formatShort
import com.salon.fichajes.core.format.parseTimeOrNull
import com.salon.fichajes.core.time.Periods
import com.salon.fichajes.core.time.TimeRange
import com.salon.fichajes.core.time.WorkedTime
import com.salon.fichajes.core.time.tickerFlow
import com.salon.fichajes.data.export.TimesheetExporter
import com.salon.fichajes.domain.auth.Permissions
import com.salon.fichajes.domain.hours.ApprovalStatus
import com.salon.fichajes.domain.hours.approvalStatus
import com.salon.fichajes.domain.hours.canApproveWeek
import com.salon.fichajes.domain.hours.targetFor
import com.salon.fichajes.domain.model.Absence
import com.salon.fichajes.domain.model.WeekApproval
import com.salon.fichajes.domain.repository.AbsenceRepository
import com.salon.fichajes.domain.repository.WeekApprovalRepository
import kotlinx.coroutines.flow.flowOf
import com.salon.fichajes.domain.clock.CorrectionRules
import com.salon.fichajes.domain.model.ClockEntry
import com.salon.fichajes.domain.model.ClockRules.MAX_OPEN_ENTRY
import com.salon.fichajes.domain.model.Employee
import com.salon.fichajes.domain.repository.ClockRepository
import com.salon.fichajes.domain.repository.EmployeeRepository
import com.salon.fichajes.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

enum class HoursPeriod(val label: String) { WEEK("Semana"), MONTH("Mes") }

data class EmployeeHoursRow(
    val employee: Employee,
    val worked: Duration,
    val target: Duration,
    /** Hora de entrada del tramo abierto si está trabajando ahora. */
    val workingSince: Instant?,
    /** Solo en la vista Semana: estado de la aprobación de sus horas. */
    val approval: ApprovalStatus? = null,
    val approvedByName: String? = null,
    /** La semana ha terminado y quien mira puede aprobarla (una encargada nunca la suya). */
    val canApprove: Boolean = false,
    /** Baja o ausencia que cubre hoy (para el aviso "De baja"). */
    val absenceToday: Absence? = null,
) {
    val progress: Float
        get() = if (target.isZero) 0f else (worked.toMinutes().toFloat() / target.toMinutes()).coerceIn(0f, 1f)
    val isWorking: Boolean get() = workingSince != null
}

data class HoursDashboardUiState(
    val isLoading: Boolean = true,
    val period: HoursPeriod = HoursPeriod.WEEK,
    val periodLabel: String = "",
    val isCurrentPeriod: Boolean = true,
    /** Nombre propuesto para el CSV, p. ej. "Fichapp_horas_2026-11.csv". */
    val exportFileName: String = "Fichapp_horas.csv",
    val isExporting: Boolean = false,
    val zone: ZoneId = ZoneId.systemDefault(),
    val rows: List<EmployeeHoursRow> = emptyList(),
    val teamTotal: Duration = Duration.ZERO,
    val workingNow: Int = 0,
    /** Tramos abiertos más de 14 h: olvidos de fichar salida que la jefa debe cerrar. */
    val staleOpenEntries: List<Pair<ClockEntry, Employee?>> = emptyList(),
)

private data class PeriodWindow(
    val period: HoursPeriod,
    val offset: Int,
    val range: TimeRange,
    val label: String,
    val fileSuffix: String,
    val month: YearMonth,
    /** Primer y último día del periodo (inclusive). */
    val from: LocalDate,
    val to: LocalDate,
)

private data class PeriodData(
    val entries: List<ClockEntry>,
    val approvals: List<WeekApproval>,
    val absences: List<Absence>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HoursDashboardViewModel @Inject constructor(
    private val clockRepository: ClockRepository,
    employeeRepository: EmployeeRepository,
    private val exporter: TimesheetExporter,
    private val weekApprovalRepository: WeekApprovalRepository,
    private val absenceRepository: AbsenceRepository,
    private val sessionManager: SessionManager,
    private val clock: Clock,
) : ViewModel() {

    private val zone = clock.zone
    private val period = MutableStateFlow(HoursPeriod.WEEK)
    /** 0 = semana/mes actual, -1 = anterior… */
    private val offset = MutableStateFlow(0)
    private val isExporting = MutableStateFlow(false)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    // Cada 30 s basta para el panel: refresca los tramos abiertos sin gastar batería.
    private val ticker = tickerFlow(30.seconds, clock)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val window: Flow<PeriodWindow> = combine(
        period,
        offset,
        ticker.map { it.atZone(zone).toLocalDate() }.distinctUntilChanged(),
    ) { p, o, today -> windowFor(p, o, today) }.distinctUntilChanged()

    private val session = sessionManager.requireSession()

    // flatMapLatest: al cambiar de periodo se cancela la consulta anterior y se abre la nueva.
    private val periodData: Flow<PeriodData> = window.flatMapLatest { w ->
        combine(
            clockRepository.observeAllOverlapping(w.range),
            // Las aprobaciones son por semana: en la vista Mes no se muestran.
            if (w.period == HoursPeriod.WEEK) weekApprovalRepository.observeForWeek(w.from) else flowOf(emptyList()),
            absenceRepository.observeOverlapping(w.from, w.to),
        ) { entries, approvals, absences -> PeriodData(entries, approvals, absences) }
    }

    private val windowAndExporting = combine(window, isExporting) { w, exporting -> w to exporting }

    val uiState: StateFlow<HoursDashboardUiState> = combine(
        employeeRepository.observeActive(),
        windowAndExporting,
        periodData,
        clockRepository.observeOpenEntries(),
        ticker,
    ) { employees, (w, exporting), data, openEntries, now ->
        val today = now.atZone(zone).toLocalDate()
        val byEmployee = data.entries.groupBy { it.employeeId }
        val openByEmployee = openEntries.associateBy { it.employeeId }
        val approvalsByEmployee = data.approvals.associateBy { it.employeeId }
        val names = employees.associate { it.id to it.name }
        val weekFinished = w.period == HoursPeriod.WEEK && canApproveWeek(w.from, today)
        val rows = employees.map { employee ->
            val open = openByEmployee[employee.id]?.takeIf { Duration.between(it.clockIn, now) <= MAX_OPEN_ENTRY }
            val worked = WorkedTime.between(byEmployee[employee.id].orEmpty(), w.range, now)
            val myAbsences = data.absences.filter { it.employeeId == employee.id }
            val approval = approvalsByEmployee[employee.id]
            val status = if (w.period == HoursPeriod.WEEK) approvalStatus(approval, worked) else null
            EmployeeHoursRow(
                employee = employee,
                worked = worked,
                // Los días de baja no cuentan para el objetivo.
                target = targetFor(employee.weeklyContractHours, w.from, w.to, myAbsences),
                // "En el salón desde…" solo tiene sentido mirando el periodo actual.
                workingSince = open?.clockIn?.takeIf { w.offset == 0 },
                approval = status,
                approvedByName = approval?.let { names[it.approvedBy] },
                canApprove = weekFinished && status != ApprovalStatus.APPROVED &&
                    Permissions.canManageStaffMember(session.role, session.employeeId, employee.id),
                absenceToday = myAbsences.firstOrNull { it.covers(today) },
            )
        }.sortedWith(compareByDescending<EmployeeHoursRow> { it.isWorking }.thenBy { it.employee.name.lowercase() })

        val employeesById = employees.associateBy { it.id }
        HoursDashboardUiState(
            isLoading = false,
            period = w.period,
            periodLabel = w.label,
            isCurrentPeriod = w.offset == 0,
            exportFileName = "Fichapp_horas_${w.fileSuffix}.csv",
            isExporting = exporting,
            zone = zone,
            rows = rows,
            teamTotal = rows.fold(Duration.ZERO) { acc, r -> acc + r.worked },
            workingNow = rows.count { it.isWorking },
            staleOpenEntries = openEntries
                .filter { Duration.between(it.clockIn, now) > MAX_OPEN_ENTRY }
                .map { it to employeesById[it.employeeId] },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HoursDashboardUiState())

    fun setPeriod(value: HoursPeriod) {
        period.value = value
        offset.value = 0
    }

    fun previousPeriod() { offset.value -= 1 }
    fun nextPeriod() { if (offset.value < 0) offset.value += 1 }

    /** Exporta el periodo que se está viendo al archivo elegido por la jefa. */
    fun export(uri: Uri) {
        val state = uiState.value
        val w = windowFor(state.period, offset.value, LocalDate.now(clock))
        isExporting.value = true
        viewModelScope.launch {
            runCatching {
                exporter.export(
                    uri = uri,
                    periodLabel = w.label,
                    range = w.range,
                    targets = state.rows.associate { it.employee.id to it.target },
                    weekStart = if (w.period == HoursPeriod.WEEK) w.from else null,
                )
            }.onSuccess { count -> _messages.send("Hoja de horas exportada ($count fichajes)") }
                .onFailure { _messages.send("No se ha podido exportar: ${it.message}") }
            isExporting.value = false
        }
    }

    /** La jefa cierra un tramo olvidado indicando la hora real de salida (en el día de la entrada). */
    fun closeStaleEntry(entry: ClockEntry, timeText: String, reason: String) {
        val time = parseTimeOrNull(timeText)
        if (time == null) {
            _messages.trySend("Hora no válida. Usa el formato HH:mm")
            return
        }
        val inZoned = entry.clockIn.atZone(zone)
        val (_, clockOut) = CorrectionRules.resolve(inZoned.toLocalDate(), inZoned.toLocalTime(), time, zone)
        val adminId = sessionManager.requireSession().employeeId
        viewModelScope.launch {
            runCatching { clockRepository.correctAsAdmin(entry.id, entry.clockIn, clockOut, reason, adminId) }
                .onSuccess { _messages.send("Fichaje corregido. La empleada verá el aviso.") }
                .onFailure { _messages.send(it.message ?: "No se ha podido corregir") }
        }
    }

    /** Aprueba las horas de la semana que se está viendo (debe haber terminado). */
    fun approveWeek(employee: Employee) {
        val w = windowFor(HoursPeriod.WEEK, offset.value, LocalDate.now(clock))
        viewModelScope.launch {
            runCatching { weekApprovalRepository.approve(session.employeeId, employee.id, w.from) }
                .onSuccess { _messages.send("Horas de ${employee.name} aprobadas") }
                .onFailure { _messages.send(it.message ?: "No se ha podido aprobar") }
        }
    }

    private fun windowFor(p: HoursPeriod, offset: Int, today: LocalDate): PeriodWindow = when (p) {
        HoursPeriod.WEEK -> {
            val monday = Periods.weekStart(today).plusWeeks(offset.toLong())
            PeriodWindow(
                period = p,
                offset = offset,
                range = Periods.week(monday, zone),
                label = "Semana ${monday.formatShort()} – ${monday.plusDays(6).formatShort()}",
                fileSuffix = "semana_$monday",
                month = YearMonth.from(monday),
                from = monday,
                to = monday.plusDays(6),
            )
        }
        HoursPeriod.MONTH -> {
            val month = YearMonth.from(today).plusMonths(offset.toLong())
            PeriodWindow(
                p, offset, Periods.month(month, zone), month.formatMonthYear(), month.toString(), month,
                from = month.atDay(1),
                to = month.atEndOfMonth(),
            )
        }
    }
}
