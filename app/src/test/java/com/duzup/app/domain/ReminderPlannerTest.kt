package com.duzup.app.domain

import com.duzup.app.core.model.ReminderKind
import com.duzup.app.core.model.ReminderOffsets
import com.duzup.app.domain.reminders.OffsetUnit
import com.duzup.app.domain.reminders.QuietHours
import com.duzup.app.domain.reminders.ReminderPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class ReminderPlannerTest {
    private val due = LocalDate.of(2025, 3, 10)
    private val nine = LocalTime.of(9, 0)

    @Test
    fun defaultOffsets_produceFourRemindersPlusOverdue() {
        val plan = ReminderPlanner.plan("occ", due, nine, ZoneOffset.UTC, ReminderOffsets.days(7, 3, 1, 0), overdueReminder = true)
        assertEquals(5, plan.size)
        assertEquals(
            listOf("2025-03-03T09:00:00Z", "2025-03-07T09:00:00Z", "2025-03-09T09:00:00Z", "2025-03-10T09:00:00Z", "2025-03-11T09:00:00Z"),
            plan.map { it.triggerAt.toString() },
        )
        assertEquals(ReminderKind.DUE, plan.first { it.offsetMinutes == 0 && it.kind != ReminderKind.OVERDUE }.kind)
        assertEquals(ReminderKind.OVERDUE, plan.last().kind)
    }

    @Test
    fun reminderIdentity_isStable_andDeduplicatesOffsets() {
        val a = ReminderPlanner.plan("occ", due, nine, ZoneOffset.UTC, ReminderOffsets.days(3, 3, 7), false)
        val b = ReminderPlanner.plan("occ", due, nine, ZoneOffset.UTC, ReminderOffsets.days(7, 3), false)
        assertEquals(a.map { it.id }, b.map { it.id })
        assertEquals(setOf("occ|7d", "occ|3d"), a.map { it.id }.toSet())
    }

    @Test
    fun invalidOffsets_areIgnored() {
        val plan = ReminderPlanner.plan("occ", due, nine, ZoneOffset.UTC, listOf(-1, 600_000, 120), false)
        assertEquals(listOf("occ|2h"), plan.map { it.id })
    }

    @Test
    fun noOffsets_meansNoReminders() {
        assertTrue(ReminderPlanner.plan("occ", due, nine, ZoneOffset.UTC, emptyList(), false).isEmpty())
    }

    @Test
    fun timezone_changesTheInstant_notTheLocalWallClock() {
        val karachi = ZoneId.of("Asia/Karachi") // UTC+5
        val plan = ReminderPlanner.plan("occ", due, nine, karachi, listOf(0), false)
        assertEquals(Instant.parse("2025-03-10T04:00:00Z"), plan.single().triggerAt)
    }

    @Test
    fun dstSpringForwardGap_resolvesToValidInstant() {
        val ny = ZoneId.of("America/New_York")
        // 2025-03-09 02:30 does not exist in New York; it resolves forward to 03:30 EDT (07:30Z).
        val instant = ReminderPlanner.instantOf(LocalDate.of(2025, 3, 9), LocalTime.of(2, 30), ny)
        assertEquals(Instant.parse("2025-03-09T07:30:00Z"), instant)
    }

    @Test
    fun offsetsAcrossDst_keepLocalWallClockTime() {
        val ny = ZoneId.of("America/New_York")
        val plan = ReminderPlanner.plan("occ", LocalDate.of(2025, 3, 10), nine, ny, ReminderOffsets.days(1, 0), false)
        // Mar 9 09:00 EDT (13:00Z) and Mar 10 09:00 EDT (13:00Z); the 1-day offset crosses the DST change.
        assertEquals(Instant.parse("2025-03-09T13:00:00Z"), plan.first { it.offsetMinutes == 1440 }.triggerAt)
        assertEquals(Instant.parse("2025-03-10T13:00:00Z"), plan.first { it.offsetMinutes == 0 }.triggerAt)
    }

    @Test
    fun snoozeAndOverdueIds_areDistinctPerOccurrence() {
        assertEquals("o|SNOOZE", ReminderPlanner.idFor("o", ReminderKind.SNOOZE, 0))
        assertEquals("o|OVERDUE", ReminderPlanner.idFor("o", ReminderKind.OVERDUE, 0))
        assertEquals("o|7d", ReminderPlanner.idFor("o", ReminderKind.UPCOMING, 7 * 1440))
    }

    @Test
    fun parseOffset_acceptsOnlyWholeNumbersInRange() {
        assertEquals(14 * 1440, ReminderPlanner.parseOffset("14", OffsetUnit.DAYS))
        assertEquals(14 * 1440, ReminderPlanner.parseOffset("  14 ", OffsetUnit.DAYS))
        assertEquals(0, ReminderPlanner.parseOffset("0", OffsetUnit.MINUTES))
        assertEquals(365 * 1440, ReminderPlanner.parseOffset("365", OffsetUnit.DAYS))
        assertEquals(90, ReminderPlanner.parseOffset("90", OffsetUnit.MINUTES))
        assertEquals(180, ReminderPlanner.parseOffset("3", OffsetUnit.HOURS))
        assertEquals(null, ReminderPlanner.parseOffset("366", OffsetUnit.DAYS))
        assertEquals(null, ReminderPlanner.parseOffset("999999", OffsetUnit.HOURS))
        assertEquals(null, ReminderPlanner.parseOffset("-1", OffsetUnit.DAYS))
        assertEquals(null, ReminderPlanner.parseOffset("2.5", OffsetUnit.DAYS))
        assertEquals(null, ReminderPlanner.parseOffset("", OffsetUnit.DAYS))
        assertEquals(null, ReminderPlanner.parseOffset("abc", OffsetUnit.MINUTES))
    }

    @Test
    fun minuteOffsets_subtractElapsedTime_andWholeDaysSubtractWallClockDays() {
        val ny = ZoneId.of("America/New_York")
        val d = LocalDate.of(2025, 3, 10)
        assertEquals(Instant.parse("2025-03-10T12:30:00Z"), ReminderPlanner.triggerFor(d, nine, ny, 30))
        assertEquals(Instant.parse("2025-03-09T13:00:00Z"), ReminderPlanner.triggerFor(d, nine, ny, 1440))
    }

    @Test
    fun idSuffix_isCompactAndStable() {
        assertEquals("0", ReminderOffsets.idSuffix(0))
        assertEquals("7d", ReminderOffsets.idSuffix(7 * 1440))
        assertEquals("3h", ReminderOffsets.idSuffix(180))
        assertEquals("15m", ReminderOffsets.idSuffix(15))
        assertEquals("90m", ReminderOffsets.idSuffix(90))
    }

    @Test
    fun quietHours_crossMidnightWindow() {
        val start = LocalTime.of(22, 0)
        val end = LocalTime.of(8, 0)
        assertTrue(QuietHours.isQuiet(LocalTime.of(23, 30), start, end))
        assertTrue(QuietHours.isQuiet(LocalTime.of(3, 0), start, end))
        assertTrue(QuietHours.isQuiet(LocalTime.of(22, 0), start, end))
        assertFalse(QuietHours.isQuiet(LocalTime.of(8, 0), start, end))
        assertFalse(QuietHours.isQuiet(LocalTime.of(12, 0), start, end))
    }

    @Test
    fun quietHours_sameDayWindow_andEmptyWindow() {
        assertTrue(QuietHours.isQuiet(LocalTime.of(13, 0), LocalTime.of(12, 0), LocalTime.of(14, 0)))
        assertFalse(QuietHours.isQuiet(LocalTime.of(14, 0), LocalTime.of(12, 0), LocalTime.of(14, 0)))
        assertFalse(QuietHours.isQuiet(LocalTime.of(12, 0), LocalTime.of(9, 0), LocalTime.of(9, 0)))
    }

    @Test
    fun quietHours_delayReminderToWindowEnd() {
        val start = LocalTime.of(22, 0)
        val end = LocalTime.of(8, 0)
        val late = Instant.parse("2025-03-10T23:30:00Z")
        assertEquals(Instant.parse("2025-03-11T08:00:00Z"), QuietHours.nextAllowed(late, ZoneOffset.UTC, true, start, end, null))
        val early = Instant.parse("2025-03-10T03:00:00Z")
        assertEquals(Instant.parse("2025-03-10T08:00:00Z"), QuietHours.nextAllowed(early, ZoneOffset.UTC, true, start, end, null))
        val day = Instant.parse("2025-03-10T12:00:00Z")
        assertEquals(day, QuietHours.nextAllowed(day, ZoneOffset.UTC, true, start, end, null))
        assertEquals(late, QuietHours.nextAllowed(late, ZoneOffset.UTC, false, start, end, null))
    }

    @Test
    fun quietHours_neverDelayPastTheEventItself() {
        val start = LocalTime.of(22, 0)
        val end = LocalTime.of(8, 0)
        val heads = Instant.parse("2025-03-10T23:00:00Z")
        val eventStart = Instant.parse("2025-03-10T23:30:00Z")
        assertEquals(heads, QuietHours.nextAllowed(heads, ZoneOffset.UTC, true, start, end, eventStart))
    }
}
