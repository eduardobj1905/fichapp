package com.salon.fichajes.core.time

import com.salon.fichajes.domain.model.ClockEntry
import java.time.Duration
import java.time.Instant

object WorkedTime {

    /**
     * Suma el solape de cada tramo con [range]. Un tramo abierto (clockOut == null) cuenta hasta [now].
     * Un tramo que cruza medianoche se reparte correctamente entre los dos días.
     */
    fun between(entries: List<ClockEntry>, range: TimeRange, now: Instant): Duration =
        entries.fold(Duration.ZERO) { acc, entry ->
            acc + overlap(entry.clockIn, entry.clockOut ?: now, range)
        }

    fun overlap(start: Instant, end: Instant, range: TimeRange): Duration {
        val from = maxOf(start, range.start)
        val to = minOf(end, range.end)
        return if (to.isAfter(from)) Duration.between(from, to) else Duration.ZERO
    }
}
