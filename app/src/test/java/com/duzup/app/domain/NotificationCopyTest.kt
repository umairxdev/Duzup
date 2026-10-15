package com.duzup.app.domain

import com.duzup.app.core.model.ReminderKind
import com.duzup.app.core.model.ReminderOffsets
import com.duzup.app.core.notifications.NotificationCopy
import com.duzup.app.core.notifications.NotificationPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCopyTest {
    @Test
    fun phase_followsHowCloseTheReminderIs() {
        fun phase(kind: ReminderKind, minutes: Int) = NotificationCopy.phaseFor(kind, minutes)
        assertEquals(NotificationPhase.EARLY, phase(ReminderKind.UPCOMING, 7 * 1440))
        assertEquals(NotificationPhase.EARLY, phase(ReminderKind.UPCOMING, 2 * 1440))
        assertEquals(NotificationPhase.TOMORROW, phase(ReminderKind.UPCOMING, 1440))
        assertEquals(NotificationPhase.HOURS, phase(ReminderKind.UPCOMING, 180))
        assertEquals(NotificationPhase.HOURS, phase(ReminderKind.UPCOMING, 60))
        assertEquals(NotificationPhase.MINUTES, phase(ReminderKind.UPCOMING, 15))
        assertEquals(NotificationPhase.NOW, phase(ReminderKind.DUE, 0))
        assertEquals(NotificationPhase.LATE, phase(ReminderKind.OVERDUE, 0))
        assertEquals(NotificationPhase.BACK, phase(ReminderKind.SNOOZE, 0))
    }

    @Test
    fun variant_isDeterministicAndInRange() {
        for (size in 1..6) {
            for (seed in listOf("a", "occ-1|7d", "", "ünïcode", "x".repeat(500))) {
                val first = NotificationCopy.variantIndex(seed, size)
                assertEquals(first, NotificationCopy.variantIndex(seed, size))
                assertTrue(first in 0 until size)
            }
        }
    }

    @Test
    fun variant_spreadsAcrossThePool() {
        val used = (1..60).map { NotificationCopy.variantIndex("reminder-$it", 3) }.toSet()
        assertEquals(setOf(0, 1, 2), used)
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyPool_isRejected() {
        NotificationCopy.variantIndex("x", 0)
    }

    @Test
    fun days_roundUp() {
        assertEquals(0, NotificationCopy.daysFor(0))
        assertEquals(1, NotificationCopy.daysFor(60))
        assertEquals(1, NotificationCopy.daysFor(ReminderOffsets.MINUTES_PER_DAY))
        assertEquals(2, NotificationCopy.daysFor(ReminderOffsets.MINUTES_PER_DAY + 1))
        assertEquals(7, NotificationCopy.daysFor(7 * ReminderOffsets.MINUTES_PER_DAY))
    }
}
