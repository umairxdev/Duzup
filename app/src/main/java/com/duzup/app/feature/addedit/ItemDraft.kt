package com.duzup.app.feature.addedit

import com.duzup.app.core.model.Bill
import com.duzup.app.core.model.BuiltInCategories
import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.Money
import com.duzup.app.core.model.Recurrence
import com.duzup.app.core.model.RecurrenceRule
import com.duzup.app.core.model.ReminderOffsets
import com.duzup.app.core.preferences.UserPreferences
import com.duzup.app.domain.quickadd.QuickAddResult
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Everything the form edits, for any kind of item. Fields that do not apply to the current [kind] are ignored when
 * saving. Amount stays as text until validated so partial input is never lost.
 */
data class ItemDraft(
    val kind: ItemKind = ItemKind.BILL,
    val title: String = "",
    val categoryId: String = BuiltInCategories.UTILITIES,
    val amountText: String = "",
    val currencyCode: String = "USD",
    val dueDate: LocalDate? = null,
    /** Due time for bills/tasks/notes, start time for events, reminder time for birthdays. */
    val time: LocalTime = UserPreferences.DEFAULT_TIME,
    val allDay: Boolean = false,
    val endTime: LocalTime? = null,
    val location: String = "",
    val priority: Int = 1,
    val pinned: Boolean = false,
    val includeAge: Boolean = true,
    /** Notes only: false keeps a plain note with no reminder. */
    val remind: Boolean = true,
    val routineTimes: List<LocalTime> = listOf(UserPreferences.DEFAULT_TIME),
    /** Routines: empty means every day. */
    val weekdays: Set<DayOfWeek> = emptySet(),
    val recurrence: Recurrence = Recurrence.None,
    val endDate: LocalDate? = null,
    /** Minutes before the due moment. */
    val offsets: Set<Int> = ReminderOffsets.defaultsFor(ItemKind.BILL).toSet(),
    val notes: String = "",
) {
    /** Whether this draft needs a date at all. */
    val needsDate: Boolean get() = kind != ItemKind.MEMO || remind

    /** Whether the draft ends up as something that fires reminders. */
    val isScheduled: Boolean get() = kind != ItemKind.MEMO || remind

    fun withQuickAdd(result: QuickAddResult, prefs: UserPreferences): ItemDraft {
        var d = copy(
            title = result.title,
            dueDate = result.date ?: dueDate,
            time = result.time ?: time,
            recurrence = result.recurrence,
            weekdays = result.weekdays,
            offsets = prefs.offsetsFor(result.kind).toSet(),
        )
        if (result.kind == ItemKind.BILL && result.amountText != null) d = d.copy(amountText = result.amountText)
        if (result.kind == ItemKind.BIRTHDAY) d = d.copy(includeAge = result.dateHasYear)
        if (result.kind == ItemKind.ROUTINE) {
            val times = listOfNotNull(result.time) + result.extraTimes
            d = d.copy(routineTimes = times.ifEmpty { routineTimes }.distinct().sorted())
        }
        if (result.kind == ItemKind.MEMO) d = d.copy(remind = false)
        if (result.kind == ItemKind.EVENT && result.time == null) d = d.copy(allDay = true)
        return d
    }

    companion object {
        fun from(bill: Bill): ItemDraft = ItemDraft(
            kind = bill.kind,
            title = bill.title,
            categoryId = bill.categoryId,
            amountText = bill.amountMinorUnits?.let { Money.toInputText(it, bill.currencyCode) }.orEmpty(),
            currencyCode = bill.currencyCode,
            dueDate = bill.startDueDate,
            time = bill.dueTime,
            allDay = bill.allDay,
            endTime = bill.endTime,
            location = bill.location.orEmpty(),
            priority = bill.priority,
            pinned = bill.isPinned,
            includeAge = bill.includeAge,
            remind = bill.scheduled,
            routineTimes = bill.routineTimes.ifEmpty { listOf(bill.dueTime) },
            weekdays = bill.weekdays,
            recurrence = bill.recurrence,
            endDate = bill.endDate,
            offsets = bill.reminderOffsets.toSet(),
            notes = bill.notes.orEmpty(),
        )

        /** Sensible starting values for a brand-new item of [kind]. */
        fun defaults(prefs: UserPreferences, kind: ItemKind, today: LocalDate, nowTime: LocalTime): ItemDraft {
            val nextHour = nowTime.plusHours(1).withMinute(0).withSecond(0).withNano(0)
            val eventTime = if (nextHour.isAfter(nowTime)) nextHour else prefs.reminderTime
            return ItemDraft(
                kind = kind,
                currencyCode = prefs.currencyCode,
                time = if (kind == ItemKind.EVENT) eventTime else prefs.reminderTime,
                dueDate = when (kind) {
                    ItemKind.BILL, ItemKind.BIRTHDAY -> null
                    else -> today
                },
                recurrence = when (kind) {
                    ItemKind.BIRTHDAY -> Recurrence(RecurrenceRule.YEARLY)
                    ItemKind.ROUTINE -> Recurrence(RecurrenceRule.DAILY)
                    else -> Recurrence.None
                },
                remind = kind != ItemKind.MEMO,
                routineTimes = listOf(prefs.reminderTime),
                offsets = prefs.offsetsFor(kind).toSet(),
            )
        }
    }
}

enum class DraftField { TITLE, AMOUNT, DATE, END_TIME, RECURRENCE, END_DATE, REMINDERS, NOTES, LOCATION, ROUTINE_TIMES }

enum class DraftError {
    TITLE_REQUIRED,
    TITLE_TOO_LONG,
    AMOUNT_INVALID,
    AMOUNT_TOO_MANY_DECIMALS,
    AMOUNT_NOT_POSITIVE,
    AMOUNT_TOO_LARGE,
    DATE_REQUIRED,
    END_TIME_BEFORE_START,
    INTERVAL_INVALID,
    END_BEFORE_START,
    OFFSET_INVALID,
    NOTES_TOO_LONG,
    LOCATION_TOO_LONG,
    ROUTINE_TIMES_REQUIRED,
}

object ItemDraftValidator {
    const val MAX_TITLE = 80
    const val MAX_NOTES = 500
    const val MAX_LOCATION = 100
    const val MAX_ROUTINE_TIMES = 8

    fun validate(draft: ItemDraft): Map<DraftField, DraftError> {
        val errors = LinkedHashMap<DraftField, DraftError>()

        val title = draft.title.trim()
        when {
            title.isEmpty() -> errors[DraftField.TITLE] = DraftError.TITLE_REQUIRED
            title.length > MAX_TITLE -> errors[DraftField.TITLE] = DraftError.TITLE_TOO_LONG
        }

        if (draft.kind == ItemKind.BILL) {
            when (Money.parse(draft.amountText, draft.currencyCode)) {
                is Money.Parsed.Ok, Money.Parsed.Empty -> Unit
                Money.Parsed.Invalid -> errors[DraftField.AMOUNT] = DraftError.AMOUNT_INVALID
                Money.Parsed.TooManyDecimals -> errors[DraftField.AMOUNT] = DraftError.AMOUNT_TOO_MANY_DECIMALS
                Money.Parsed.NotPositive -> errors[DraftField.AMOUNT] = DraftError.AMOUNT_NOT_POSITIVE
                Money.Parsed.TooLarge -> errors[DraftField.AMOUNT] = DraftError.AMOUNT_TOO_LARGE
            }
        }

        if (draft.needsDate && draft.dueDate == null) errors[DraftField.DATE] = DraftError.DATE_REQUIRED

        if (draft.kind == ItemKind.EVENT && !draft.allDay && draft.endTime != null && !draft.endTime.isAfter(draft.time)) {
            errors[DraftField.END_TIME] = DraftError.END_TIME_BEFORE_START
        }

        val repeats = draft.kind != ItemKind.ROUTINE && draft.kind != ItemKind.BIRTHDAY && draft.kind != ItemKind.MEMO
        if (repeats && draft.recurrence.isRecurring && draft.recurrence.interval !in 1..Recurrence.MAX_INTERVAL) {
            errors[DraftField.RECURRENCE] = DraftError.INTERVAL_INVALID
        }

        val due = draft.dueDate
        if (repeats && draft.recurrence.isRecurring && draft.endDate != null && due != null && draft.endDate < due) {
            errors[DraftField.END_DATE] = DraftError.END_BEFORE_START
        }

        if (draft.kind == ItemKind.ROUTINE && (draft.routineTimes.isEmpty() || draft.routineTimes.size > MAX_ROUTINE_TIMES)) {
            errors[DraftField.ROUTINE_TIMES] = DraftError.ROUTINE_TIMES_REQUIRED
        }

        if (draft.offsets.any { it !in 0..ReminderOffsets.MAX_MINUTES }) {
            errors[DraftField.REMINDERS] = DraftError.OFFSET_INVALID
        }

        if (draft.notes.length > MAX_NOTES) errors[DraftField.NOTES] = DraftError.NOTES_TOO_LONG
        if (draft.location.length > MAX_LOCATION) errors[DraftField.LOCATION] = DraftError.LOCATION_TOO_LONG
        return errors
    }

    /** Minor units of a validated bill draft, or null when the amount is left empty (or the item is not a bill). */
    fun amountMinorUnits(draft: ItemDraft): Long? =
        if (draft.kind != ItemKind.BILL) null
        else (Money.parse(draft.amountText, draft.currencyCode) as? Money.Parsed.Ok)?.minorUnits
}
