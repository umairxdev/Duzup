package com.duzup.app.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.OccurrenceStatus
import com.duzup.app.core.model.Recurrence
import com.duzup.app.core.model.RecurrenceRule
import com.duzup.app.core.model.ReminderOffsets
import com.duzup.app.core.model.SnoozeChoice
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Events, tasks, notes, birthdays and routines through the real repository and reminder engine. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = android.app.Application::class)
class KindsFlowTest {
    private lateinit var env: TestEnv

    @Before fun setUp() { env = TestEnv("2025-03-12T10:00:00Z") }
    @After fun tearDown() { env.close() }

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private fun at(date: String, time: String) = Instant.parse("${date}T$time:00Z").toEpochMilli()

    @Test
    fun event_minuteAndHourOffsets_scheduleExactInstants() = runBlocking {
        val event = env.bill(
            title = "Dinner", start = d(2025, 3, 14), time = LocalTime.of(19, 30),
            offsets = listOf(1440, 60, 15, 0), amount = null,
        ).copy(kind = ItemKind.EVENT)
        env.repo.saveBill(event)
        val occ = env.occurrenceOn(event.id, d(2025, 3, 14))
        assertEquals(at("2025-03-13", "19:30"), env.scheduler.active["${occ.id}|1d"])
        assertEquals(at("2025-03-14", "18:30"), env.scheduler.active["${occ.id}|1h"])
        assertEquals(at("2025-03-14", "19:15"), env.scheduler.active["${occ.id}|15m"])
        assertEquals(at("2025-03-14", "19:30"), env.scheduler.active["${occ.id}|0"])
        assertNull("events never get a late nudge", env.scheduler.active["${occ.id}|OVERDUE"])
    }

    @Test
    fun event_isNeverOverdue_andGetsNoOverdueReminder() = runBlocking {
        val event = env.bill(start = d(2025, 3, 1), offsets = listOf(0), amount = null).copy(kind = ItemKind.EVENT)
        env.repo.saveBill(event)
        assertTrue(env.scheduler.active.keys.none { it.endsWith("|OVERDUE") })
    }

    @Test
    fun task_getsALateNudge_likeABill() = runBlocking {
        val task = env.bill(start = d(2025, 3, 13), offsets = listOf(0), amount = null).copy(kind = ItemKind.TASK)
        env.repo.saveBill(task)
        val occ = env.occurrenceOn(task.id, d(2025, 3, 13))
        assertEquals(at("2025-03-14", "09:00"), env.scheduler.active["${occ.id}|OVERDUE"])
    }

    @Test
    fun note_withoutReminder_neverCreatesOccurrencesOrAlarms() = runBlocking {
        val note = env.bill(title = "Wifi", offsets = emptyList(), amount = null)
            .copy(kind = ItemKind.MEMO, scheduled = false)
        env.repo.saveBill(note)
        assertTrue(env.occurrences(note.id).isEmpty())
        assertTrue(env.scheduler.active.isEmpty())
    }

    @Test
    fun note_withReminder_firesOnce() = runBlocking {
        val note = env.bill(title = "Call back", start = d(2025, 3, 13), offsets = listOf(0), amount = null)
            .copy(kind = ItemKind.MEMO, scheduled = true)
        env.repo.saveBill(note)
        val occ = env.occurrenceOn(note.id, d(2025, 3, 13))
        assertEquals(1, env.scheduler.active.size)
        assertTrue(env.engine.deliver("${occ.id}|0"))
        assertFalse(env.engine.deliver("${occ.id}|0"))
        assertEquals(1, env.notifier.shown.size)
    }

    @Test
    fun birthday_repeatsYearly_andKeepsFebruary29Anchor() = runBlocking {
        val birthday = env.bill(title = "Sara", start = d(2024, 2, 29), recurrence = Recurrence(RecurrenceRule.YEARLY),
            offsets = ReminderOffsets.days(1, 0), amount = null).copy(kind = ItemKind.BIRTHDAY)
        env.repo.saveBill(birthday)
        env.time.instant = Instant.parse("2025-02-01T08:00:00Z")
        env.repo.reconcileAll(extendThrough = d(2028, 3, 1))
        val dates = env.occurrences(birthday.id).map { it.dueDate }
        assertTrue(d(2025, 2, 28) in dates)
        assertTrue(d(2028, 2, 29) in dates)
        assertEquals(dates.size, dates.toSet().size)
    }

    @Test
    fun routine_createsOneOccurrencePerTime_andMarkingOneDoesNotTouchTheOther() = runBlocking {
        val routine = env.bill(
            title = "Vitamins", start = d(2025, 3, 12), recurrence = Recurrence(RecurrenceRule.DAILY),
            offsets = listOf(0), amount = null, time = LocalTime.of(20, 0),
        ).copy(
            kind = ItemKind.ROUTINE,
            routineTimes = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
        )
        env.repo.saveBill(routine)
        val today = env.occurrences(routine.id).filter { it.dueDate == d(2025, 3, 12) }
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), today.map { it.dueTime }.sorted())

        val evening = today.first { it.dueTime == LocalTime.of(20, 0) }
        env.repo.markPaid(evening.id)
        val after = env.occurrences(routine.id).filter { it.dueDate == d(2025, 3, 12) }
        assertEquals(OccurrenceStatus.PAID, after.first { it.dueTime == LocalTime.of(20, 0) }.status)
        assertEquals(OccurrenceStatus.PENDING, after.first { it.dueTime == LocalTime.of(8, 0) }.status)
        assertEquals("no duplicates after another reconcile", 2, run {
            env.repo.reconcileAll()
            env.occurrences(routine.id).count { it.dueDate == d(2025, 3, 12) }
        })
    }

    @Test
    fun routine_onlyOnSelectedWeekdays() = runBlocking {
        val routine = env.bill(
            title = "Gym", start = d(2025, 3, 12), recurrence = Recurrence(RecurrenceRule.DAILY),
            offsets = listOf(0), amount = null,
        ).copy(kind = ItemKind.ROUTINE, routineTimes = listOf(LocalTime.of(9, 0)), weekdays = setOf(DayOfWeek.FRIDAY))
        env.repo.saveBill(routine)
        val days = env.occurrences(routine.id).map { it.dueDate.dayOfWeek }.toSet()
        assertEquals(setOf(DayOfWeek.FRIDAY), days)
    }

    @Test
    fun quietHours_holdAReminderUntilMorning_butNeverPastTheDueMoment() = runBlocking {
        env.prefs.setQuietHoursEnabled(true)
        // due 23:00; the 1-hour heads-up (22:00) would be quiet, but delaying to 08:00 would pass the due time
        val event = env.bill(start = d(2025, 3, 13), time = LocalTime.of(23, 0), offsets = listOf(60, 0), amount = null)
            .copy(kind = ItemKind.EVENT)
        env.repo.saveBill(event)
        val occ = env.occurrenceOn(event.id, d(2025, 3, 13))
        assertEquals(at("2025-03-13", "22:00"), env.scheduler.active["${occ.id}|1h"])
        assertEquals(at("2025-03-13", "23:00"), env.scheduler.active["${occ.id}|0"])
    }

    @Test
    fun quietHours_delayAHeadsUpWhoseDueTimeIsTheNextMorningOrLater() = runBlocking {
        env.prefs.setQuietHoursEnabled(true)
        // due 10:00 on the 14th, heads-up 12 hours before = 22:00 on the 13th (quiet) -> held until 08:00 the 14th
        val event = env.bill(start = d(2025, 3, 14), time = LocalTime.of(10, 0), offsets = listOf(720), amount = null)
            .copy(kind = ItemKind.EVENT)
        env.repo.saveBill(event)
        val occ = env.occurrenceOn(event.id, d(2025, 3, 14))
        assertEquals(at("2025-03-14", "08:00"), env.scheduler.active["${occ.id}|12h"])
    }

    @Test
    fun snoozeChoices_scheduleTheRightMoment_andNeverMoveTheDueDate() = runBlocking {
        val bill = env.bill(start = d(2025, 3, 12), offsets = listOf(0))
        env.repo.saveBill(bill)
        val occ = env.occurrenceOn(bill.id, d(2025, 3, 12))

        assertTrue(env.engine.snoozeWith(occ.id, SnoozeChoice.MINUTES_10))
        assertEquals(at("2025-03-12", "10:10"), env.scheduler.active["${occ.id}|SNOOZE"])
        assertTrue(env.engine.snoozeWith(occ.id, SnoozeChoice.HOURS_3))
        assertEquals(at("2025-03-12", "13:00"), env.scheduler.active["${occ.id}|SNOOZE"])
        assertTrue(env.engine.snoozeWith(occ.id, SnoozeChoice.TOMORROW))
        assertEquals(at("2025-03-13", "09:00"), env.scheduler.active["${occ.id}|SNOOZE"])
        assertEquals(d(2025, 3, 12), env.occurrenceOn(bill.id, d(2025, 3, 12)).dueDate)
    }

    @Test
    fun snooze_afterPaid_isRefused() = runBlocking {
        val bill = env.bill(start = d(2025, 3, 12), offsets = listOf(0))
        env.repo.saveBill(bill)
        val occ = env.occurrenceOn(bill.id, d(2025, 3, 12))
        env.repo.markPaid(occ.id)
        assertFalse(env.engine.snoozeWith(occ.id, SnoozeChoice.HOUR_1))
        assertTrue(env.scheduler.active.isEmpty())
    }

    @Test
    fun pinning_onlyChangesThePinnedFlag() = runBlocking {
        val note = env.bill(title = "Idea", amount = null).copy(kind = ItemKind.MEMO, scheduled = false)
        env.repo.saveBill(note)
        env.repo.setPinned(note.id, true)
        assertTrue(env.repo.getBill(note.id)!!.isPinned)
        env.repo.setPinned(note.id, false)
        assertFalse(env.repo.getBill(note.id)!!.isPinned)
    }
}
