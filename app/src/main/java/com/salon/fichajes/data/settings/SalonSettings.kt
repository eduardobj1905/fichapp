package com.salon.fichajes.data.settings

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Ajustes sencillos del salón (de momento, el personal mínimo por día). */
@Singleton
class SalonSettings @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("salon_settings", Context.MODE_PRIVATE)

    private val _minStaff = MutableStateFlow(prefs.getInt(KEY_MIN_STAFF, DEFAULT_MIN_STAFF))
    val minStaff: StateFlow<Int> = _minStaff.asStateFlow()

    fun setMinStaff(value: Int) {
        val clamped = value.coerceIn(0, 50)
        prefs.edit { putInt(KEY_MIN_STAFF, clamped) }
        _minStaff.value = clamped
    }

    private companion object {
        const val KEY_MIN_STAFF = "min_staff"
        const val DEFAULT_MIN_STAFF = 2
    }
}
