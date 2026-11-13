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
) {
    val isOpen: Boolean get() = clockOut == null
    val isAdminModified: Boolean get() = source != ClockSource.TABLET_PIN
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
}

data class Shift(
    val id: Long,
    val employeeId: Long,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
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
