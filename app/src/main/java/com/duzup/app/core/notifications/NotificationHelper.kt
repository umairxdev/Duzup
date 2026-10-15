package com.duzup.app.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.media.AudioAttributes
import android.provider.Settings
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.duzup.app.MainActivity
import com.duzup.app.R
import com.duzup.app.core.model.Bill
import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.Money
import com.duzup.app.core.model.Occurrence
import com.duzup.app.core.model.ReminderKind
import com.duzup.app.core.model.ReminderRow
import com.duzup.app.core.time.DateFormats
import com.duzup.app.core.time.TimeProvider
import com.duzup.app.domain.reminders.ReminderNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val time: TimeProvider,
) : ReminderNotifier {

    private val manager: NotificationManager get() = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        fun channel(id: String, name: Int, desc: Int, importance: Int) =
            NotificationChannel(id, context.getString(name), importance).apply {
                description = context.getString(desc)
                // Sound and vibration are on by default; each can be changed per channel in system settings.
                setSound(
                    Settings.System.DEFAULT_NOTIFICATION_URI,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
            }
        // Heads-up banners are on by default: every channel is HIGH importance. Channel importance can't be raised
        // after creation, so the earlier channels (which used a lower importance) are retired.
        LEGACY_CHANNELS.forEach { manager.deleteNotificationChannel(it) }
        manager.createNotificationChannels(
            listOf(
                channel(CHANNEL_DUE, R.string.channel_due_name, R.string.channel_due_desc, NotificationManager.IMPORTANCE_HIGH),
                channel(CHANNEL_UPCOMING, R.string.channel_upcoming_name, R.string.channel_upcoming_desc, NotificationManager.IMPORTANCE_HIGH),
                channel(CHANNEL_OVERDUE, R.string.channel_overdue_name, R.string.channel_overdue_desc, NotificationManager.IMPORTANCE_HIGH),
                channel(CHANNEL_EVENTS, R.string.channel_events_name, R.string.channel_events_desc, NotificationManager.IMPORTANCE_HIGH),
                channel(CHANNEL_TASKS, R.string.channel_tasks_name, R.string.channel_tasks_desc, NotificationManager.IMPORTANCE_HIGH),
                channel(CHANNEL_NOTES, R.string.channel_notes_name, R.string.channel_notes_desc, NotificationManager.IMPORTANCE_HIGH),
                channel(CHANNEL_BIRTHDAYS, R.string.channel_birthdays_name, R.string.channel_birthdays_desc, NotificationManager.IMPORTANCE_HIGH),
                channel(CHANNEL_ROUTINES, R.string.channel_routines_name, R.string.channel_routines_desc, NotificationManager.IMPORTANCE_HIGH),
            ),
        )
    }

    override fun show(occurrence: Occurrence, bill: Bill, reminder: ReminderRow, hideAmount: Boolean) {
        createChannels()
        val now = time.now()
        val dueInstant = LocalDateTime.of(occurrence.dueDate, occurrence.dueTime).atZone(bill.zoneId).toInstant()
        val phase = NotificationCopy.phaseFor(reminder.kind, reminder.offsetMinutes)
        val kind = bill.kind

        val whenPhrase = whenPhrase(phase, reminder, occurrence, bill, now)
        val age = if (kind == ItemKind.BIRTHDAY && bill.includeAge) occurrence.dueDate.year - bill.startDueDate.year else 0
        val pool = context.resources.getStringArray(copyArray(kind, phase, withAge = age > 0))
        val line = pool[NotificationCopy.variantIndex(reminder.id, pool.size)]
            .format(bill.title, whenPhrase, DateFormats.time(occurrence.dueTime), if (age > 0) age.toString() else "")

        // Optional amount: never show a made-up zero.
        val amount = bill.amountMinorUnits?.takeIf { kind == ItemKind.BILL && !hideAmount }
            ?.let { Money.format(it, bill.currencyCode) }
        val text = if (amount != null) context.getString(R.string.notif_text_with_amount, line, amount) else line
        val title = bill.title

        val builder = NotificationCompat.Builder(context, channelFor(kind, phase, dueInstant.isBefore(now)))
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(if (kind == ItemKind.EVENT) NotificationCompat.CATEGORY_EVENT else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            // Each reminder for an item reuses one notification slot (so the shade never fills up), but every
            // reminder must still alert: do NOT set onlyAlertOnce, or a later reminder would replace an
            // ignored earlier one silently.
            .setOnlyAlertOnce(false)
            .setGroup(GROUP_KEY)
            .setVisibility(if (hideAmount) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openIntent(bill.id, occurrence.id))

        when (kind) {
            ItemKind.BILL -> builder.addAction(0, context.getString(R.string.action_paid_it), actionIntent(ACTION_MARK_PAID, occurrence.id))
            ItemKind.TASK, ItemKind.MEMO, ItemKind.ROUTINE ->
                builder.addAction(0, context.getString(R.string.action_done), actionIntent(ACTION_MARK_PAID, occurrence.id))
            ItemKind.EVENT, ItemKind.BIRTHDAY ->
                builder.addAction(0, context.getString(R.string.action_got_it), actionIntent(ACTION_GOT_IT, occurrence.id))
        }
        builder.addAction(0, context.getString(R.string.action_snooze), actionIntent(ACTION_SNOOZE, occurrence.id))
        builder.addAction(0, context.getString(R.string.action_open), openIntent(bill.id, occurrence.id))

        if (canPost()) {
            manager.notify(notificationId(occurrence.id), builder.build())
            refreshSummary()
        }
    }

    private fun whenPhrase(
        phase: NotificationPhase,
        reminder: ReminderRow,
        occurrence: Occurrence,
        bill: Bill,
        now: java.time.Instant,
    ): String {
        val r = context.resources
        return when (phase) {
            NotificationPhase.EARLY -> NotificationCopy.daysFor(reminder.offsetMinutes).let { r.getQuantityString(R.plurals.phrase_in_days, it, it) }
            NotificationPhase.TOMORROW -> context.getString(R.string.phrase_tomorrow)
            NotificationPhase.HOURS -> (reminder.offsetMinutes / 60).let { r.getQuantityString(R.plurals.phrase_in_hours, it, it) }
            NotificationPhase.MINUTES -> r.getQuantityString(R.plurals.phrase_in_minutes, reminder.offsetMinutes, reminder.offsetMinutes)
            NotificationPhase.NOW -> context.getString(R.string.phrase_now)
            NotificationPhase.BACK -> context.getString(R.string.phrase_now)
            NotificationPhase.LATE -> {
                val today = now.atZone(bill.zoneId).toLocalDate()
                when (ChronoUnit.DAYS.between(occurrence.dueDate, today)) {
                    0L -> context.getString(R.string.phrase_earlier_today)
                    1L -> context.getString(R.string.phrase_yesterday)
                    else -> context.getString(R.string.phrase_since, DateFormats.dayMonth(occurrence.dueDate))
                }
            }
        }
    }

    /** Bills keep the three date-based channels; every other kind has its own so it can be muted separately. */
    private fun channelFor(kind: ItemKind, phase: NotificationPhase, pastDue: Boolean): String = when (kind) {
        ItemKind.BILL -> when {
            phase == NotificationPhase.LATE || (phase == NotificationPhase.BACK && pastDue) -> CHANNEL_OVERDUE
            phase == NotificationPhase.NOW || phase == NotificationPhase.BACK -> CHANNEL_DUE
            else -> CHANNEL_UPCOMING
        }
        ItemKind.EVENT -> CHANNEL_EVENTS
        ItemKind.TASK -> CHANNEL_TASKS
        ItemKind.MEMO -> CHANNEL_NOTES
        ItemKind.BIRTHDAY -> CHANNEL_BIRTHDAYS
        ItemKind.ROUTINE -> CHANNEL_ROUTINES
    }

    /** Wording pool for a kind and phase; kinds without a dedicated pool fall back to the generic one. */
    private fun copyArray(kind: ItemKind, phase: NotificationPhase, withAge: Boolean): Int {
        val generic = when (phase) {
            NotificationPhase.EARLY -> R.array.notif_generic_early
            NotificationPhase.TOMORROW -> R.array.notif_generic_tomorrow
            NotificationPhase.HOURS -> R.array.notif_generic_hours
            NotificationPhase.MINUTES -> R.array.notif_generic_minutes
            NotificationPhase.NOW -> R.array.notif_generic_now
            NotificationPhase.LATE -> R.array.notif_generic_late
            NotificationPhase.BACK -> R.array.notif_generic_back
        }
        return when (kind) {
            ItemKind.BILL -> when (phase) {
                NotificationPhase.EARLY -> R.array.notif_bill_early
                NotificationPhase.TOMORROW -> R.array.notif_bill_tomorrow
                NotificationPhase.NOW -> R.array.notif_bill_now
                NotificationPhase.LATE -> R.array.notif_bill_late
                NotificationPhase.BACK -> R.array.notif_bill_back
                else -> generic
            }
            ItemKind.EVENT -> when (phase) {
                NotificationPhase.EARLY -> R.array.notif_event_early
                NotificationPhase.TOMORROW -> R.array.notif_event_tomorrow
                NotificationPhase.HOURS -> R.array.notif_event_hours
                NotificationPhase.MINUTES -> R.array.notif_event_minutes
                NotificationPhase.NOW -> R.array.notif_event_now
                NotificationPhase.BACK -> R.array.notif_event_back
                else -> generic
            }
            ItemKind.TASK -> when (phase) {
                NotificationPhase.TOMORROW -> R.array.notif_task_tomorrow
                NotificationPhase.NOW -> R.array.notif_task_now
                NotificationPhase.LATE -> R.array.notif_task_late
                NotificationPhase.BACK -> R.array.notif_task_back
                else -> generic
            }
            ItemKind.MEMO -> when (phase) {
                NotificationPhase.NOW -> R.array.notif_memo_now
                NotificationPhase.BACK -> R.array.notif_memo_back
                else -> generic
            }
            ItemKind.BIRTHDAY -> when {
                withAge && phase == NotificationPhase.EARLY -> R.array.notif_birthday_age_early
                withAge && phase == NotificationPhase.TOMORROW -> R.array.notif_birthday_age_tomorrow
                withAge && phase == NotificationPhase.NOW -> R.array.notif_birthday_age_now
                phase == NotificationPhase.EARLY -> R.array.notif_birthday_early
                phase == NotificationPhase.TOMORROW -> R.array.notif_birthday_tomorrow
                phase == NotificationPhase.NOW -> R.array.notif_birthday_now
                phase == NotificationPhase.BACK -> R.array.notif_birthday_back
                else -> generic
            }
            ItemKind.ROUTINE -> when (phase) {
                NotificationPhase.MINUTES -> R.array.notif_routine_minutes
                NotificationPhase.NOW -> R.array.notif_routine_now
                NotificationPhase.BACK -> R.array.notif_routine_back
                else -> generic
            }
        }
    }

    override fun cancel(occurrenceId: String) {
        manager.cancel(notificationId(occurrenceId))
        refreshSummary()
    }

    override fun cancelAll() {
        manager.cancelAll()
    }

    fun areNotificationsEnabled(): Boolean = canPost()

    private fun canPost(): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    /** Groups several reminders under one summary; removes the summary when fewer than two remain. */
    private fun refreshSummary() {
        val active = manager.activeNotifications.filter { it.notification.group == GROUP_KEY && it.id != SUMMARY_ID }
        if (active.size < 2) {
            manager.cancel(SUMMARY_ID)
            return
        }
        val summary = NotificationCompat.Builder(context, CHANNEL_UPCOMING)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.resources.getQuantityString(R.plurals.notif_summary, active.size, active.size))
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setContentIntent(openIntent(null, "summary"))
            .build()
        manager.notify(SUMMARY_ID, summary)
    }

    private fun openIntent(itemId: String?, key: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_ITEM)
            .setData(Uri.Builder().scheme("duzup").authority("open").appendPath(key).build())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (itemId != null) intent.putExtra(EXTRA_ITEM_ID, itemId)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun actionIntent(action: String, occurrenceId: String): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .setData(Uri.Builder().scheme("duzup").authority("action").appendPath(action).appendPath(occurrenceId).build())
            .putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        private val LEGACY_CHANNELS = listOf(
            "due_today", "upcoming_reminders", "overdue_bills", "events", "tasks", "notes", "birthdays", "routines",
        )
        const val CHANNEL_DUE = "due_today_v2"
        const val CHANNEL_UPCOMING = "upcoming_reminders_v2"
        const val CHANNEL_OVERDUE = "overdue_bills_v2"
        const val CHANNEL_EVENTS = "events_v2"
        const val CHANNEL_TASKS = "tasks_v2"
        const val CHANNEL_NOTES = "notes_v2"
        const val CHANNEL_BIRTHDAYS = "birthdays_v2"
        const val CHANNEL_ROUTINES = "routines_v2"

        /** Every channel with the string resource that names it, for the settings shortcuts. */
        val CHANNELS: List<Pair<String, Int>> = listOf(
            CHANNEL_DUE to R.string.channel_due_name,
            CHANNEL_UPCOMING to R.string.channel_upcoming_name,
            CHANNEL_OVERDUE to R.string.channel_overdue_name,
            CHANNEL_EVENTS to R.string.channel_events_name,
            CHANNEL_TASKS to R.string.channel_tasks_name,
            CHANNEL_NOTES to R.string.channel_notes_name,
            CHANNEL_BIRTHDAYS to R.string.channel_birthdays_name,
            CHANNEL_ROUTINES to R.string.channel_routines_name,
        )

        const val GROUP_KEY = "com.duzup.app.REMINDERS"
        const val SUMMARY_ID = 1

        const val ACTION_MARK_PAID = "com.duzup.app.action.MARK_PAID"
        const val ACTION_GOT_IT = "com.duzup.app.action.GOT_IT"
        const val ACTION_SNOOZE = "com.duzup.app.action.SNOOZE"
        const val ACTION_OPEN_ITEM = "com.duzup.app.action.OPEN_ITEM"
        const val EXTRA_OCCURRENCE_ID = "occurrence_id"
        const val EXTRA_ITEM_ID = "item_id"

        fun notificationId(occurrenceId: String): Int = (occurrenceId.hashCode() and 0x7FFFFFFF).coerceAtLeast(2)
    }
}
