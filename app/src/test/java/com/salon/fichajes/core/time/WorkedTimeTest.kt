package com.salon.fichajes.core.time

import com.salon.fichajes.domain.model.ClockEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class WorkedTimeTest {

    private val zone = ZoneId.of("Europe/Madrid")

    private fun at(text: String): Instant = LocalDateTime.parse(text).atZone(zone).toInstant()

    private fun entry(from: String, to: String?, id: Long = 1) =
        ClockEntry(id = id, employeeId = 1, clockIn = at(from), clockOut = to?.let(::at))

    @Test
    fun `tramo que cruza medianoche se reparte entre los dos dias`() {
        val entries = listOf(entry("2026-03-10T22:00", "2026-03-11T02:30"))
        val now = at("2026-03-12T10:00")

        val day10 = WorkedTime.between(entries, Periods.day(LocalDate.of(2026, 3, 10), zone), now)
        val day11 = WorkedTime.between(entries, Periods.day(LocalDate.of(2026, 3, 11), zone), now)

        assertEquals(Duration.ofHours(2), day10)
        assertEquals(Duration.ofMinutes(150), day11)
    }

    @Test
    fun `tramo abierto cuenta hasta ahora`() {
        val entries = listOf(entry("2026-11-09T09:00", null))
        val now = at("2026-11-09T11:30")

        val worked = WorkedTime.between(entries, Periods.day(LocalDate.of(2026, 11, 9), zone), now)

        assertEquals(Duration.ofMinutes(150), worked)
    }

    @Test
    fun `tramo fuera del rango no suma`() {
        val entries = listOf(entry("2026-11-08T09:00", "2026-11-08T14:00"))
        val now = at("2026-11-09T12:00")

        val worked = WorkedTime.between(entries, Periods.day(LocalDate.of(2026, 11, 9), zone), now)

        assertEquals(Duration.ZERO, worked)
    }

    @Test
    fun `tramo que termina justo al empezar el rango no suma`() {
        val entries = listOf(entry("2026-11-08T20:00", "2026-11-09T00:00"))
        val worked = WorkedTime.between(entries, Periods.day(LocalDate.of(2026, 11, 9), zone), at("2026-11-09T12:00"))
        assertEquals(Duration.ZERO, worked)
    }

    @Test
    fun `tramo abierto con la hora del dispositivo atrasada no resta`() {
        val entries = listOf(entry("2026-11-09T10:00", null))
        val now = at("2026-11-09T09:00") // reloj cambiado hacia atrás
        val worked = WorkedTime.between(entries, Periods.day(LocalDate.of(2026, 11, 9), zone), now)
        assertEquals(Duration.ZERO, worked)
    }

    @Test
    fun `jornada partida suma manana y tarde`() {
        val entries = listOf(
            entry("2026-11-09T09:30", "2026-11-09T14:00", id = 1),
            entry("2026-11-09T16:00", "2026-11-09T20:15", id = 2),
        )
        val worked = WorkedTime.between(entries, Periods.week(LocalDate.of(2026, 11, 11), zone), at("2026-11-11T08:00"))
        assertEquals(Duration.ofMinutes(270 + 255), worked)
    }
}
