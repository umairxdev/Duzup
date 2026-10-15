package com.duzup.app.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sparse "pixel disintegration" cluster. Dense toward the top-right corner and fading out toward the
 * bottom-left, so it reads as a transition rather than a texture. Purely decorative (no semantics),
 * deterministic (no per-frame randomness) and only ever used in brand marks, headers and empty states.
 */
@Composable
fun PixelField(
    modifier: Modifier = Modifier,
    columns: Int = 9,
    rows: Int = 9,
    unit: Dp = 6.dp,
    color: Color = DuzupTheme.colors.pixel,
    accent: Color = DuzupTheme.colors.accent,
) {
    Canvas(modifier.size(unit * columns, unit * rows).clearAndSetSemantics { }) {
        val u = unit.toPx()
        val gap = u * 0.14f
        val maxDistance = (columns + rows).toFloat()
        for (c in 0 until columns) {
            for (r in 0 until rows) {
                // Distance from the dense corner (top-right).
                val distance = ((columns - 1 - c) + r) / maxDistance
                val noise = (((c * 73856093) xor (r * 19349663)) and 0xFF) / 255f
                val keep = 1f - distance * 1.25f
                if (noise > keep) continue
                val alpha = (0.25f + 0.75f * (1f - distance)).coerceIn(0.2f, 1f)
                val isAccent = noise < 0.07f && distance < 0.55f
                drawRect(
                    color = (if (isAccent) accent else color).copy(alpha = alpha),
                    topLeft = Offset(c * u + gap, r * u + gap),
                    size = Size(u - gap * 2, u - gap * 2),
                )
            }
        }
    }
}

/** The tiny 3x3 pixel "plus" brand mark from the header. */
@Composable
fun PixelMark(
    modifier: Modifier = Modifier,
    unit: Dp = 5.dp,
    color: Color = DuzupTheme.colors.textPrimary,
    accent: Color = DuzupTheme.colors.accent,
) {
    Canvas(modifier.size(unit * 3, unit * 3).clearAndSetSemantics { }) {
        val u = unit.toPx()
        val gap = u * 0.1f
        val cells = listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1, 1 to 2)
        cells.forEach { (c, r) ->
            drawRect(
                color = if (c == 1 && r == 1) accent else color,
                topLeft = Offset(c * u + gap, r * u + gap),
                size = Size(u - gap * 2, u - gap * 2),
            )
        }
    }
}

/** 2x2 square cluster used as a small accent next to hero text. */
@Composable
fun PixelAccent(
    modifier: Modifier = Modifier,
    unit: Dp = 4.dp,
    color: Color = DuzupTheme.colors.accent,
) {
    Canvas(modifier.size(unit * 3, unit * 3).clearAndSetSemantics { }) {
        val u = unit.toPx()
        val gap = u * 0.12f
        listOf(2 to 0, 1 to 1, 2 to 1, 0 to 2).forEachIndexed { index, (c, r) ->
            drawRect(
                color = color.copy(alpha = if (index == 0) 1f else 0.55f),
                topLeft = Offset(c * u + gap, r * u + gap),
                size = Size(u - gap * 2, u - gap * 2),
            )
        }
    }
}
