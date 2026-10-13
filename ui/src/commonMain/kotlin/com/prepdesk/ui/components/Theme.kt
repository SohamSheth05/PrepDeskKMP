package com.prepdesk.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Soft tinted surfaces and their text color. Each has a dark-mode counterpart so cards stay readable. */
data class PrepPalette(
    val ink: Color, val accent: Color, val lilac: Color, val mint: Color, val peach: Color, val sky: Color,
    val night: Color, val heroStart: Color, val heroEnd: Color,
)

private val LightPalette = PrepPalette(
    ink = Color(0xFF1F2440), accent = Color(0xFF4B56A8), lilac = Color(0xFFEEF0FA), mint = Color(0xFFE6F3EE),
    peach = Color(0xFFFAEFE6), sky = Color(0xFFE7EFF8), night = Color(0xFF1E2347),
    heroStart = Color(0xFF262C5C), heroEnd = Color(0xFF4B56A8),
)
private val DarkPalette = PrepPalette(
    ink = Color(0xFFE4E6F2), accent = Color(0xFFA9B1EE), lilac = Color(0xFF262A3D), mint = Color(0xFF1E2F2A),
    peach = Color(0xFF33291F), sky = Color(0xFF1F2A38), night = Color(0xFF151833),
    heroStart = Color(0xFF1A1E3D), heroEnd = Color(0xFF323A7A),
)

private val LocalPalette = staticCompositionLocalOf { LightPalette }

val Ink: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.ink
/** The brand accent; named for its original purple, now a calm indigo. */
val Purple: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accent
val Mint: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.mint
val Lilac: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.lilac
val Peach: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.peach
val Sky: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.sky
val Night: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.night
val HeroStart: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.heroStart
val HeroEnd: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.heroEnd
val TrackColors: List<Color> @Composable @ReadOnlyComposable get() = LocalPalette.current.let { listOf(it.lilac, it.mint, it.peach, it.sky) }

// Reward accents read well on both light and dark surfaces.
val Gold = Color(0xFFF2B93B)
val Flame = Color(0xFFE8783A)
val Good = Color(0xFF2E9B68)
val Bad = Color(0xFFD0544F)
val ConfettiColors = listOf(Gold, Flame, Color(0xFF7BDFF2), Color(0xFFB388FF), Color(0xFF7CE0A3), Color(0xFFFF7AA2))

private val LightColors = lightColorScheme(
    primary = Color(0xFF4B56A8), onPrimary = Color.White, primaryContainer = Color(0xFFE2E5F8), onPrimaryContainer = Color(0xFF151B4F),
    secondary = Color(0xFF5A6273), onSecondary = Color.White, secondaryContainer = Color(0xFFE4E7EE), onSecondaryContainer = Color(0xFF181C25),
    tertiary = Color(0xFF2E7D6B), onTertiary = Color.White, tertiaryContainer = Color(0xFFD3EEE5), onTertiaryContainer = Color(0xFF00201A),
    background = Color(0xFFF7F8FA), onBackground = Color(0xFF1A1C22), surface = Color(0xFFF7F8FA), onSurface = Color(0xFF1A1C22),
    surfaceVariant = Color(0xFFEDEFF3), onSurfaceVariant = Color(0xFF5A606B),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F3F7), surfaceContainer = Color(0xFFECEEF2),
    surfaceContainerHigh = Color(0xFFE6E8ED), surfaceContainerHighest = Color(0xFFE0E2E8),
    outline = Color(0xFFC3C7D0), outlineVariant = Color(0xFFE0E3E9), error = Color(0xFFB3261E),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFB8BFF5), onPrimary = Color(0xFF1E2563), primaryContainer = Color(0xFF353E86), onPrimaryContainer = Color(0xFFE0E3FF),
    secondary = Color(0xFFC1C6D4), onSecondary = Color(0xFF2B303B), secondaryContainer = Color(0xFF414755), onSecondaryContainer = Color(0xFFDDE2F0),
    tertiary = Color(0xFF8FD5BF), onTertiary = Color(0xFF00382D), tertiaryContainer = Color(0xFF125142), onTertiaryContainer = Color(0xFFABF2DA),
    background = Color(0xFF121318), onBackground = Color(0xFFE3E4EA), surface = Color(0xFF121318), onSurface = Color(0xFFE3E4EA),
    surfaceVariant = Color(0xFF2A2D35), onSurfaceVariant = Color(0xFFC3C6D0),
    surfaceContainerLowest = Color(0xFF0D0E12), surfaceContainerLow = Color(0xFF1A1B21), surfaceContainer = Color(0xFF1E2026),
    surfaceContainerHigh = Color(0xFF282A31), surfaceContainerHighest = Color(0xFF33353C),
    outline = Color(0xFF8D919B), outlineVariant = Color(0xFF43464F), error = Color(0xFFF2B8B5),
)

private val PrepShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp),
)

private val PrepTypography = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.Medium),
    )
}

/** PrepDesk's calm indigo theme. Follows the system light/dark setting. */
@Composable
fun PrepDeskTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides if (dark) DarkPalette else LightPalette) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = PrepTypography, shapes = PrepShapes, content = content)
    }
}
