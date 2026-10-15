package com.duzup.app.core.notifications

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.duzup.app.di.BackgroundEntryPoint
import kotlinx.coroutines.launch

/** Runs [block] off the main thread while keeping the receiver alive; always finishes the broadcast. */
private fun BroadcastReceiver.async(context: Context, block: suspend (BackgroundEntryPoint) -> Unit) {
    val pending = goAsync()
    val entry = BackgroundEntryPoint.from(context)
    entry.applicationScope().launch {
        try {
            block(entry)
        } finally {
            pending.finish()
        }
    }
}

/** Delivers one reminder. Minimal work: look up the row, claim delivery atomically, show. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AndroidAlarmScheduler.ACTION_FIRE_REMINDER) return
        val reminderId = intent.data?.lastPathSegment ?: return
        async(context) { it.reminderEngine().deliver(reminderId) }
    }
}

/** Notification actions: Mark Paid and Remind Later. Idempotent and independent of the UI process. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val occurrenceId = intent.getStringExtra(NotificationHelper.EXTRA_OCCURRENCE_ID) ?: return
        when (intent.action) {
            NotificationHelper.ACTION_MARK_PAID -> async(context) { it.billRepository().markPaid(occurrenceId) }
            NotificationHelper.ACTION_SNOOZE -> async(context) { it.billRepository().snoozeWith(occurrenceId) }
            // "Got it": nothing to record, just clear the notification (events and birthdays).
            NotificationHelper.ACTION_GOT_IT -> async(context) { it.reminderNotifier().cancel(occurrenceId) }
        }
    }
}

/** Boot, time/timezone change, app update and exact-alarm permission changes: re-plan everything. */
class SystemEventsReceiver : BroadcastReceiver() {
    @Suppress("InlinedApi") // the action string is a compile-time constant; the broadcast only exists on API 31+
    override fun onReceive(context: Context, intent: Intent) {
        val handled = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
        if (intent.action !in handled) return
        async(context) { it.billRepository().reconcileAll() }
    }
}
