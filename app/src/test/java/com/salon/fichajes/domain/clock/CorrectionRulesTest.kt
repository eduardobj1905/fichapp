package com.salon.fichajes.domain.clock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class CorrectionRulesTest {

    private val zone = ZoneId.of("Europe/Madrid")
    private fun at(text: String): Instant = LocalDateTime.parse(text).atZone(zone).toInstant()
    private val now = at("2026-11-13T12:00")

    @Test
    fun `correccion valida`() {
        assertNull(CorrectionRules.validate(at("2026-11-12T09:00"), at("2026-11-12T14:00"), "Olvidó fichar", now))
    }

    @Test
    fun `exige motivo`() {
        assertNotNull(CorrectionRules.validate(at("2026-11-12T09:00"), at("2026-11-12T14:00"), "  ", now))
    }

    @Test
    fun `rechaza salida anterior, futuro y tramos de mas de 16 h`() {
        assertNotNull(CorrectionRules.validate(at("2026-11-12T14:00"), at("2026-11-12T09:00"), "x", now))
        assertNotNull(CorrectionRules.validate(at("2026-11-13T09:00"), at("2026-11-13T18:00"), "x", now))
        assertNotNull(CorrectionRules.validate(at("2026-11-11T06:00"), at("2026-11-11T23:00"), "x", now))
    }

    @Test
    fun `tramo abierto valido si la entrada no es futura`() {
        assertNull(CorrectionRules.validate(at("2026-11-13T09:00"), null, "Llegó antes", now))
    }

    @Test
    fun `salida anterior a la entrada se interpreta como dia siguiente`() {
        val (clockIn, clockOut) = CorrectionRules.resolve(LocalDate.of(2026, 11, 10), LocalTime.of(22, 0), LocalTime.of(1, 0), zone)
        assertEquals(at("2026-11-10T22:00"), clockIn)
        assertEquals(at("2026-11-11T01:00"), clockOut)
    }
}
