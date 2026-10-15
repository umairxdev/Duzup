package com.duzup.app.core.notifications

import com.duzup.app.core.model.ReminderKind
import com.duzup.app.core.model.ReminderOffsets

/** How close to the due moment a reminder fires; picks the wording pool. */
enum class NotificationPhase { EARLY, TOMORROW, HOURS, MINUTES, NOW, LATE, BACK }

/** Pure choices behind the notification wording, kept separate from Android so they can be unit-tested. */
object NotificationCopy {

    fun phaseFor(kind: ReminderKind, offsetMinutes: Int): NotificationPhase = when (kind) {
        ReminderKind.SNOOZE -> NotificationPhase.BACK
        ReminderKind.OVERDUE -> NotificationPhase.LATE
        ReminderKind.DUE -> NotificationPhase.NOW
        ReminderKind.UPCOMING -> when {
            offsetMinutes >= 2 * ReminderOffsets.MINUTES_PER_DAY -> NotificationPhase.EARLY
            offsetMinutes >= ReminderOffsets.MINUTES_PER_DAY -> NotificationPhase.TOMORROW
            offsetMinutes >= ReminderOffsets.MINUTES_PER_HOUR -> NotificationPhase.HOURS
            else -> NotificationPhase.MINUTES
        }
    }

    /**
     * Deterministic variant choice: the same reminder always gets the same line (so a re-delivery or a re-plan never
     * changes the wording), while different reminders spread across the pool.
     */
    fun variantIndex(seed: String, poolSize: Int): Int {
        require(poolSize > 0) { "pool must not be empty" }
        return (seed.hashCode() and Int.MAX_VALUE) % poolSize
    }

    /** Whole days for the "in N days" phrase, rounded up so "1.5 days" never reads as 1. */
    fun daysFor(offsetMinutes: Int): Int =
        (offsetMinutes + ReminderOffsets.MINUTES_PER_DAY - 1) / ReminderOffsets.MINUTES_PER_DAY
}
