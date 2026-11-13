package com.salon.fichajes.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.salon.fichajes.domain.model.ClockEntry
import com.salon.fichajes.domain.model.ClockSource
import com.salon.fichajes.domain.model.Employee
import com.salon.fichajes.domain.model.Role
import com.salon.fichajes.domain.model.Shift
import com.salon.fichajes.domain.model.VacationRequest
import com.salon.fichajes.domain.model.VacationStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "employees", indices = [Index("active")])
data class EmployeeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val role: Role,
    val pinHash: String,
    val pinSalt: String,
    val weeklyContractHours: Double,
    val annualVacationDays: Int,
    val colorArgb: Long,
    /** Baja lógica: nunca se borra una empleada (sus fichajes tienen FK RESTRICT). */
    val active: Boolean = true,
)

/**
 * Registro de jornada (art. 34.9 ET, RD-ley 8/2019): se conserva 4 años y NO se borra.
 * Las correcciones de la dueña se marcan con [source] = ADMIN_CORRECTION, [correctedAt] y [correctionReason].
 */
@Entity(
    tableName = "clock_entries",
    foreignKeys = [
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("employeeId"),
        Index("clockIn"),
        Index(value = ["employeeId", "clockOut"]),
    ],
)
data class ClockEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val clockIn: Instant,
    val clockOut: Instant? = null,
    val source: ClockSource = ClockSource.TABLET_PIN,
    val correctedAt: Instant? = null,
    val correctionReason: String? = null,
    // v2: auditoría de cambios hechos por la jefa.
    /** Lo que se fichó originalmente en la tablet (antes de la primera corrección). */
    val originalClockIn: Instant? = null,
    val originalClockOut: Instant? = null,
    /** Id de la administradora que hizo el último cambio. */
    val correctedBy: Long? = null,
    /** Cuándo vio la empleada el aviso del cambio (null = pendiente de ver). */
    val correctionSeenAt: Instant? = null,
)

@Entity(
    tableName = "shifts",
    foreignKeys = [
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["employeeId", "date"]), Index("date")],
)
data class ShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
)

@Entity(
    tableName = "vacation_requests",
    foreignKeys = [
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("employeeId"), Index("status"), Index(value = ["startDate", "endDate"])],
)
data class VacationRequestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val startDate: LocalDate,
    /** Inclusive. */
    val endDate: LocalDate,
    val status: VacationStatus = VacationStatus.PENDING,
    val requestedAt: Instant,
    val reviewedAt: Instant? = null,
    val adminComment: String? = null,
)

/**
 * Contraseña maestra de la dueña (una sola fila, id = 1). Solo se guardan hashes PBKDF2 con salt:
 * ni la contraseña ni el código de recuperación se pueden leer de la base de datos.
 */
@Entity(tableName = "owner_credentials")
data class OwnerCredentialEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val passwordHash: String,
    val passwordSalt: String,
    val recoveryHash: String,
    val recoverySalt: String,
    val updatedAt: Instant,
) {
    companion object {
        const val SINGLE_ROW_ID = 1
    }
}

// --- Mappers entidad -> dominio (el hash del PIN nunca sale de la capa de datos) ---

fun EmployeeEntity.toDomain() = Employee(
    id = id,
    name = name,
    role = role,
    weeklyContractHours = weeklyContractHours,
    annualVacationDays = annualVacationDays,
    colorArgb = colorArgb,
    active = active,
)

fun ClockEntryEntity.toDomain() = ClockEntry(
    id = id,
    employeeId = employeeId,
    clockIn = clockIn,
    clockOut = clockOut,
    source = source,
    correctedAt = correctedAt,
    correctionReason = correctionReason,
    originalClockIn = originalClockIn,
    originalClockOut = originalClockOut,
    correctedBy = correctedBy,
    correctionSeenAt = correctionSeenAt,
)

fun ShiftEntity.toDomain() = Shift(id = id, employeeId = employeeId, date = date, start = start, end = end)

fun Shift.toEntity() = ShiftEntity(id = id, employeeId = employeeId, date = date, start = start, end = end)

fun VacationRequestEntity.toDomain() = VacationRequest(
    id = id,
    employeeId = employeeId,
    startDate = startDate,
    endDate = endDate,
    status = status,
    requestedAt = requestedAt,
    reviewedAt = reviewedAt,
    adminComment = adminComment,
)
