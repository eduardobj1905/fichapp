package com.salon.fichajes.ui.admin.vacations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salon.fichajes.data.settings.SalonSettings
import com.salon.fichajes.domain.auth.Permissions
import com.salon.fichajes.domain.repository.AbsenceRepository
import com.salon.fichajes.session.SessionManager
import com.salon.fichajes.domain.model.Employee
import com.salon.fichajes.domain.model.VacationRequest
import com.salon.fichajes.domain.model.VacationStatus
import com.salon.fichajes.domain.repository.EmployeeRepository
import com.salon.fichajes.domain.repository.VacationRepository
import com.salon.fichajes.domain.staffing.DayStaffing
import com.salon.fichajes.domain.staffing.daysBelowMinimumIfApproved
import com.salon.fichajes.domain.staffing.staffingByDay
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
import java.time.YearMonth
import javax.inject.Inject

/** Aviso antes de aprobar: esos días el salón quedaría por debajo del mínimo. */
data class StaffingWarning(val request: VacationRequest, val days: List<DayStaffing>)

data class AdminVacationsUiState(
    val isLoading: Boolean = true,
    val month: YearMonth = YearMonth.of(2000, 1),
    val calendar: List<DayStaffing> = emptyList(),
    val minStaff: Int = 0,
    val pending: List<VacationRequest> = emptyList(),
    val reviewed: List<VacationRequest> = emptyList(),
    val employees: Map<Long, Employee> = emptyMap(),
    val warning: StaffingWarning? = null,
    val today: LocalDate = LocalDate.MIN,
)

@HiltViewModel
class AdminVacationsViewModel @Inject constructor(
    private val vacationRepository: VacationRepository,
    absenceRepository: AbsenceRepository,
    employeeRepository: EmployeeRepository,
    private val settings: SalonSettings,
    sessionManager: SessionManager,
    private val clock: Clock,
) : ViewModel() {

    private val session = sessionManager.requireSession()

    private val month = MutableStateFlow(YearMonth.now(clock))
    private val warning = MutableStateFlow<StaffingWarning?>(null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private val staff = employeeRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val requests = vacationRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Las bajas también restan personal disponible (son pocas: se observan todas).
    private val absences = absenceRepository.observeOverlapping(LocalDate.of(2000, 1, 1), LocalDate.of(2100, 12, 31))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val requestsAndAbsences = combine(requests, absences) { r, a -> r to a }

    val uiState: StateFlow<AdminVacationsUiState> = combine(
        month, staff, requestsAndAbsences, settings.minStaff, warning,
    ) { m, employees, (all, leaves), minStaff, warn ->
        val activeIds = employees.map { it.id }.toSet()
        AdminVacationsUiState(
            isLoading = false,
            month = m,
            calendar = staffingByDay(m, activeIds, all.filter { it.status == VacationStatus.APPROVED }, leaves),
            minStaff = minStaff,
            pending = all.filter { it.status == VacationStatus.PENDING }.sortedBy { it.startDate },
            reviewed = all.filter { it.status != VacationStatus.PENDING }.take(30),
            employees = employees.associateBy { it.id },
            warning = warn,
            today = LocalDate.now(clock),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminVacationsUiState())

    fun previousMonth() { month.value = month.value.minusMonths(1) }
    fun nextMonth() { month.value = month.value.plusMonths(1) }

    /** El personal mínimo lo decide la dueña (si no, una encargada podría bajarlo para aprobar). */
    val canChangeMinStaff: Boolean get() = Permissions.canAdministerTeam(session.role)

    fun setMinStaff(value: Int) {
        if (canChangeMinStaff) settings.setMinStaff(value)
    }

    /** Las vacaciones solo las aprueba o rechaza la dueña; la encargada solo ve el calendario. */
    val canReview: Boolean get() = Permissions.canReviewVacations(session.role)

    /** Antes de aprobar se simula la aprobación: si deja días por debajo del mínimo, se avisa. */
    fun approve(request: VacationRequest) {
        val activeIds = staff.value.map { it.id }.toSet()
        val approved = requests.value.filter { it.status == VacationStatus.APPROVED }
        val below = daysBelowMinimumIfApproved(request, activeIds, approved, settings.minStaff.value, absences.value)
        if (below.isEmpty()) review(request, approve = true, comment = null)
        else warning.value = StaffingWarning(request, below)
    }

    fun confirmApproveDespiteWarning() {
        val w = warning.value ?: return
        warning.value = null
        review(w.request, approve = true, comment = "Aprobada con personal por debajo del mínimo")
    }

    fun dismissWarning() { warning.value = null }

    fun reject(request: VacationRequest, comment: String?) = review(request, approve = false, comment = comment)

    private fun review(request: VacationRequest, approve: Boolean, comment: String?) {
        viewModelScope.launch {
            runCatching { vacationRepository.review(session.employeeId, request.id, approve, comment) }
                .onSuccess { _messages.send(if (approve) "Vacaciones aprobadas" else "Solicitud rechazada") }
                .onFailure { _messages.send(it.message ?: "No se ha podido guardar") }
        }
    }
}
