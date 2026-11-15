package com.salon.fichajes.data.repository

import com.salon.fichajes.core.time.Periods
import com.salon.fichajes.core.time.WorkedTime
import com.salon.fichajes.data.local.dao.AbsenceDao
import com.salon.fichajes.data.local.dao.ClockEntryDao
import com.salon.fichajes.data.local.dao.WeekApprovalDao
import com.salon.fichajes.data.local.entity.AbsenceEntity
import com.salon.fichajes.data.local.entity.WeekApprovalEntity
import com.salon.fichajes.data.local.entity.toDomain
import com.salon.fichajes.domain.hours.canApproveWeek
import com.salon.fichajes.domain.model.Absence
import com.salon.fichajes.domain.model.AbsenceType
import com.salon.fichajes.domain.model.WeekApproval
import com.salon.fichajes.domain.repository.AbsenceRepository
import com.salon.fichajes.domain.repository.WeekApprovalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomAbsenceRepository @Inject constructor(
    private val dao: AbsenceDao,
    private val permissions: PermissionChecker,
    private val clock: Clock,
) : AbsenceRepository {

    override fun observeOverlapping(from: LocalDate, to: LocalDate): Flow<List<Absence>> =
        dao.observeOverlapping(from, to).map { list -> list.map { it.toDomain() } }

    override suspend fun add(
        actorId: Long,
        employeeId: Long,
        type: AbsenceType,
        startDate: LocalDate,
        endDate: LocalDate?,
        note: String?,
    ): Long {
        permissions.requireCanManage(actorId, employeeId)
        require(endDate == null || !endDate.isBefore(startDate)) { "El alta no puede ser anterior al inicio de la baja" }
        val until = endDate ?: LocalDate.MAX
        require(dao.countOverlapping(employeeId, startDate, until) == 0) { "Ya tiene una baja o ausencia en esas fechas" }
        return dao.insert(
            AbsenceEntity(
                employeeId = employeeId,
                type = type,
                startDate = startDate,
                endDate = endDate,
                note = note?.trim()?.ifBlank { null },
                createdBy = actorId,
                createdAt = clock.instant(),
            )
        )
    }

    override suspend fun end(actorId: Long, absenceId: Long, lastDay: LocalDate) {
        val absence = requireNotNull(dao.getById(absenceId)) { "La baja no existe" }
        permissions.requireCanManage(actorId, absence.employeeId)
        require(!lastDay.isBefore(absence.startDate)) { "El último día de baja no puede ser anterior a su inicio" }
        dao.setEndDate(absenceId, lastDay)
    }

    override suspend fun delete(actorId: Long, absenceId: Long) {
        val absence = dao.getById(absenceId) ?: return
        permissions.requireCanManage(actorId, absence.employeeId)
        dao.delete(absenceId)
    }
}

@Singleton
class RoomWeekApprovalRepository @Inject constructor(
    private val dao: WeekApprovalDao,
    private val clockDao: ClockEntryDao,
    private val permissions: PermissionChecker,
    private val clock: Clock,
) : WeekApprovalRepository {

    override fun observeForWeek(weekStart: LocalDate): Flow<List<WeekApproval>> =
        dao.observeForWeek(weekStart).map { list -> list.map { it.toDomain() } }

    override fun observeForEmployee(employeeId: Long, limit: Int): Flow<List<WeekApproval>> =
        dao.observeForEmployee(employeeId, limit).map { list -> list.map { it.toDomain() } }

    override suspend fun getBetween(from: LocalDate, to: LocalDate): List<WeekApproval> =
        dao.getBetween(from, to).map { it.toDomain() }

    override suspend fun approve(actorId: Long, employeeId: Long, weekStart: LocalDate) {
        permissions.requireCanManage(actorId, employeeId)
        val zone = clock.zone
        val now = clock.instant()
        val monday = Periods.weekStart(weekStart)
        require(canApproveWeek(monday, now.atZone(zone).toLocalDate())) { "Solo se pueden aprobar semanas ya terminadas" }
        val range = Periods.week(monday, zone)
        val entries = clockDao.getOverlapping(employeeId, range.start, range.end).map { it.toDomain() }
        require(entries.none { it.isOpen }) { "Tiene un fichaje sin cerrar esa semana: corrígelo antes de aprobar" }
        // Guardamos los minutos aprobados: si después cambian, la semana aparecerá como "cambió tras aprobarse".
        val worked = WorkedTime.between(entries, range, now)
        dao.upsert(
            WeekApprovalEntity(
                employeeId = employeeId,
                weekStart = monday,
                approvedBy = actorId,
                approvedAt = now,
                workedMinutes = worked.toMinutes(),
            )
        )
    }
}
