package com.salon.fichajes.ui.employee.clock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salon.fichajes.core.format.formatDateTime
import com.salon.fichajes.core.format.formatHoursMinutes
import com.salon.fichajes.core.format.formatTime
import com.salon.fichajes.core.time.Periods
import com.salon.fichajes.core.time.WorkedTime
import com.salon.fichajes.core.time.tickerFlow
import com.salon.fichajes.domain.model.ClockEntry
import com.salon.fichajes.domain.model.ClockResult
import com.salon.fichajes.domain.model.Employee
import com.salon.fichajes.domain.repository.ClockRepository
import com.salon.fichajes.domain.repository.EmployeeRepository
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
) {
    val isWorking: Boolean get() = openEntry != null
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

    // Equipo (para poner nombre a quien corrigió) + cambios de la jefa aún no vistos.
    private val staffAndCorrections = combine(
        employeeRepository.observeActive(),
        clockRepository.observeUnseenCorrections(employeeId),
    ) { staff, unseen -> staff to unseen }

    val uiState: StateFlow<ClockUiState> = combine(
        staffAndCorrections,
        clockRepository.observeRecent(employeeId, limit = 15),
        weekEntries,
        isProcessing,
        ticker,
    ) { (staff, unseen), recent, week, processing, now ->
        val today = now.atZone(zone).toLocalDate()
        val open = recent.firstOrNull { it.isOpen }
        val employee = staff.firstOrNull { it.id == employeeId }
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
            weekTarget = Duration.ofMinutes(((employee?.weeklyContractHours ?: 0.0) * 60).toLong()),
            recent = recent,
            isProcessing = processing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClockUiState())

    fun onClockButton() {
        // Anti doble toque: si ya hay un fichaje en marcha (o se acaba de fichar), se ignora.
        if (isProcessing.value) return
        isProcessing.value = true
        viewModelScope.launch {
            val result = runCatching { clockRepository.toggle(employeeId) }
            result.onSuccess { r ->
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
            }.onFailure {
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
