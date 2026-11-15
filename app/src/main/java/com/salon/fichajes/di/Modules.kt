package com.salon.fichajes.di

import android.content.Context
import androidx.room.Room
import com.salon.fichajes.data.local.AppDatabase
import com.salon.fichajes.data.local.dao.ClockEntryDao
import com.salon.fichajes.data.local.dao.EmployeeDao
import com.salon.fichajes.data.local.dao.OwnerCredentialDao
import com.salon.fichajes.data.repository.RoomOwnerSecurityRepository
import com.salon.fichajes.domain.repository.OwnerSecurityRepository
import com.salon.fichajes.data.local.dao.ShiftDao
import com.salon.fichajes.data.local.dao.VacationDao
import com.salon.fichajes.data.repository.RoomClockRepository
import com.salon.fichajes.data.repository.RoomEmployeeRepository
import com.salon.fichajes.data.repository.RoomScheduleRepository
import com.salon.fichajes.data.repository.RoomVacationRepository
import com.salon.fichajes.domain.repository.ClockRepository
import com.salon.fichajes.domain.repository.EmployeeRepository
import com.salon.fichajes.domain.repository.ScheduleRepository
import com.salon.fichajes.domain.repository.VacationRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // Sin fallbackToDestructiveMigration: perder el registro de jornada no es aceptable.
            // Cada cambio de esquema necesitará su Migration (los esquemas se exportan en app/schemas).
            .build()

    @Provides fun employeeDao(db: AppDatabase): EmployeeDao = db.employeeDao()
    @Provides fun clockEntryDao(db: AppDatabase): ClockEntryDao = db.clockEntryDao()
    @Provides fun shiftDao(db: AppDatabase): ShiftDao = db.shiftDao()
    @Provides fun vacationDao(db: AppDatabase): VacationDao = db.vacationDao()
    @Provides fun ownerCredentialDao(db: AppDatabase): OwnerCredentialDao = db.ownerCredentialDao()
    @Provides fun absenceDao(db: AppDatabase): com.salon.fichajes.data.local.dao.AbsenceDao = db.absenceDao()
    @Provides fun weekApprovalDao(db: AppDatabase): com.salon.fichajes.data.local.dao.WeekApprovalDao = db.weekApprovalDao()
}

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    /** Reloj del sistema con la zona del dispositivo. En tests se sustituye por Clock.fixed(...). */
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}

/** Aquí se cambia Room por Firestore el día de la migración: una línea por repositorio. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun clockRepository(impl: RoomClockRepository): ClockRepository
    @Binds abstract fun employeeRepository(impl: RoomEmployeeRepository): EmployeeRepository
    @Binds abstract fun vacationRepository(impl: RoomVacationRepository): VacationRepository
    @Binds abstract fun scheduleRepository(impl: RoomScheduleRepository): ScheduleRepository
    @Binds abstract fun ownerSecurityRepository(impl: RoomOwnerSecurityRepository): OwnerSecurityRepository
    @Binds abstract fun absenceRepository(
        impl: com.salon.fichajes.data.repository.RoomAbsenceRepository,
    ): com.salon.fichajes.domain.repository.AbsenceRepository
    @Binds abstract fun weekApprovalRepository(
        impl: com.salon.fichajes.data.repository.RoomWeekApprovalRepository,
    ): com.salon.fichajes.domain.repository.WeekApprovalRepository
}
