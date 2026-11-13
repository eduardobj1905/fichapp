package com.salon.fichajes.data.local

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * - Instant  -> Long epochMilli (momento absoluto UTC; la zona horaria solo se aplica al mostrar).
 * - LocalDate -> Long epochDay (fechas "de calendario": vacaciones, turnos).
 * - LocalTime -> Int segundos del día (hora de un turno).
 * Al ser números, las consultas por rango (<, >) funcionan directamente en SQLite.
 */
class Converters {
    @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter fun localDateToLong(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun longToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter fun localTimeToInt(value: LocalTime?): Int? = value?.toSecondOfDay()
    @TypeConverter fun intToLocalTime(value: Int?): LocalTime? = value?.let { LocalTime.ofSecondOfDay(it.toLong()) }
}
