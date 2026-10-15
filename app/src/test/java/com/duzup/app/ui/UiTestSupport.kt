package com.duzup.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.duzup.app.TestData
import com.duzup.app.core.designsystem.LocalBottomBarPadding
import com.duzup.app.core.designsystem.DuzupBackground
import com.duzup.app.core.designsystem.DuzupTheme
import com.duzup.app.core.model.BuiltInCategories
import com.duzup.app.core.model.Category
import com.duzup.app.core.model.OccurrenceItem
import com.duzup.app.core.model.OccurrenceStatus
import com.duzup.app.core.model.ThemeMode
import com.duzup.app.domain.payments.PaymentRules
import com.duzup.app.feature.home.HomeRow
import com.duzup.app.navigation.DuzupBottomBar
import com.duzup.app.navigation.TopLevel
import com.duzup.app.navigation.bottomBarOccupiedHeight
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** Wraps a screen in the real theme (and optionally the real bottom bar) with controllable dark mode / font scale. */
@Composable
fun TestApp(
    dark: Boolean = false,
    fontScale: Float = 1f,
    bottomBar: TopLevel? = null,
    content: @Composable () -> Unit,
) {
    val base = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
        DuzupTheme(
            themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
            reduceMotionPreference = true,
            hapticsEnabled = false,
        ) {
            DuzupBackground {
                val barSpace = if (bottomBar != null) bottomBarOccupiedHeight() else 0.dp
                CompositionLocalProvider(LocalBottomBarPadding provides barSpace) {
                    Box(Modifier.fillMaxSize()) {
                        content()
                        if (bottomBar != null) {
                            DuzupBottomBar(
                                selected = bottomBar,
                                onSelect = {},
                                onAdd = {},
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The bills from the approved mockup, as real model objects. */
object Fixtures {
    val today: LocalDate = LocalDate.of(2025, 3, 12)
    val now: Instant = Instant.parse("2025-03-12T07:00:00Z")

    val categories: List<Category> = BuiltInCategories.all.mapIndexed { index, (id, icon) ->
        Category(id, id.replaceFirstChar { it.uppercase() }, icon, null, true, index)
    }
    val categoryMap: Map<String, Category> = categories.associateBy { it.id }

    fun item(
        id: String,
        title: String,
        category: String,
        due: LocalDate,
        amount: Long?,
        currency: String = "PKR",
        status: OccurrenceStatus = OccurrenceStatus.PENDING,
        paidAt: Instant? = null,
    ): OccurrenceItem {
        val bill = TestData.bill(id = "bill-$id", title = title, categoryId = category, amount = amount, currency = currency, start = due)
        return OccurrenceItem(
            TestData.occurrence(id = id, billId = bill.id, date = due, status = status, paidAt = paidAt),
            bill,
        )
    }

    val electricity = item("e", "Electricity Bill", "utilities", today.plusDays(3), 850_000)
    val internet = item("i", "Internet", "internet", today.plusDays(5), 300_000)
    val netflix = item("n", "Netflix", "subscriptions", today.plusDays(7), 149_000)
    val spotify = item("s", "Spotify", "subscriptions", today.plusDays(12), 29_900)
    val overdueRent = item("r", "Rent", "rent", today.minusDays(2), 4_500_000)
    val paidGym = item(
        "g", "Gym Membership", "other", today.minusDays(5), 400_000,
        status = OccurrenceStatus.PAID, paidAt = Instant.parse("2025-03-08T10:00:00Z"),
    )

    val mockupRows: List<HomeRow> = listOf(electricity, internet, netflix, spotify)
        .map { HomeRow(it, PaymentRules.displayStatus(it, now, today)) }
}

fun SemanticsNodeInteraction.saveScreenshot(name: String) {
    val dir = File(System.getProperty("screenshot.dir") ?: "build/screenshots").apply { mkdirs() }
    val bitmap = captureToImage().asAndroidBitmap()
    File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
}
