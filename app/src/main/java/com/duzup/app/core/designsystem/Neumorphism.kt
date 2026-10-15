package com.duzup.app.core.designsystem

import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private fun Outline.toPath(): Path = when (this) {
    is Outline.Rectangle -> Path().apply { addRect(rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
    is Outline.Generic -> path
}

/**
 * Raised neumorphic surface: a soft blue-gray shadow to the bottom-right and a light highlight to the
 * top-left, both small and low in opacity. Below API 28 (no hardware shadow layers) it degrades to a
 * crisp hairline so it never looks muddy.
 */
fun Modifier.neumorphic(
    shape: Shape = RoundedCornerShape(Radii.card),
    elevation: Dp = 6.dp,
): Modifier = composed {
    val colors = DuzupTheme.colors
    val hairline = colors.outline.copy(alpha = if (colors.isDark) 0.55f else 0.7f)
    val edge = Brush.linearGradient(
        0f to colors.shadowLight.copy(alpha = if (colors.isDark) 0.07f else 0.9f),
        1f to hairline.copy(alpha = 0.5f),
    )
    val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Modifier.drawBehind {
            val path = shape.createOutline(size, LayoutDirection.Ltr, this).toPath()
            val blur = elevation.toPx() * 1.6f
            val offset = elevation.toPx() * 0.7f
            drawIntoCanvas { canvas ->
                val dark = Paint().apply {
                    color = colors.surface
                    asFrameworkPaint().setShadowLayer(blur, offset, offset, colors.shadowDark.toArgb())
                }
                canvas.drawPath(path, dark)
                val light = Paint().apply {
                    color = colors.surface
                    asFrameworkPaint().setShadowLayer(blur, -offset, -offset, colors.shadowLight.toArgb())
                }
                canvas.drawPath(path, light)
            }
        }
    } else {
        Modifier.drawBehind {
            val path = shape.createOutline(size, LayoutDirection.Ltr, this).toPath()
            drawPath(path, colors.surface)
        }
    }
    base.border(1.dp, edge, shape).clip(shape)
}

/** Restrained inset surface (inputs): a thin inner shadow at the top-left, a faint highlight bottom-right. */
fun Modifier.neumorphicInset(
    shape: Shape = RoundedCornerShape(Radii.field),
    depth: Dp = 5.dp,
): Modifier = composed {
    val colors = DuzupTheme.colors
    this
        .clip(shape)
        .drawWithContent {
            val d = depth.toPx()
            val inner = colors.shadowDark.copy(alpha = colors.shadowDark.alpha * 0.55f)
            drawRect(colors.surfaceInset, size = size)
            clipPath(shape.createOutline(size, layoutDirection, this).toPath()) {
                drawRect(
                    brush = Brush.verticalGradient(listOf(inner, Color.Transparent), startY = 0f, endY = d),
                    size = Size(size.width, d),
                )
                drawRect(
                    brush = Brush.horizontalGradient(listOf(inner, Color.Transparent), startX = 0f, endX = d),
                    size = Size(d, size.height),
                )
                drawRect(
                    color = colors.shadowLight.copy(alpha = colors.shadowLight.alpha * 0.6f),
                    topLeft = Offset(0f, size.height - 1.dp.toPx()),
                    size = Size(size.width, 1.dp.toPx()),
                )
            }
            drawContent()
        }
        .border(1.dp, colors.outline.copy(alpha = 0.35f), shape)
}

/** Soft cyan edge light used on the primary CTA and the central Add button. */
@Composable
fun cyanEdgeBrush(): Brush {
    val colors = DuzupTheme.colors
    return Brush.linearGradient(
        0f to colors.accent.copy(alpha = 0.85f),
        0.55f to colors.accent.copy(alpha = 0.18f),
        1f to colors.accent.copy(alpha = 0.55f),
    )
}
