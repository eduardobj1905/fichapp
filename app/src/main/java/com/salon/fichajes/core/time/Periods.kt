package com.salon.fichajes.core.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Intervalo semiabierto [start, end) en tiempo absoluto (UTC). */
data class TimeRange(val start: Instant, val end: Instant) {
    init {
        require(!end.isBefore(start)) { "end ($end) no puede ser anterior a start ($start)" }
    }

    operator fun contains(instant: Instant): Boolean = !instant.isBefore(start) && instant.isBefore(end)
}

/**
 * Convierte periodos "humanos" (día, semana, mes en hora local del salón) a rangos de Instant.
 * Se calcula con atStartOfDay(zone) para que los cambios de hora (DST) den días reales de 23 h o 25 h.
 */
object Periods {

    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun day(date: LocalDate, zone: ZoneId): TimeRange =
        TimeRange(date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant())

    /** Semana de lunes a domingo que contiene [date]. */
    fun week(date: LocalDate, zone: ZoneId): TimeRange {
        val monday = weekStart(date)
        return TimeRange(monday.atStartOfDay(zone).toInstant(), monday.plusWeeks(1).atStartOfDay(zone).toInstant())
    }

    fun month(month: YearMonth, zone: ZoneId): TimeRange =
        TimeRange(
            month.atDay(1).atStartOfDay(zone).toInstant(),
            month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant(),
        )

    /** Días de [from] a [to], ambos inclusive. */
    fun daysInclusive(from: LocalDate, to: LocalDate): Sequence<LocalDate> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
}
