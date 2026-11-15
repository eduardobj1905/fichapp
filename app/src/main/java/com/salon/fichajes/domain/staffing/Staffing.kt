package com.salon.fichajes.domain.staffing

import com.salon.fichajes.core.time.Periods
import com.salon.fichajes.domain.model.Absence
import com.salon.fichajes.domain.model.VacationRequest
import com.salon.fichajes.domain.model.VacationStatus
import java.time.LocalDate
import java.time.YearMonth

data class DayStaffing(
    val date: LocalDate,
    val total: Int,
    val onVacation: Int,
    val onLeave: Int = 0,
    /** Personas fuera ese día (si alguien está de vacaciones y de baja a la vez cuenta una vez). */
    val away: Int = onVacation + onLeave,
) {
    val available: Int get() = total - away

    /** 1.0 = todo el equipo disponible, 0.0 = nadie. */
    val ratio: Float get() = if (total == 0) 0f else available.toFloat() / total
}

/**
 * Disponibilidad por día del mes: personal activo menos quien tiene vacaciones APROBADAS o está
 * de baja ese día. Solo cuenta personal activo, y cada persona una vez por día.
 */
fun staffingByDay(
    month: YearMonth,
    activeStaff: Set<Long>,
    approved: List<VacationRequest>,
    absences: List<Absence> = emptyList(),
): List<DayStaffing> = staffingBetween(month.atDay(1), month.atEndOfMonth(), activeStaff, approved, absences)

fun staffingBetween(
    from: LocalDate,
    to: LocalDate,
    activeStaff: Set<Long>,
    approved: List<VacationRequest>,
    absences: List<Absence> = emptyList(),
): List<DayStaffing> {
    val vacations = approved.filter {
        it.status == VacationStatus.APPROVED && it.employeeId in activeStaff && it.overlaps(from, to)
    }
    val leaves = absences.filter { it.employeeId in activeStaff && it.overlaps(from, to) }
    return Periods.daysInclusive(from, to).map { day ->
        val onVacation = vacations.filter { day in it.startDate..it.endDate }.map { it.employeeId }.toSet()
        val onLeave = leaves.filter { it.covers(day) }.map { it.employeeId }.toSet()
        DayStaffing(
            date = day,
            total = activeStaff.size,
            onVacation = onVacation.size,
            onLeave = onLeave.size,
            away = (onVacation + onLeave).size,
        )
    }.toList()
}

/** Días en los que, si se aprueba [candidate], quedaría menos personal que [minStaff]. */
fun daysBelowMinimumIfApproved(
    candidate: VacationRequest,
    activeStaff: Set<Long>,
    approved: List<VacationRequest>,
    minStaff: Int,
    absences: List<Absence> = emptyList(),
): List<DayStaffing> {
    val simulated = approved + candidate.copy(status = VacationStatus.APPROVED)
    return staffingBetween(candidate.startDate, candidate.endDate, activeStaff, simulated, absences)
        .filter { it.available < minStaff }
}
