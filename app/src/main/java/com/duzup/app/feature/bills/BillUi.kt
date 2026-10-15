package com.duzup.app.feature.bills

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duzup.app.R
import com.duzup.app.core.designsystem.CategoryIcons
import com.duzup.app.core.designsystem.RaisedCard
import com.duzup.app.core.designsystem.DuzupTheme
import com.duzup.app.core.designsystem.cyanEdgeBrush
import com.duzup.app.core.designsystem.neumorphic
import com.duzup.app.core.model.BuiltInCategories
import com.duzup.app.core.model.Category
import com.duzup.app.core.model.DisplayStatus
import com.duzup.app.core.model.IntervalUnit
import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.Money
import com.duzup.app.core.model.OccurrenceItem
import com.duzup.app.core.model.Recurrence
import com.duzup.app.core.model.RecurrenceRule
import com.duzup.app.core.model.ReminderOffsets
import com.duzup.app.core.time.DateFormats
import com.duzup.app.domain.payments.PaymentRules
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun Category?.displayName(): String {
    if (this == null) return stringResource(R.string.category_other)
    if (!builtIn) return name
    return stringResource(
        when (id) {
            BuiltInCategories.UTILITIES -> R.string.category_utilities
            BuiltInCategories.INTERNET -> R.string.category_internet
            BuiltInCategories.SUBSCRIPTIONS -> R.string.category_subscriptions
            BuiltInCategories.RENT -> R.string.category_rent
            BuiltInCategories.LOANS -> R.string.category_loans
            BuiltInCategories.INSURANCE -> R.string.category_insurance
            BuiltInCategories.EDUCATION -> R.string.category_education
            BuiltInCategories.VEHICLE -> R.string.category_vehicle
            else -> R.string.category_other
        },
    )
}

/** Icon in a small tile. [selected] renders the dark / cyan state from the Add chip row. */
@Composable
fun CategoryBadge(
    category: Category?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    selected: Boolean = false,
) {
    val colors = DuzupTheme.colors
    val shape = RoundedCornerShape(size / 3)
    val icon = CategoryIcons.get(category?.iconKey ?: "more")
    val custom = category?.colorArgb?.let { Color(it) }
    val tileModifier = if (selected) {
        Modifier.size(size).clip(shape).background(colors.buttonPrimary).border(1.dp, cyanEdgeBrush(), shape)
    } else {
        Modifier.size(size).neumorphic(shape, 3.dp)
    }
    Box(modifier.then(tileModifier), contentAlignment = Alignment.Center) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) colors.accent else custom ?: colors.textSecondary,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

data class DueText(val caption: String, val value: String, val color: Color, val spoken: String)

@Composable
fun daysText(days: Long): String = pluralStringResource(R.plurals.days_count, days.toInt(), days.toInt())

/** Two-line due status ("Due in" / "3 days") plus the full spoken form. Never color alone. */
@Composable
fun dueText(
    status: DisplayStatus,
    dueDate: LocalDate,
    today: LocalDate,
    paidOn: LocalDate?,
    kind: ItemKind = ItemKind.BILL,
): DueText {
    val colors = DuzupTheme.colors
    val days = PaymentRules.daysUntil(dueDate, today)
    return when (status) {
        DisplayStatus.UPCOMING, DisplayStatus.DUE_SOON -> {
            val value = daysText(days)
            val caption = stringResource(if (kind == ItemKind.BILL) R.string.due_in else R.string.in_caption)
            DueText(caption, value, if (status == DisplayStatus.DUE_SOON) colors.dueSoon else colors.accentText, "$caption $value")
        }
        DisplayStatus.DUE_TODAY -> {
            val caption = stringResource(if (kind == ItemKind.BILL) R.string.due_caption else R.string.when_caption)
            val value = stringResource(R.string.due_today)
            DueText(caption, value, colors.dueSoon, stringResource(R.string.due_today_spoken))
        }
        DisplayStatus.OVERDUE -> {
            val overdueDays = -days
            if (overdueDays <= 0) {
                DueText(stringResource(R.string.overdue), stringResource(R.string.due_today), colors.overdue, stringResource(R.string.overdue_today_spoken))
            } else {
                val caption = stringResource(R.string.overdue_by)
                val value = daysText(overdueDays)
                DueText(caption, value, colors.overdue, "$caption $value")
            }
        }
        DisplayStatus.PAST -> {
            val value = DateFormats.dayMonth(dueDate)
            DueText(stringResource(R.string.status_past), value, colors.textMuted, stringResource(R.string.past_spoken, value))
        }
        DisplayStatus.PAID -> {
            val value = DateFormats.dayMonth(paidOn ?: dueDate)
            val caption = stringResource(if (kind == ItemKind.BILL) R.string.status_paid else R.string.status_done)
            DueText(
                caption, value, colors.paid,
                stringResource(if (kind == ItemKind.BILL) R.string.paid_on_spoken else R.string.done_on_spoken, value),
            )
        }
        DisplayStatus.SKIPPED -> {
            val value = DateFormats.dayMonth(dueDate)
            DueText(stringResource(R.string.status_skipped), value, colors.textMuted, stringResource(R.string.skipped_spoken, value))
        }
    }
}

/** The one-line detail under an item's title, which depends on its kind. */
@Composable
fun itemSubtitle(item: OccurrenceItem): String {
    val bill = item.bill
    val time = item.occurrence.dueTime
    return when (bill.kind) {
        ItemKind.BILL -> item.effectiveAmountMinorUnits?.let { Money.format(it, bill.currencyCode) }
            ?: stringResource(R.string.no_amount)
        ItemKind.EVENT -> {
            val whenText = when {
                bill.allDay -> stringResource(R.string.subtitle_all_day)
                bill.endTime != null -> stringResource(R.string.subtitle_time_range, DateFormats.time(time), DateFormats.time(bill.endTime))
                else -> DateFormats.time(time)
            }
            bill.location?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.subtitle_with_place, whenText, it) } ?: whenText
        }
        ItemKind.TASK -> stringResource(R.string.subtitle_with_place, DateFormats.time(time), priorityLabel(bill.priority))
        ItemKind.MEMO -> bill.notes?.lineSequence()?.firstOrNull { it.isNotBlank() }
            ?: if (bill.scheduled) DateFormats.time(time) else stringResource(R.string.subtitle_note)
        ItemKind.BIRTHDAY -> {
            val age = item.occurrence.dueDate.year - bill.startDueDate.year
            if (bill.includeAge && age > 0) stringResource(R.string.subtitle_turns, age) else stringResource(R.string.subtitle_birthday)
        }
        ItemKind.ROUTINE -> DateFormats.time(time)
    }
}

@Composable
fun priorityLabel(priority: Int): String = stringResource(
    when (priority) {
        0 -> R.string.priority_low
        2 -> R.string.priority_high
        else -> R.string.priority_normal
    },
)

@Composable
fun ItemRow(
    item: OccurrenceItem,
    category: Category?,
    status: DisplayStatus,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DuzupTheme.colors
    val paidOn = item.occurrence.paidAt?.atZone(item.bill.zoneId)?.toLocalDate()
    val due = dueText(status, item.occurrence.dueDate, today, paidOn, item.kind)
    val subtitle = itemSubtitle(item)
    val badge = if (item.kind == ItemKind.BILL) category else kindBadge(item.kind)
    val spoken = "${item.bill.title}, $subtitle, ${due.spoken}"

    RaisedCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        onClick = onClick,
        onClickLabel = stringResource(R.string.open_item),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryBadge(badge)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.bill.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.kind == ItemKind.BILL && item.effectiveAmountMinorUnits == null) colors.textMuted else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(due.caption, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (status == DisplayStatus.PAID) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = due.color, modifier = Modifier.size(14.dp))
                    }
                    Text(due.value, style = MaterialTheme.typography.titleSmall, color = due.color, maxLines = 1)
                }
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.padding(start = 4.dp).size(22.dp),
            )
        }
    }
}

@Composable
fun recurrenceSummary(recurrence: Recurrence): String = when (recurrence.rule) {
    RecurrenceRule.NONE -> stringResource(R.string.repeat_none)
    RecurrenceRule.DAILY -> stringResource(R.string.repeat_daily)
    RecurrenceRule.WEEKLY -> stringResource(R.string.repeat_weekly)
    RecurrenceRule.BIWEEKLY -> stringResource(R.string.repeat_biweekly)
    RecurrenceRule.MONTHLY -> stringResource(R.string.repeat_monthly)
    RecurrenceRule.QUARTERLY -> stringResource(R.string.repeat_quarterly)
    RecurrenceRule.YEARLY -> stringResource(R.string.repeat_yearly)
    RecurrenceRule.CUSTOM -> {
        val n = recurrence.interval
        val res = when (recurrence.unit) {
            IntervalUnit.DAY -> R.plurals.repeat_every_days
            IntervalUnit.WEEK -> R.plurals.repeat_every_weeks
            IntervalUnit.MONTH -> R.plurals.repeat_every_months
            IntervalUnit.YEAR -> R.plurals.repeat_every_years
        }
        pluralStringResource(res, n, n)
    }
}

/** "7 days before", "15 min before", "At start": one reminder offset in words, aware of the item's kind. */
@Composable
fun reminderOffsetLabel(minutes: Int, kind: ItemKind): String = when {
    minutes == 0 -> stringResource(
        when (kind) {
            ItemKind.BILL -> R.string.reminder_on_due_date
            ItemKind.EVENT -> R.string.reminder_at_start
            ItemKind.BIRTHDAY -> R.string.reminder_on_the_day
            else -> R.string.reminder_at_the_time
        },
    )
    ReminderOffsets.isWholeDays(minutes) -> {
        val d = minutes / ReminderOffsets.MINUTES_PER_DAY
        pluralStringResource(R.plurals.reminder_days_before, d, d)
    }
    minutes % ReminderOffsets.MINUTES_PER_HOUR == 0 -> {
        val h = minutes / ReminderOffsets.MINUTES_PER_HOUR
        pluralStringResource(R.plurals.reminder_hours_before, h, h)
    }
    else -> pluralStringResource(R.plurals.reminder_minutes_before, minutes, minutes)
}

@Composable
fun reminderSummary(offsets: List<Int>, time: LocalTime, kind: ItemKind = ItemKind.BILL): String {
    if (offsets.isEmpty()) return stringResource(R.string.reminders_none)
    val parts = offsets.sortedDescending().map { reminderOffsetLabel(it, kind) }
    return stringResource(R.string.reminders_summary, parts.joinToString(", "), DateFormats.time(time))
}
