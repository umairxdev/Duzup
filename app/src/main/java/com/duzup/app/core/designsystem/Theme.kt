package com.duzup.app.core.designsystem

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.duzup.app.core.model.ThemeMode

/** True when the user (or the system animator scale) asks for reduced motion. */
val LocalReduceMotion = compositionLocalOf { false }

/** True when haptic feedback is enabled in settings. */
val LocalHapticsEnabled = compositionLocalOf { true }

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val screen = 20.dp
}

object Radii {
    val small = 14.dp
    val card = 22.dp
    val field = 16.dp
    val pill = 28.dp
}

object DuzupTheme {
    val colors: DuzupColors
        @Composable @ReadOnlyComposable get() = LocalDuzupColors.current
}

@Composable
fun DuzupTheme(
    themeMode: ThemeMode,
    reduceMotionPreference: Boolean,
    hapticsEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkDuzupColors else LightDuzupColors
    val context = LocalContext.current
    val systemReduce = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }

    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = DuzupPalette.InkNavy,
            secondary = colors.textSecondary,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceInset,
            onSurfaceVariant = colors.textMuted,
            outline = colors.outline,
            error = colors.overdue,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surface,
            surfaceContainerHighest = colors.surfaceInset,
        )
    } else {
        lightColorScheme(
            primary = colors.buttonPrimary,
            onPrimary = colors.onButtonPrimary,
            secondary = colors.textSecondary,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceInset,
            onSurfaceVariant = colors.textMuted,
            outline = colors.outline,
            error = colors.overdue,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surface,
            surfaceContainerHighest = colors.surfaceInset,
        )
    }

    CompositionLocalProvider(
        LocalDuzupColors provides colors,
        LocalReduceMotion provides (reduceMotionPreference || systemReduce),
        LocalHapticsEnabled provides hapticsEnabled,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = DuzupTypography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(10.dp),
                small = RoundedCornerShape(Radii.small),
                medium = RoundedCornerShape(Radii.field),
                large = RoundedCornerShape(Radii.card),
                extraLarge = RoundedCornerShape(28.dp),
            ),
            content = content,
        )
    }
}
