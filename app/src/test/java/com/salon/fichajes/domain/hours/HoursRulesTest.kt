package com.salon.fichajes.domain.hours

import com.salon.fichajes.domain.model.Absence
import com.salon.fichajes.domain.model.AbsenceType
import com.salon.fichajes.domain.model.WeekApproval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class HoursRulesTest {

    private val monday = LocalDate.of(2026, 11, 9)
    private val sunday = monday.plusDays(6)

    private fun absence(from: LocalDate, to: LocalDate?) =
        Absence(id = 1, employeeId = 1, type = AbsenceType.SICK_LEAVE, startDate = from, endDate = to)

    @Test
    fun `semana sin bajas tiene como objetivo las horas de contrato`() {
        assertEquals(Duration.ofHours(40), targetFor(40.0, monday, sunday, emptyList()))
    }

    @Test
    fun `los dias de baja se descuentan del objetivo`() {
        // 2 días de baja de 7: 40 × 5/7 = 28 h 34 min
        val target = targetFor(40.0, monday, sunday, listOf(absence(monday, monday.plusDays(1))))
        assertEquals(Duration.ofMinutes(1714), target)
    }

    @Test
    fun `baja abierta sin fecha de alta cubre el resto del periodo`() {
        val target = targetFor(40.0, monday, sunday, listOf(absence(monday.plusDays(3), null)))
        assertEquals(Duration.ofMinutes((40 * 60 / 7.0 * 3).toLong()), target)
    }

    @Test
    fun `mes completo equivale a semanal por dias del mes entre 7`() {
        val target = targetFor(35.0, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), emptyList())
        assertEquals(Duration.ofMinutes(9000), target) // 35 × 30/7 = 150 h
    }

    @Test
    fun `estado de aprobacion detecta cambios posteriores`() {
        val approval = WeekApproval(1, monday, approvedBy = 2, approvedAt = Instant.EPOCH, workedMinutes = 2400)
        assertEquals(ApprovalStatus.PENDING, approvalStatus(null, Duration.ofMinutes(2400)))
        assertEquals(ApprovalStatus.APPROVED, approvalStatus(approval, Duration.ofMinutes(2400)))
        assertEquals(ApprovalStatus.CHANGED, approvalStatus(approval, Duration.ofMinutes(2430)))
    }

    @Test
    fun `solo se aprueban semanas terminadas`() {
        assertFalse(canApproveWeek(monday, sunday))
        assertTrue(canApproveWeek(monday, sunday.plusDays(1)))
    }
}
