package com.salon.fichajes.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class PeriodsTest {

    private val zone = ZoneId.of("Europe/Madrid")

    @Test
    fun `la semana empieza en lunes aunque el dia sea domingo`() {
        assertEquals(LocalDate.of(2026, 11, 9), Periods.weekStart(LocalDate.of(2026, 11, 15)))
        assertEquals(LocalDate.of(2026, 11, 9), Periods.weekStart(LocalDate.of(2026, 11, 9)))
    }

    @Test
    fun `semana normal dura 168 horas`() {
        val week = Periods.week(LocalDate.of(2026, 11, 11), zone)
        assertEquals(Duration.ofHours(168), Duration.between(week.start, week.end))
    }

    @Test
    fun `semana con cambio a horario de verano dura 167 horas`() {
        // El 29/03/2026 a las 02:00 en Madrid se adelanta el reloj una hora.
        val week = Periods.week(LocalDate.of(2026, 3, 25), zone)
        assertEquals(Duration.ofHours(167), Duration.between(week.start, week.end))
    }

    @Test
    fun `mes de febrero bisiesto tiene 29 dias`() {
        val month = Periods.month(YearMonth.of(2028, 2), zone)
        assertEquals(Duration.ofDays(29), Duration.between(month.start, month.end))
    }

    @Test
    fun `el rango es semiabierto`() {
        val day = Periods.day(LocalDate.of(2026, 11, 9), zone)
        assertTrue(day.start in day)
        assertFalse(day.end in day)
    }

    @Test
    fun `daysInclusive incluye los dos extremos`() {
        val days = Periods.daysInclusive(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 2)).toList()
        assertEquals(4, days.size)
        assertEquals(LocalDate.of(2027, 1, 2), days.last())
    }
}
