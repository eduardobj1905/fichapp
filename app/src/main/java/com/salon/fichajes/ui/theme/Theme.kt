package com.salon.fichajes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/*
 * Identidad visual de salón de peluquería: negro carbón, dorado champán en los detalles y
 * rótulos y el crema del salón. Sin color dinámico para que la tablet se vea siempre igual.
 * Para adaptar la app a otro salón basta con cambiar estos colores y los textos de strings.xml.
 */
private val Charcoal = Color(0xFF1C1B1A)
private val Gold = Color(0xFFB08D57)
private val GoldLight = Color(0xFFD9C29A)
private val Champagne = Color(0xFFF1E6D3)
private val Cream = Color(0xFFFAF7F2)
private val Taupe = Color(0xFF6F655B)

/** Colores semánticos del fichaje: no dependen del tema para que verde = entrar y rojo = salir siempre. */
val ClockInGreen = Color(0xFF2E7D32)
val ClockOutRed = Color(0xFFC62828)
val WarningAmber = Color(0xFFE0A21B)

/** Dorado de la marca, para detalles (logo, líneas). */
val BrandGold = Gold

/** Colores que se asignan a cada persona (avatar, filas del panel): tonos sobrios que combinan con la marca. */
val StaffPalette: List<Long> = listOf(
    0xFF1C1B1A, // carbón (la dueña)
    0xFFB08D57, // dorado
    0xFF8C6D62, // moca
    0xFF7A8B6F, // salvia
    0xFFA0525B, // vino rosado
    0xFF5B6C8F, // azul pizarra
    0xFFC27C4E, // terracota
    0xFF6E5A8A, // malva
)

private val LightColors = lightColorScheme(
    primary = Charcoal,
    onPrimary = Color.White,
    primaryContainer = Champagne,
    onPrimaryContainer = Charcoal,
    secondary = Gold,
    onSecondary = Color.White,
    secondaryContainer = Champagne,
    onSecondaryContainer = Charcoal,
    tertiary = Color(0xFF8A6A3B),
    tertiaryContainer = Color(0xFFF3E7D2),
    onTertiaryContainer = Charcoal,
    background = Cream,
    onBackground = Charcoal,
    surface = Cream,
    onSurface = Charcoal,
    surfaceVariant = Color(0xFFEDE6DC),
    onSurfaceVariant = Taupe,
    // Fondos de tarjetas, diálogos y barras en la gama crema (si no, Material los pone lilas).
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F3ED),
    surfaceContainer = Color(0xFFF3EEE6),
    surfaceContainerHigh = Color(0xFFF0EAE1),
    surfaceContainerHighest = Color(0xFFEAE3D8),
    surfaceBright = Cream,
    surfaceDim = Color(0xFFE3DCD2),
    secondaryFixed = Champagne,
    outline = Color(0xFFB9AE9F),
)

private val DarkColors = darkColorScheme(
    primary = GoldLight,
    onPrimary = Charcoal,
    primaryContainer = Color(0xFF4A3F2E),
    onPrimaryContainer = Champagne,
    secondary = GoldLight,
    onSecondary = Charcoal,
    tertiary = GoldLight,
    tertiaryContainer = Color(0xFF3D3428),
    onTertiaryContainer = Champagne,
    background = Color(0xFF141312),
    surface = Color(0xFF141312),
)

/** Títulos en serif, como el logo del salón; el resto del texto en sans para que se lea bien. */
private val BrandTypography = Typography().let { base ->
    val serif = FontFamily.Serif
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = serif),
        displaySmall = base.displaySmall.copy(fontFamily = serif),
        headlineLarge = base.headlineLarge.copy(fontFamily = serif, fontWeight = FontWeight.Medium),
        headlineMedium = base.headlineMedium.copy(fontFamily = serif, fontWeight = FontWeight.Medium),
        headlineSmall = base.headlineSmall.copy(fontFamily = serif, fontWeight = FontWeight.Medium),
        titleLarge = base.titleLarge.copy(fontFamily = serif, fontWeight = FontWeight.Medium),
    )
}

@Composable
fun FichappTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = BrandTypography,
        content = content,
    )
}
