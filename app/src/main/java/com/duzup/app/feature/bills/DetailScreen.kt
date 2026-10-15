package com.duzup.app.feature.bills

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.duzup.app.R
import com.duzup.app.core.designsystem.BackButton
import com.duzup.app.core.designsystem.CenteredContent
import com.duzup.app.core.designsystem.ConfirmDialog
import com.duzup.app.core.designsystem.DuzupDatePickerDialog
import com.duzup.app.core.designsystem.DuzupSheet
import com.duzup.app.core.designsystem.DuzupTheme
import com.duzup.app.core.designsystem.EmptyState
import com.duzup.app.core.designsystem.InsetTextField
import com.duzup.app.core.designsystem.NeuIconButton
import com.duzup.app.core.designsystem.PrimaryButton
import com.duzup.app.core.designsystem.RaisedCard
import com.duzup.app.core.designsystem.SecondaryButton
import com.duzup.app.core.designsystem.SectionLabel
import com.duzup.app.core.designsystem.SelectorField
import com.duzup.app.core.designsystem.listBottomPadding
import com.duzup.app.core.designsystem.rememberHaptics
import com.duzup.app.core.designsystem.statusBarTop
import com.duzup.app.core.model.Bill
import com.duzup.app.core.model.DisplayStatus
import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.Money
import com.duzup.app.core.model.Occurrence
import com.duzup.app.core.model.OccurrenceStatus
import com.duzup.app.core.model.SnoozeChoice
import com.duzup.app.core.notifications.NotificationStatus
import com.duzup.app.core.notifications.rememberNotificationStatus
import com.duzup.app.core.time.DateFormats
import java.time.Instant
import java.time.LocalDate

private sealed interface PaymentTarget {
    data class Mark(val occurrence: Occurrence) : PaymentTarget
    data class Edit(val occurrence: Occurrence) : PaymentTarget
}

/** Everything the details page can do. Defaults are no-ops so tests supply only what they need. */
class DetailCallbacks(
    val onBack: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onMarkPaid: (occurrenceId: String, amount: Long?, paidAt: Instant, note: String?) -> Unit = { _, _, _, _ -> },
    val onMarkDone: (String) -> Unit = {},
    val onEditPayment: (occurrenceId: String, amount: Long?, paidAt: Instant, note: String?) -> Unit = { _, _, _, _ -> },
    val onUndo: (String) -> Unit = {},
    val onSkip: (String) -> Unit = {},
    val onRestoreSkipped: (String) -> Unit = {},
    val onSnooze: (occurrenceId: String, choice: SnoozeChoice?) -> Unit = { _, _ -> },
    val onSetPinned: (Boolean) -> Unit = {},
    val onSetArchived: (Boolean) -> Unit = {},
    val onDelete: () -> Unit = {},
    val paidInstant: (LocalDate, Bill) -> Instant = { _, _ -> Instant.EPOCH },
)

@Composable
fun DetailScreen(state: DetailUiState, callbacks: DetailCallbacks) {
    val colors = DuzupTheme.colors
    val bill = state.bill
    val context = LocalContext.current
    val haptic = rememberHaptics()
    var paymentTarget by remember { mutableStateOf<PaymentTarget?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmArchive by rememberSaveable { mutableStateOf(false) }
    val notificationStatus by rememberNotificationStatus()

    CenteredContent {
        if (state.isLoading) return@CenteredContent
        if (bill == null) {
            EmptyState(
                title = stringResource(R.string.item_not_found_title),
                message = stringResource(R.string.item_not_found_message),
                actionLabel = stringResource(R.string.action_back),
                onAction = callbacks.onBack,
                modifier = Modifier.padding(top = statusBarTop() + 48.dp),
            )
            return@CenteredContent
        }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(top = statusBarTop() + 4.dp, bottom = listBottomPadding(32.dp))
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BackButton(callbacks.onBack)
                Spacer(Modifier.weight(1f))
                NeuIconButton(Icons.Rounded.Edit, stringResource(R.string.edit_item), callbacks.onEdit)
                Spacer(Modifier.width(8.dp))
                var menu by remember { mutableStateOf(false) }
                Column {
                    NeuIconButton(Icons.Rounded.MoreVert, stringResource(R.string.more_actions), { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = colors.surface) {
                        if (bill.kind == ItemKind.MEMO) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (bill.isPinned) R.string.unpin_note else R.string.pin_note), color = colors.textPrimary) },
                                leadingIcon = { Icon(Icons.Rounded.PushPin, null, tint = colors.textSecondary) },
                                onClick = { menu = false; callbacks.onSetPinned(!bill.isPinned) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(if (bill.isArchived) R.string.restore_item else R.string.archive_item), color = colors.textPrimary) },
                            leadingIcon = { Icon(if (bill.isArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, null, tint = colors.textSecondary) },
                            onClick = {
                                menu = false
                                if (bill.isArchived) callbacks.onSetArchived(false) else confirmArchive = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_item), color = colors.overdue) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = colors.overdue) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(if (bill.kind == ItemKind.BILL) state.category else kindBadge(bill.kind), size = 56.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        bill.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.textPrimary,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        if (bill.kind == ItemKind.BILL) state.category.displayName() else bill.kind.label(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                }
            }

            if (bill.isArchived) {
                RaisedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.archived_banner), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                        SecondaryButton(stringResource(R.string.restore_item), { callbacks.onSetArchived(false) }, leadingIcon = Icons.Rounded.Unarchive)
                    }
                }
            }

            NextCard(
                state = state,
                bill = bill,
                callbacks = callbacks,
                onMarkPaid = { paymentTarget = PaymentTarget.Mark(it) },
                onDoneHaptic = haptic,
            )
            if (bill.kind == ItemKind.ROUTINE && state.todays.isNotEmpty()) {
                TodayChecklist(state, bill, callbacks, haptic)
            }

            DetailsCard(bill, state)
            NotificationStatusCard(bill, notificationStatus, context, callbacks.onEdit)

            if (state.pending.size > 1 || (state.pending.isNotEmpty() && state.next == null)) {
                SectionLabel(stringResource(R.string.section_upcoming_overdue))
                state.pending.forEach { row ->
                    OccurrenceCard(
                        row = row, bill = bill, today = state.today,
                        actions = pendingActions(bill, row.occurrence, callbacks) { paymentTarget = PaymentTarget.Mark(row.occurrence) },
                    )
                }
            }

            SectionLabel(stringResource(historyLabel(bill.kind)))
            if (state.history.isEmpty()) {
                Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            } else {
                state.history.forEach { row ->
                    OccurrenceCard(
                        row = row, bill = bill, today = state.today,
                        actions = historyActions(bill, row, callbacks) { paymentTarget = PaymentTarget.Edit(row.occurrence) },
                    )
                }
            }
        }
    }

    paymentTarget?.let { target ->
        val occurrence = when (target) {
            is PaymentTarget.Mark -> target.occurrence
            is PaymentTarget.Edit -> target.occurrence
        }
        if (bill != null) {
            PaymentSheet(
                title = stringResource(if (target is PaymentTarget.Edit) R.string.edit_payment else R.string.mark_as_paid),
                bill = bill,
                initialAmount = occurrence.actualAmountMinorUnits ?: bill.amountMinorUnits,
                initialDate = occurrence.paidAt?.atZone(bill.zoneId)?.toLocalDate() ?: state.today,
                initialNote = occurrence.paymentNote.orEmpty(),
                onSave = { amount, date, note ->
                    val paidAt = callbacks.paidInstant(date, bill)
                    if (target is PaymentTarget.Edit) callbacks.onEditPayment(occurrence.id, amount, paidAt, note)
                    else {
                        callbacks.onMarkPaid(occurrence.id, amount, paidAt, note)
                        haptic()
                    }
                    paymentTarget = null
                },
                onDismiss = { paymentTarget = null },
            )
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_item_title),
            message = stringResource(R.string.delete_item_message),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { confirmDelete = false; callbacks.onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
    if (confirmArchive) {
        ConfirmDialog(
            title = stringResource(R.string.archive_item_title),
            message = stringResource(R.string.archive_item_message),
            confirmLabel = stringResource(R.string.archive_item),
            onConfirm = { confirmArchive = false; callbacks.onSetArchived(true) },
            onDismiss = { confirmArchive = false },
        )
    }
}

private fun historyLabel(kind: ItemKind): Int = when (kind) {
    ItemKind.BILL -> R.string.section_history_bill
    else -> R.string.section_history_other
}

@Composable
private fun nextLabel(kind: ItemKind): String = stringResource(
    when (kind) {
        ItemKind.BILL -> R.string.next_due
        ItemKind.EVENT -> R.string.next_event
        ItemKind.TASK -> R.string.next_task
        ItemKind.MEMO -> R.string.next_memo
        ItemKind.BIRTHDAY -> R.string.next_birthday
        ItemKind.ROUTINE -> R.string.next_routine
    },
)

@Composable
private fun NextCard(
    state: DetailUiState,
    bill: Bill,
    callbacks: DetailCallbacks,
    onMarkPaid: (Occurrence) -> Unit,
    onDoneHaptic: () -> Unit,
) {
    val colors = DuzupTheme.colors
    val next = state.next
    RaisedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel(nextLabel(bill.kind))
            if (next == null) {
                Text(stringResource(allDoneTitle(bill)), style = MaterialTheme.typography.titleLarge, color = colors.paid)
                Text(stringResource(allDoneBody(bill)), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            } else {
                val due = dueText(next.status, next.occurrence.dueDate, state.today, null, bill.kind)
                Text(DateFormats.fullDate(next.occurrence.dueDate), style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                if (bill.kind != ItemKind.BILL && !(bill.kind == ItemKind.EVENT && bill.allDay) && bill.kind != ItemKind.BIRTHDAY) {
                    Text(DateFormats.time(next.occurrence.dueTime), style = MaterialTheme.typography.titleMedium, color = colors.textSecondary)
                }
                if (bill.kind == ItemKind.EVENT && bill.allDay) {
                    Text(stringResource(R.string.subtitle_all_day), style = MaterialTheme.typography.titleMedium, color = colors.textSecondary)
                }
                Text(
                    due.spoken,
                    style = MaterialTheme.typography.titleSmall,
                    color = due.color,
                    modifier = Modifier.semantics { contentDescription = due.spoken },
                )
                if (bill.kind == ItemKind.BILL) {
                    bill.amountMinorUnits?.let {
                        Text(Money.format(it, bill.currencyCode), style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
                    }
                }
                if (bill.kind == ItemKind.BIRTHDAY && bill.includeAge) {
                    val age = next.occurrence.dueDate.year - bill.startDueDate.year
                    if (age > 0) Text(stringResource(R.string.subtitle_turns, age), style = MaterialTheme.typography.titleMedium, color = colors.textSecondary)
                }
                Spacer(Modifier.height(10.dp))

                when (bill.kind) {
                    ItemKind.BILL -> PrimaryButton(
                        text = stringResource(R.string.mark_as_paid),
                        onClick = { onMarkPaid(next.occurrence) },
                        leadingIcon = Icons.Rounded.CheckCircle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ItemKind.TASK, ItemKind.MEMO, ItemKind.ROUTINE -> PrimaryButton(
                        text = stringResource(R.string.mark_done),
                        onClick = { callbacks.onMarkDone(next.occurrence.id); onDoneHaptic() },
                        leadingIcon = Icons.Rounded.CheckCircle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ItemKind.EVENT, ItemKind.BIRTHDAY -> Unit
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    if (bill.kind != ItemKind.BIRTHDAY) {
                        SecondaryButton(
                            stringResource(if (bill.kind == ItemKind.EVENT) R.string.action_skip_event else R.string.action_skip),
                            { callbacks.onSkip(next.occurrence.id) },
                            Modifier.weight(1f),
                            Icons.Rounded.SkipNext,
                        )
                    }
                    SnoozeButton({ choice -> callbacks.onSnooze(next.occurrence.id, choice) }, Modifier.weight(1f))
                }
            }
        }
    }
}

private fun allDoneTitle(bill: Bill): Int = when (bill.kind) {
    ItemKind.BILL -> R.string.all_paid_up
    ItemKind.EVENT -> R.string.all_clear_event
    ItemKind.BIRTHDAY -> R.string.all_clear_birthday
    else -> R.string.all_done
}

private fun allDoneBody(bill: Bill): Int = when {
    bill.kind == ItemKind.BILL && bill.recurrence.isRecurring -> R.string.no_unpaid_recurring
    bill.kind == ItemKind.BILL -> R.string.no_unpaid_single
    bill.recurrence.isRecurring || bill.kind == ItemKind.ROUTINE || bill.kind == ItemKind.BIRTHDAY -> R.string.no_next_recurring
    else -> R.string.no_next_single
}

/** "Remind later" with a choice of how long, defaulting to nothing: each option is explicit. */
@Composable
private fun SnoozeButton(onChoose: (SnoozeChoice?) -> Unit, modifier: Modifier) {
    val colors = DuzupTheme.colors
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        SecondaryButton(stringResource(R.string.action_snooze), { open = true }, Modifier.fillMaxWidth(), Icons.Rounded.Snooze)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.surface) {
            SnoozeChoice.entries.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(stringResource(snoozeLabel(choice)), color = colors.textPrimary) },
                    onClick = { open = false; onChoose(choice) },
                )
            }
        }
    }
}

fun snoozeLabel(choice: SnoozeChoice): Int = when (choice) {
    SnoozeChoice.MINUTES_10 -> R.string.snooze_10m
    SnoozeChoice.HOUR_1 -> R.string.snooze_1h
    SnoozeChoice.HOURS_3 -> R.string.snooze_3h
    SnoozeChoice.TOMORROW -> R.string.snooze_tomorrow
}

@Composable
private fun TodayChecklist(state: DetailUiState, bill: Bill, callbacks: DetailCallbacks, haptic: () -> Unit) {
    val colors = DuzupTheme.colors
    RaisedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel(stringResource(R.string.routine_today))
            state.todays.forEach { row ->
                val done = row.occurrence.status == OccurrenceStatus.PAID
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        DateFormats.time(row.occurrence.dueTime),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (done) colors.paid else colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    when (row.occurrence.status) {
                        OccurrenceStatus.PAID -> TextButton(onClick = { callbacks.onUndo(row.occurrence.id) }, modifier = Modifier.height(48.dp)) {
                            Text(stringResource(R.string.routine_done_undo), color = colors.paid, style = MaterialTheme.typography.labelLarge)
                        }
                        OccurrenceStatus.PENDING -> TextButton(
                            onClick = { callbacks.onMarkDone(row.occurrence.id); haptic() },
                            modifier = Modifier.height(48.dp),
                        ) {
                            Text(stringResource(R.string.routine_mark), color = colors.accentText, style = MaterialTheme.typography.labelLarge)
                        }
                        OccurrenceStatus.SKIPPED -> Text(stringResource(R.string.status_skipped), color = colors.textMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsCard(bill: Bill, state: DetailUiState) {
    val colors = DuzupTheme.colors
    RaisedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (bill.kind) {
                ItemKind.BILL -> {
                    DetailLine(stringResource(R.string.field_repeat), recurrenceSummary(bill.recurrence))
                    bill.endDate?.takeIf { bill.recurrence.isRecurring }?.let { DetailLine(stringResource(R.string.field_ends), DateFormats.date(it)) }
                    DetailLine(
                        stringResource(R.string.field_amount),
                        bill.amountMinorUnits?.let { Money.format(it, bill.currencyCode) } ?: stringResource(R.string.no_amount),
                    )
                }
                ItemKind.EVENT -> {
                    DetailLine(
                        stringResource(R.string.detail_when),
                        when {
                            bill.allDay -> stringResource(R.string.subtitle_all_day)
                            bill.endTime != null -> stringResource(R.string.subtitle_time_range, DateFormats.time(bill.dueTime), DateFormats.time(bill.endTime))
                            else -> DateFormats.time(bill.dueTime)
                        },
                    )
                    bill.location?.let { DetailLine(stringResource(R.string.detail_place), it) }
                    DetailLine(stringResource(R.string.field_repeat), recurrenceSummary(bill.recurrence))
                }
                ItemKind.TASK -> {
                    DetailLine(stringResource(R.string.field_priority), priorityLabel(bill.priority))
                    DetailLine(stringResource(R.string.field_repeat), recurrenceSummary(bill.recurrence))
                }
                ItemKind.MEMO -> {
                    if (bill.scheduled) DetailLine(stringResource(R.string.detail_remind_on), "${DateFormats.date(bill.startDueDate)} · ${DateFormats.time(bill.dueTime)}")
                }
                ItemKind.BIRTHDAY -> {
                    DetailLine(stringResource(R.string.detail_birthday_date), DateFormats.date(bill.startDueDate))
                }
                ItemKind.ROUTINE -> {
                    DetailLine(
                        stringResource(R.string.field_routine_times),
                        (bill.routineTimes.ifEmpty { listOf(bill.dueTime) }).joinToString(", ") { DateFormats.time(it) },
                    )
                    DetailLine(
                        stringResource(R.string.field_routine_days),
                        if (bill.weekdays.isEmpty()) stringResource(R.string.routine_every_day_short)
                        else bill.weekdays.sorted().joinToString(", ") { it.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) },
                    )
                }
            }
            if (bill.scheduled) {
                DetailLine(stringResource(R.string.field_reminders), reminderSummary(bill.reminderOffsets, bill.dueTime, bill.kind))
            }
            bill.notes?.let { DetailLine(stringResource(if (bill.kind == ItemKind.MEMO) R.string.detail_note else R.string.field_notes), it) }
            if (bill.kind != ItemKind.MEMO || bill.scheduled) {
                Text(stringResource(R.string.edit_future_note), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    val colors = DuzupTheme.colors
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" }) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
    }
}

@Composable
private fun NotificationStatusCard(bill: Bill, status: NotificationStatus, context: Context, onEdit: () -> Unit) {
    val colors = DuzupTheme.colors
    data class Notice(val text: Int, val action: Int?, val onAction: (() -> Unit)?)
    val notice = when {
        !bill.scheduled -> null
        bill.reminderOffsets.isEmpty() -> Notice(R.string.notice_no_reminders, R.string.edit_item, onEdit)
        !status.notificationsAllowed -> Notice(
            R.string.notice_notifications_blocked, R.string.open_settings,
            { context.startActivity(NotificationStatus.notificationSettingsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
        )
        !status.exactAlarmsAllowed -> Notice(R.string.notice_inexact_alarms, null, null)
        else -> null
    }
    if (notice == null) return
    RaisedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (status.notificationsAllowed) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                null,
                tint = colors.dueSoon,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(stringResource(notice.text), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.weight(1f))
            if (notice.action != null && notice.onAction != null) {
                TextButton(onClick = notice.onAction) { Text(stringResource(notice.action), color = colors.accentText) }
            }
        }
    }
}

private data class OccAction(val label: Int, val icon: ImageVector, val onClick: () -> Unit)

private fun pendingActions(bill: Bill, occ: Occurrence, c: DetailCallbacks, markPaid: () -> Unit): List<OccAction> = buildList {
    when (bill.kind) {
        ItemKind.BILL -> add(OccAction(R.string.action_mark_paid, Icons.Rounded.CheckCircle, markPaid))
        ItemKind.TASK, ItemKind.MEMO, ItemKind.ROUTINE -> add(OccAction(R.string.mark_done_short, Icons.Rounded.CheckCircle) { c.onMarkDone(occ.id) })
        else -> Unit
    }
    if (bill.kind != ItemKind.BIRTHDAY) add(OccAction(R.string.action_skip, Icons.Rounded.SkipNext) { c.onSkip(occ.id) })
    add(OccAction(R.string.action_snooze, Icons.Rounded.Snooze) { c.onSnooze(occ.id, null) })
}

private fun historyActions(bill: Bill, row: OccurrenceRowUi, c: DetailCallbacks, editPayment: () -> Unit): List<OccAction> {
    val occ = row.occurrence
    return when (occ.status) {
        OccurrenceStatus.PAID -> buildList {
            if (bill.kind == ItemKind.BILL) add(OccAction(R.string.action_edit_payment, Icons.Rounded.Edit, editPayment))
            add(OccAction(R.string.action_undo, Icons.Rounded.Undo) { c.onUndo(occ.id) })
        }
        OccurrenceStatus.SKIPPED -> listOf(OccAction(R.string.action_restore_skipped, Icons.Rounded.Restore) { c.onRestoreSkipped(occ.id) })
        OccurrenceStatus.PENDING -> listOf(OccAction(R.string.mark_done_short, Icons.Rounded.CheckCircle) { c.onMarkDone(occ.id) })
    }
}

@Composable
private fun OccurrenceCard(row: OccurrenceRowUi, bill: Bill, today: LocalDate, actions: List<OccAction>) {
    val colors = DuzupTheme.colors
    val occ = row.occurrence
    val paidOn = occ.paidAt?.atZone(bill.zoneId)?.toLocalDate()
    val due = dueText(row.status, occ.dueDate, today, paidOn, bill.kind)
    val amount = if (bill.kind == ItemKind.BILL) (occ.actualAmountMinorUnits ?: bill.amountMinorUnits)?.let { Money.format(it, bill.currencyCode) } else null
    var menu by remember { mutableStateOf(false) }
    val spoken = buildString {
        append(DateFormats.date(occ.dueDate)).append(", ").append(due.spoken)
        amount?.let { append(", ").append(it) }
        occ.paymentNote?.let { append(", ").append(it) }
    }
    RaisedCard(Modifier.fillMaxWidth(), elevation = 4.dp) {
        Row(
            Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp).semantics(mergeDescendants = true) { contentDescription = spoken },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(
                    if (bill.kind == ItemKind.ROUTINE) "${DateFormats.date(occ.dueDate)} · ${DateFormats.time(occ.dueTime)}" else DateFormats.date(occ.dueDate),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (row.status == DisplayStatus.PAID) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = due.color, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text("${due.caption} ${due.value}", style = MaterialTheme.typography.bodySmall, color = due.color)
                }
                if (amount != null) Text(amount, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                occ.paymentNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted) }
            }
            if (actions.isNotEmpty()) {
                Column {
                    com.duzup.app.core.designsystem.FlatIconButton(
                        Icons.Rounded.MoreVert, stringResource(R.string.more_actions), { menu = true }, tint = colors.textMuted,
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = colors.surface) {
                        actions.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(stringResource(action.label), color = colors.textPrimary) },
                                leadingIcon = { Icon(action.icon, null, tint = colors.textSecondary) },
                                onClick = { menu = false; action.onClick() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentSheet(
    title: String,
    bill: Bill,
    initialAmount: Long?,
    initialDate: LocalDate,
    initialNote: String,
    onSave: (amount: Long?, date: LocalDate, note: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by rememberSaveable { mutableStateOf(initialAmount?.let { Money.toInputText(it, bill.currencyCode) }.orEmpty()) }
    var date by rememberSaveable { mutableStateOf(initialDate) }
    var note by rememberSaveable { mutableStateOf(initialNote) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var showErrors by rememberSaveable { mutableStateOf(false) }

    val parsed = Money.parse(amountText, bill.currencyCode)
    val amountError = when (parsed) {
        is Money.Parsed.Ok, Money.Parsed.Empty -> null
        Money.Parsed.Invalid -> stringResource(R.string.error_amount_invalid)
        Money.Parsed.TooManyDecimals -> stringResource(R.string.error_amount_decimals)
        Money.Parsed.NotPositive -> stringResource(R.string.error_amount_positive)
        Money.Parsed.TooLarge -> stringResource(R.string.error_amount_large)
    }

    DuzupSheet(onDismiss) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = DuzupTheme.colors.textPrimary,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp).semantics { heading() },
        )
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            InsetTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = stringResource(R.string.payment_amount_paid),
                placeholder = stringResource(R.string.field_amount_hint),
                prefix = Money.symbolFor(bill.currencyCode),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                error = if (showErrors) amountError else null,
            )
            SelectorField(
                label = stringResource(R.string.payment_date),
                value = DateFormats.fullDate(date),
                placeholder = "",
                leadingIcon = Icons.Rounded.CalendarToday,
                onClick = { pickingDate = true },
            )
            InsetTextField(
                value = note,
                onValueChange = { note = it.take(140) },
                label = stringResource(R.string.payment_note_optional),
                placeholder = stringResource(R.string.payment_note_hint),
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            )
            PrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = {
                    showErrors = true
                    if (amountError == null) {
                        onSave((parsed as? Money.Parsed.Ok)?.minorUnits, date, note.trim().ifEmpty { null })
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (pickingDate) {
        DuzupDatePickerDialog(
            initial = date,
            onConfirm = { date = it; pickingDate = false },
            onDismiss = { pickingDate = false },
        )
    }
}
