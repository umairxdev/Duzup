package com.duzup.app.integration

import com.duzup.app.core.model.ReminderOffsets
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duzup.app.core.model.OccurrenceStatus
import com.duzup.app.core.model.Recurrence
import com.duzup.app.core.model.RecurrenceRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/** Add / edit / delete / archive a bill and check the exact reminder schedule that follows. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = android.app.Application::class)
class ReminderFlowTest {
    private lateinit var env: TestEnv

    @Before fun setUp() { env = TestEnv() }
    @After fun tearDown() { env.close() }

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test
    fun oneTimeBill_schedulesTheDefaultFourPlusOverdue() = runBlocking {
        val bill = env.bill(start = d(2025, 1, 31))
        env.repo.saveBill(bill)

        val occ = env.occurrenceOn(bill.id, d(2025, 1, 31))
        val id = { suffix: String -> "${occ.id}|$suffix" }
        assertEquals(env.alarmMillis("2025-01-24"), env.scheduler.active[id("7d")])
        assertEquals(env.alarmMillis("2025-01-28"), env.scheduler.active[id("3d")])
        assertEquals(env.alarmMillis("2025-01-30"), env.scheduler.active[id("1d")])
        assertEquals(env.alarmMillis("2025-01-31"), env.scheduler.active[id("0")])
        assertEquals(env.alarmMillis("2025-02-01"), env.scheduler.active[id("OVERDUE")])
        assertEquals(5, env.scheduler.active.size)
    }

    @Test
    fun monthlyBillOnThe31st_generatesFebruary28AndMarch31() = runBlocking {
        val bill = env.bill(start = d(2025, 1, 31), recurrence = Recurrence(RecurrenceRule.MONTHLY))
        env.repo.saveBill(bill)
        val dates = env.occurrences(bill.id).map { it.dueDate }
        assertTrue(d(2025, 1, 31) in dates)
        assertTrue(d(2025, 2, 28) in dates)
        assertTrue(d(2025, 3, 31) in dates)
        assertEquals("no duplicates", dates.size, dates.toSet().size)
    }

    @Test
    fun reconcileRepeatedly_neverDuplicatesOccurrencesRowsOrAlarms() = runBlocking {
        val bill = env.bill(start = d(2025, 1, 31), recurrence = Recurrence(RecurrenceRule.MONTHLY))
        env.repo.saveBill(bill)
        val occurrences = env.occurrences(bill.id).size
        val rows = env.db.reminderDao().getAll().size
        val alarms = env.scheduler.active.toMap()

        repeat(3) { env.repo.reconcileAll() }

        assertEquals(occurrences, env.occurrences(bill.id).size)
        assertEquals(rows, env.db.reminderDao().getAll().size)
        assertEquals(alarms, env.scheduler.active)
    }

    @Test
    fun markingOneMonthPaid_leavesTheNextMonthPendingWithItsAlarms() = runBlocking {
        val bill = env.bill(start = d(2025, 1, 31), recurrence = Recurrence(RecurrenceRule.MONTHLY))
        env.repo.saveBill(bill)
        val january = env.occurrenceOn(bill.id, d(2025, 1, 31))
        val february = env.occurrenceOn(bill.id, d(2025, 2, 28))

        env.repo.markPaid(january.id)

        assertEquals(OccurrenceStatus.PAID, env.occurrenceOn(bill.id, d(2025, 1, 31)).status)
        assertEquals(OccurrenceStatus.PENDING, env.occurrenceOn(bill.id, d(2025, 2, 28)).status)
        assertTrue("January alarms are gone", env.scheduler.active.keys.none { it.startsWith(january.id) })
        assertTrue("February alarms remain", env.scheduler.active.keys.any { it.startsWith(february.id) })
        assertTrue("visible notification dismissed", january.id in env.notifier.canceled)
    }

    @Test
    fun markPaidTwice_keepsTheFirstPayment() = runBlocking {
        val bill = env.bill()
        env.repo.saveBill(bill)
        val occ = env.occurrenceOn(bill.id, d(2025, 1, 31))
        val firstPaidAt = Instant.parse("2025-01-12T10:00:00Z")

        env.repo.markPaid(occ.id, paidAt = firstPaidAt, actualAmountMinorUnits = 111, note = "first")
        env.repo.markPaid(occ.id, paidAt = Instant.parse("2025-01-20T10:00:00Z"), actualAmountMinorUnits = 999, note = "second")

        val stored = env.occurrenceOn(bill.id, d(2025, 1, 31))
        assertEquals(firstPaidAt, stored.paidAt)
        assertEquals(111L, stored.actualAmountMinorUnits)
        assertEquals("first", stored.paymentNote)
    }

    @Test
    fun undoPaid_rearmsTheRemainingFutureReminders() = runBlocking {
        val bill = env.bill()
        env.repo.saveBill(bill)
        val occ = env.occurrenceOn(bill.id, d(2025, 1, 31))
        env.repo.markPaid(occ.id)
        assertTrue(env.scheduler.active.isEmpty())

        env.repo.undoPaid(occ.id)

        assertEquals(OccurrenceStatus.PENDING, env.occurrenceOn(bill.id, d(2025, 1, 31)).status)
        assertEquals(5, env.scheduler.active.size)
    }

    @Test
    fun skippingAnOccurrence_cancelsItsAlarms_butKeepsFutureCycles() = runBlocking {
        val bill = env.bill(start = d(2025, 1, 31), recurrence = Recurrence(RecurrenceRule.MONTHLY))
        env.repo.saveBill(bill)
        val january = env.occurrenceOn(bill.id, d(2025, 1, 31))

        env.repo.skip(january.id)

        assertEquals(OccurrenceStatus.SKIPPED, env.occurrenceOn(bill.id, d(2025, 1, 31)).status)
        assertTrue(env.scheduler.active.keys.none { it.startsWith(january.id) })
        assertEquals(OccurrenceStatus.PENDING, env.occurrenceOn(bill.id, d(2025, 2, 28)).status)
    }

    @Test
    fun editingOffsets_cancelsObsoleteAlarms_andKeepsTheNewOnes() = runBlocking {
        val bill = env.bill(offsets = ReminderOffsets.days(7, 3, 1, 0))
        env.repo.saveBill(bill)
        val occ = env.occurrenceOn(bill.id, d(2025, 1, 31))

        env.repo.saveBill(bill.copy(reminderOffsets = ReminderOffsets.days(1, 0)))

        val keys = env.scheduler.active.keys
        assertFalse("${occ.id}|7d" in keys)
        assertFalse("${occ.id}|3d" in keys)
        assertTrue("${occ.id}|1d" in keys)
        assertTrue("${occ.id}|0" in keys)
        val sevenDayRow = env.db.reminderDao().getById("${occ.id}|7d")
        assertNotNull(sevenDayRow)
        assertTrue("obsolete row is marked canceled, not left armed", sevenDayRow!!.canceled)
    }

    @Test
    fun editingTheDueDate_movesAlarms_andReplacesThePendingOccurrence() = runBlocking {
        val bill = env.bill(start = d(2025, 1, 31), offsets = ReminderOffsets.days(1, 0))
        env.repo.saveBill(bill)
        val oldOccurrence = env.occurrenceOn(bill.id, d(2025, 1, 31))

        env.repo.saveBill(bill.copy(startDueDate = d(2025, 2, 10)))

        assertEquals(listOf(d(2025, 2, 10)), env.occurrences(bill.id).map { it.dueDate })
        assertTrue("old occurrence alarms removed", env.scheduler.active.keys.none { it.startsWith(oldOccurrence.id) })
        val newOccurrence = env.occurrenceOn(bill.id, d(2025, 2, 10))
        assertEquals(env.alarmMillis("2025-02-09"), env.scheduler.active["${newOccurrence.id}|1d"])
        assertEquals(env.alarmMillis("2025-02-10"), env.scheduler.active["${newOccurrence.id}|0"])
    }

    @Test
    fun editingRecurrence_neverRewritesPaidHistory() = runBlocking {
        val bill = env.bill(start = d(2025, 1, 31), recurrence = Recurrence(RecurrenceRule.MONTHLY))
        env.repo.saveBill(bill)
        val january = env.occurrenceOn(bill.id, d(2025, 1, 31))
        env.repo.markPaid(january.id, actualAmountMinorUnits = 123_00, note = "kept")

        env.repo.saveBill(bill.copy(recurrence = Recurrence(RecurrenceRule.QUARTERLY), amountMinorUnits = 999_00))

        val paid = env.occurrenceOn(bill.id, d(2025, 1, 31))
        assertEquals(OccurrenceStatus.PAID, paid.status)
        assertEquals(123_00L, paid.actualAmountMinorUnits)
        assertEquals("kept", paid.paymentNote)
        val dates = env.occurrences(bill.id).map { it.dueDate }
        assertFalse("monthly Feb occurrence removed under the quarterly template", d(2025, 2, 28) in dates)
        assertTrue(d(2025, 4, 30) in dates)
    }

    @Test
    fun archiving_stopsReminders_andRestoringBringsThemBack() = runBlocking {
        val bill = env.bill()
        env.repo.saveBill(bill)
        val occ = env.occurrenceOn(bill.id, d(2025, 1, 31))

        env.repo.setArchived(bill.id, true)
        assertTrue(env.scheduler.active.isEmpty())
        assertFalse("archived bill never delivers", env.engine.deliver("${occ.id}|7d"))

        env.repo.setArchived(bill.id, false)
        assertEquals(5, env.scheduler.active.size)
    }

    @Test
    fun deletingABill_cancelsAlarms_andCascadesItsRows() = runBlocking {
        val bill = env.bill(start = d(2025, 1, 31), recurrence = Recurrence(RecurrenceRule.MONTHLY))
        env.repo.saveBill(bill)

        env.repo.deleteBill(bill.id)

        assertTrue(env.scheduler.active.isEmpty())
        assertTrue(env.occurrences(bill.id).isEmpty())
        assertTrue(env.db.reminderDao().getAll().isEmpty())
    }

    @Test
    fun deliveringTheSameReminderTwice_showsExactlyOneNotification() = runBlocking {
        val bill = env.bill()
        env.repo.saveBill(bill)
        val occ = env.occurrenceOn(bill.id, d(2025, 1, 31))
        val id = "${occ.id}|7d"

        assertTrue(env.engine.deliver(id))
        assertFalse(env.engine.deliver(id))

        assertEquals(1, env.notifier.shown.size)
        assertEquals("Electricity", env.notifier.shown.single().billTitle)
    }

    @Test
    fun deliveredReminders_areNeverResent_afterReconcile() = runBlocking {
        val bill = env.bill()
        env.repo.saveBill(bill)
        val occ = env.occurrenceOn(bill.id, d(2025, 1, 31))
        assertTrue(env.engine.deliver("${occ.id}|7d"))
        env.scheduler.active.remove("${occ.id}|7d") // a fired alarm is consumed by AlarmManager

        env.time.instant = Instant.parse("2025-01-24T09:00:30Z") // just after the delivered reminder time
        env.repo.reconcileAll()

        assertFalse("delivered reminder is not re-armed", "${occ.id}|7d" in env.scheduler.active)
        assertFalse(env.engine.deliver("${occ.id}|7d"))
        assertEquals(1, env.notifier.shown.size)
    }

    @Test
    fun noNotificationAfterPayment() = runBlocking {
        val bill = env.bill()
        env.repo.saveBill(bill)
        val occ = env.occurrenceOn(bill.id, d(2025, 1, 31))
        env.repo.markPaid(occ.id)

        assertFalse(env.engine.deliver("${occ.id}|3d"))
        assertTrue(env.notifier.shown.isEmpty())
    }

    @Test
    fun snooze_schedulesANewReminder_withoutTouchingTheDueDate() = runBlocking {
        val bill = env.bill()
        env.repo.saveBill(bill)
        val occ = env.occurrenceOn(bill.id, d(2025, 1, 31))

        assertTrue(env.repo.snooze(occ.id, minutes = 60))

        val snoozeId = "${occ.id}|SNOOZE"
        assertEquals(env.time.now().plusSeconds(3600).toEpochMilli(), env.scheduler.active[snoozeId])
        assertEquals(d(2025, 1, 31), env.occurrenceOn(bill.id, d(2025, 1, 31)).dueDate)

        // It delivers once, and survives a reconcile before it fires.
        env.repo.reconcileAll()
        assertTrue(snoozeId in env.scheduler.active)
        assertTrue(env.engine.deliver(snoozeId))
        assertFalse(env.engine.deliver(snoozeId))
    }

    @Test
    fun snoozingAPaidBill_isRejected() = runBlocking {
        val bill = env.bill()
        env.repo.saveBill(bill)
        val occ = env.occurrenceOn(bill.id, d(2025, 1, 31))
        env.repo.markPaid(occ.id)
        assertFalse(env.repo.snooze(occ.id))
        assertNull(env.db.reminderDao().getById("${occ.id}|SNOOZE"))
    }

    @Test
    fun afterReboot_reconcileRestoresEveryFutureAlarm() = runBlocking {
        val bill = env.bill(start = d(2025, 1, 31), recurrence = Recurrence(RecurrenceRule.MONTHLY))
        env.repo.saveBill(bill)
        val before = env.scheduler.active.toMap()
        assertTrue(before.isNotEmpty())

        env.scheduler.wipe() // reboot: AlarmManager forgets everything
        env.repo.reconcileAll()

        assertEquals(before, env.scheduler.active)
    }

    @Test
    fun reminderMissedShortlyBefore_isDeliveredSoon_butStaleOnesAreDropped() = runBlocking {
        val bill = env.bill(offsets = ReminderOffsets.days(7, 0))
        env.repo.saveBill(bill)
        val occ = env.occurrenceOn(bill.id, d(2025, 1, 31))

        // Phone was off: it is now 2h after the 7-day reminder (Jan 24 09:00).
        env.time.instant = Instant.parse("2025-01-24T11:00:00Z")
        env.repo.reconcileAll()
        val soon = env.scheduler.active["${occ.id}|7d"]
        assertNotNull(soon)
        assertTrue("fires within seconds", soon!! - env.time.now().toEpochMilli() <= 5_000)

        // Ten hours later it is too stale to be useful.
        env.scheduler.wipe()
        env.time.instant = Instant.parse("2025-01-24T20:00:00Z")
        env.repo.reconcileAll()
        assertFalse("${occ.id}|7d" in env.scheduler.active)
        assertTrue(env.db.reminderDao().getById("${occ.id}|7d")!!.canceled)
    }

    @Test
    fun disablingOverdueReminders_removesThem() = runBlocking {
        val bill = env.bill()
        env.repo.saveBill(bill)
        val occ = env.occurrenceOn(bill.id, d(2025, 1, 31))
        assertTrue("${occ.id}|OVERDUE" in env.scheduler.active)

        env.prefs.setOverdueReminders(false)
        env.repo.reconcileAll()

        assertFalse("${occ.id}|OVERDUE" in env.scheduler.active)
    }

    @Test
    fun alarmWindow_isBounded_soAlarmManagerLimitIsNeverApproached() = runBlocking {
        val daily = env.bill(start = d(2025, 1, 11), recurrence = Recurrence(RecurrenceRule.DAILY))
        env.repo.saveBill(daily)
        env.repo.saveBill(env.bill(title = "Second", start = d(2025, 1, 12), recurrence = Recurrence(RecurrenceRule.DAILY)))
        assertTrue("alarms capped, was ${env.scheduler.active.size}", env.scheduler.active.size <= 400)
        val farthest = env.scheduler.active.values.max()
        assertTrue("nothing beyond the 60 day window", farthest <= env.time.now().plusSeconds(61L * 86_400).toEpochMilli())
    }

    @Test
    fun reminderWithNoAmount_neverShowsAFakeZero() = runBlocking {
        val bill = env.bill(amount = null)
        env.repo.saveBill(bill)
        val occ = env.occurrenceOn(bill.id, d(2025, 1, 31))
        env.engine.deliver("${occ.id}|7d")
        assertEquals(1, env.notifier.shown.size)
        assertNull(env.db.billDao().getById(bill.id)!!.amountMinorUnits)
    }
}
