package com.duzup.app.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.duzup.app.R
import com.duzup.app.core.designsystem.CenteredContent
import com.duzup.app.core.designsystem.NeuIconButton
import com.duzup.app.core.designsystem.RaisedCard
import com.duzup.app.core.designsystem.DuzupTheme
import com.duzup.app.core.designsystem.listBottomPadding
import com.duzup.app.core.designsystem.statusBarTop
import com.duzup.app.core.time.DateFormats
import com.duzup.app.feature.bills.accentArgb
import com.duzup.app.feature.bills.ItemRow
import java.time.format.TextStyle

@Composable
fun CalendarScreen(
    state: CalendarUiState,
    onShiftMonth: (Long) -> Unit,
    onToday: () -> Unit,
    onSelectDate: (java.time.LocalDate) -> Unit,
    onOpenBill: (String) -> Unit,
) {
    val colors = DuzupTheme.colors
    CenteredContent {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = statusBarTop() + 12.dp,
                bottom = listBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        DateFormats.monthYear(state.month),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    if (state.month != java.time.YearMonth.from(state.today) || state.selectedDate != state.today) {
                        TextButton(onClick = onToday, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.calendar_today), color = colors.accentText, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    NeuIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.previous_month), { onShiftMonth(-1) }, size = 44.dp)
                    Spacer(Modifier.size(8.dp))
                    NeuIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.next_month), { onShiftMonth(1) }, size = 44.dp)
                }
            }
            item(key = "grid") { MonthGrid(state, onShiftMonth, onSelectDate) }
            item(key = "day-title") {
                Text(
                    stringResource(R.string.bills_on_day, DateFormats.dayMonth(state.selectedDate)),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                )
            }
            if (state.dayRows.isEmpty()) {
                item(key = "day-empty") {
                    Text(stringResource(R.string.calendar_day_empty), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                }
            } else {
                items(state.dayRows, key = { it.item.id }) { row ->
                    ItemRow(
                        item = row.item,
                        category = state.categories[row.item.bill.categoryId],
                        status = row.status,
                        today = state.today,
                        onClick = { onOpenBill(row.item.bill.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    state: CalendarUiState,
    onShiftMonth: (Long) -> Unit,
    onSelectDate: (java.time.LocalDate) -> Unit,
) {
    val colors = DuzupTheme.colors
    var drag by remember { mutableFloatStateOf(0f) }
    val locale = LocalConfiguration.current.locales[0]
    val weekdays = List(7) { state.firstDay.plus(it.toLong()) }
    RaisedCard(
        Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = {
                        val threshold = 80.dp.toPx()
                        if (drag > threshold) onShiftMonth(-1) else if (drag < -threshold) onShiftMonth(1)
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                    onHorizontalDrag = { _, amount -> drag += amount },
                )
            },
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                weekdays.forEach { day ->
                    Text(
                        day.getDisplayName(TextStyle.SHORT, locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).padding(bottom = 6.dp),
                    )
                }
            }
            state.cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { cell ->
                        DayCellView(cell, Modifier.weight(1f), onSelectDate)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCellView(cell: DayCell, modifier: Modifier, onSelect: (java.time.LocalDate) -> Unit) {
    val colors = DuzupTheme.colors
    val dateLabel = DateFormats.fullDate(cell.date)
    val spoken = if (cell.total == 0) {
        stringResource(R.string.calendar_day_none, dateLabel)
    } else {
        stringResource(R.string.calendar_day_counts, dateLabel, cell.total, cell.overdue, cell.pending, cell.paid)
    }
    Column(
        modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClickLabel = dateLabel) { onSelect(cell.date) }
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
                selected = cell.isSelected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val circleColor = when {
            cell.isSelected -> colors.buttonPrimary
            cell.isToday -> colors.accent
            else -> androidx.compose.ui.graphics.Color.Transparent
        }
        val textColor = when {
            cell.isSelected -> colors.onButtonPrimary
            cell.isToday -> androidx.compose.ui.graphics.Color(0xFF071724)
            cell.inMonth -> colors.textPrimary
            else -> colors.textMuted.copy(alpha = 0.5f)
        }
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(circleColor)
                .then(if (cell.isSelected && cell.isToday) Modifier.border(2.dp, colors.accent, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                cell.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
        Row(Modifier.height(10.dp).padding(top = 3.dp).clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            cell.dots.forEach { dot ->
                when (dot.state) {
                    DotState.OVERDUE -> Box(Modifier.size(6.dp).clip(CircleShape).background(colors.overdue))
                    // Upcoming dots are tinted by kind; done dots are hollow rings so status never relies on color alone.
                    DotState.PENDING -> Box(
                        Modifier.size(6.dp).clip(CircleShape).background(dot.kind.accentArgb?.let { Color(it) } ?: colors.textSecondary),
                    )
                    DotState.DONE -> Box(Modifier.size(6.dp).border(1.5.dp, colors.paid, CircleShape))
                }
            }
        }
    }
}
