package io.github.eonewg.gnome.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class GnomeColors(
    val appBackground: Color,
    val cardBackground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val tagBackground: Color,
    val tagForeground: Color,
    val accent: Color,
    val subtleSurface: Color,
    val divider: Color,
)

internal val LightGnomeColors = GnomeColors(
    appBackground = Color(0xFFF7F7F7),
    cardBackground = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF383A39),
    textSecondary = Color(0xFF9A9C9B),
    tagBackground = Color(0xFFF0F4FF),
    tagForeground = Color(0xFF5F8DE4),
    accent = Color(0xFF2FCC7A),
    subtleSurface = Color(0xFFEFEFEF),
    divider = Color(0xFFE7E8E7),
)

internal val DarkGnomeColors = GnomeColors(
    appBackground = Color(0xFF151716),
    cardBackground = Color(0xFF202321),
    textPrimary = Color(0xFFE5E8E6),
    textSecondary = Color(0xFF939995),
    tagBackground = Color(0xFF25334A),
    tagForeground = Color(0xFF8BAEF0),
    accent = Color(0xFF5BD694),
    subtleSurface = Color(0xFF292C2A),
    divider = Color(0xFF343835),
)

internal val LocalGnomeColors = staticCompositionLocalOf { LightGnomeColors }

object GnomeDesign {
    val colors: GnomeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalGnomeColors.current
}
