package com.duzup.app.feature

import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.Recurrence
import com.duzup.app.core.model.RecurrenceRule
import com.duzup.app.core.model.ReminderOffsets
import com.duzup.app.feature.addedit.DraftError
import com.duzup.app.feature.addedit.DraftField
import com.duzup.app.feature.addedit.ItemDraft
import com.duzup.app.feature.addedit.ItemDraftValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ItemDraftValidatorTest {
    private val date = LocalDate.of(2025, 3, 12)
    private fun draft(kind: ItemKind = ItemKind.BILL) = ItemDraft(kind = kind, title = "Thing", dueDate = date, currencyCode = "USD")
    private fun errors(d: ItemDraft) = ItemDraftValidator.validate(d)

    @Test
    fun validDraft_ofEveryKind_hasNoErrors() {
        for (kind in ItemKind.entries) assertTrue("$kind", errors(draft(kind)).isEmpty())
    }

    @Test
    fun title_isRequiredAndBounded() {
        assertEquals(DraftError.TITLE_REQUIRED, errors(draft().copy(title = "  "))[DraftField.TITLE])
        assertEquals(DraftError.TITLE_TOO_LONG, errors(draft().copy(title = "x".repeat(81)))[DraftField.TITLE])
        assertNull(errors(draft().copy(title = "x".repeat(80)))[DraftField.TITLE])
    }

    @Test
    fun amount_onlyMattersForBills() {
        assertEquals(DraftError.AMOUNT_INVALID, errors(draft().copy(amountText = "abc"))[DraftField.AMOUNT])
        assertEquals(DraftError.AMOUNT_NOT_POSITIVE, errors(draft().copy(amountText = "0"))[DraftField.AMOUNT])
        assertEquals(DraftError.AMOUNT_TOO_MANY_DECIMALS, errors(draft().copy(amountText = "1.234"))[DraftField.AMOUNT])
        assertNull(errors(draft().copy(amountText = ""))[DraftField.AMOUNT])
        assertNull(errors(draft(ItemKind.TASK).copy(amountText = "abc"))[DraftField.AMOUNT])
    }

    @Test
    fun date_isRequired_exceptForAPlainNote() {
        assertEquals(DraftError.DATE_REQUIRED, errors(draft().copy(dueDate = null))[DraftField.DATE])
        assertEquals(DraftError.DATE_REQUIRED, errors(draft(ItemKind.EVENT).copy(dueDate = null))[DraftField.DATE])
        assertNull(errors(draft(ItemKind.MEMO).copy(dueDate = null, remind = false))[DraftField.DATE])
        assertEquals(DraftError.DATE_REQUIRED, errors(draft(ItemKind.MEMO).copy(dueDate = null, remind = true))[DraftField.DATE])
    }

    @Test
    fun event_endMustBeAfterStart_unlessAllDay() {
        val event = draft(ItemKind.EVENT).copy(time = LocalTime.of(18, 0))
        assertEquals(DraftError.END_TIME_BEFORE_START, errors(event.copy(endTime = LocalTime.of(17, 0)))[DraftField.END_TIME])
        assertEquals(DraftError.END_TIME_BEFORE_START, errors(event.copy(endTime = LocalTime.of(18, 0)))[DraftField.END_TIME])
        assertNull(errors(event.copy(endTime = LocalTime.of(19, 0)))[DraftField.END_TIME])
        assertNull(errors(event.copy(endTime = LocalTime.of(17, 0), allDay = true))[DraftField.END_TIME])
    }

    @Test
    fun recurrence_intervalAndEndDate() {
        val custom = Recurrence(RecurrenceRule.CUSTOM, 0)
        assertEquals(DraftError.INTERVAL_INVALID, errors(draft().copy(recurrence = custom))[DraftField.RECURRENCE])
        val monthly = Recurrence(RecurrenceRule.MONTHLY)
        assertEquals(
            DraftError.END_BEFORE_START,
            errors(draft().copy(recurrence = monthly, endDate = date.minusDays(1)))[DraftField.END_DATE],
        )
        assertNull(errors(draft().copy(recurrence = monthly, endDate = date.plusMonths(3)))[DraftField.END_DATE])
    }

    @Test
    fun routine_needsBetweenOneAndEightTimes() {
        val routine = draft(ItemKind.ROUTINE)
        assertEquals(DraftError.ROUTINE_TIMES_REQUIRED, errors(routine.copy(routineTimes = emptyList()))[DraftField.ROUTINE_TIMES])
        val nine = (1..9).map { LocalTime.of(it, 0) }
        assertEquals(DraftError.ROUTINE_TIMES_REQUIRED, errors(routine.copy(routineTimes = nine))[DraftField.ROUTINE_TIMES])
        assertNull(errors(routine.copy(routineTimes = nine.take(8)))[DraftField.ROUTINE_TIMES])
    }

    @Test
    fun offsets_mustBeWithinRange() {
        assertEquals(DraftError.OFFSET_INVALID, errors(draft().copy(offsets = setOf(-5)))[DraftField.REMINDERS])
        assertEquals(DraftError.OFFSET_INVALID, errors(draft().copy(offsets = setOf(ReminderOffsets.MAX_MINUTES + 1)))[DraftField.REMINDERS])
        assertNull(errors(draft().copy(offsets = setOf(0, 15, 1440)))[DraftField.REMINDERS])
        assertNull(errors(draft().copy(offsets = emptySet()))[DraftField.REMINDERS])
    }

    @Test
    fun notesAndLocation_areBounded() {
        assertEquals(DraftError.NOTES_TOO_LONG, errors(draft().copy(notes = "n".repeat(501)))[DraftField.NOTES])
        assertEquals(DraftError.LOCATION_TOO_LONG, errors(draft(ItemKind.EVENT).copy(location = "l".repeat(101)))[DraftField.LOCATION])
    }

    @Test
    fun amountMinorUnits_isOnlyForBills() {
        assertEquals(1250L, ItemDraftValidator.amountMinorUnits(draft().copy(amountText = "12.50")))
        assertNull(ItemDraftValidator.amountMinorUnits(draft().copy(amountText = "")))
        assertNull(ItemDraftValidator.amountMinorUnits(draft(ItemKind.TASK).copy(amountText = "12.50")))
    }
}
