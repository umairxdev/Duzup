package com.duzup.app.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Digital Ice base tokens (exact hex values from the brand spec). */
object DuzupPalette {
    val IceWhite = Color(0xFFE6ECF0)
    val FrostSilver = Color(0xFFB8C9D3)
    val NeonCyan = Color(0xFF00D9FF)
    val DeepNavy = Color(0xFF203345)
    val InkNavy = Color(0xFF071724)

    // Semantic status colors (tuned for contrast in each theme; never the only status signal).
    val PaidLight = Color(0xFF087F67)
    val DueSoonLight = Color(0xFFA65A00)
    val OverdueLight = Color(0xFFB4233B)
    val MutedLight = Color(0xFF4C6274)
}

@Immutable
data class DuzupColors(
    val isDark: Boolean,
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val background: Color,
    /** Raised neumorphic surface (cards, pills). */
    val surface: Color,
    /** Pressed / inset surface (inputs). */
    val surfaceInset: Color,
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    /** Decorative neon cyan: edges, selected glyphs, bars. */
    val accent: Color,
    /** Cyan variant that passes contrast as text on the background. */
    val accentText: Color,
    val buttonPrimary: Color,
    val onButtonPrimary: Color,
    val paid: Color,
    val dueSoon: Color,
    val overdue: Color,
    val shadowDark: Color,
    val shadowLight: Color,
    val pixel: Color,
)

val LightDuzupColors = DuzupColors(
    isDark = false,
    backgroundTop = Color(0xFFF1F5F8),
    backgroundBottom = Color(0xFFDCE5EB),
    background = DuzupPalette.IceWhite,
    surface = Color(0xFFEDF2F5),
    surfaceInset = Color(0xFFDDE6EC),
    outline = DuzupPalette.FrostSilver,
    textPrimary = DuzupPalette.InkNavy,
    textSecondary = DuzupPalette.DeepNavy,
    textMuted = DuzupPalette.MutedLight,
    accent = DuzupPalette.NeonCyan,
    accentText = Color(0xFF006A80),
    buttonPrimary = DuzupPalette.InkNavy,
    onButtonPrimary = Color(0xFFF4F8FA),
    paid = DuzupPalette.PaidLight,
    dueSoon = DuzupPalette.DueSoonLight,
    overdue = DuzupPalette.OverdueLight,
    shadowDark = Color(0x668FA4B3),
    shadowLight = Color(0xE6FFFFFF),
    pixel = Color(0xFF7FA3B8),
)

/** Matte near-black with neutral graphite surfaces; cyan is the only color accent. */
val DarkDuzupColors = DuzupColors(
    isDark = true,
    backgroundTop = Color(0xFF0E1012),
    backgroundBottom = Color(0xFF0A0B0D),
    background = Color(0xFF0A0B0D),
    surface = Color(0xFF16191C),
    surfaceInset = Color(0xFF0F1113),
    outline = Color(0xFF2A2F34),
    textPrimary = Color(0xFFEDF1F3),
    textSecondary = Color(0xFFC5CCD2),
    textMuted = Color(0xFF9BA4AB),
    accent = DuzupPalette.NeonCyan,
    accentText = DuzupPalette.NeonCyan,
    buttonPrimary = Color(0xFF1F2428),
    onButtonPrimary = Color(0xFFF4F8FA),
    paid = Color(0xFF3FD8B4),
    dueSoon = Color(0xFFFFB547),
    overdue = Color(0xFFFF8094),
    shadowDark = Color(0xB3000000),
    shadowLight = Color(0x0AFFFFFF),
    pixel = Color(0xFF3A4852),
)

val LocalDuzupColors = staticCompositionLocalOf { LightDuzupColors }
