package com.salon.fichajes.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import com.salon.fichajes.data.local.dao.AbsenceDao
import com.salon.fichajes.data.local.dao.WeekApprovalDao
import com.salon.fichajes.data.local.entity.AbsenceEntity
import com.salon.fichajes.data.local.entity.WeekApprovalEntity
import com.salon.fichajes.data.local.dao.ClockEntryDao
import com.salon.fichajes.data.local.dao.EmployeeDao
import com.salon.fichajes.data.local.dao.OwnerCredentialDao
import com.salon.fichajes.data.local.dao.ShiftDao
import com.salon.fichajes.data.local.dao.VacationDao
import com.salon.fichajes.data.local.entity.ClockEntryEntity
import com.salon.fichajes.data.local.entity.EmployeeEntity
import com.salon.fichajes.data.local.entity.OwnerCredentialEntity
import com.salon.fichajes.data.local.entity.ShiftEntity
import com.salon.fichajes.data.local.entity.VacationRequestEntity

@Database(
    entities = [
        EmployeeEntity::class,
        ClockEntryEntity::class,
        ShiftEntity::class,
        VacationRequestEntity::class,
        OwnerCredentialEntity::class,
        AbsenceEntity::class,
        WeekApprovalEntity::class,
    ],
    version = 4,
    exportSchema = true,
    // Room genera las migraciones a partir de los esquemas exportados en app/schemas,
    // así no se pierde ningún fichaje al actualizar la app.
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // columnas de auditoría en clock_entries
        AutoMigration(from = 2, to = 3, spec = AppDatabase.RolesMigration::class), // roles + contraseña maestra
        AutoMigration(from = 3, to = 4), // descansos, bajas y aprobación semanal de horas
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun employeeDao(): EmployeeDao
    abstract fun clockEntryDao(): ClockEntryDao
    abstract fun shiftDao(): ShiftDao
    abstract fun vacationDao(): VacationDao
    abstract fun ownerCredentialDao(): OwnerCredentialDao
    abstract fun absenceDao(): AbsenceDao
    abstract fun weekApprovalDao(): WeekApprovalDao

    /**
     * v2 → v3: el antiguo rol ADMIN pasa a ENCARGADA y la administradora más antigua activa pasa a DUEÑA.
     * Como en v2 no había contraseña maestra, la app se la pedirá a la dueña en su siguiente inicio de sesión.
     */
    class RolesMigration : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE employees SET role = 'MANAGER' WHERE role = 'ADMIN'")
            db.execSQL(
                "UPDATE employees SET role = 'OWNER' WHERE id = " +
                    "(SELECT MIN(id) FROM employees WHERE role = 'MANAGER' AND active = 1)"
            )
        }
    }

    companion object {
        /** Se guarda en la memoria interna privada de la app (/data/data/<paquete>/databases). */
        const val NAME = "fichapp.db"
    }
}
