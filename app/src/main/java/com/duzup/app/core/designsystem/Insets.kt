package com.duzup.app.core.designsystem

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Space the floating bottom navigation occupies on top-level screens (0 elsewhere). */
val LocalBottomBarPadding = compositionLocalOf { 0.dp }

@Composable
fun statusBarTop(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

@Composable
fun navigationBarBottom(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** Bottom content padding: clears the bottom bar on top-level screens, else the system navigation bar. */
@Composable
fun listBottomPadding(extra: Dp = 24.dp): Dp {
    val bar = LocalBottomBarPadding.current
    return if (bar > 0.dp) bar + extra else navigationBarBottom() + extra
}
