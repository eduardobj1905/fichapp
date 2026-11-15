package com.salon.fichajes.data.export

import android.content.Context
import android.net.Uri
import com.salon.fichajes.core.time.TimeRange
import com.salon.fichajes.core.time.WorkedTime
import com.salon.fichajes.domain.hours.ApprovalStatus
import com.salon.fichajes.domain.hours.approvalStatus
import com.salon.fichajes.domain.repository.ClockRepository
import com.salon.fichajes.domain.repository.EmployeeRepository
import com.salon.fichajes.domain.repository.WeekApprovalRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Escribe la hoja de horas en el archivo que la jefa elige con el selector del sistema
 * (Storage Access Framework): p. ej. la carpeta Descargas de la tablet, un pendrive o Drive.
 * No hace falta pedir permisos de almacenamiento.
 */
@Singleton
class TimesheetExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clockRepository: ClockRepository,
    private val employeeRepository: EmployeeRepository,
    private val weekApprovalRepository: WeekApprovalRepository,
    private val clock: Clock,
) {
    /**
     * Devuelve el número de fichajes exportados. Si [weekStart] no es null (exportación semanal),
     * el resumen incluye si las horas de cada persona están aprobadas y por quién.
     */
    suspend fun export(
        uri: Uri,
        periodLabel: String,
        range: TimeRange,
        targets: Map<Long, Duration>,
        weekStart: LocalDate? = null,
    ): Int {
        val now = clock.instant()
        val entries = clockRepository.getAllOverlapping(range)
        val employees = employeeRepository.getAll()
        val names = employees.associate { it.id to it.name }
        val approvalLabels = weekStart?.let { monday ->
            val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            val byEmployee = entries.groupBy { it.employeeId }
            weekApprovalRepository.getBetween(monday, monday).associate { approval ->
                val worked = WorkedTime.between(byEmployee[approval.employeeId].orEmpty(), range, now)
                val label = when (approvalStatus(approval, worked)) {
                    ApprovalStatus.APPROVED ->
                        "Aprobada por ${names[approval.approvedBy] ?: "?"} el ${dateFormat.format(approval.approvedAt.atZone(clock.zone))}"
                    else -> ApprovalStatus.CHANGED.label
                }
                approval.employeeId to label
            }
        }
        val csv = TimesheetCsv.build(periodLabel, range, clock.zone, employees, entries, targets, now, approvalLabels)
        withContext(Dispatchers.IO) {
            val stream = checkNotNull(context.contentResolver.openOutputStream(uri, "w")) { "No se ha podido abrir el archivo" }
            stream.use {
                it.write(UTF8_BOM) // para que Excel reconozca las tildes
                it.write(csv.toByteArray(Charsets.UTF_8))
            }
        }
        return entries.size
    }

    private companion object {
        val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    }
}
