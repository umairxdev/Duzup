package com.duzup.app.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.duzup.app.domain.reminders.AlarmScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AlarmManager-backed scheduler. Each reminder gets a unique PendingIntent identity through its data
 * URI (`duzup://reminder/<id>`), so re-scheduling replaces and cancelling targets exactly one alarm.
 * Uses exact-while-idle alarms when permitted and honestly falls back to inexact-while-idle otherwise.
 */
@Singleton
class AndroidAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : AlarmScheduler {

    private val alarmManager: AlarmManager get() = context.getSystemService(AlarmManager::class.java)

    override fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    override fun schedule(reminderId: String, triggerAtMillis: Long) {
        val pi = pendingIntent(reminderId)
        val exact = canScheduleExact()
        try {
            if (exact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } catch (_: SecurityException) {
            // Exact permission revoked between the check and the call.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    override fun cancel(reminderId: String) {
        val pi = pendingIntent(reminderId)
        alarmManager.cancel(pi)
        pi.cancel()
    }

    private fun pendingIntent(reminderId: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE_REMINDER)
            .setData(Uri.Builder().scheme("duzup").authority("reminder").appendPath(reminderId).build())
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_FIRE_REMINDER = "com.duzup.app.action.FIRE_REMINDER"
    }
}
