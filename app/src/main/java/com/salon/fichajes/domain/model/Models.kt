package com.salon.fichajes.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * - OWNER (dueña): perfil maestro, solo hay una. Lo puede todo; las acciones delicadas piden la contraseña maestra.
 * - MANAGER (encargada): gestiona fichajes, horarios y vacaciones de las demás, pero no los suyos.
 * - EMPLOYEE (empleada): ficha y pide vacaciones.
 */
enum class Role(val label: String) {
    OWNER("Dueña"),
    MANAGER("Encargada"),
    EMPLOYEE("Empleada");

    /** Puede entrar al panel de gestión. */
    val canManage: Boolean get() = this != EMPLOYEE
}

/** Una empleada nunca se borra (baja lógica con [active] = false) porque sus fichajes deben conservarse. */
data class Employee(
    val id: Long,
    val name: String,
    val role: Role,
    val weeklyContractHours: Double,
    val annualVacationDays: Int,
    val colorArgb: Long,
    val active: Boolean,
) {
    val initial: String get() = name.trim().firstOrNull()?.uppercase() ?: "?"
}

enum class ClockSource {
    /** Fichado por la empleada en la tablet. */
    TABLET_PIN,
    /** Fichado en la tablet y luego modificado por la jefa. */
    ADMIN_CORRECTION,
    /** Tramo creado por la jefa (p. ej. un día que la empleada olvidó fichar). */
    ADMIN_ENTRY,
}

/**
 * Un tramo entrada/salida. Si [clockOut] es null, la empleada está trabajando ahora mismo.
 * Si la jefa lo modifica, [originalClockIn]/[originalClockOut] guardan lo que se fichó en la tablet
 * y [correctionSeenAt] queda a null hasta que la empleada ve el aviso.
 */
data class ClockEntry(
    val id: Long,
    val employeeId: Long,
    val clockIn: Instant,
    val clockOut: Instant?,
    val source: ClockSource = ClockSource.TABLET_PIN,
    val correctedAt: Instant? = null,
    val correctionReason: String? = null,
    val originalClockIn: Instant? = null,
    val originalClockOut: Instant? = null,
    val correctedBy: Long? = null,
    val correctionSeenAt: Instant? = null,
    /** El tramo se cerró para hacer un descanso (no es la salida de la jornada). */
    val endedForBreak: Boolean = false,
) {
    val isOpen: Boolean get() = clockOut == null
    val isAdminModified: Boolean get() = source != ClockSource.TABLET_PIN
}

/** En qué situación está una empleada ahora mismo, según su último fichaje. */
sealed interface WorkState {
    data object Off : WorkState
    data class Working(val since: Instant, val entry: ClockEntry) : WorkState
    /** Salió a descansar hoy y aún no ha vuelto. */
    data class OnBreak(val since: Instant, val lastEntry: ClockEntry) : WorkState

    companion object {
        /**
         * [latest] es el último tramo de la empleada. Un descanso solo cuenta si empezó HOY:
         * si alguien salió "a descansar" y no volvió, al día siguiente vuelve a ver "Fichar entrada".
         */
        fun from(latest: ClockEntry?, now: Instant, zone: java.time.ZoneId): WorkState {
            if (latest == null) return Off
            val out = latest.clockOut ?: return Working(latest.clockIn, latest)
            val sameDay = out.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()
            return if (latest.endedForBreak && sameDay && !out.isAfter(now)) OnBreak(out, latest) else Off
        }
    }
}

object ClockRules {
    /** A partir de este tiempo abierto, un tramo ya no se cierra solo: lo revisa la dueña. */
    val MAX_OPEN_ENTRY: Duration = Duration.ofHours(14)
}

sealed interface ClockResult {
    data class ClockedIn(val at: Instant) : ClockResult
    data class ClockedOut(val at: Instant, val worked: Duration) : ClockResult

    /** Hay un tramo abierto sospechoso (se olvidó fichar la salida o el reloj se ha tocado): no se cierra solo. */
    data class NeedsAdminReview(val openSince: Instant) : ClockResult

    data class BreakStarted(val at: Instant, val workedBefore: Duration) : ClockResult
    data class BreakEnded(val at: Instant, val breakDuration: Duration) : ClockResult
}

/** Turno planificado. El descanso es opcional y debe quedar dentro del turno. */
data class Shift(
    val id: Long,
    val employeeId: Long,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val breakStart: LocalTime? = null,
    val breakEnd: LocalTime? = null,
) {
    val hasBreak: Boolean get() = breakStart != null && breakEnd != null

    val breakDuration: Duration
        get() = if (hasBreak) Duration.between(breakStart, breakEnd) else Duration.ZERO

    /** Horas planificadas de trabajo (sin el descanso). */
    val plannedWork: Duration get() = Duration.between(start, end) - breakDuration
}

enum class AbsenceType(val label: String) {
    SICK_LEAVE("Baja médica"),
    OTHER("Otra ausencia"),
}

/** Baja u otra ausencia. [endDate] null = sigue de baja (sin fecha de alta todavía). Fechas inclusive. */
data class Absence(
    val id: Long,
    val employeeId: Long,
    val type: AbsenceType,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val note: String? = null,
    val createdBy: Long? = null,
) {
    fun covers(date: LocalDate): Boolean = !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate))

    fun overlaps(from: LocalDate, to: LocalDate): Boolean = !startDate.isAfter(to) && (endDate == null || !endDate.isBefore(from))
}

/**
 * Aprobación de las horas de una semana (de lunes [weekStart] a domingo). Guarda los minutos que
 * había al aprobar: si luego se corrige un fichaje de esa semana, deja de coincidir y hay que revisarla.
 */
data class WeekApproval(
    val employeeId: Long,
    val weekStart: LocalDate,
    val approvedBy: Long,
    val approvedAt: Instant,
    val workedMinutes: Long,
)

enum class VacationStatus { PENDING, APPROVED, REJECTED }

/** [endDate] es inclusive. */
data class VacationRequest(
    val id: Long,
    val employeeId: Long,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val status: VacationStatus,
    val requestedAt: Instant,
    val reviewedAt: Instant? = null,
    val adminComment: String? = null,
) {
    val days: Int get() = (endDate.toEpochDay() - startDate.toEpochDay() + 1).toInt()

    fun overlaps(from: LocalDate, to: LocalDate): Boolean = !startDate.isAfter(to) && !endDate.isBefore(from)
}
