package com.duzup.app.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.duzup.app.R
import com.duzup.app.core.designsystem.BackButton
import com.duzup.app.core.designsystem.CenteredContent
import com.duzup.app.core.designsystem.EmptyState
import com.duzup.app.core.designsystem.NeuIconButton
import com.duzup.app.core.designsystem.PixelCounter
import com.duzup.app.core.designsystem.RaisedCard
import com.duzup.app.core.designsystem.DuzupTheme
import com.duzup.app.core.designsystem.SectionLabel
import com.duzup.app.core.designsystem.listBottomPadding
import com.duzup.app.core.designsystem.neumorphicInset
import com.duzup.app.core.designsystem.statusBarTop
import com.duzup.app.core.model.Money
import com.duzup.app.core.time.DateFormats
import com.duzup.app.domain.summary.CurrencyAmount
import com.duzup.app.domain.summary.MonthStats
import com.duzup.app.feature.bills.displayName

@Composable
fun StatsScreen(
    state: StatsUiState,
    onShiftMonth: (Long) -> Unit,
    onResetMonth: () -> Unit,
    onBack: () -> Unit = {},
) {
    val colors = DuzupTheme.colors
    CenteredContent {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = statusBarTop() + 12.dp,
                bottom = listBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BackButton(onBack)
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.stats_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = colors.textPrimary,
                            modifier = Modifier.semantics { heading() },
                        )
                        TextButton(onClick = onResetMonth, enabled = !state.isCurrentMonth, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(DateFormats.monthYear(state.month), style = MaterialTheme.typography.titleSmall, color = colors.textMuted)
                        }
                    }
                    NeuIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.previous_month), { onShiftMonth(-1) }, size = 44.dp)
                    Spacer(Modifier.size(8.dp))
                    NeuIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.next_month), { onShiftMonth(1) }, size = 44.dp)
                }
            }

            val stats = state.stats
            if (stats == null || stats.isEmpty) {
                if (!state.isLoading) {
                    item(key = "empty") {
                        EmptyState(
                            title = stringResource(R.string.stats_empty_title),
                            message = stringResource(R.string.stats_empty_message),
                        )
                    }
                }
            } else {
                item(key = "counts") { CountsRow(stats) }
                state.currencies.forEach { code ->
                    item(key = "currency-$code") { CurrencyCard(code, stats, state) }
                }
                item(key = "definitions") {
                    Text(
                        stringResource(R.string.stats_definitions),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CountsRow(stats: MonthStats) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CountTile(stringResource(R.string.status_paid), stats.paidCount, DuzupTheme.colors.paid, Modifier.weight(1f))
        CountTile(stringResource(R.string.stats_pending), stats.pendingCount, DuzupTheme.colors.textSecondary, Modifier.weight(1f))
        CountTile(stringResource(R.string.overdue), stats.overdueCount, DuzupTheme.colors.overdue, Modifier.weight(1f))
        CountTile(stringResource(R.string.status_skipped), stats.skippedCount, DuzupTheme.colors.textMuted, Modifier.weight(1f))
    }
}

@Composable
private fun CountTile(label: String, count: Int, color: Color, modifier: Modifier) {
    RaisedCard(modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $count" }, elevation = 4.dp) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(count.toString(), style = PixelCounter, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = DuzupTheme.colors.textMuted, maxLines = 1)
        }
    }
}

@Composable
private fun CurrencyCard(code: String, stats: MonthStats, state: StatsUiState) {
    val colors = DuzupTheme.colors
    fun pick(list: List<CurrencyAmount>) = list.firstOrNull { it.currencyCode == code }
    val slices = stats.categories.filter { it.currencyCode == code }
    val sliceTotal = slices.sumOf { it.minorUnits }.coerceAtLeast(1L)

    RaisedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionLabel(code)
            AmountLine(Icons.Rounded.CheckCircle, stringResource(R.string.stats_paid_amount), pick(stats.paid), code, colors.paid)
            AmountLine(Icons.Rounded.Schedule, stringResource(R.string.stats_pending), pick(stats.pending), code, colors.textSecondary)
            AmountLine(Icons.Rounded.ErrorOutline, stringResource(R.string.overdue), pick(stats.overdue), code, colors.overdue)

            if (slices.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                SectionLabel(stringResource(R.string.stats_by_category))
                slices.forEach { slice ->
                    val category = state.categories[slice.categoryId]
                    val name = category.displayName()
                    val fraction = slice.minorUnits.toFloat() / sliceTotal
                    val percent = (fraction * 100).toInt()
                    val amount = Money.format(slice.minorUnits, code)
                    Column(
                        Modifier.semantics(mergeDescendants = true) { contentDescription = "$name, $amount, $percent%" },
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                            Text("$amount · $percent%", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .neumorphicInset(RoundedCornerShape(5.dp), depth = 3.dp)
                                .clearAndSetSemantics { },
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(fraction.coerceIn(0.03f, 1f))
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(category?.colorArgb?.let { Color(it) } ?: colors.accent),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AmountLine(icon: ImageVector, label: String, amount: CurrencyAmount?, code: String, color: Color) {
    val colors = DuzupTheme.colors
    val value = Money.format(amount?.minorUnits ?: 0L, code)
    val unknown = amount?.unknownAmountCount ?: 0
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            if (unknown > 0) {
                Text(
                    pluralStringResource(R.plurals.without_amount, unknown, unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
        }
        Text(value, style = MaterialTheme.typography.titleMedium, color = color)
    }
}
