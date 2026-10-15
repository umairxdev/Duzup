package com.duzup.app.domain

import com.duzup.app.TestData
import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.OccurrenceStatus
import com.duzup.app.core.model.Recurrence
import com.duzup.app.core.model.RecurrenceRule
import com.duzup.app.domain.recurrence.OccurrencePlanner
import com.duzup.app.domain.recurrence.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class OccurrencePlannerTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private val monthly = Recurrence(RecurrenceRule.MONTHLY)

    @Test
    fun oneTimeBill_alwaysGeneratesItsSingleOccurrence_evenWhenPastDue() {
        val bill = TestData.bill(start = d(2023, 1, 5))
        val diff = OccurrencePlanner.plan(bill, emptyList(), today = d(2025, 6, 1), through = d(2025, 12, 1))
        assertEquals(listOf(d(2023, 1, 5)), diff.insertSlots.map { it.date })
    }

    @Test
    fun recurringBill_generatesBoundedWindow_withOverdueLookback() {
        val bill = TestData.bill(start = d(2020, 1, 15), recurrence = monthly)
        val diff = OccurrencePlanner.plan(bill, emptyList(), today = d(2025, 6, 10), through = d(2025, 9, 30))
        // lookback = 90 days -> window starts 2025-03-12, so Mar 15 is the first date
        assertEquals(
            listOf(d(2025, 3, 15), d(2025, 4, 15), d(2025, 5, 15), d(2025, 6, 15), d(2025, 7, 15), d(2025, 8, 15), d(2025, 9, 15)),
            diff.insertSlots.map { it.date },
        )
    }

    @Test
    fun reconcile_isIdempotent_noDuplicatesOnSecondRun() {
        val bill = TestData.bill(start = d(2025, 1, 31), recurrence = monthly)
        val today = d(2025, 1, 31)
        val first = OccurrencePlanner.plan(bill, emptyList(), today, d(2025, 6, 30))
        val existing = first.insertSlots.map { it.date }.mapIndexed { i, date ->
            TestData.occurrence(id = "o$i", date = date)
        }
        val second = OccurrencePlanner.plan(bill, existing, today, d(2025, 6, 30))
        assertTrue(second.insertSlots.isEmpty())
        assertTrue(second.deleteIds.isEmpty())
    }

    @Test
    fun paidHistory_survivesTemplateChange() {
        val bill = TestData.bill(start = d(2025, 1, 31), recurrence = Recurrence(RecurrenceRule.WEEKLY))
        val paid = TestData.occurrence(id = "paid", date = d(2025, 1, 31), status = OccurrenceStatus.PAID)
        val pendingOld = TestData.occurrence(id = "old", date = d(2025, 2, 28))
        // Template changed to monthly on the 31st: the weekly Feb 28 is no longer on schedule... unless it is.
        val changed = bill.copy(recurrence = monthly, startDueDate = d(2025, 1, 31))
        val diff = OccurrencePlanner.plan(changed, listOf(paid, pendingOld), d(2025, 2, 1), d(2025, 4, 30))
        assertTrue("paid occurrence must never be deleted", "paid" !in diff.deleteIds)
        assertTrue("Feb 28 is still the clamped monthly date", "old" !in diff.deleteIds)
        assertEquals(listOf(d(2025, 3, 31), d(2025, 4, 30)), diff.insertSlots.map { it.date })
    }

    @Test
    fun pendingOffScheduleOccurrences_areRemoved_butPaidAndSkippedKept() {
        val bill = TestData.bill(start = d(2025, 3, 1), recurrence = monthly)
        val offPending = TestData.occurrence(id = "p", date = d(2025, 3, 8))
        val offPaid = TestData.occurrence(id = "x", date = d(2025, 3, 9), status = OccurrenceStatus.PAID)
        val offSkipped = TestData.occurrence(id = "s", date = d(2025, 3, 10), status = OccurrenceStatus.SKIPPED)
        val diff = OccurrencePlanner.plan(bill, listOf(offPending, offPaid, offSkipped), d(2025, 3, 1), d(2025, 5, 1))
        assertEquals(listOf("p"), diff.deleteIds)
    }

    @Test
    fun endDate_removesPendingOccurrencesAfterIt() {
        val bill = TestData.bill(start = d(2025, 1, 10), recurrence = monthly, endDate = d(2025, 2, 10))
        val tooLate = TestData.occurrence(id = "late", date = d(2025, 3, 10))
        val diff = OccurrencePlanner.plan(bill, listOf(tooLate), d(2025, 1, 1), d(2025, 6, 1))
        assertEquals(listOf("late"), diff.deleteIds)
    }

    @Test
    fun editedDueTime_replacesPendingOnly_neverPaidHistory() {
        val bill = TestData.bill(start = d(2025, 1, 10), recurrence = monthly, time = LocalTime.of(18, 30))
        val pending = TestData.occurrence(id = "a", date = d(2025, 1, 10), time = LocalTime.of(9, 0))
        val paid = TestData.occurrence(
            id = "b", date = d(2025, 2, 10), time = LocalTime.of(9, 0), status = OccurrenceStatus.PAID,
        )
        val diff = OccurrencePlanner.plan(bill, listOf(pending, paid), d(2025, 1, 1), d(2025, 3, 1))
        assertEquals(listOf("a"), diff.deleteIds)
        assertTrue(Slot(d(2025, 1, 10), LocalTime.of(18, 30)) in diff.insertSlots)
        assertTrue("paid history is untouched", "b" !in diff.deleteIds)
    }

    @Test
    fun routine_generatesOneOccurrencePerTimeOfDay_onSelectedWeekdaysOnly() {
        val routine = TestData.bill(
            start = d(2025, 3, 3), recurrence = Recurrence(RecurrenceRule.DAILY),
        ).copy(
            kind = ItemKind.ROUTINE,
            routineTimes = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        )
        val diff = OccurrencePlanner.plan(routine, emptyList(), d(2025, 3, 3), d(2025, 3, 9))
        assertEquals(
            listOf(
                Slot(d(2025, 3, 3), LocalTime.of(8, 0)), Slot(d(2025, 3, 3), LocalTime.of(20, 0)),
                Slot(d(2025, 3, 5), LocalTime.of(8, 0)), Slot(d(2025, 3, 5), LocalTime.of(20, 0)),
            ),
            diff.insertSlots,
        )
    }

    @Test
    fun unscheduledNote_neverProducesOccurrences() {
        val note = TestData.bill().copy(kind = ItemKind.MEMO, scheduled = false)
        assertTrue(OccurrencePlanner.plan(note, emptyList(), d(2025, 1, 1), d(2025, 6, 1)).insertSlots.isEmpty())
    }

    @Test
    fun nonBillKinds_useAShortLookback() {
        val event = TestData.bill(start = d(2020, 1, 15), recurrence = monthly).copy(kind = ItemKind.EVENT)
        val dates = OccurrencePlanner.plan(event, emptyList(), d(2025, 6, 10), d(2025, 7, 30)).insertSlots.map { it.date }
        assertEquals(listOf(d(2025, 6, 15), d(2025, 7, 15)), dates)
    }

    @Test
    fun oneTimeBill_dateEdit_replacesPendingOccurrence() {
        val bill = TestData.bill(start = d(2025, 5, 20))
        val stale = TestData.occurrence(id = "stale", date = d(2025, 5, 5))
        val diff = OccurrencePlanner.plan(bill, listOf(stale), d(2025, 5, 1), d(2025, 11, 1))
        assertEquals(listOf("stale"), diff.deleteIds)
        assertEquals(listOf(d(2025, 5, 20)), diff.insertSlots.map { it.date })
    }
}
