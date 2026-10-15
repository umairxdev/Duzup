package com.duzup.app.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.duzup.app.R

/** Inter (OFL) as variable font: modern sans for all functional text. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val InterFamily = FontFamily(
    listOf(
        FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold,
    ).map { weight ->
        Font(
            resId = R.font.inter_variable,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

/** Pixelify Sans (OFL): restrained pixel display face for short hero phrases, counters and micro-labels. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val PixelFamily = FontFamily(
    listOf(FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(
            resId = R.font.pixelify_sans,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

/** The pixel face renders "fi" and similar pairs as odd ligature glyphs; keep letters separate. */
private const val NoLigatures = "liga 0, clig 0"

private fun inter(size: Int, weight: FontWeight, line: Int, spacing: Double = 0.0) = TextStyle(
    fontFamily = InterFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.sp,
)

val DuzupTypography = Typography(
    displayLarge = TextStyle(fontFamily = PixelFamily, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp, fontFeatureSettings = NoLigatures),
    displayMedium = TextStyle(fontFamily = PixelFamily, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 40.sp, fontFeatureSettings = NoLigatures),
    displaySmall = TextStyle(fontFamily = PixelFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 32.sp, fontFeatureSettings = NoLigatures),
    headlineLarge = inter(30, FontWeight.Bold, 36, -0.4),
    headlineMedium = inter(26, FontWeight.Bold, 32, -0.3),
    headlineSmall = inter(22, FontWeight.SemiBold, 28, -0.2),
    titleLarge = inter(20, FontWeight.SemiBold, 26),
    titleMedium = inter(16, FontWeight.SemiBold, 22, 0.1),
    titleSmall = inter(14, FontWeight.SemiBold, 20, 0.1),
    bodyLarge = inter(16, FontWeight.Normal, 24, 0.1),
    bodyMedium = inter(14, FontWeight.Normal, 20, 0.1),
    bodySmall = inter(12, FontWeight.Normal, 16, 0.2),
    labelLarge = inter(14, FontWeight.Medium, 20, 0.1),
    labelMedium = inter(12, FontWeight.Medium, 16, 0.3),
    labelSmall = inter(11, FontWeight.Medium, 16, 0.4),
)

/** Tiny pixel micro-label, e.g. "BILLS · SUBSCRIPTIONS · ALERTS". Always paired with real text. */
val PixelMicroLabel = TextStyle(
    fontFamily = PixelFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 1.5.sp,
    fontFeatureSettings = NoLigatures,
)

val PixelCounter = TextStyle(
    fontFamily = PixelFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 32.sp,
    lineHeight = 36.sp,
    fontFeatureSettings = NoLigatures,
)
