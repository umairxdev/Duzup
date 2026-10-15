package com.duzup.app.domain.payments

import com.duzup.app.core.model.DisplayStatus
import com.duzup.app.core.model.Occurrence
import com.duzup.app.core.model.OccurrenceItem
import com.duzup.app.core.model.OccurrenceStatus
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Payment state transitions. Every function is idempotent: applying it to an occurrence that is
 * already in the target state returns the SAME instance, so repeated notification taps or worker
 * runs cannot overwrite a recorded payment.
 */
object PaymentRules {

    fun markPaid(
        occurrence: Occurrence,
        paidAt: Instant,
        actualAmountMinorUnits: Long?,
        note: String?,
    ): Occurrence {
        if (occurrence.status == OccurrenceStatus.PAID) return occurrence
        return occurrence.copy(
            status = OccurrenceStatus.PAID,
            paidAt = paidAt,
            actualAmountMinorUnits = actualAmountMinorUnits,
            paymentNote = note?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    /** Edit the recorded details of an already-paid occurrence. */
    fun editPayment(
        occurrence: Occurrence,
        paidAt: Instant,
        actualAmountMinorUnits: Long?,
        note: String?,
    ): Occurrence {
        if (occurrence.status != OccurrenceStatus.PAID) return occurrence
        return occurrence.copy(
            paidAt = paidAt,
            actualAmountMinorUnits = actualAmountMinorUnits,
            paymentNote = note?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    /** PAID -> PENDING (clears payment details). */
    fun undoPaid(occurrence: Occurrence): Occurrence {
        if (occurrence.status != OccurrenceStatus.PAID) return occurrence
        return occurrence.copy(
            status = OccurrenceStatus.PENDING,
            paidAt = null,
            actualAmountMinorUnits = null,
            paymentNote = null,
        )
    }

    fun skip(occurrence: Occurrence): Occurrence {
        if (occurrence.status != OccurrenceStatus.PENDING) return occurrence
        return occurrence.copy(status = OccurrenceStatus.SKIPPED)
    }

    /** SKIPPED -> PENDING. */
    fun restoreSkipped(occurrence: Occurrence): Occurrence {
        if (occurrence.status != OccurrenceStatus.SKIPPED) return occurrence
        return occurrence.copy(status = OccurrenceStatus.PENDING)
    }

    /**
     * Overdue is derived from the due instant, never stored. Only kinds that can be late (bills, tasks) are ever
     * overdue; an event, birthday, routine or note whose time has passed is merely past (see [isPast]).
     */
    fun isOverdue(item: OccurrenceItem, now: Instant): Boolean =
        item.kind.canBeOverdue &&
            item.occurrence.status == OccurrenceStatus.PENDING && item.dueInstant.isBefore(now)

    /** An unfinished occurrence of a kind that cannot be late, whose moment has gone by. */
    fun isPast(item: OccurrenceItem, now: Instant): Boolean =
        !item.kind.canBeOverdue &&
            item.occurrence.status == OccurrenceStatus.PENDING && item.dueInstant.isBefore(now)

    fun displayStatus(item: OccurrenceItem, now: Instant, today: LocalDate): DisplayStatus =
        when (item.occurrence.status) {
            OccurrenceStatus.PAID -> DisplayStatus.PAID
            OccurrenceStatus.SKIPPED -> DisplayStatus.SKIPPED
            OccurrenceStatus.PENDING -> when {
                isOverdue(item, now) -> DisplayStatus.OVERDUE
                isPast(item, now) -> DisplayStatus.PAST
                item.occurrence.dueDate == today -> DisplayStatus.DUE_TODAY
                daysUntil(item.occurrence.dueDate, today) <= DUE_SOON_DAYS -> DisplayStatus.DUE_SOON
                else -> DisplayStatus.UPCOMING
            }
        }

    /** Negative when the date is in the past. */
    fun daysUntil(dueDate: LocalDate, today: LocalDate): Long = ChronoUnit.DAYS.between(today, dueDate)

    const val DUE_SOON_DAYS = 3L
}
