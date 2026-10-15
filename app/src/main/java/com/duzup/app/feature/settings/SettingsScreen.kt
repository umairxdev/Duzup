package com.duzup.app.feature.settings

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.duzup.app.BuildConfig
import com.duzup.app.R
import com.duzup.app.core.backup.RestoreMode
import com.duzup.app.core.designsystem.CenteredContent
import com.duzup.app.core.designsystem.ConfirmDialog
import com.duzup.app.core.designsystem.DuzupSheet
import com.duzup.app.core.designsystem.DuzupTheme
import com.duzup.app.core.designsystem.DuzupTimePickerDialog
import com.duzup.app.core.designsystem.FilterPill
import com.duzup.app.core.designsystem.PrimaryButton
import com.duzup.app.core.designsystem.RaisedCard
import com.duzup.app.core.designsystem.SecondaryButton
import com.duzup.app.core.designsystem.SectionLabel
import com.duzup.app.core.designsystem.SelectorField
import com.duzup.app.core.designsystem.SwitchRow
import com.duzup.app.core.designsystem.listBottomPadding
import com.duzup.app.core.designsystem.statusBarTop
import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.SnoozeChoice
import com.duzup.app.core.model.ThemeMode
import com.duzup.app.core.notifications.NotificationHelper
import com.duzup.app.core.notifications.NotificationStatus
import com.duzup.app.core.notifications.rememberNotificationPermissionRequester
import com.duzup.app.core.notifications.rememberNotificationStatus
import com.duzup.app.core.preferences.UserPreferences
import com.duzup.app.core.time.DateFormats
import com.duzup.app.feature.addedit.CurrencySheet
import com.duzup.app.feature.addedit.CustomOffsetDialog
import com.duzup.app.feature.addedit.ReminderChips
import com.duzup.app.feature.bills.chipLabel
import com.duzup.app.feature.bills.snoozeLabel
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Currency

private enum class SettingsDialog { NONE, CURRENCY, TIME, QUIET_START, QUIET_END, CUSTOM_OFFSET, CHANNELS, PRIVACY, RESET, ABOUT }

/** Everything Settings can do; no-op defaults so tests supply only what they care about. */
class SettingsCallbacks(
    val onTheme: (ThemeMode) -> Unit = {},
    val onCurrency: (String) -> Unit = {},
    val onReminderTime: (LocalTime) -> Unit = {},
    val onToggleOffset: (ItemKind, Int) -> Unit = { _, _ -> },
    val onAddOffset: (ItemKind, Int) -> Unit = { _, _ -> },
    val onOverdue: (Boolean) -> Unit = {},
    val onSnooze: (SnoozeChoice) -> Unit = {},
    val onQuietEnabled: (Boolean) -> Unit = {},
    val onQuietStart: (LocalTime) -> Unit = {},
    val onQuietEnd: (LocalTime) -> Unit = {},
    val onFirstDay: (DayOfWeek) -> Unit = {},
    val onHaptics: (Boolean) -> Unit = {},
    val onReduceMotion: (Boolean) -> Unit = {},
    val onHideAmounts: (Boolean) -> Unit = {},
    val onExport: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onConfirmImport: (RestoreMode) -> Unit = {},
    val onCancelImport: () -> Unit = {},
    val onReset: () -> Unit = {},
    val onOpenReminders: () -> Unit = {},
    val onOpenStats: () -> Unit = {},
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    prefs: UserPreferences?,
    busy: Boolean,
    pendingImport: PendingImport?,
    callbacks: SettingsCallbacks,
) {
    val colors = DuzupTheme.colors
    val context = LocalContext.current
    var dialog by rememberSaveable { mutableStateOf(SettingsDialog.NONE) }
    var defaultsKind by rememberSaveable { mutableStateOf(ItemKind.BILL) }
    val status by rememberNotificationStatus()
    val requestPermission = rememberNotificationPermissionRequester { }

    CenteredContent {
        if (prefs == null) return@CenteredContent
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(top = statusBarTop() + 12.dp, bottom = listBottomPadding())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )

            // ---- Stats live here now ----
            RaisedCard(Modifier.fillMaxWidth(), onClick = callbacks.onOpenStats, onClickLabel = stringResource(R.string.settings_stats)) {
                val statsLabel = stringResource(R.string.settings_stats)
                Row(Modifier.padding(16.dp).semantics(mergeDescendants = true) { contentDescription = statsLabel }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_stats), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        Text(stringResource(R.string.settings_stats_desc), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.textMuted)
                }
            }

            // ---- Look & feel ----
            Section(R.string.settings_appearance) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterPill(
                            label = stringResource(
                                when (mode) {
                                    ThemeMode.SYSTEM -> R.string.theme_system
                                    ThemeMode.LIGHT -> R.string.theme_light
                                    ThemeMode.DARK -> R.string.theme_dark
                                },
                            ),
                            selected = prefs.theme == mode,
                            onClick = { callbacks.onTheme(mode) },
                            role = Role.RadioButton,
                        )
                    }
                }
                Text(stringResource(R.string.settings_first_day), style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterPill(stringResource(R.string.day_monday), prefs.firstDayOfWeek == DayOfWeek.MONDAY, { callbacks.onFirstDay(DayOfWeek.MONDAY) }, role = Role.RadioButton)
                    FilterPill(stringResource(R.string.day_sunday), prefs.firstDayOfWeek == DayOfWeek.SUNDAY, { callbacks.onFirstDay(DayOfWeek.SUNDAY) }, role = Role.RadioButton)
                }
                SwitchRow(stringResource(R.string.settings_haptics), stringResource(R.string.settings_haptics_desc), prefs.haptics, callbacks.onHaptics)
                SwitchRow(stringResource(R.string.settings_reduce_motion), stringResource(R.string.settings_reduce_motion_desc), prefs.reduceMotion, callbacks.onReduceMotion)
            }

            // ---- Reminder defaults ----
            Section(R.string.settings_defaults) {
                ValueRow(
                    title = stringResource(R.string.field_currency),
                    value = "${prefs.currencyCode} · ${runCatching { Currency.getInstance(prefs.currencyCode).displayName }.getOrDefault("")}",
                    onClick = { dialog = SettingsDialog.CURRENCY },
                )
                ValueRow(
                    title = stringResource(R.string.settings_default_time),
                    value = DateFormats.time(prefs.reminderTime),
                    onClick = { dialog = SettingsDialog.TIME },
                )
                Text(stringResource(R.string.settings_defaults_for), style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ItemKind.entries.forEach { kind ->
                        FilterPill(kind.chipLabel(), defaultsKind == kind, { defaultsKind = kind }, role = Role.RadioButton)
                    }
                }
                ReminderChips(
                    kind = defaultsKind,
                    offsets = prefs.offsetsFor(defaultsKind).toSet(),
                    onToggle = { callbacks.onToggleOffset(defaultsKind, it) },
                    onCustom = { dialog = SettingsDialog.CUSTOM_OFFSET },
                    title = stringResource(R.string.settings_defaults_when),
                )
                Text(stringResource(R.string.settings_default_offsets_note), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                SwitchRow(stringResource(R.string.settings_overdue_reminders), stringResource(R.string.settings_overdue_reminders_desc), prefs.overdueReminders, callbacks.onOverdue)
                Text(stringResource(R.string.settings_snooze), style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SnoozeChoice.entries.forEach { choice ->
                        FilterPill(stringResource(snoozeLabel(choice)), prefs.snooze == choice, { callbacks.onSnooze(choice) }, role = Role.RadioButton)
                    }
                }
            }

            // ---- Quiet hours ----
            Section(R.string.settings_quiet) {
                SwitchRow(stringResource(R.string.settings_quiet_switch), stringResource(R.string.settings_quiet_desc), prefs.quietHoursEnabled, callbacks.onQuietEnabled)
                if (prefs.quietHoursEnabled) {
                    SelectorField(
                        label = stringResource(R.string.settings_quiet_from),
                        value = DateFormats.time(prefs.quietStart),
                        placeholder = "",
                        leadingIcon = Icons.Rounded.Schedule,
                        onClick = { dialog = SettingsDialog.QUIET_START },
                    )
                    SelectorField(
                        label = stringResource(R.string.settings_quiet_to),
                        value = DateFormats.time(prefs.quietEnd),
                        placeholder = "",
                        leadingIcon = Icons.Rounded.Schedule,
                        onClick = { dialog = SettingsDialog.QUIET_END },
                    )
                    Text(stringResource(R.string.settings_quiet_note), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                }
            }

            // ---- Notifications ----
            Section(R.string.settings_notifications) {
                NotificationsBlock(status, context, requestPermission, callbacks.onOpenReminders) { dialog = SettingsDialog.CHANNELS }
                SwitchRow(stringResource(R.string.settings_hide_amounts), stringResource(R.string.settings_hide_amounts_desc), prefs.hideAmountsInNotifications, callbacks.onHideAmounts)
            }

            // ---- Backup ----
            Section(R.string.settings_backup) {
                Text(stringResource(R.string.backup_explainer), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                Text(stringResource(R.string.backup_warning), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton(stringResource(R.string.backup_export), callbacks.onExport, Modifier.weight(1f), enabled = !busy)
                    SecondaryButton(stringResource(R.string.backup_import), callbacks.onImport, Modifier.weight(1f), enabled = !busy)
                }
                if (busy) {
                    CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
                }
            }

            // ---- About ----
            Section(R.string.settings_about) {
                ValueRow(stringResource(R.string.about_version), BuildConfig.VERSION_NAME, onClick = { dialog = SettingsDialog.ABOUT })
                ValueRow(stringResource(R.string.privacy_title), stringResource(R.string.privacy_row_hint), onClick = { dialog = SettingsDialog.PRIVACY })
                SecondaryButton(
                    text = stringResource(R.string.reset_data),
                    onClick = { dialog = SettingsDialog.RESET },
                    destructive = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    val is24 = DateFormat.is24HourFormat(context)
    when (dialog) {
        SettingsDialog.NONE -> Unit
        SettingsDialog.CURRENCY -> CurrencySheet(prefs?.currencyCode.orEmpty(), { callbacks.onCurrency(it); dialog = SettingsDialog.NONE }, { dialog = SettingsDialog.NONE })
        SettingsDialog.TIME -> DuzupTimePickerDialog(
            initial = prefs?.reminderTime ?: UserPreferences.DEFAULT_TIME,
            is24Hour = is24,
            onConfirm = { callbacks.onReminderTime(it); dialog = SettingsDialog.NONE },
            onDismiss = { dialog = SettingsDialog.NONE },
        )
        SettingsDialog.QUIET_START -> DuzupTimePickerDialog(
            initial = prefs?.quietStart ?: UserPreferences.DEFAULT_QUIET_START,
            is24Hour = is24,
            onConfirm = { callbacks.onQuietStart(it); dialog = SettingsDialog.NONE },
            onDismiss = { dialog = SettingsDialog.NONE },
        )
        SettingsDialog.QUIET_END -> DuzupTimePickerDialog(
            initial = prefs?.quietEnd ?: UserPreferences.DEFAULT_QUIET_END,
            is24Hour = is24,
            onConfirm = { callbacks.onQuietEnd(it); dialog = SettingsDialog.NONE },
            onDismiss = { dialog = SettingsDialog.NONE },
        )
        SettingsDialog.CUSTOM_OFFSET -> CustomOffsetDialog({ callbacks.onAddOffset(defaultsKind, it); dialog = SettingsDialog.NONE }, { dialog = SettingsDialog.NONE })
        SettingsDialog.CHANNELS -> ChannelsSheet(context) { dialog = SettingsDialog.NONE }
        SettingsDialog.PRIVACY -> InfoDialog(R.string.privacy_title, R.string.privacy_body) { dialog = SettingsDialog.NONE }
        SettingsDialog.ABOUT -> InfoDialog(R.string.about_title, R.string.about_body) { dialog = SettingsDialog.NONE }
        SettingsDialog.RESET -> ConfirmDialog(
            title = stringResource(R.string.reset_title),
            message = stringResource(R.string.reset_message),
            confirmLabel = stringResource(R.string.reset_confirm),
            destructive = true,
            onConfirm = { callbacks.onReset(); dialog = SettingsDialog.NONE },
            onDismiss = { dialog = SettingsDialog.NONE },
        )
    }

    pendingImport?.let { pending ->
        ImportDialog(pending, callbacks.onConfirmImport, callbacks.onCancelImport)
    }
}

@Composable
private fun Section(title: Int, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(title), Modifier.padding(start = 4.dp))
        RaisedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        }
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    val colors = DuzupTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$title, $value" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.textMuted)
    }
}

@Composable
private fun NotificationsBlock(
    status: NotificationStatus,
    context: Context,
    requestPermission: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenChannels: () -> Unit,
) {
    val colors = DuzupTheme.colors
    fun open(intent: Intent) = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    StatusLine(
        title = stringResource(R.string.status_notifications),
        ok = status.notificationsAllowed,
        okText = stringResource(R.string.status_allowed),
        badText = stringResource(R.string.status_blocked),
    )
    if (!status.notificationsAllowed) {
        Text(stringResource(R.string.notifications_blocked_explainer), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        if (status.permissionRequestable) {
            PrimaryButton(stringResource(R.string.allow_notifications), requestPermission, Modifier.fillMaxWidth())
        }
    }
    SecondaryButton(
        stringResource(R.string.open_notification_settings),
        { open(NotificationStatus.notificationSettingsIntent(context)) },
        Modifier.fillMaxWidth(),
    )
    SecondaryButton(stringResource(R.string.channels_button), onOpenChannels, Modifier.fillMaxWidth())

    StatusLine(
        title = stringResource(R.string.status_exact_alarms),
        ok = status.exactAlarmsAllowed,
        okText = stringResource(R.string.status_allowed),
        badText = stringResource(R.string.status_inexact),
    )
    if (!status.exactAlarmsAllowed) {
        Text(stringResource(R.string.exact_alarm_explainer), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        SecondaryButton(
            stringResource(R.string.allow_exact_alarms),
            { open(NotificationStatus.exactAlarmSettingsIntent(context)) },
            Modifier.fillMaxWidth(),
        )
    }

    Text(stringResource(R.string.battery_title), style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
    Text(stringResource(R.string.battery_explainer), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
    SecondaryButton(stringResource(R.string.battery_settings), { open(NotificationStatus.batterySettingsIntent()) }, Modifier.fillMaxWidth())
    SecondaryButton(stringResource(R.string.reminders_diagnostics), onOpenReminders, Modifier.fillMaxWidth())
}

/** One row per notification channel, so each type of reminder can be muted or tuned on its own. */
@Composable
private fun ChannelsSheet(context: Context, onDismiss: () -> Unit) {
    val colors = DuzupTheme.colors
    DuzupSheet(onDismiss) {
        Text(
            stringResource(R.string.channels_title),
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        Text(stringResource(R.string.channels_desc), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            NotificationHelper.CHANNELS.forEach { (id, nameRes) ->
                val name = stringResource(nameRes)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable(role = Role.Button, onClickLabel = name) {
                            runCatching {
                                context.startActivity(
                                    NotificationStatus.channelSettingsIntent(context, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.textMuted)
                }
            }
        }
    }
}

@Composable
private fun StatusLine(title: String, ok: Boolean, okText: String, badText: String) {
    val colors = DuzupTheme.colors
    val text = if (ok) okText else badText
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$title: $text" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Text(
            // The words carry the meaning; color only reinforces it.
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (ok) colors.paid else colors.dueSoon,
        )
    }
}

@Composable
private fun InfoDialog(title: Int, body: Int, onDismiss: () -> Unit) {
    val colors = DuzupTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        title = { Text(stringResource(title), color = colors.textPrimary, style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                stringResource(body),
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok), color = colors.accentText) } },
    )
}

@Composable
private fun ImportDialog(pending: PendingImport, onConfirm: (RestoreMode) -> Unit, onCancel: () -> Unit) {
    val colors = DuzupTheme.colors
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = colors.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        title = { Text(stringResource(R.string.import_title), color = colors.textPrimary, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.import_summary, pending.bills, pending.occurrences, pending.categories),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(stringResource(R.string.import_replace_desc), color = colors.textMuted, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.import_merge_desc), color = colors.textMuted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onConfirm(RestoreMode.MERGE) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.import_merge), color = colors.accentText)
                }
                TextButton(onClick = { onConfirm(RestoreMode.REPLACE) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.import_replace), color = colors.overdue)
                }
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel), color = colors.textSecondary) } },
    )
}
