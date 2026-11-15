package com.salon.fichajes.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.salon.fichajes.data.local.entity.AbsenceEntity
import com.salon.fichajes.data.local.entity.WeekApprovalEntity
import com.salon.fichajes.data.local.entity.EmployeeEntity
import com.salon.fichajes.data.local.entity.OwnerCredentialEntity
import com.salon.fichajes.data.local.entity.ShiftEntity
import com.salon.fichajes.data.local.entity.VacationRequestEntity
import com.salon.fichajes.domain.model.Role
import com.salon.fichajes.domain.model.VacationStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface EmployeeDao {
    // Dueña primero, luego encargadas y después el resto, por nombre.
    @Query(
        """
        SELECT * FROM employees WHERE active = 1
        ORDER BY CASE role WHEN 'OWNER' THEN 0 WHEN 'MANAGER' THEN 1 ELSE 2 END, name COLLATE NOCASE
        """
    )
    fun observeActive(): Flow<List<EmployeeEntity>>

    @Query("SELECT * FROM employees WHERE id = :id")
    fun observeById(id: Long): Flow<EmployeeEntity?>

    @Query("SELECT * FROM employees WHERE id = :id")
    suspend fun getById(id: Long): EmployeeEntity?

    /** Incluye las dadas de baja: hacen falta para exportar fichajes antiguos. */
    @Query("SELECT * FROM employees ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<EmployeeEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM employees WHERE role = 'OWNER' AND active = 1)")
    fun observeHasOwner(): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM employees WHERE role = 'OWNER' AND active = 1")
    suspend fun countOwners(): Int

    @Insert
    suspend fun insert(employee: EmployeeEntity): Long

    @Query("UPDATE employees SET active = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)

    @Query("UPDATE employees SET role = :role WHERE id = :id")
    suspend fun updateRole(id: Long, role: Role)

    @Query("UPDATE employees SET pinHash = :pinHash, pinSalt = :pinSalt WHERE id = :id")
    suspend fun updatePin(id: Long, pinHash: String, pinSalt: String)
}

@Dao
interface AbsenceDao {
    @Query(
        """
        SELECT * FROM absences
        WHERE startDate <= :to AND (endDate IS NULL OR endDate >= :from)
        ORDER BY startDate
        """
    )
    fun observeOverlapping(from: LocalDate, to: LocalDate): Flow<List<AbsenceEntity>>

    @Query("SELECT * FROM absences WHERE id = :id")
    suspend fun getById(id: Long): AbsenceEntity?

    @Query(
        """
        SELECT COUNT(*) FROM absences
        WHERE employeeId = :employeeId AND startDate <= :to AND (endDate IS NULL OR endDate >= :from)
        """
    )
    suspend fun countOverlapping(employeeId: Long, from: LocalDate, to: LocalDate): Int

    @Insert
    suspend fun insert(absence: AbsenceEntity): Long

    @Query("UPDATE absences SET endDate = :endDate WHERE id = :id")
    suspend fun setEndDate(id: Long, endDate: LocalDate)

    @Query("DELETE FROM absences WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface WeekApprovalDao {
    @Query("SELECT * FROM week_approvals WHERE weekStart = :weekStart")
    fun observeForWeek(weekStart: LocalDate): Flow<List<WeekApprovalEntity>>

    @Query("SELECT * FROM week_approvals WHERE employeeId = :employeeId ORDER BY weekStart DESC LIMIT :limit")
    fun observeForEmployee(employeeId: Long, limit: Int): Flow<List<WeekApprovalEntity>>

    @Query("SELECT * FROM week_approvals WHERE weekStart BETWEEN :from AND :to")
    suspend fun getBetween(from: LocalDate, to: LocalDate): List<WeekApprovalEntity>

    @Upsert
    suspend fun upsert(approval: WeekApprovalEntity)
}

@Dao
interface OwnerCredentialDao {
    @Query("SELECT * FROM owner_credentials WHERE id = 1")
    suspend fun get(): OwnerCredentialEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM owner_credentials WHERE id = 1)")
    fun observeExists(): Flow<Boolean>

    @Upsert
    suspend fun upsert(credential: OwnerCredentialEntity)
}

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts WHERE date BETWEEN :from AND :to ORDER BY date, start")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<ShiftEntity>>

    @Upsert
    suspend fun upsert(shift: ShiftEntity): Long

    @Query("SELECT * FROM shifts WHERE id = :id")
    suspend fun getById(id: Long): ShiftEntity?

    // Los turnos son planificación, no registro de jornada: sí se pueden borrar.
    @Query("DELETE FROM shifts WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface VacationDao {
    @Query("SELECT * FROM vacation_requests WHERE employeeId = :employeeId ORDER BY startDate DESC")
    fun observeForEmployee(employeeId: Long): Flow<List<VacationRequestEntity>>

    @Query("SELECT * FROM vacation_requests ORDER BY status = 'PENDING' DESC, startDate DESC")
    fun observeAll(): Flow<List<VacationRequestEntity>>

    @Query(
        """
        SELECT * FROM vacation_requests
        WHERE status = 'APPROVED' AND startDate <= :to AND endDate >= :from
        """
    )
    fun observeApprovedOverlapping(from: LocalDate, to: LocalDate): Flow<List<VacationRequestEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM vacation_requests
        WHERE employeeId = :employeeId AND status IN ('PENDING', 'APPROVED')
          AND startDate <= :to AND endDate >= :from
        """
    )
    suspend fun countActiveOverlapping(employeeId: Long, from: LocalDate, to: LocalDate): Int

    @Insert
    suspend fun insert(request: VacationRequestEntity): Long

    @Query("SELECT * FROM vacation_requests WHERE id = :id")
    suspend fun getById(id: Long): VacationRequestEntity?

    @Query(
        """
        UPDATE vacation_requests SET status = :status, reviewedAt = :reviewedAt, adminComment = :comment
        WHERE id = :id AND status = 'PENDING'
        """
    )
    suspend fun review(id: Long, status: VacationStatus, reviewedAt: Instant, comment: String?): Int
}
