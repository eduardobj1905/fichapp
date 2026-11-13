package com.salon.fichajes.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.salon.fichajes.data.local.entity.ClockEntryEntity
import com.salon.fichajes.domain.model.ClockResult
import com.salon.fichajes.domain.model.ClockSource
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant

@Dao
abstract class ClockEntryDao {

    @Query("SELECT * FROM clock_entries WHERE employeeId = :employeeId AND clockOut IS NULL ORDER BY clockIn DESC LIMIT 1")
    abstract suspend fun findOpen(employeeId: Long): ClockEntryEntity?

    @Query("SELECT * FROM clock_entries WHERE id = :id")
    abstract suspend fun getById(id: Long): ClockEntryEntity?

    @Insert
    protected abstract suspend fun insert(entry: ClockEntryEntity): Long

    @Update
    protected abstract suspend fun update(entry: ClockEntryEntity)

    @Query("UPDATE clock_entries SET clockOut = :clockOut WHERE id = :id AND clockOut IS NULL")
    protected abstract suspend fun close(id: Long, clockOut: Instant): Int

    @Query(
        """
        SELECT COUNT(*) FROM clock_entries
        WHERE employeeId = :employeeId AND id != :excludeId
          AND clockIn < :to AND (clockOut IS NULL OR clockOut > :from)
        """
    )
    protected abstract suspend fun countOverlapping(employeeId: Long, excludeId: Long, from: Instant, to: Instant): Int

    /**
     * Leer el tramo abierto y decidir entrada/salida ocurre dentro de UNA transacción:
     * dos toques casi simultáneos no pueden crear dos tramos abiertos.
     */
    @Transaction
    open suspend fun toggle(employeeId: Long, now: Instant, maxOpen: Duration): ClockResult {
        val open = findOpen(employeeId)
            ?: run {
                insert(ClockEntryEntity(employeeId = employeeId, clockIn = now, source = ClockSource.TABLET_PIN))
                return ClockResult.ClockedIn(now)
            }
        val elapsed = Duration.between(open.clockIn, now)
        // Más de maxOpen (olvido de salida) o tiempo negativo (hora del dispositivo cambiada): lo revisa la jefa.
        if (elapsed > maxOpen || elapsed.isNegative) {
            return ClockResult.NeedsAdminReview(open.clockIn)
        }
        close(open.id, now)
        return ClockResult.ClockedOut(now, elapsed)
    }

    /**
     * La jefa modifica un tramo. Nunca se pierde lo fichado: en la primera corrección se copia
     * la entrada/salida original, y el aviso a la empleada se reactiva en cada cambio.
     */
    @Transaction
    open suspend fun correct(
        entryId: Long,
        clockIn: Instant,
        clockOut: Instant?,
        reason: String,
        adminId: Long,
        now: Instant,
    ) {
        val entry = requireNotNull(getById(entryId)) { "El fichaje no existe" }
        require(clockOut != null || entry.clockOut == null) { "Indica la hora de salida" }
        requireNoOverlap(entry.employeeId, entryId, clockIn, clockOut)
        val firstCorrection = entry.source == ClockSource.TABLET_PIN
        update(
            entry.copy(
                clockIn = clockIn,
                clockOut = clockOut,
                source = if (entry.source == ClockSource.ADMIN_ENTRY) ClockSource.ADMIN_ENTRY else ClockSource.ADMIN_CORRECTION,
                correctedAt = now,
                correctionReason = reason,
                correctedBy = adminId,
                correctionSeenAt = null,
                originalClockIn = if (firstCorrection) entry.clockIn else entry.originalClockIn,
                originalClockOut = if (firstCorrection) entry.clockOut else entry.originalClockOut,
            )
        )
    }

    /** La jefa añade un tramo completo que la empleada no fichó. */
    @Transaction
    open suspend fun addByAdmin(
        employeeId: Long,
        clockIn: Instant,
        clockOut: Instant,
        reason: String,
        adminId: Long,
        now: Instant,
    ): Long {
        requireNoOverlap(employeeId, excludeId = 0, from = clockIn, to = clockOut)
        return insert(
            ClockEntryEntity(
                employeeId = employeeId,
                clockIn = clockIn,
                clockOut = clockOut,
                source = ClockSource.ADMIN_ENTRY,
                correctedAt = now,
                correctionReason = reason,
                correctedBy = adminId,
            )
        )
    }

    private suspend fun requireNoOverlap(employeeId: Long, excludeId: Long, from: Instant, to: Instant?) {
        val end = to ?: Instant.ofEpochMilli(Long.MAX_VALUE) // tramo abierto: llega "hasta siempre"
        require(countOverlapping(employeeId, excludeId, from, end) == 0) {
            "Se solapa con otro fichaje de la misma persona"
        }
    }

    @Query("SELECT * FROM clock_entries WHERE employeeId = :employeeId ORDER BY clockIn DESC LIMIT :limit")
    abstract fun observeRecent(employeeId: Long, limit: Int): Flow<List<ClockEntryEntity>>

    /** Tramos que solapan [from, to): un tramo abierto solapa si empezó antes de [to]. */
    @Query(
        """
        SELECT * FROM clock_entries
        WHERE employeeId = :employeeId AND clockIn < :to AND (clockOut IS NULL OR clockOut > :from)
        ORDER BY clockIn
        """
    )
    abstract fun observeOverlapping(employeeId: Long, from: Instant, to: Instant): Flow<List<ClockEntryEntity>>

    @Query(
        """
        SELECT * FROM clock_entries
        WHERE clockIn < :to AND (clockOut IS NULL OR clockOut > :from)
        ORDER BY clockIn
        """
    )
    abstract fun observeAllOverlapping(from: Instant, to: Instant): Flow<List<ClockEntryEntity>>

    @Query(
        """
        SELECT * FROM clock_entries
        WHERE clockIn < :to AND (clockOut IS NULL OR clockOut > :from)
        ORDER BY employeeId, clockIn
        """
    )
    abstract suspend fun getAllOverlapping(from: Instant, to: Instant): List<ClockEntryEntity>

    @Query("SELECT * FROM clock_entries WHERE clockOut IS NULL ORDER BY clockIn")
    abstract fun observeOpen(): Flow<List<ClockEntryEntity>>

    @Query(
        """
        SELECT * FROM clock_entries
        WHERE employeeId = :employeeId AND source != 'TABLET_PIN' AND correctionSeenAt IS NULL
        ORDER BY clockIn DESC
        """
    )
    abstract fun observeUnseenCorrections(employeeId: Long): Flow<List<ClockEntryEntity>>

    @Query(
        """
        UPDATE clock_entries SET correctionSeenAt = :seenAt
        WHERE employeeId = :employeeId AND source != 'TABLET_PIN' AND correctionSeenAt IS NULL
        """
    )
    abstract suspend fun markCorrectionsSeen(employeeId: Long, seenAt: Instant)
}
