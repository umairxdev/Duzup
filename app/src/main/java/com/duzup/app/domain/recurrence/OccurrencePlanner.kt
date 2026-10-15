package com.duzup.app.domain.recurrence

import com.duzup.app.core.model.Bill
import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.Occurrence
import com.duzup.app.core.model.OccurrenceStatus
import java.time.LocalDate
import java.time.LocalTime

/** One generated occurrence: a due date and the time of day it is due. */
data class Slot(val date: LocalDate, val time: LocalTime)

/** Result of reconciling a template against the occurrences that already exist. */
data class OccurrenceDiff(
    val insertSlots: List<Slot>,
    val deleteIds: List<String>,
)

/**
 * Pure, idempotent occurrence reconciliation.
 *
 * - Occurrences are generated lazily for a bounded window: [ItemKind.lookbackDays] back from today (recurring items
 *   only) up to the requested horizon.
 * - PAID / SKIPPED occurrences are history and are never deleted or altered here.
 * - PENDING occurrences that no longer belong to the template are removed; the rest are kept.
 * - Everything except routines has one occurrence per date; routines have one per date AND time of day.
 */
object OccurrencePlanner {
    const val DEFAULT_HORIZON_MONTHS = 6L

    /** Times of day an item is due: all routine times, otherwise the single due time. */
    fun timesOf(item: Bill): List<LocalTime> =
        if (item.kind == ItemKind.ROUTINE && item.routineTimes.isNotEmpty()) item.routineTimes.distinct().sorted()
        else listOf(item.dueTime)

    private fun dayApplies(item: Bill, date: LocalDate): Boolean =
        item.kind != ItemKind.ROUTINE || item.weekdays.isEmpty() || date.dayOfWeek in item.weekdays

    fun desiredSlots(item: Bill, today: LocalDate, through: LocalDate): List<Slot> {
        if (!item.scheduled) return emptyList()
        val dates = if (!item.recurrence.isRecurring) {
            listOf(item.startDueDate)
        } else {
            RecurrenceCalculator.datesBetween(
                start = item.startDueDate,
                recurrence = item.recurrence,
                endDate = item.endDate,
                from = maxOf(item.startDueDate, today.minusDays(item.kind.lookbackDays)),
                to = through,
            )
        }
        val times = timesOf(item)
        return dates.filter { dayApplies(item, it) }.flatMap { date -> times.map { Slot(date, it) } }
    }

    /** Whether an existing occurrence at [date] / [time] still belongs to the template's schedule. */
    fun isScheduled(item: Bill, date: LocalDate, time: LocalTime): Boolean =
        item.scheduled &&
            RecurrenceCalculator.isScheduled(item.startDueDate, item.recurrence, date) &&
            dayApplies(item, date) &&
            time in timesOf(item) &&
            !(item.recurrence.isRecurring && item.endDate != null && date > item.endDate)

    fun plan(
        item: Bill,
        existing: List<Occurrence>,
        today: LocalDate,
        through: LocalDate,
    ): OccurrenceDiff {
        val desired = desiredSlots(item, today, through)

        val deleteIds = existing
            .filter { it.status == OccurrenceStatus.PENDING && !isScheduled(item, it.dueDate, it.dueTime) }
            .map { it.id }
        val deleted = deleteIds.toHashSet()
        val remaining = existing.filter { it.id !in deleted }

        // A date that already has an occurrence (even a paid one) must not get a second one when only the time
        // changed; routines are the exception because they legitimately repeat within a day.
        val insert = if (item.kind == ItemKind.ROUTINE) {
            val taken = remaining.mapTo(HashSet()) { Slot(it.dueDate, it.dueTime) }
            desired.filter { it !in taken }
        } else {
            val takenDates = remaining.mapTo(HashSet()) { it.dueDate }
            desired.filter { it.date !in takenDates }
        }
        return OccurrenceDiff(insertSlots = insert, deleteIds = deleteIds)
    }

    fun defaultHorizon(today: LocalDate): LocalDate = today.plusMonths(DEFAULT_HORIZON_MONTHS)
}
