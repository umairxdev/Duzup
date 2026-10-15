package com.duzup.app.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.duzup.app.R
import com.duzup.app.core.designsystem.CenteredContent
import com.duzup.app.core.designsystem.DuzupTheme
import com.duzup.app.core.designsystem.EmptyState
import com.duzup.app.core.designsystem.FilterPill
import com.duzup.app.core.designsystem.FlatIconButton
import com.duzup.app.core.designsystem.NeuIconButton
import com.duzup.app.core.designsystem.PixelField
import com.duzup.app.core.designsystem.PixelMark
import com.duzup.app.core.designsystem.RaisedCard
import com.duzup.app.core.designsystem.listBottomPadding
import com.duzup.app.core.designsystem.statusBarTop
import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.Money
import com.duzup.app.core.time.DateFormats
import com.duzup.app.domain.summary.MonthTotals
import com.duzup.app.feature.bills.ItemRow

/** Everything Home can do; no-op defaults so tests supply only what they care about. */
class HomeCallbacks(
    val onSelectTab: (HomeTab) -> Unit = {},
    val onSelectKind: (ItemKind?) -> Unit = {},
    val onShiftMonth: (Long) -> Unit = {},
    val onResetMonth: () -> Unit = {},
    val onOpenItem: (String) -> Unit = {},
    val onAdd: () -> Unit = {},
    val onOpenCalendar: () -> Unit = {},
    val onOpenStats: () -> Unit = {},
    val onOpenReminders: () -> Unit = {},
    val onOpenAllItems: () -> Unit = {},
)

@Composable
fun HomeScreen(state: HomeUiState, callbacks: HomeCallbacks) {
    val colors = DuzupTheme.colors
    CenteredContent {
        if (state.isLoading) {
            CircularProgressIndicator(color = colors.accent, modifier = Modifier.align(Alignment.Center))
            return@CenteredContent
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = statusBarTop() + 8.dp,
                bottom = listBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header") { HomeHeader(state, callbacks.onOpenReminders) }
            if (state.showMoneyCard) {
                item(key = "overview") { OverviewCard(state, callbacks) }
            }
            item(key = "tabs") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilterPill(stringResource(R.string.tab_upcoming), state.tab == HomeTab.UPCOMING, { callbacks.onSelectTab(HomeTab.UPCOMING) })
                    FilterPill(stringResource(R.string.tab_done), state.tab == HomeTab.PAID, { callbacks.onSelectTab(HomeTab.PAID) })
                    FilterPill(
                        if (state.overdueCount > 0) stringResource(R.string.tab_late_count, state.overdueCount)
                        else stringResource(R.string.tab_late),
                        state.tab == HomeTab.OVERDUE,
                        { callbacks.onSelectTab(HomeTab.OVERDUE) },
                    )
                    Spacer(Modifier.width(8.dp))
                    NeuIconButton(
                        icon = Icons.Rounded.CalendarMonth,
                        contentDescription = stringResource(R.string.nav_calendar),
                        onClick = callbacks.onOpenCalendar,
                    )
                }
            }

            if (!state.hasAnyItems) {
                item(key = "empty-all") {
                    EmptyState(
                        title = stringResource(R.string.home_empty_title),
                        message = stringResource(R.string.home_empty_message),
                        actionLabel = stringResource(R.string.add_something),
                        onAction = callbacks.onAdd,
                    )
                }
            } else if (state.isEmpty) {
                item(key = "empty-tab") {
                    val (title, message) = when (state.tab) {
                        HomeTab.UPCOMING -> R.string.empty_upcoming_title to R.string.empty_upcoming_message
                        HomeTab.PAID -> R.string.empty_done_title to R.string.empty_done_message
                        HomeTab.OVERDUE -> R.string.empty_late_title to R.string.empty_late_message
                    }
                    EmptyState(
                        title = stringResource(title),
                        message = stringResource(message),
                        actionLabel = if (state.tab == HomeTab.UPCOMING) stringResource(R.string.add_something) else null,
                        onAction = if (state.tab == HomeTab.UPCOMING) callbacks.onAdd else null,
                    )
                }
            } else {
                state.sections.filter { it.rows.isNotEmpty() }.forEach { section ->
                    item(key = "section-${section.kind}") {
                        Text(
                            stringResource(
                                when (section.kind) {
                                    HomeSectionKind.LATE -> R.string.section_late
                                    HomeSectionKind.TODAY -> R.string.section_today
                                    HomeSectionKind.COMING_UP -> R.string.section_coming_up
                                    HomeSectionKind.DONE -> R.string.section_done
                                },
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(top = 4.dp).semantics { heading() },
                        )
                    }
                    items(section.rows, key = { "${section.kind}-${it.item.id}" }) { row ->
                        ItemRow(
                            item = row.item,
                            category = state.categories[row.item.bill.categoryId],
                            status = row.status,
                            today = state.today,
                            onClick = { callbacks.onOpenItem(row.item.bill.id) },
                        )
                    }
                }
            }

            if (state.hasAnyItems) {
                item(key = "all") {
                    TextButton(onClick = callbacks.onOpenAllItems, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.view_everything), color = colors.accentText, style = MaterialTheme.typography.labelLarge)
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = colors.accentText,
                            modifier = Modifier.padding(start = 6.dp).size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(state: HomeUiState, onOpenReminders: () -> Unit) {
    val colors = DuzupTheme.colors
    Box(Modifier.fillMaxWidth()) {
        PixelField(
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = (-4).dp),
            columns = 11, rows = 11, unit = 7.dp,
        )
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PixelMark(Modifier.padding(start = 4.dp))
                Spacer(Modifier.weight(1f))
                NeuIconButton(
                    icon = Icons.Rounded.NotificationsNone,
                    contentDescription = stringResource(R.string.reminders_title),
                    onClick = onOpenReminders,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(
                    when (state.greeting) {
                        Greeting.MORNING -> R.string.greeting_morning
                        Greeting.AFTERNOON -> R.string.greeting_afternoon
                        Greeting.EVENING -> R.string.greeting_evening
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textMuted,
            )
            Text(
                stringResource(R.string.home_headline),
                style = MaterialTheme.typography.displayLarge,
                color = colors.textPrimary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (state.overdueCount > 0) {
                    pluralStringResource(R.plurals.home_subtitle_late, state.overdueCount, state.overdueCount)
                } else {
                    stringResource(R.string.home_subtitle_clear)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                modifier = Modifier.widthIn(max = 260.dp),
            )
        }
    }
}

@Composable
private fun OverviewCard(state: HomeUiState, callbacks: HomeCallbacks) {
    val colors = DuzupTheme.colors
    val totals = state.totals ?: return
    RaisedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlatIconButton(
                    Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    stringResource(R.string.previous_month),
                    { callbacks.onShiftMonth(-1) },
                    tint = colors.textMuted,
                )
                TextButton(onClick = callbacks.onResetMonth, enabled = !state.isCurrentMonth, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(
                        if (state.isCurrentMonth) stringResource(R.string.due_this_month) else DateFormats.monthYear(state.month),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textMuted,
                    )
                }
                FlatIconButton(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    stringResource(R.string.next_month),
                    { callbacks.onShiftMonth(1) },
                    tint = colors.textMuted,
                )
                Spacer(Modifier.weight(1f))
                FlatIconButton(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    stringResource(R.string.open_statistics),
                    callbacks.onOpenStats,
                    tint = colors.textMuted,
                )
            }
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    TotalsBlock(totals)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        pluralStringResource(R.plurals.bills_remaining, totals.pendingCount, totals.pendingCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                }
                if (totals.weeklyBuckets.isNotEmpty()) MiniBars(totals.weeklyBuckets)
            }
        }
    }
}

@Composable
private fun TotalsBlock(totals: MonthTotals) {
    val colors = DuzupTheme.colors
    // A currency whose bills have no amounts is not a total of zero: it is simply unknown.
    val known = totals.perCurrency.filter { it.minorUnits > 0 }
    if (known.isEmpty()) {
        Text(
            if (totals.pendingCount == 0) stringResource(R.string.total_all_clear) else stringResource(R.string.total_no_amounts),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
        )
        return
    }
    known.forEachIndexed { index, amount ->
        Text(
            Money.format(amount.minorUnits, amount.currencyCode),
            style = if (index == 0) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
        )
    }
    val unknown = totals.perCurrency.sumOf { it.unknownAmountCount }
    if (unknown > 0) {
        Text(
            pluralStringResource(R.plurals.without_amount, unknown, unknown),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
    }
}

/** Restrained mini bars: real pending amount per week of the month (decorative; the numbers are in the text). */
@Composable
private fun MiniBars(buckets: List<Long>) {
    val colors = DuzupTheme.colors
    val max = (buckets.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Canvas(Modifier.size(width = 72.dp, height = 48.dp).clearAndSetSemantics { }) {
        val barWidth = 8.dp.toPx()
        val gap = (size.width - barWidth * buckets.size) / (buckets.size - 1).coerceAtLeast(1)
        buckets.forEachIndexed { index, value ->
            val fraction = value.toFloat() / max
            val h = (size.height * (0.18f + 0.82f * fraction)).coerceAtMost(size.height)
            drawRoundRect(
                color = if (value == 0L) colors.outline.copy(alpha = 0.6f) else colors.accent.copy(alpha = 0.35f + 0.65f * fraction),
                topLeft = Offset(index * (barWidth + gap), size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        }
    }
}
