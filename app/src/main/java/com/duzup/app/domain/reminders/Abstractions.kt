package com.duzup.app.domain.reminders

import com.duzup.app.core.model.Bill
import com.duzup.app.core.model.Occurrence
import com.duzup.app.core.model.ReminderRow

/** Testable seam over AlarmManager. Scheduling the same reminderId again replaces the earlier alarm. */
interface AlarmScheduler {
    fun schedule(reminderId: String, triggerAtMillis: Long)
    fun cancel(reminderId: String)

    /** True when precise alarms are permitted; otherwise reminders fall back to inexact delivery. */
    fun canScheduleExact(): Boolean
}

/** Testable seam over the notification system. */
interface ReminderNotifier {
    fun show(occurrence: Occurrence, bill: Bill, reminder: ReminderRow, hideAmount: Boolean)
    fun cancel(occurrenceId: String)
    fun cancelAll()
}
