package com.ebad.flightfilters.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ebad.flightfilters.R
import com.ebad.flightfilters.data.ThemeMode

/*
 * Midnight Route Diagram. The app is a metro map fired in enamel: a midnight-blue ground,
 * porcelain station ticks and labels, and one ink per departure line. The chosen line burns
 * brightest and every other line recedes. Porcelain is the same diagram printed on a white
 * enamel plate for daylight.
 */

private object Enamel {
    val midnight = Color(0xFF0E1A3A)
    val midnightLow = Color(0xFF122147)
    val midnightMid = Color(0xFF172853)
    val midnightHigh = Color(0xFF1D305F)
    val midnightHighest = Color(0xFF243A6E)
    val porcelain = Color(0xFFF4F1E8)
    val porcelainDim = Color(0xFFB8C0D8)
    val seam = Color(0xFF34477A)
    val seamStrong = Color(0xFF5C6E9E)

    val plate = Color(0xFFF4F1E8)
    val plateLow = Color(0xFFEDE9DD)
    val plateMid = Color(0xFFE6E1D3)
    val plateHigh = Color(0xFFDED8C8)
    val plateHighest = Color(0xFFD5CEBC)
    val ink = Color(0xFF0E1A3A)
    val inkDim = Color(0xFF3F4A6C)
}

/** One ink per departure line, in the order lines are added. Index 0 is always home. */
@Immutable
data class LineInks(val inks: List<Color>, val amber: Color, val green: Color, val cobalt: Color, val scarlet: Color) {
    fun forIndex(i: Int): Color = inks[((i % inks.size) + inks.size) % inks.size]
}

private val midnightInks = LineInks(
    inks = listOf(
        Color(0xFFE2382B), Color(0xFF3B7BE8), Color(0xFFF2A900), Color(0xFF2E9E57),
        Color(0xFFA06AE0), Color(0xFF22A9A9), Color(0xFFE56FA3), Color(0xFFB7B03A),
    ),
    amber = Color(0xFFF2A900), green = Color(0xFF3DB46A), cobalt = Color(0xFF3B7BE8), scarlet = Color(0xFFE2382B),
)

private val porcelainInks = LineInks(
    inks = listOf(
        Color(0xFFCC2A1E), Color(0xFF1F5CC4), Color(0xFFB57A00), Color(0xFF1F7F45),
        Color(0xFF7A45BF), Color(0xFF13807F), Color(0xFFC24B80), Color(0xFF7F7A1E),
    ),
    amber = Color(0xFFB57A00), green = Color(0xFF1F7F45), cobalt = Color(0xFF1F5CC4), scarlet = Color(0xFFCC2A1E),
)

val LocalLineInks = staticCompositionLocalOf { midnightInks }

private val midnightScheme: ColorScheme = darkColorScheme(
    primary = Enamel.porcelain,
    onPrimary = Enamel.midnight,
    primaryContainer = Enamel.midnightHighest,
    onPrimaryContainer = Enamel.porcelain,
    secondary = Color(0xFFF2A900),
    onSecondary = Enamel.midnight,
    secondaryContainer = Color(0xFF2B4689),
    onSecondaryContainer = Enamel.porcelain,
    tertiary = Color(0xFF3DB46A),
    onTertiary = Enamel.midnight,
    background = Enamel.midnight,
    onBackground = Enamel.porcelain,
    surface = Enamel.midnight,
    onSurface = Enamel.porcelain,
    surfaceVariant = Enamel.midnightHigh,
    onSurfaceVariant = Enamel.porcelainDim,
    surfaceContainerLowest = Color(0xFF0A1430),
    surfaceContainerLow = Enamel.midnightLow,
    surfaceContainer = Enamel.midnightMid,
    surfaceContainerHigh = Enamel.midnightHigh,
    surfaceContainerHighest = Enamel.midnightHighest,
    inverseSurface = Enamel.porcelain,
    inverseOnSurface = Enamel.midnight,
    outline = Enamel.seamStrong,
    outlineVariant = Enamel.seam,
    error = Color(0xFFFF7A6E),
    onError = Enamel.midnight,
    errorContainer = Color(0xFF5A1A1A),
    onErrorContainer = Color(0xFFFFDAD5),
    scrim = Color(0xFF000000),
)

private val porcelainScheme: ColorScheme = lightColorScheme(
    primary = Enamel.ink,
    onPrimary = Enamel.plate,
    primaryContainer = Color(0xFFD9DEEC),
    onPrimaryContainer = Enamel.ink,
    secondary = Color(0xFF8A5D00),
    onSecondary = Enamel.plate,
    secondaryContainer = Color(0xFFD3DCF3),
    onSecondaryContainer = Enamel.ink,
    tertiary = Color(0xFF1F7F45),
    onTertiary = Enamel.plate,
    background = Enamel.plate,
    onBackground = Enamel.ink,
    surface = Enamel.plate,
    onSurface = Enamel.ink,
    surfaceVariant = Enamel.plateHigh,
    onSurfaceVariant = Enamel.inkDim,
    surfaceContainerLowest = Color(0xFFFAF8F2),
    surfaceContainerLow = Enamel.plateLow,
    surfaceContainer = Enamel.plateMid,
    surfaceContainerHigh = Enamel.plateHigh,
    surfaceContainerHighest = Enamel.plateHighest,
    inverseSurface = Enamel.ink,
    inverseOnSurface = Enamel.plate,
    outline = Color(0xFF7C8299),
    outlineVariant = Color(0xFFC6C2B4),
    error = Color(0xFFB3261E),
    onError = Enamel.plate,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

/** Fira Sans: a humanist sans in the tradition of transit signage, with tabular figures for fares. */
val Fira = FontFamily(
    Font(R.font.fira_sans_regular, FontWeight.Normal),
    Font(R.font.fira_sans_medium, FontWeight.Medium),
    Font(R.font.fira_sans_semibold, FontWeight.SemiBold),
    Font(R.font.fira_sans_bold, FontWeight.Bold),
)

private const val FIGURES = "tnum, lnum"

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = Fira, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight,
    letterSpacing = tracking.sp, fontFeatureSettings = FIGURES,
)

private val typography = Typography(
    displayLarge = style(56, 60, FontWeight.Bold, -1.0),
    displayMedium = style(44, 50, FontWeight.Bold, -0.6),
    displaySmall = style(36, 42, FontWeight.SemiBold, -0.3),
    headlineLarge = style(30, 36, FontWeight.SemiBold, -0.2),
    headlineMedium = style(26, 32, FontWeight.SemiBold),
    headlineSmall = style(22, 28, FontWeight.SemiBold),
    titleLarge = style(20, 26, FontWeight.SemiBold),
    titleMedium = style(16, 22, FontWeight.SemiBold, 0.1),
    titleSmall = style(14, 20, FontWeight.SemiBold, 0.1),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(12, 16, FontWeight.Normal, 0.1),
    labelLarge = style(14, 20, FontWeight.SemiBold, 0.2),
    labelMedium = style(12, 16, FontWeight.SemiBold, 0.4),
    labelSmall = style(11, 14, FontWeight.Medium, 0.5),
)

/** Enamel plates have a small, pressed radius; nothing in this world is pill-soft except roundels. */
private val shapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

@Composable
fun FlightFiltersTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.MIDNIGHT -> true
        ThemeMode.PORCELAIN -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalLineInks provides if (dark) midnightInks else porcelainInks) {
        MaterialTheme(
            colorScheme = if (dark) midnightScheme else porcelainScheme,
            typography = typography,
            shapes = shapes,
            content = content,
        )
    }
}

val isMidnight: Boolean
    @Composable get() = MaterialTheme.colorScheme.background == Enamel.midnight
