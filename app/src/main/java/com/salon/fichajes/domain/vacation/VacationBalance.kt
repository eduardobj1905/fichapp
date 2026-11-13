package com.salon.fichajes.domain.vacation

import com.salon.fichajes.domain.model.VacationRequest
import com.salon.fichajes.domain.model.VacationStatus
import java.time.LocalDate

/** Días naturales de vacaciones de un año. Las solicitudes que cruzan de año se reparten. */
data class VacationBalance(val annual: Int, val approved: Int, val pending: Int) {
    val remaining: Int get() = annual - approved - pending
}

fun vacationBalance(annualDays: Int, requests: List<VacationRequest>, year: Int): VacationBalance {
    val yearStart = LocalDate.of(year, 1, 1)
    val yearEnd = LocalDate.of(year, 12, 31)
    fun daysIn(r: VacationRequest): Int {
        val from = maxOf(r.startDate, yearStart)
        val to = minOf(r.endDate, yearEnd)
        return if (to.isBefore(from)) 0 else (to.toEpochDay() - from.toEpochDay() + 1).toInt()
    }
    return VacationBalance(
        annual = annualDays,
        approved = requests.filter { it.status == VacationStatus.APPROVED }.sumOf(::daysIn),
        pending = requests.filter { it.status == VacationStatus.PENDING }.sumOf(::daysIn),
    )
}
