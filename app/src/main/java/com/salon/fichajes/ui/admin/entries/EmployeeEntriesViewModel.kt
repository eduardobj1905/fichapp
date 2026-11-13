package com.salon.fichajes.ui.admin.entries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salon.fichajes.core.format.parseTimeOrNull
import com.salon.fichajes.core.time.Periods
import com.salon.fichajes.core.time.WorkedTime
import com.salon.fichajes.core.time.tickerFlow
import com.salon.fichajes.domain.auth.Permissions
import com.salon.fichajes.domain.clock.CorrectionRules
import com.salon.fichajes.domain.model.ClockEntry
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

data class DayEntries(val date: LocalDate, val entries: List<ClockEntry>, val worked: Duration)

/** Qué está editando la jefa: un tramo existente o uno nuevo en un día concreto. */
sealed interface EntryEditor {
    val date: LocalDate
    data class Edit(val entry: ClockEntry, override val date: LocalDate) : EntryEditor
    data class Add(override val date: LocalDate) : EntryEditor
}

data class EmployeeEntriesUiState(
    val isLoading: Boolean = true,
    val employee: Employee? = null,
    val zone: ZoneId = ZoneId.systemDefault(),
    val today: LocalDate = LocalDate.MIN,
    val weekStart: LocalDate = LocalDate.MIN,
    val days: List<DayEntries> = emptyList(),
    val weekTotal: Duration = Duration.ZERO,
    val weekTarget: Duration = Duration.ZERO,
    val adminNames: Map<Long, String> = emptyMap(),
    val editor: EntryEditor? = null,
    val editorError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EmployeeEntriesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val clockRepository: ClockRepository,
    employeeRepository: EmployeeRepository,
    private val sessionManager: SessionManager,
    private val clock: Clock,
) : ViewModel() {

    private val employeeId: Long = checkNotNull(savedStateHandle["employeeId"])

    /** Una encargada ve sus propios fichajes en solo lectura: los corrige la dueña. */
    val canEdit: Boolean = sessionManager.requireSession().let {
        Permissions.canManageStaffMember(it.role, it.employeeId, employeeId)
    }
    private val zone = clock.zone
    private val weekStart = MutableStateFlow(Periods.weekStart(LocalDate.now(clock)))
    private val editor = MutableStateFlow<Pair<EntryEditor?, String?>>(null to null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private val entries = weekStart.flatMapLatest { clockRepository.observeOverlapping(employeeId, Periods.week(it, zone)) }

    val uiState: StateFlow<EmployeeEntriesUiState> = combine(
        employeeRepository.observeActive(),
        weekStart,
        entries,
        editor,
        tickerFlow(30.seconds, clock),
    ) { staff, start, list, (currentEditor, editorError), now ->
        val employee = staff.firstOrNull { it.id == employeeId }
        val days = (0L..6L).map { offset ->
            val date = start.plusDays(offset)
            DayEntries(
                date = date,
                entries = list.filter { it.clockIn.atZone(zone).toLocalDate() == date },
                worked = WorkedTime.between(list, Periods.day(date, zone), now),
            )
        }
        EmployeeEntriesUiState(
            isLoading = false,
            employee = employee,
            zone = zone,
            today = now.atZone(zone).toLocalDate(),
            weekStart = start,
            days = days,
            weekTotal = WorkedTime.between(list, Periods.week(start, zone), now),
            weekTarget = Duration.ofMinutes(((employee?.weeklyContractHours ?: 0.0) * 60).toLong()),
            adminNames = staff.associate { it.id to it.name },
            editor = currentEditor,
            editorError = editorError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EmployeeEntriesUiState())

    fun previousWeek() { weekStart.value = weekStart.value.minusWeeks(1) }
    fun nextWeek() { weekStart.value = weekStart.value.plusWeeks(1) }

    fun startEdit(entry: ClockEntry) {
        editor.value = EntryEditor.Edit(entry, entry.clockIn.atZone(zone).toLocalDate()) to null
    }

    fun startAdd(date: LocalDate) { editor.value = EntryEditor.Add(date) to null }

    fun dismissEditor() { editor.value = null to null }

    fun save(inText: String, outText: String, reason: String) {
        val current = editor.value.first ?: return
        val inTime = parseTimeOrNull(inText)
        val outTime = if (outText.isBlank()) null else parseTimeOrNull(outText)
        val canStayOpen = current is EntryEditor.Edit && current.entry.isOpen
        val error = when {
            inTime == null -> "Hora de entrada no válida (HH:mm)"
            outText.isNotBlank() && outTime == null -> "Hora de salida no válida (HH:mm)"
            outTime == null && !canStayOpen -> "Indica la hora de salida"
            else -> null
        }
        if (error != null) {
            editor.value = current to error
            return
        }
        val (clockIn, clockOut) = CorrectionRules.resolve(current.date, inTime!!, outTime, zone)
        val adminId = sessionManager.requireSession().employeeId
        viewModelScope.launch {
            runCatching {
                when (current) {
                    is EntryEditor.Edit -> clockRepository.correctAsAdmin(current.entry.id, clockIn, clockOut, reason, adminId)
                    is EntryEditor.Add -> clockRepository.addAsAdmin(employeeId, clockIn, clockOut!!, reason, adminId)
                }
            }.onSuccess {
                editor.value = null to null
                _messages.send("Guardado. La empleada verá el aviso del cambio.")
            }.onFailure { e ->
                editor.value = current to (e.message ?: "No se ha podido guardar")
            }
        }
    }
}
