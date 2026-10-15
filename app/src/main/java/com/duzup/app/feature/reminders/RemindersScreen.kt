package com.duzup.app.feature.reminders

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.duzup.app.R
import com.duzup.app.core.designsystem.BackButton
import com.duzup.app.core.designsystem.CenteredContent
import com.duzup.app.core.designsystem.EmptyState
import com.duzup.app.core.designsystem.PrimaryButton
import com.duzup.app.core.designsystem.RaisedCard
import com.duzup.app.core.designsystem.DuzupTheme
import com.duzup.app.core.designsystem.ScreenTitle
import com.duzup.app.core.designsystem.SecondaryButton
import com.duzup.app.core.designsystem.SectionLabel
import com.duzup.app.core.designsystem.listBottomPadding
import com.duzup.app.core.designsystem.statusBarTop
import com.duzup.app.core.model.ReminderKind
import com.duzup.app.core.notifications.NotificationStatus
import com.duzup.app.core.notifications.rememberNotificationPermissionRequester
import com.duzup.app.core.notifications.rememberNotificationStatus
import com.duzup.app.core.time.DateFormats
import java.time.ZoneId

@Composable
fun RemindersScreen(
    state: RemindersUiState,
    onBack: () -> Unit,
    onOpenBill: (String) -> Unit,
) {
    val colors = DuzupTheme.colors
    val context = LocalContext.current
    val status by rememberNotificationStatus()
    val requestPermission = rememberNotificationPermissionRequester { }

    CenteredContent {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = statusBarTop() + 4.dp,
                bottom = listBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "top") {
                Column {
                    BackButton(onBack)
                    ScreenTitle(stringResource(R.string.reminders_title), subtitle = stringResource(R.string.reminders_subtitle))
                }
            }
            item(key = "status") { StatusCard(status, context, requestPermission) }

            item(key = "scheduled-label") { SectionLabel(stringResource(R.string.reminders_scheduled)) }
            if (state.scheduled.isEmpty() && !state.isLoading) {
                item(key = "scheduled-empty") {
                    EmptyState(
                        title = stringResource(R.string.reminders_none_title),
                        message = stringResource(R.string.reminders_none_message),
                    )
                }
            }
            items(state.scheduled, key = { "s-" + it.reminder.id }) { ReminderCard(it, delivered = false, onOpenBill) }

            if (state.delivered.isNotEmpty()) {
                item(key = "delivered-label") { SectionLabel(stringResource(R.string.reminders_delivered)) }
                items(state.delivered, key = { "d-" + it.reminder.id }) { ReminderCard(it, delivered = true, onOpenBill) }
            }
        }
    }
}

@Composable
private fun StatusCard(status: NotificationStatus, context: Context, requestPermission: () -> Unit) {
    val colors = DuzupTheme.colors
    RaisedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusRow(stringResource(R.string.status_notifications), status.notificationsAllowed, stringResource(R.string.status_allowed), stringResource(R.string.status_blocked))
            StatusRow(stringResource(R.string.status_exact_alarms), status.exactAlarmsAllowed, stringResource(R.string.status_allowed), stringResource(R.string.status_inexact))
            if (!status.notificationsAllowed) {
                Text(stringResource(R.string.notifications_blocked_explainer), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                if (status.permissionRequestable) {
                    PrimaryButton(stringResource(R.string.allow_notifications), requestPermission, Modifier.fillMaxWidth())
                } else {
                    SecondaryButton(
                        stringResource(R.string.open_notification_settings),
                        { runCatching { context.startActivity(NotificationStatus.notificationSettingsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                        Modifier.fillMaxWidth(),
                    )
                }
            }
            Text(stringResource(R.string.battery_explainer), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
    }
}

@Composable
private fun StatusRow(title: String, ok: Boolean, okText: String, badText: String) {
    val colors = DuzupTheme.colors
    val value = if (ok) okText else badText
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$title: $value" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = if (ok) colors.paid else colors.dueSoon)
    }
}

@Composable
private fun ReminderCard(item: ReminderUi, delivered: Boolean, onOpenBill: (String) -> Unit) {
    val colors = DuzupTheme.colors
    val zone = ZoneId.systemDefault()
    val instant = if (delivered) item.reminder.deliveredAt ?: item.reminder.scheduledAt else item.reminder.scheduledAt
    val local = instant.atZone(zone)
    val whenText = "${DateFormats.date(local.toLocalDate())} · ${DateFormats.time(local.toLocalTime())}"
    val kind = stringResource(
        when (item.reminder.kind) {
            ReminderKind.UPCOMING -> R.string.reminder_kind_upcoming
            ReminderKind.DUE -> R.string.reminder_kind_due
            ReminderKind.OVERDUE -> R.string.reminder_kind_overdue
            ReminderKind.SNOOZE -> R.string.reminder_kind_snoozed
        },
    )
    RaisedCard(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "${item.title}, $kind, $whenText" },
        onClick = item.billId?.let { id -> { onOpenBill(id) } },
        onClickLabel = stringResource(R.string.open_bill),
        elevation = 4.dp,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                Text(whenText, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
            Text(kind, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
        }
    }
}
