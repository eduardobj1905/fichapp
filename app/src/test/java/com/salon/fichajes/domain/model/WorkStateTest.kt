package com.salon.fichajes.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class WorkStateTest {

    private val zone = ZoneId.of("Europe/Madrid")
    private fun at(text: String): Instant = LocalDateTime.parse(text).atZone(zone).toInstant()

    @Test
    fun `sin fichajes esta fuera`() {
        assertEquals(WorkState.Off, WorkState.from(null, at("2026-11-13T10:00"), zone))
    }

    @Test
    fun `tramo abierto es trabajando`() {
        val entry = ClockEntry(1, 1, at("2026-11-13T09:00"), null)
        assertTrue(WorkState.from(entry, at("2026-11-13T10:00"), zone) is WorkState.Working)
    }

    @Test
    fun `salida a descanso hoy es estar en el descanso`() {
        val entry = ClockEntry(1, 1, at("2026-11-13T09:00"), at("2026-11-13T14:00"), endedForBreak = true)
        val state = WorkState.from(entry, at("2026-11-13T14:20"), zone)
        assertEquals(WorkState.OnBreak(at("2026-11-13T14:00"), entry), state)
    }

    @Test
    fun `un descanso de ayer sin volver ya no cuenta`() {
        val entry = ClockEntry(1, 1, at("2026-11-12T09:00"), at("2026-11-12T14:00"), endedForBreak = true)
        assertEquals(WorkState.Off, WorkState.from(entry, at("2026-11-13T09:00"), zone))
    }

    @Test
    fun `salida normal es estar fuera`() {
        val entry = ClockEntry(1, 1, at("2026-11-13T09:00"), at("2026-11-13T14:00"))
        assertEquals(WorkState.Off, WorkState.from(entry, at("2026-11-13T15:00"), zone))
    }

    @Test
    fun `turno con descanso descuenta el descanso de las horas planificadas`() {
        val shift = Shift(1, 1, LocalDate.of(2026, 11, 13), LocalTime.of(10, 0), LocalTime.of(19, 0), LocalTime.of(14, 0), LocalTime.of(15, 0))
        assertEquals(Duration.ofHours(8), shift.plannedWork)
        assertEquals(Duration.ofHours(1), shift.breakDuration)
    }
}
