package com.salon.fichajes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta del salón: ciruela/rosa empolvado. Sin color dinámico para que la tablet se vea siempre igual.
private val Plum = Color(0xFF6B3A5B)
private val PlumLight = Color(0xFFF3DCEB)
private val Rose = Color(0xFFB4637A)

/** Colores semánticos del fichaje: no dependen del tema para que verde = entrar y rojo = salir siempre. */
val ClockInGreen = Color(0xFF2E7D32)
val ClockOutRed = Color(0xFFC62828)
val WarningAmber = Color(0xFFF9A825)

/** Colores que se asignan a cada empleada (avatar, filas del panel). */
val StaffPalette: List<Long> = listOf(
    0xFF8E24AA, 0xFF3949AB, 0xFF00897B, 0xFFF4511E,
    0xFF6D4C41, 0xFFD81B60, 0xFF7CB342, 0xFF1E88E5,
)

private val LightColors = lightColorScheme(
    primary = Plum,
    onPrimary = Color.White,
    primaryContainer = PlumLight,
    onPrimaryContainer = Color(0xFF2B0F23),
    secondary = Rose,
    onSecondary = Color.White,
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE6B5D3),
    onPrimary = Color(0xFF3F1D35),
    primaryContainer = Plum,
    onPrimaryContainer = PlumLight,
    secondary = Color(0xFFF0B8C6),
)

@Composable
fun FichappTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
