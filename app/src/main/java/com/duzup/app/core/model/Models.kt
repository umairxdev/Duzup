package com.duzup.app.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

enum class OccurrenceStatus { PENDING, PAID, SKIPPED }

enum class RecurrenceRule { NONE, DAILY, WEEKLY, BIWEEKLY, MONTHLY, QUARTERLY, YEARLY, CUSTOM }

enum class IntervalUnit { DAY, WEEK, MONTH, YEAR }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * What a reminder item is. Every kind shares the same engine (recurrence, occurrences, alarms, snooze, backup);
 * only the form fields, wording and a few rules differ.
 */
enum class ItemKind {
    BILL, EVENT, TASK, MEMO, BIRTHDAY, ROUTINE;

    /** Only bills carry an amount, a currency, a category and payment history. */
    val hasMoney: Boolean get() = this == BILL

    /** Bills and tasks become "late" when their time passes unfinished; events, birthdays, routines and notes just pass. */
    val canBeOverdue: Boolean get() = this == BILL || this == TASK

    /** How far back unfinished occurrences are generated and kept visible. */
    val lookbackDays: Long get() = if (canBeOverdue) 90L else 7L
}

/** What the user sees. OVERDUE is derived from the due instant, never stored. */
enum class DisplayStatus {
    UPCOMING, DUE_SOON, DUE_TODAY, OVERDUE,

    /** The time has passed for something that cannot be late (an event, a birthday, a routine, a note). */
    PAST,
    PAID, SKIPPED,
}

/** A recurrence = preset rule, or CUSTOM with [interval] x [unit]. */
data class Recurrence(
    val rule: RecurrenceRule = RecurrenceRule.NONE,
    val interval: Int = 1,
    val unit: IntervalUnit = IntervalUnit.MONTH,
) {
    val isRecurring: Boolean get() = rule != RecurrenceRule.NONE

    companion object {
        val None = Recurrence()
        const val MAX_INTERVAL = 999
    }
}

/** Reminder offsets are stored as minutes before the due moment. Whole days are wall-clock days (DST-safe). */
object ReminderOffsets {
    const val MINUTES_PER_HOUR = 60
    const val MINUTES_PER_DAY = 1440
    const val MAX_MINUTES = 365 * MINUTES_PER_DAY

    fun days(vararg d: Int): List<Int> = d.map { it * MINUTES_PER_DAY }

    fun isWholeDays(minutes: Int): Boolean = minutes % MINUTES_PER_DAY == 0

    fun defaultsFor(kind: ItemKind): List<Int> = when (kind) {
        ItemKind.BILL -> days(7, 3, 1, 0)
        ItemKind.EVENT -> listOf(MINUTES_PER_DAY, 60, 15, 0)
        ItemKind.TASK -> listOf(0)
        ItemKind.MEMO -> listOf(0)
        ItemKind.BIRTHDAY -> days(1, 0)
        ItemKind.ROUTINE -> listOf(0)
    }

    /** Quick-pick choices shown as chips in the form, per kind. */
    fun choicesFor(kind: ItemKind): List<Int> = when (kind) {
        ItemKind.BILL -> days(7, 3, 1, 0)
        ItemKind.EVENT -> listOf(MINUTES_PER_DAY, 60, 15, 5, 0)
        ItemKind.TASK -> listOf(MINUTES_PER_DAY, 60, 15, 0)
        ItemKind.MEMO -> listOf(60, 15, 0)
        ItemKind.BIRTHDAY -> days(7, 1, 0)
        ItemKind.ROUTINE -> listOf(15, 5, 0)
    }

    /** Short, stable id fragment: "0", "7d", "3h", "15m". */
    fun idSuffix(minutes: Int): String = when {
        minutes == 0 -> "0"
        minutes % MINUTES_PER_DAY == 0 -> "${minutes / MINUTES_PER_DAY}d"
        minutes % MINUTES_PER_HOUR == 0 -> "${minutes / MINUTES_PER_HOUR}h"
        else -> "${minutes}m"
    }
}

/**
 * A reminder template. Despite the historical name it is not only a bill: [kind] says whether it is a bill, event,
 * task, note, birthday or routine. Each due date it produces is an [Occurrence].
 */
data class Bill(
    val id: String,
    val title: String,
    val categoryId: String,
    val notes: String?,
    val amountMinorUnits: Long?,
    val currencyCode: String,
    val startDueDate: LocalDate,
    val dueTime: LocalTime,
    val zoneId: ZoneId,
    val recurrence: Recurrence,
    val endDate: LocalDate?,
    /** Minutes before the due moment (see [ReminderOffsets]). */
    val reminderOffsets: List<Int>,
    val isArchived: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val kind: ItemKind = ItemKind.BILL,
    /** False for a plain note without a reminder: it never produces occurrences or alarms. */
    val scheduled: Boolean = true,
    val endTime: LocalTime? = null,
    val allDay: Boolean = false,
    val location: String? = null,
    /** 0 low, 1 normal, 2 high (tasks). */
    val priority: Int = 1,
    val isPinned: Boolean = false,
    /** Birthdays: whether notifications mention the age. */
    val includeAge: Boolean = true,
    /** Routines: every time of day (first one mirrors [dueTime]). */
    val routineTimes: List<LocalTime> = emptyList(),
    /** Routines: days it applies to; empty means every day. */
    val weekdays: Set<DayOfWeek> = emptySet(),
)

data class Occurrence(
    val id: String,
    val billId: String,
    val dueDate: LocalDate,
    val dueTime: LocalTime,
    val status: OccurrenceStatus,
    val paidAt: Instant?,
    val actualAmountMinorUnits: Long?,
    val paymentNote: String?,
    val createdAt: Instant,
)

/** An occurrence joined with its template. */
data class OccurrenceItem(val occurrence: Occurrence, val bill: Bill) {
    val id: String get() = occurrence.id
    val kind: ItemKind get() = bill.kind
    val dueInstant: Instant
        get() = LocalDateTime.of(occurrence.dueDate, occurrence.dueTime).atZone(bill.zoneId).toInstant()

    /** Amount that applies to this occurrence: actual paid amount if recorded, else the bill's amount. */
    val effectiveAmountMinorUnits: Long?
        get() = occurrence.actualAmountMinorUnits ?: bill.amountMinorUnits
}

data class Category(
    val id: String,
    val name: String,
    val iconKey: String,
    val colorArgb: Long?,
    val builtIn: Boolean,
    val sortOrder: Int,
)

object BuiltInCategories {
    const val UTILITIES = "utilities"
    const val INTERNET = "internet"
    const val SUBSCRIPTIONS = "subscriptions"
    const val RENT = "rent"
    const val LOANS = "loans"
    const val INSURANCE = "insurance"
    const val EDUCATION = "education"
    const val VEHICLE = "vehicle"
    const val OTHER = "other"

    /** id to icon key. Display names come from string resources (see CategoryNames). */
    val all: List<Pair<String, String>> = listOf(
        UTILITIES to "bolt",
        INTERNET to "wifi",
        SUBSCRIPTIONS to "card",
        RENT to "home",
        LOANS to "loan",
        INSURANCE to "shield",
        EDUCATION to "school",
        VEHICLE to "car",
        OTHER to "more",
    )
    val ids: Set<String> = all.map { it.first }.toSet()
}

enum class ReminderKind { UPCOMING, DUE, OVERDUE, SNOOZE }

data class ReminderRow(
    val id: String,
    val occurrenceId: String,
    val kind: ReminderKind,
    /** Minutes before the due moment; 0 for due/overdue/snooze reminders. */
    val offsetMinutes: Int,
    val scheduledAt: Instant,
    val deliveredAt: Instant?,
    val snoozeUntil: Instant?,
    val canceled: Boolean,
)

/** How long "remind me later" waits. TOMORROW means the next morning at the default reminder time. */
enum class SnoozeChoice(val minutes: Long) {
    MINUTES_10(10),
    HOUR_1(60),
    HOURS_3(180),
    TOMORROW(-1),
}
