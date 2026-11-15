package com.salon.fichajes.data.repository

import com.salon.fichajes.core.security.PinHasher
import com.salon.fichajes.core.time.TimeRange
import com.salon.fichajes.data.local.dao.ClockEntryDao
import com.salon.fichajes.data.local.dao.EmployeeDao
import com.salon.fichajes.data.local.dao.ShiftDao
import com.salon.fichajes.data.local.dao.VacationDao
import com.salon.fichajes.data.local.entity.EmployeeEntity
import com.salon.fichajes.data.local.entity.VacationRequestEntity
import com.salon.fichajes.data.local.entity.toDomain
import com.salon.fichajes.data.local.entity.toEntity
import com.salon.fichajes.domain.auth.PermissionDeniedException
import com.salon.fichajes.domain.auth.Permissions
import com.salon.fichajes.domain.clock.CorrectionRules
import com.salon.fichajes.domain.model.ClockEntry
import com.salon.fichajes.domain.model.ClockResult
import com.salon.fichajes.domain.model.ClockRules.MAX_OPEN_ENTRY
import com.salon.fichajes.domain.model.Employee
import com.salon.fichajes.domain.model.Role
import com.salon.fichajes.domain.model.Shift
import com.salon.fichajes.domain.model.VacationRequest
import com.salon.fichajes.domain.model.VacationStatus
import com.salon.fichajes.domain.repository.ClockRepository
import com.salon.fichajes.domain.repository.EmployeeRepository
import com.salon.fichajes.domain.repository.ScheduleRepository
import com.salon.fichajes.domain.repository.VacationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprueba permisos en la capa de datos: aunque un fallo de la UI dejara ver un botón,
 * la operación se rechaza aquí.
 */
@Singleton
class PermissionChecker @Inject constructor(private val employeeDao: EmployeeDao) {

    suspend fun actor(actorId: Long): EmployeeEntity =
        employeeDao.getById(actorId)?.takeIf { it.active }
            ?: throw PermissionDeniedException("Sesión no válida. Vuelve a entrar con tu PIN.")

    /** Gestionar fichajes, turnos o vacaciones de [targetEmployeeId]. */
    suspend fun requireCanManage(actorId: Long, targetEmployeeId: Long) {
        val actor = actor(actorId)
        if (!Permissions.canManageStaffMember(actor.role, actor.id, targetEmployeeId)) {
            throw PermissionDeniedException(
                if (actor.role == Role.MANAGER) "No puedes modificar lo tuyo: pídeselo a la dueña"
                else "No tienes permiso para hacer esto"
            )
        }
    }

    suspend fun requireOwner(actorId: Long): EmployeeEntity {
        val actor = actor(actorId)
        if (!Permissions.canAdministerTeam(actor.role)) throw PermissionDeniedException("Solo la dueña puede hacer esto")
        return actor
    }
}

@Singleton
class RoomClockRepository @Inject constructor(
    private val dao: ClockEntryDao,
    private val permissions: PermissionChecker,
    private val clock: Clock,
) : ClockRepository {

    // La hora sale del Clock inyectado (nunca Instant.now()): en tests se usa Clock.fixed
    // y, al migrar a Firestore, la hora autoritativa será serverTimestamp().
    override suspend fun toggle(employeeId: Long): ClockResult =
        dao.toggle(employeeId, clock.instant(), MAX_OPEN_ENTRY, clock.zone)

    override suspend fun startBreak(employeeId: Long): ClockResult =
        dao.startBreak(employeeId, clock.instant(), MAX_OPEN_ENTRY)

    override suspend fun finishDayFromBreak(employeeId: Long) {
        val latest = dao.findLatest(employeeId) ?: return
        if (latest.endedForBreak && latest.clockOut != null) dao.markBreakAsFinalExit(latest.id)
    }

    override fun observeRecent(employeeId: Long, limit: Int): Flow<List<ClockEntry>> =
        dao.observeRecent(employeeId, limit).map { list -> list.map { it.toDomain() } }

    override fun observeOverlapping(employeeId: Long, range: TimeRange): Flow<List<ClockEntry>> =
        dao.observeOverlapping(employeeId, range.start, range.end).map { list -> list.map { it.toDomain() } }

    override fun observeAllOverlapping(range: TimeRange): Flow<List<ClockEntry>> =
        dao.observeAllOverlapping(range.start, range.end).map { list -> list.map { it.toDomain() } }

    override fun observeOpenEntries(): Flow<List<ClockEntry>> =
        dao.observeOpen().map { list -> list.map { it.toDomain() } }

    override suspend fun getAllOverlapping(range: TimeRange): List<ClockEntry> =
        dao.getAllOverlapping(range.start, range.end).map { it.toDomain() }

    override suspend fun correctAsAdmin(entryId: Long, clockIn: Instant, clockOut: Instant?, reason: String, actorId: Long) {
        val entry = requireNotNull(dao.getById(entryId)) { "El fichaje no existe" }
        permissions.requireCanManage(actorId, entry.employeeId)
        val now = clock.instant()
        CorrectionRules.validate(clockIn, clockOut, reason, now)?.let { throw IllegalArgumentException(it) }
        dao.correct(entryId, clockIn, clockOut, reason.trim(), actorId, now)
    }

    override suspend fun addAsAdmin(employeeId: Long, clockIn: Instant, clockOut: Instant, reason: String, actorId: Long): Long {
        permissions.requireCanManage(actorId, employeeId)
        val now = clock.instant()
        CorrectionRules.validate(clockIn, clockOut, reason, now)?.let { throw IllegalArgumentException(it) }
        return dao.addByAdmin(employeeId, clockIn, clockOut, reason.trim(), actorId, now)
    }

    override fun observeUnseenCorrections(employeeId: Long): Flow<List<ClockEntry>> =
        dao.observeUnseenCorrections(employeeId).map { list -> list.map { it.toDomain() } }

    override suspend fun markCorrectionsSeen(employeeId: Long) =
        dao.markCorrectionsSeen(employeeId, clock.instant())
}

@Singleton
class RoomEmployeeRepository @Inject constructor(
    private val dao: EmployeeDao,
    private val permissions: PermissionChecker,
) : EmployeeRepository {

    override fun observeActive(): Flow<List<Employee>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observe(id: Long): Flow<Employee?> = dao.observeById(id).map { it?.toDomain() }

    override suspend fun getAll(): List<Employee> = dao.getAll().map { it.toDomain() }

    override suspend fun verifyPin(id: Long, pin: String): Boolean {
        val employee = dao.getById(id)?.takeIf { it.active } ?: return false
        return withContext(Dispatchers.Default) { PinHasher.verify(pin, employee.pinSalt, employee.pinHash) }
    }

    override suspend fun create(
        actorId: Long,
        name: String,
        role: Role,
        pin: String,
        weeklyContractHours: Double,
        annualVacationDays: Int,
        colorArgb: Long,
    ): Long {
        permissions.requireOwner(actorId)
        require(role in Permissions.ASSIGNABLE_ROLES) { "Solo puede haber una dueña: usa «Ceder el control»" }
        require(name.isNotBlank()) { "El nombre es obligatorio" }
        require(PinHasher.isValidPin(pin)) { "El PIN debe tener 4 dígitos" }
        val salt = PinHasher.newSalt()
        val hash = withContext(Dispatchers.Default) { PinHasher.hash(pin, salt) }
        return dao.insert(
            EmployeeEntity(
                name = name.trim(),
                role = role,
                pinHash = hash,
                pinSalt = salt,
                weeklyContractHours = weeklyContractHours,
                annualVacationDays = annualVacationDays,
                colorArgb = colorArgb,
            )
        )
    }

    override suspend fun changeRole(actorId: Long, employeeId: Long, role: Role) {
        permissions.requireOwner(actorId)
        require(role in Permissions.ASSIGNABLE_ROLES) { "Para nombrar otra dueña usa «Ceder el control»" }
        val target = requireNotNull(dao.getById(employeeId)?.takeIf { it.active }) { "Esa persona no existe" }
        require(target.role != Role.OWNER) { "La dueña no puede cambiarse de rol a sí misma" }
        dao.updateRole(employeeId, role)
    }

    override suspend fun resetPin(actorId: Long, employeeId: Long, newPin: String) {
        permissions.requireOwner(actorId)
        require(PinHasher.isValidPin(newPin)) { "El PIN debe tener 4 dígitos" }
        requireNotNull(dao.getById(employeeId)?.takeIf { it.active }) { "Esa persona no existe" }
        val salt = PinHasher.newSalt()
        val hash = withContext(Dispatchers.Default) { PinHasher.hash(newPin, salt) }
        dao.updatePin(employeeId, hash, salt)
    }

    override suspend fun deactivate(actorId: Long, employeeId: Long) {
        permissions.requireOwner(actorId)
        val target = requireNotNull(dao.getById(employeeId)) { "Esa persona no existe" }
        require(target.role != Role.OWNER) { "No se puede dar de baja a la dueña. Antes cede el control." }
        dao.deactivate(employeeId)
    }
}

@Singleton
class RoomVacationRepository @Inject constructor(
    private val dao: VacationDao,
    private val permissions: PermissionChecker,
    private val clock: Clock,
) : VacationRepository {

    override fun observeForEmployee(employeeId: Long): Flow<List<VacationRequest>> =
        dao.observeForEmployee(employeeId).map { list -> list.map { it.toDomain() } }

    override fun observeAll(): Flow<List<VacationRequest>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeApprovedOverlapping(from: LocalDate, to: LocalDate): Flow<List<VacationRequest>> =
        dao.observeApprovedOverlapping(from, to).map { list -> list.map { it.toDomain() } }

    override suspend fun request(employeeId: Long, startDate: LocalDate, endDate: LocalDate): Long {
        require(!endDate.isBefore(startDate)) { "La fecha de fin no puede ser anterior al inicio" }
        require(dao.countActiveOverlapping(employeeId, startDate, endDate) == 0) {
            "Ya tienes una solicitud en esas fechas"
        }
        return dao.insert(
            VacationRequestEntity(
                employeeId = employeeId,
                startDate = startDate,
                endDate = endDate,
                requestedAt = clock.instant(),
            )
        )
    }

    override suspend fun review(actorId: Long, requestId: Long, approve: Boolean, comment: String?) {
        requireNotNull(dao.getById(requestId)) { "La solicitud no existe" }
        if (!Permissions.canReviewVacations(permissions.actor(actorId).role)) {
            throw PermissionDeniedException("Las vacaciones solo las aprueba la dueña")
        }
        val status = if (approve) VacationStatus.APPROVED else VacationStatus.REJECTED
        dao.review(requestId, status, clock.instant(), comment?.trim()?.ifBlank { null })
    }
}

@Singleton
class RoomScheduleRepository @Inject constructor(
    private val dao: ShiftDao,
    private val permissions: PermissionChecker,
) : ScheduleRepository {

    override fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<Shift>> =
        dao.observeBetween(from, to).map { list -> list.map { it.toDomain() } }

    override suspend fun save(actorId: Long, shift: Shift): Long {
        permissions.requireCanManage(actorId, shift.employeeId)
        require(shift.end.isAfter(shift.start)) { "La hora de fin debe ser posterior a la de inicio" }
        require((shift.breakStart == null) == (shift.breakEnd == null)) { "Indica el inicio y el fin del descanso" }
        if (shift.hasBreak) {
            val bs = shift.breakStart!!
            val be = shift.breakEnd!!
            require(be.isAfter(bs)) { "El descanso debe terminar después de empezar" }
            require(!bs.isBefore(shift.start) && !be.isAfter(shift.end)) { "El descanso debe estar dentro del turno" }
        }
        return dao.upsert(shift.toEntity())
    }

    override suspend fun delete(actorId: Long, shiftId: Long) {
        val shift = dao.getById(shiftId) ?: return
        permissions.requireCanManage(actorId, shift.employeeId)
        dao.delete(shiftId)
    }
}
