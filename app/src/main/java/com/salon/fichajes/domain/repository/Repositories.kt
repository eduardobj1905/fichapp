package com.salon.fichajes.domain.repository

import com.salon.fichajes.core.time.TimeRange
import com.salon.fichajes.domain.model.Absence
import com.salon.fichajes.domain.model.AbsenceType
import com.salon.fichajes.domain.model.WeekApproval
import com.salon.fichajes.domain.model.ClockEntry
import com.salon.fichajes.domain.model.ClockResult
import com.salon.fichajes.domain.model.Employee
import com.salon.fichajes.domain.model.Role
import com.salon.fichajes.domain.model.Shift
import com.salon.fichajes.domain.model.VacationRequest
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/*
 * Estas interfaces son la frontera con la persistencia. Los ViewModels solo conocen esto y los
 * modelos de dominio: hoy las implementa Room (data/repository) y mañana podría implementarlas
 * Firestore sin tocar la UI.
 *
 * Las operaciones de gestión reciben `actorId` (quién la hace) y el repositorio comprueba los
 * permisos (domain/auth/Permissions) aunque la UI ya oculte los botones: defensa en profundidad.
 */

interface ClockRepository {
    /** Ficha entrada (o vuelta del descanso) si no hay tramo abierto; si lo hay, ficha salida. */
    suspend fun toggle(employeeId: Long): ClockResult

    /** Cierra el tramo en curso para hacer un descanso. */
    suspend fun startBreak(employeeId: Long): ClockResult

    /** Estaba en el descanso y no vuelve hoy: la salida a descanso pasa a ser el fin de la jornada. */
    suspend fun finishDayFromBreak(employeeId: Long)
    fun observeRecent(employeeId: Long, limit: Int = 20): Flow<List<ClockEntry>>
    fun observeOverlapping(employeeId: Long, range: TimeRange): Flow<List<ClockEntry>>
    fun observeAllOverlapping(range: TimeRange): Flow<List<ClockEntry>>
    fun observeOpenEntries(): Flow<List<ClockEntry>>
    suspend fun getAllOverlapping(range: TimeRange): List<ClockEntry>

    /** Modifica un tramo (o cierra uno abierto). Queda auditado y se avisa a la empleada. */
    suspend fun correctAsAdmin(entryId: Long, clockIn: Instant, clockOut: Instant?, reason: String, actorId: Long)

    /** Añade un tramo que la empleada olvidó fichar. */
    suspend fun addAsAdmin(employeeId: Long, clockIn: Instant, clockOut: Instant, reason: String, actorId: Long): Long

    /** Cambios de gestión que la empleada todavía no ha visto. */
    fun observeUnseenCorrections(employeeId: Long): Flow<List<ClockEntry>>
    suspend fun markCorrectionsSeen(employeeId: Long)
}

interface EmployeeRepository {
    fun observeActive(): Flow<List<Employee>>
    fun observe(id: Long): Flow<Employee?>
    /** Todas, incluidas las dadas de baja. */
    suspend fun getAll(): List<Employee>
    suspend fun verifyPin(id: Long, pin: String): Boolean

    /** Solo la dueña. [role] puede ser EMPLOYEE o MANAGER (dueña solo hay una y se cede). */
    suspend fun create(
        actorId: Long,
        name: String,
        role: Role,
        pin: String,
        weeklyContractHours: Double,
        annualVacationDays: Int,
        colorArgb: Long,
    ): Long

    /** Solo la dueña: ascender a encargada o volver a empleada. */
    suspend fun changeRole(actorId: Long, employeeId: Long, role: Role)

    /** Solo la dueña: por si alguien olvida su PIN. */
    suspend fun resetPin(actorId: Long, employeeId: Long, newPin: String)

    /** Solo la dueña. Baja lógica: los fichajes se conservan. */
    suspend fun deactivate(actorId: Long, employeeId: Long)
}

/** Estado de la configuración inicial de la app. */
enum class OwnerSetupState {
    /** Primer arranque: no hay dueña. */
    NO_OWNER,
    /** Hay dueña pero aún no ha creado su contraseña maestra (datos de una versión anterior). */
    NO_MASTER_PASSWORD,
    READY,
}

/** Perfil maestro: contraseña de la dueña, código de recuperación y traspaso del control. */
interface OwnerSecurityRepository {
    fun observeSetupState(): Flow<OwnerSetupState>

    /** Primer arranque: crea a la dueña con su PIN y su contraseña. Devuelve el código de recuperación. */
    suspend fun setupOwner(name: String, weeklyContractHours: Double, pin: String, password: String): String

    /** Para una dueña que viene de una versión sin contraseña. Devuelve el código de recuperación. */
    suspend fun createMasterPassword(actorId: Long, password: String): String

    suspend fun verifyMasterPassword(password: String): Boolean

    suspend fun changeMasterPassword(actorId: Long, currentPassword: String, newPassword: String)

    /** Si la dueña olvida la contraseña. Gasta el código y devuelve uno nuevo. */
    suspend fun resetWithRecoveryCode(actorId: Long, recoveryCode: String, newPassword: String): String

    /**
     * Cede el control: [newOwnerId] pasa a ser la dueña con SU contraseña y la dueña actual pasa a encargada.
     * Exige la contraseña actual. Devuelve el código de recuperación de la nueva dueña.
     */
    suspend fun transferOwnership(actorId: Long, currentPassword: String, newOwnerId: Long, newOwnerPassword: String): String
}

interface VacationRepository {
    fun observeForEmployee(employeeId: Long): Flow<List<VacationRequest>>
    fun observeAll(): Flow<List<VacationRequest>>
    fun observeApprovedOverlapping(from: LocalDate, to: LocalDate): Flow<List<VacationRequest>>
    suspend fun request(employeeId: Long, startDate: LocalDate, endDate: LocalDate): Long

    /** Solo la dueña aprueba o rechaza vacaciones. */
    suspend fun review(actorId: Long, requestId: Long, approve: Boolean, comment: String?)
}

/** Bajas y otras ausencias (las gestionan la dueña y la encargada, nunca la suya propia). */
interface AbsenceRepository {
    fun observeOverlapping(from: LocalDate, to: LocalDate): Flow<List<Absence>>
    suspend fun add(actorId: Long, employeeId: Long, type: AbsenceType, startDate: LocalDate, endDate: LocalDate?, note: String?): Long
    /** Dar el alta: [lastDay] es el último día de baja. */
    suspend fun end(actorId: Long, absenceId: Long, lastDay: LocalDate)
    /** Solo para corregir una baja puesta por error. */
    suspend fun delete(actorId: Long, absenceId: Long)
}

/** Aprobación de las horas semanales (dueña o encargada, nunca las suyas). */
interface WeekApprovalRepository {
    fun observeForWeek(weekStart: LocalDate): Flow<List<WeekApproval>>
    fun observeForEmployee(employeeId: Long, limit: Int = 8): Flow<List<WeekApproval>>
    suspend fun getBetween(from: LocalDate, to: LocalDate): List<WeekApproval>
    suspend fun approve(actorId: Long, employeeId: Long, weekStart: LocalDate)
}

interface ScheduleRepository {
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<Shift>>
    /** Una encargada no puede tocar su propio horario. */
    suspend fun save(actorId: Long, shift: Shift): Long
    suspend fun delete(actorId: Long, shiftId: Long)
}
