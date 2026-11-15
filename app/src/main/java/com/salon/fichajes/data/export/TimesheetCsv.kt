package com.salon.fichajes.data.export

import com.salon.fichajes.core.format.SpanishLocale
import com.salon.fichajes.core.time.TimeRange
import com.salon.fichajes.core.time.WorkedTime
import com.salon.fichajes.domain.model.ClockEntry
import com.salon.fichajes.domain.model.ClockSource
import com.salon.fichajes.domain.model.Employee
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Genera la hoja de horas en CSV pensada para abrirla con Excel en español:
 * separador ";", decimales con coma y fin de línea CRLF. El BOM UTF-8 lo añade quien escribe el archivo.
 *
 * Incluye la auditoría completa (origen, quién y cuándo modificó, motivo y horas originales) para
 * que sirva como registro de jornada ante una inspección.
 */
object TimesheetCsv {
    private const val SEP = ";"
    private const val NEW_LINE = "\r\n"
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", SpanishLocale)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", SpanishLocale)
    private val dateTimeFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", SpanishLocale)

    val DETAIL_HEADER = listOf(
        "Empleada", "Fecha", "Entrada", "Salida", "Horas", "Origen",
        "Modificado el", "Modificado por", "Motivo", "Entrada original", "Salida original",
    )

    /**
     * @param employees todas las personas, incluidas las dadas de baja (para poner nombre a fichajes antiguos).
     * @param targets horas objetivo del periodo por empleada (solo personal activo).
     */
    fun build(
        periodLabel: String,
        range: TimeRange,
        zone: ZoneId,
        employees: List<Employee>,
        entries: List<ClockEntry>,
        targets: Map<Long, Duration>,
        now: Instant,
        /** Solo al exportar una semana: estado de la aprobación de horas por empleada. */
        approvalLabels: Map<Long, String>? = null,
    ): String {
        val names = employees.associate { it.id to it.name }
        fun nameOf(id: Long?) = id?.let { names[it] ?: "#$it" }.orEmpty()
        val lines = mutableListOf<List<String>>()

        lines += listOf("Fichapp · Hoja de horas", periodLabel)
        lines += listOf("Generado", dateTimeFormat.format(now.atZone(zone)))
        lines += emptyList<String>()
        lines += DETAIL_HEADER

        val sorted = entries.sortedWith(compareBy<ClockEntry>({ nameOf(it.employeeId).lowercase() }, { it.clockIn }))
        for (entry in sorted) {
            val inZoned = entry.clockIn.atZone(zone)
            val out = entry.clockOut
            val outText = when {
                out == null -> "en curso"
                out.atZone(zone).toLocalDate() != inZoned.toLocalDate() -> timeFormat.format(out.atZone(zone)) + " (+1)"
                else -> timeFormat.format(out.atZone(zone))
            } + if (entry.endedForBreak && out != null) " (descanso)" else ""
            lines += listOf(
                nameOf(entry.employeeId),
                dateFormat.format(inZoned),
                timeFormat.format(inZoned),
                outText,
                hours(WorkedTime.overlap(entry.clockIn, out ?: now, range)),
                sourceLabel(entry.source),
                entry.correctedAt?.let { dateTimeFormat.format(it.atZone(zone)) }.orEmpty(),
                nameOf(entry.correctedBy),
                entry.correctionReason.orEmpty(),
                entry.originalClockIn?.let { dateTimeFormat.format(it.atZone(zone)) }.orEmpty(),
                entry.originalClockOut?.let { dateTimeFormat.format(it.atZone(zone)) }.orEmpty(),
            )
        }

        lines += emptyList<String>()
        lines += listOf("RESUMEN")
        lines += listOf("Empleada", "Horas trabajadas", "Horas de contrato", "Diferencia") +
            (if (approvalLabels != null) listOf("Aprobación") else emptyList())
        val byEmployee = entries.groupBy { it.employeeId }
        val ids = (byEmployee.keys + targets.keys).sortedBy { nameOf(it).lowercase() }
        for (id in ids) {
            val worked = WorkedTime.between(byEmployee[id].orEmpty(), range, now)
            val target = targets[id]
            lines += listOf(
                nameOf(id),
                hours(worked),
                target?.let(::hours).orEmpty(),
                target?.let { signedHours(worked - it) }.orEmpty(),
            ) + (if (approvalLabels != null) listOf(approvalLabels[id] ?: "Pendiente de aprobar") else emptyList())
        }

        return lines.joinToString(NEW_LINE, postfix = NEW_LINE) { row -> row.joinToString(SEP) { escape(it) } }
    }

    fun sourceLabel(source: ClockSource): String = when (source) {
        ClockSource.TABLET_PIN -> "Tablet"
        ClockSource.ADMIN_CORRECTION -> "Corregido por la encargada"
        ClockSource.ADMIN_ENTRY -> "Añadido por la encargada"
    }

    /** "7,50" (horas decimales para poder sumar en Excel). */
    fun hours(duration: Duration): String = String.format(SpanishLocale, "%.2f", duration.toMinutes() / 60.0)

    private fun signedHours(duration: Duration): String =
        (if (duration.isNegative) "-" else "+") + hours(duration.abs())

    /** Comillas si el texto lleva separador, comillas o saltos de línea (RFC 4180). */
    fun escape(value: String): String =
        if (value.contains(SEP) || value.contains('"') || value.contains('\n') || value.contains('\r')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value
}
