package com.salon.fichajes.domain.staffing

import com.salon.fichajes.core.time.Periods
import com.salon.fichajes.domain.model.VacationRequest
import com.salon.fichajes.domain.model.VacationStatus
import java.time.LocalDate
import java.time.YearMonth

data class DayStaffing(
    val date: LocalDate,
    val total: Int,
    val onVacation: Int,
) {
    val available: Int get() = total - onVacation

    /** 1.0 = todo el equipo disponible, 0.0 = nadie. */
    val ratio: Float get() = if (total == 0) 0f else available.toFloat() / total
}

/**
 * Disponibilidad por día del mes: personal activo menos quien tiene vacaciones APROBADAS ese día.
 * Solo cuenta solicitudes de personal activo, y cada persona una vez por día aunque tenga solicitudes solapadas.
 */
fun staffingByDay(
    month: YearMonth,
    activeStaff: Set<Long>,
    approved: List<VacationRequest>,
): List<DayStaffing> = staffingBetween(month.atDay(1), month.atEndOfMonth(), activeStaff, approved)

fun staffingBetween(
    from: LocalDate,
    to: LocalDate,
    activeStaff: Set<Long>,
    approved: List<VacationRequest>,
): List<DayStaffing> {
    val relevant = approved.filter {
        it.status == VacationStatus.APPROVED && it.employeeId in activeStaff && it.overlaps(from, to)
    }
    return Periods.daysInclusive(from, to).map { day ->
        val away = relevant.filter { day in it.startDate..it.endDate }.map { it.employeeId }.toSet()
        DayStaffing(date = day, total = activeStaff.size, onVacation = away.size)
    }.toList()
}

/** Días en los que, si se aprueba [candidate], quedaría menos personal que [minStaff]. */
fun daysBelowMinimumIfApproved(
    candidate: VacationRequest,
    activeStaff: Set<Long>,
    approved: List<VacationRequest>,
    minStaff: Int,
): List<DayStaffing> {
    val simulated = approved + candidate.copy(status = VacationStatus.APPROVED)
    return staffingBetween(candidate.startDate, candidate.endDate, activeStaff, simulated)
        .filter { it.available < minStaff }
}
