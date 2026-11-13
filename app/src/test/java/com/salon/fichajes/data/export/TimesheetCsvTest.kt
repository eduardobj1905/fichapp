package com.salon.fichajes.data.export

import com.salon.fichajes.core.time.Periods
import com.salon.fichajes.domain.model.ClockEntry
import com.salon.fichajes.domain.model.ClockSource
import com.salon.fichajes.domain.model.Employee
import com.salon.fichajes.domain.model.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class TimesheetCsvTest {

    private val zone = ZoneId.of("Europe/Madrid")
    private fun at(text: String): Instant = LocalDateTime.parse(text).atZone(zone).toInstant()

    private val ana = Employee(1, "Ana", Role.EMPLOYEE, 40.0, 30, 0, active = true)
    private val marta = Employee(2, "Marta", Role.OWNER, 40.0, 30, 0, active = true)
    private val antigua = Employee(3, "Lucía", Role.EMPLOYEE, 20.0, 30, 0, active = false)

    private fun csv(entries: List<ClockEntry>, targets: Map<Long, Duration> = emptyMap()): List<String> =
        TimesheetCsv.build(
            periodLabel = "Noviembre 2026",
            range = Periods.month(YearMonth.of(2026, 11), zone),
            zone = zone,
            employees = listOf(ana, marta, antigua),
            entries = entries,
            targets = targets,
            now = at("2026-11-13T12:00"),
        ).split("\r\n")

    @Test
    fun `fila de detalle con horas decimales con coma`() {
        val lines = csv(listOf(ClockEntry(1, 1, at("2026-11-10T09:00"), at("2026-11-10T14:30"))))
        assertTrue(lines.contains("Ana;10/11/2026;09:00;14:30;5,50;Tablet;;;;;"))
    }

    @Test
    fun `fichaje corregido incluye quien, motivo y horas originales`() {
        val corrected = ClockEntry(
            id = 1, employeeId = 1,
            clockIn = at("2026-11-10T09:00"), clockOut = at("2026-11-10T14:00"),
            source = ClockSource.ADMIN_CORRECTION,
            correctedAt = at("2026-11-11T10:00"),
            correctionReason = "Olvidó fichar; llegó a las 9",
            originalClockIn = at("2026-11-10T11:15"),
            originalClockOut = at("2026-11-10T14:00"),
            correctedBy = 2,
        )
        val row = csv(listOf(corrected)).first { it.startsWith("Ana;") }
        assertEquals(
            "Ana;10/11/2026;09:00;14:00;5,00;Corregido por la encargada;11/11/2026 10:00;Marta;" +
                "\"Olvidó fichar; llegó a las 9\";10/11/2026 11:15;10/11/2026 14:00",
            row,
        )
    }

    @Test
    fun `tramo que cruza medianoche marca la salida con +1 y tramo abierto dice en curso`() {
        val lines = csv(
            listOf(
                ClockEntry(1, 1, at("2026-11-10T22:00"), at("2026-11-11T01:00")),
                ClockEntry(2, 1, at("2026-11-13T09:00"), null),
            )
        )
        assertTrue(lines.any { it.startsWith("Ana;10/11/2026;22:00;01:00 (+1);3,00;") })
        assertTrue(lines.any { it.startsWith("Ana;13/11/2026;09:00;en curso;3,00;") })
    }

    @Test
    fun `resumen con diferencia frente al contrato e incluye personal dado de baja`() {
        val lines = csv(
            entries = listOf(
                ClockEntry(1, 1, at("2026-11-10T09:00"), at("2026-11-10T17:00")),
                ClockEntry(2, 3, at("2026-11-02T10:00"), at("2026-11-02T12:00")),
            ),
            targets = mapOf(1L to Duration.ofHours(10), 2L to Duration.ofHours(4)),
        )
        assertTrue(lines.contains("Ana;8,00;10,00;-2,00"))
        assertTrue(lines.contains("Marta;0,00;4,00;-4,00"))
        assertTrue(lines.contains("Lucía;2,00;;"))
    }

    @Test
    fun `escape duplica comillas`() {
        assertEquals("\"Dijo \"\"hola\"\"\"", TimesheetCsv.escape("Dijo \"hola\""))
        assertEquals("normal", TimesheetCsv.escape("normal"))
    }
}
