package com.salon.fichajes.core.format

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val SpanishLocale: Locale = Locale.forLanguageTag("es-ES")

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", SpanishLocale)
private val shortDateFormatter = DateTimeFormatter.ofPattern("EEE d MMM", SpanishLocale)
private val dateFormatter = DateTimeFormatter.ofPattern("d/MM/yyyy", SpanishLocale)
private val dateTimeFormatter = DateTimeFormatter.ofPattern("d/MM HH:mm", SpanishLocale)

/** "7 h 05 min" */
fun Duration.formatHoursMinutes(): String {
    val totalMinutes = toMinutes().coerceAtLeast(0)
    return String.format(SpanishLocale, "%d h %02d min", totalMinutes / 60, totalMinutes % 60)
}

/** "7,5 h" para totales compactos. */
fun Duration.formatDecimalHours(): String =
    String.format(SpanishLocale, "%.1f h", toMinutes().coerceAtLeast(0) / 60.0)

/** Cronómetro "01:23:45". */
fun Duration.formatStopwatch(): String {
    val s = seconds.coerceAtLeast(0)
    return String.format(SpanishLocale, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

fun Double.formatHours(): String = String.format(SpanishLocale, "%.1f h", this)

// Las fechas se guardan como Instant (UTC) y solo aquí se convierten a la hora local del salón.
fun Instant.formatTime(zone: ZoneId): String = timeFormatter.format(atZone(zone))
fun Instant.formatDateTime(zone: ZoneId): String = dateTimeFormatter.format(atZone(zone))
fun Instant.formatShortDate(zone: ZoneId): String = shortDateFormatter.format(atZone(zone))
fun LocalDate.formatShort(): String = shortDateFormatter.format(this)
fun LocalDate.formatDate(): String = dateFormatter.format(this)
fun LocalTime.formatTime(): String = timeFormatter.format(this)

fun YearMonth.formatMonthYear(): String =
    month.getDisplayName(TextStyle.FULL_STANDALONE, SpanishLocale)
        .replaceFirstChar { it.titlecase(SpanishLocale) } + " " + year

/** Interpreta "9:30", "09:30" o "9.30". Devuelve null si no es una hora válida. */
fun parseTimeOrNull(text: String): LocalTime? {
    val match = Regex("""^\s*(\d{1,2})[:.](\d{2})\s*$""").find(text) ?: return null
    val (h, m) = match.destructured
    return runCatching { LocalTime.of(h.toInt(), m.toInt()) }.getOrNull()
}
