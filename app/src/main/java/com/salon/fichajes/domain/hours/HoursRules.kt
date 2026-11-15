package com.salon.fichajes.domain.hours

import com.salon.fichajes.core.time.Periods
import com.salon.fichajes.domain.model.Absence
import com.salon.fichajes.domain.model.WeekApproval
import java.time.Duration
import java.time.LocalDate

/**
 * Horas objetivo de un periodo: (horas semanales / 7) por cada día del periodo en el que NO
 * está de baja. Semana completa = horas de contrato; mes = semanal × díasDelMes / 7.
 */
fun targetFor(weeklyContractHours: Double, from: LocalDate, to: LocalDate, absences: List<Absence>): Duration {
    val workingDays = Periods.daysInclusive(from, to).count { day -> absences.none { it.covers(day) } }
    return Duration.ofMinutes((weeklyContractHours * 60 / 7 * workingDays).toLong())
}

enum class ApprovalStatus(val label: String) {
    PENDING("Pendiente de aprobar"),
    APPROVED("Aprobada"),
    /** Se aprobó, pero después se corrigió algún fichaje de esa semana. */
    CHANGED("Cambió tras aprobarse"),
}

fun approvalStatus(approval: WeekApproval?, currentWorked: Duration): ApprovalStatus = when {
    approval == null -> ApprovalStatus.PENDING
    approval.workedMinutes == currentWorked.toMinutes() -> ApprovalStatus.APPROVED
    else -> ApprovalStatus.CHANGED
}

/** Solo se aprueban semanas terminadas: el lunes siguiente ya ha llegado. */
fun canApproveWeek(weekStart: LocalDate, today: LocalDate): Boolean = !today.isBefore(weekStart.plusWeeks(1))
