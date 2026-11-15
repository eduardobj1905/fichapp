package com.salon.fichajes.domain.staffing

import com.salon.fichajes.domain.model.VacationRequest
import com.salon.fichajes.domain.model.VacationStatus
import com.salon.fichajes.domain.vacation.vacationBalance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class StaffingTest {

    private val staff = setOf(1L, 2L, 3L, 4L)
    private val month = YearMonth.of(2026, 8)

    private fun vacation(
        employeeId: Long,
        from: String,
        to: String,
        status: VacationStatus = VacationStatus.APPROVED,
        id: Long = employeeId,
    ) = VacationRequest(
        id = id,
        employeeId = employeeId,
        startDate = LocalDate.parse(from),
        endDate = LocalDate.parse(to),
        status = status,
        requestedAt = Instant.EPOCH,
    )

    private fun List<DayStaffing>.on(day: Int) = first { it.date.dayOfMonth == day }

    @Test
    fun `devuelve un dia por cada dia del mes`() {
        val result = staffingByDay(month, staff, emptyList())
        assertEquals(31, result.size)
        assertTrue(result.all { it.available == 4 && it.total == 4 })
    }

    @Test
    fun `resta solo vacaciones aprobadas y con fin inclusive`() {
        val result = staffingByDay(
            month, staff,
            listOf(
                vacation(1, "2026-08-03", "2026-08-07"),
                vacation(2, "2026-08-05", "2026-08-06", status = VacationStatus.PENDING),
                vacation(3, "2026-08-05", "2026-08-06", status = VacationStatus.REJECTED),
            ),
        )
        assertEquals(4, result.on(2).available)
        assertEquals(3, result.on(3).available)
        assertEquals(3, result.on(7).available) // el último día también es vacaciones
        assertEquals(4, result.on(8).available)
        assertEquals(3, result.on(5).available) // pendiente y rechazada no cuentan
    }

    @Test
    fun `vacaciones que empiezan el mes anterior cuentan desde el dia 1`() {
        val result = staffingByDay(month, staff, listOf(vacation(1, "2026-07-28", "2026-08-02")))
        assertEquals(3, result.on(1).available)
        assertEquals(3, result.on(2).available)
        assertEquals(4, result.on(3).available)
    }

    @Test
    fun `ignora personal inactivo y no cuenta dos veces solicitudes solapadas`() {
        val result = staffingByDay(
            month, staff,
            listOf(
                vacation(1, "2026-08-10", "2026-08-12", id = 10),
                vacation(1, "2026-08-11", "2026-08-13", id = 11),
                vacation(99, "2026-08-10", "2026-08-20"), // ya no está activa
            ),
        )
        assertEquals(3, result.on(11).available)
        assertEquals(1, result.on(11).onVacation)
    }

    @Test
    fun `avisa de los dias que quedarian por debajo del minimo`() {
        val approved = listOf(
            vacation(1, "2026-08-10", "2026-08-14"),
            vacation(2, "2026-08-13", "2026-08-20"),
        )
        val candidate = vacation(3, "2026-08-12", "2026-08-14", status = VacationStatus.PENDING)

        val below = daysBelowMinimumIfApproved(candidate, staff, approved, minStaff = 2)

        // Con 1, 2 y 3 fuera solo queda 1 persona los días 13 y 14.
        assertEquals(listOf(13, 14), below.map { it.date.dayOfMonth })
        assertTrue(below.all { it.available == 1 })
    }

    @Test
    fun `saldo de vacaciones reparte las solicitudes que cruzan de ano`() {
        val balance = vacationBalance(
            annualDays = 30,
            requests = listOf(
                vacation(1, "2026-12-28", "2027-01-03"), // 4 días en 2026
                vacation(1, "2026-08-01", "2026-08-10", status = VacationStatus.PENDING, id = 2),
                vacation(1, "2026-06-01", "2026-06-05", status = VacationStatus.REJECTED, id = 3),
            ),
            year = 2026,
        )
        assertEquals(4, balance.approved)
        assertEquals(10, balance.pending)
        assertEquals(16, balance.remaining)
    }

    private fun leave(employeeId: Long, from: String, to: String?) = com.salon.fichajes.domain.model.Absence(
        id = employeeId, employeeId = employeeId, type = com.salon.fichajes.domain.model.AbsenceType.SICK_LEAVE,
        startDate = LocalDate.parse(from), endDate = to?.let(LocalDate::parse),
    )

    @Test
    fun `las bajas restan personal y no se cuentan dos veces si coinciden con vacaciones`() {
        val result = staffingByDay(
            month, staff,
            approved = listOf(vacation(1, "2026-08-10", "2026-08-12")),
            absences = listOf(leave(1, "2026-08-11", "2026-08-20"), leave(2, "2026-08-25", null)),
        )
        assertEquals(3, result.on(10).available) // solo vacaciones de 1
        assertEquals(3, result.on(11).available) // 1 de vacaciones y de baja: cuenta una vez
        assertEquals(1, result.on(11).onLeave)
        assertEquals(3, result.on(15).available) // 1 de baja
        assertEquals(3, result.on(31).available) // baja abierta de 2 sigue hasta fin de mes
        assertEquals(4, result.on(22).available)
    }

    @Test
    fun `aviso de minimo tiene en cuenta las bajas`() {
        val candidate = vacation(3, "2026-08-11", "2026-08-11", status = VacationStatus.PENDING)
        val below = daysBelowMinimumIfApproved(
            candidate, staff, approved = emptyList(), minStaff = 3,
            absences = listOf(leave(1, "2026-08-11", "2026-08-11")),
        )
        assertEquals(listOf(11), below.map { it.date.dayOfMonth })
    }
}
