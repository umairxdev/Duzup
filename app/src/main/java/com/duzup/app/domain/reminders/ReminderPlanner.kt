package com.duzup.app.domain.reminders

import com.duzup.app.core.model.ReminderKind
import com.duzup.app.core.model.ReminderOffsets
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class PlannedReminder(
    val id: String,
    val occurrenceId: String,
    val kind: ReminderKind,
    val offsetMinutes: Int,
    val triggerAt: Instant,
)

/** Unit choices when typing a custom "how long before" value. */
enum class OffsetUnit(val minutes: Int) {
    MINUTES(1),
    HOURS(ReminderOffsets.MINUTES_PER_HOUR),
    DAYS(ReminderOffsets.MINUTES_PER_DAY),
}

/**
 * Pure reminder math. A reminder identity is stable (`occurrenceId|offset`), which is what prevents
 * duplicate notifications across reboots, edits and repeated reconcile runs.
 *
 * Offsets are minutes before the due moment. Whole days subtract wall-clock days (so "1 day before 9:00" stays 9:00
 * across a DST change); any other value subtracts elapsed time.
 */
object ReminderPlanner {
    const val OVERDUE_DELAY_DAYS = 1L

    /** A reminder that was missed by less than this (e.g. phone was off) is still delivered late. */
    val MISSED_GRACE: Duration = Duration.ofHours(6)

    /** Parses a user-entered "how long before" value; null unless it is a whole number within 0..365 days. */
    fun parseOffset(text: String, unit: OffsetUnit): Int? {
        val value = text.trim().toLongOrNull() ?: return null
        if (value < 0) return null
        val minutes = value * unit.minutes
        return if (minutes <= ReminderOffsets.MAX_MINUTES) minutes.toInt() else null
    }

    fun idFor(occurrenceId: String, kind: ReminderKind, offsetMinutes: Int): String = when (kind) {
        ReminderKind.SNOOZE -> "$occurrenceId|SNOOZE"
        ReminderKind.OVERDUE -> "$occurrenceId|OVERDUE"
        else -> "$occurrenceId|${ReminderOffsets.idSuffix(offsetMinutes)}"
    }

    /** When a reminder with [offsetMinutes] fires for a due moment of [dueDate] at [dueTime]. */
    fun triggerFor(dueDate: LocalDate, dueTime: LocalTime, zone: ZoneId, offsetMinutes: Int): Instant =
        if (ReminderOffsets.isWholeDays(offsetMinutes)) {
            instantOf(dueDate.minusDays((offsetMinutes / ReminderOffsets.MINUTES_PER_DAY).toLong()), dueTime, zone)
        } else {
            instantOf(dueDate, dueTime, zone).minus(Duration.ofMinutes(offsetMinutes.toLong()))
        }

    /** All reminders (including ones already in the past) that an occurrence should have. */
    fun plan(
        occurrenceId: String,
        dueDate: LocalDate,
        dueTime: LocalTime,
        zone: ZoneId,
        offsets: List<Int>,
        overdueReminder: Boolean,
    ): List<PlannedReminder> {
        val planned = offsets
            .filter { it in 0..ReminderOffsets.MAX_MINUTES }
            .distinct()
            .sortedDescending()
            .map { offset ->
                val kind = if (offset == 0) ReminderKind.DUE else ReminderKind.UPCOMING
                PlannedReminder(
                    id = idFor(occurrenceId, kind, offset),
                    occurrenceId = occurrenceId,
                    kind = kind,
                    offsetMinutes = offset,
                    triggerAt = triggerFor(dueDate, dueTime, zone, offset),
                )
            }
            .toMutableList()
        if (overdueReminder) {
            planned += PlannedReminder(
                id = idFor(occurrenceId, ReminderKind.OVERDUE, 0),
                occurrenceId = occurrenceId,
                kind = ReminderKind.OVERDUE,
                offsetMinutes = 0,
                triggerAt = instantOf(dueDate.plusDays(OVERDUE_DELAY_DAYS), dueTime, zone),
            )
        }
        return planned
    }

    /** DST-safe: a local time inside a spring-forward gap resolves to the next valid instant. */
    fun instantOf(date: LocalDate, time: LocalTime, zone: ZoneId): Instant =
        LocalDateTime.of(date, time).atZone(zone).toInstant()
}

/** Pure quiet-hours maths. The window may cross midnight (e.g. 22:00 to 08:00). */
object QuietHours {
    fun isQuiet(time: LocalTime, start: LocalTime, end: LocalTime): Boolean = when {
        start == end -> false
        start < end -> time >= start && time < end
        else -> time >= start || time < end
    }

    /**
     * The first instant at or after [trigger] when a notification may be shown.
     *
     * If [notLaterThan] is given and delaying would push the reminder past it (e.g. past the start of the event
     * it is about), the reminder is NOT delayed: something happening during quiet hours still gets through.
     */
    fun nextAllowed(
        trigger: Instant,
        zone: ZoneId,
        enabled: Boolean,
        start: LocalTime,
        end: LocalTime,
        notLaterThan: Instant?,
    ): Instant {
        if (!enabled || start == end) return trigger
        val local = trigger.atZone(zone).toLocalDateTime()
        if (!isQuiet(local.toLocalTime(), start, end)) return trigger
        val endDay = if (start < end || local.toLocalTime() < end) local.toLocalDate() else local.toLocalDate().plusDays(1)
        val adjusted = LocalDateTime.of(endDay, end).atZone(zone).toInstant()
        if (notLaterThan != null && adjusted.isAfter(notLaterThan)) return trigger
        return adjusted
    }
}
