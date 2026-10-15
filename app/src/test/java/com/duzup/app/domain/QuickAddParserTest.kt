package com.duzup.app.domain

import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.IntervalUnit
import com.duzup.app.core.model.RecurrenceRule
import com.duzup.app.domain.quickadd.QuickAddParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class QuickAddParserTest {
    /** Wednesday 12 March 2025, 10:00. */
    private val now = LocalDateTime.of(2025, 3, 12, 10, 0)
    private fun parse(text: String) = QuickAddParser.parse(text, now)!!
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test
    fun blankInput_isNotParsed() {
        assertNull(QuickAddParser.parse("", now))
        assertNull(QuickAddParser.parse("   ", now))
    }

    @Test
    fun tomorrowWithTime_becomesATask() {
        val r = parse("call mom tomorrow 6pm")
        assertEquals(ItemKind.TASK, r.kind)
        assertEquals("call mom", r.title.lowercase())
        assertEquals(d(2025, 3, 13), r.date)
        assertEquals(LocalTime.of(18, 0), r.time)
    }

    @Test
    fun reminderPrefix_isDropped() {
        val r = parse("remind me to drink water tomorrow at 9am")
        assertEquals("drink water", r.title.lowercase())
        assertEquals(LocalTime.of(9, 0), r.time)
    }

    @Test
    fun plainText_becomesANote() {
        val r = parse("wifi password is in the drawer")
        assertEquals(ItemKind.MEMO, r.kind)
        assertNull(r.date)
        assertNull(r.time)
    }

    @Test
    fun billWithAmount_isABill() {
        val r = parse("pay rent rs 45,000 on the 1st")
        assertEquals(ItemKind.BILL, r.kind)
        assertEquals("45000", r.amountText)
        assertEquals(d(2025, 4, 1), r.date)
    }

    @Test
    fun dollarAmount_isParsed() {
        val r = parse("netflix \$12.99 every month")
        assertEquals(ItemKind.BILL, r.kind)
        assertEquals("12.99", r.amountText)
        assertEquals(RecurrenceRule.MONTHLY, r.recurrence.rule)
    }

    @Test
    fun meetingWithTime_isAnEvent() {
        val r = parse("team meeting friday 3pm")
        assertEquals(ItemKind.EVENT, r.kind)
        assertEquals(d(2025, 3, 14), r.date)
        assertEquals(LocalTime.of(15, 0), r.time)
    }

    @Test
    fun birthday_isYearly() {
        val r = parse("sara's birthday mar 5")
        assertEquals(ItemKind.BIRTHDAY, r.kind)
        assertEquals(RecurrenceRule.YEARLY, r.recurrence.rule)
        assertEquals(d(2026, 3, 5), r.date)
    }

    @Test
    fun monthDayInTheFuture_staysInThisYear() {
        val r = parse("concert dec 20 8pm")
        assertEquals(d(2025, 12, 20), r.date)
        assertEquals(LocalTime.of(20, 0), r.time)
    }

    @Test
    fun explicitYear_isFlagged() {
        val r = parse("exam 3 june 2026 9am")
        assertEquals(d(2026, 6, 3), r.date)
        assertTrue(r.dateHasYear)
    }

    @Test
    fun isoDate_isParsed() {
        val r = parse("submit report 2025-04-02")
        assertEquals(d(2025, 4, 2), r.date)
        assertTrue(r.dateHasYear)
    }

    @Test
    fun relativeMinutes_resolveFromNow() {
        val r = parse("stretch in 30 min")
        assertEquals(d(2025, 3, 12), r.date)
        assertEquals(LocalTime.of(10, 30), r.time)
    }

    @Test
    fun relativeHoursAcrossMidnight_rollToTheNextDay() {
        val r = QuickAddParser.parse("water the plants in 3 hours", LocalDateTime.of(2025, 3, 12, 22, 30))!!
        assertEquals(d(2025, 3, 13), r.date)
        assertEquals(LocalTime.of(1, 30), r.time)
    }

    @Test
    fun relativeDaysAndWeeks() {
        assertEquals(d(2025, 3, 15), parse("dentist in 3 days").date)
        assertEquals(d(2025, 3, 26), parse("review in 2 weeks").date)
    }

    @Test
    fun tonight_defaultsToEvening() {
        val r = parse("movie tonight")
        assertEquals(d(2025, 3, 12), r.date)
        assertEquals(LocalTime.of(20, 0), r.time)
    }

    @Test
    fun timeOnly_pastTimeMeansTomorrow_futureTimeMeansToday() {
        assertEquals(d(2025, 3, 12), parse("call bank at 5pm").date)
        assertEquals(d(2025, 3, 13), parse("call bank at 8am").date)
    }

    @Test
    fun weekdayName_isTheNextOccurrence() {
        // Today is a Wednesday, so "wednesday" means next week.
        assertEquals(d(2025, 3, 19), parse("pick up parcel wednesday").date)
        assertEquals(d(2025, 3, 17), parse("pick up parcel monday").date)
    }

    @Test
    fun routineWithSeveralWeekdays() {
        val r = parse("gym every mon wed fri 7am")
        assertEquals(ItemKind.ROUTINE, r.kind)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), r.weekdays)
        assertEquals(LocalTime.of(7, 0), r.time)
        assertEquals(RecurrenceRule.DAILY, r.recurrence.rule)
    }

    @Test
    fun dailyRoutineWithTwoTimes() {
        val r = parse("take vitamins every day 8am and 8pm")
        assertEquals(ItemKind.ROUTINE, r.kind)
        assertEquals(LocalTime.of(8, 0), r.time)
        assertEquals(listOf(LocalTime.of(20, 0)), r.extraTimes)
    }

    @Test
    fun everyMorning_impliesAMorningTime() {
        val r = parse("meditate every morning")
        assertEquals(ItemKind.ROUTINE, r.kind)
        assertNotNull(r.time)
        assertTrue(r.time!!.hour in 6..9)
    }

    @Test
    fun customInterval_everyNDays() {
        val r = parse("water cactus every 3 days")
        assertEquals(RecurrenceRule.CUSTOM, r.recurrence.rule)
        assertEquals(3, r.recurrence.interval)
        assertEquals(IntervalUnit.DAY, r.recurrence.unit)
    }

    @Test
    fun yearlyAdverb_isRecognised() {
        val r = parse("renew passport yearly")
        assertEquals(RecurrenceRule.YEARLY, r.recurrence.rule)
    }

    @Test
    fun invalidCalendarDate_isIgnoredNotCrashed() {
        val r = parse("party feb 30")
        assertNull(r.date)
    }

    @Test
    fun invalidClockTime_isIgnored() {
        val r = parse("call at 25:99")
        assertNull(r.time)
    }

    @Test
    fun noonAndMidnight_andTwelveHourEdges() {
        assertEquals(LocalTime.NOON, parse("lunch tomorrow at noon").time)
        assertEquals(LocalTime.of(0, 30), parse("sleep tomorrow 12:30am").time)
        assertEquals(LocalTime.of(12, 15), parse("lunch tomorrow 12:15pm").time)
    }

    @Test
    fun titleKeepsItsMeaning_afterWhenWordsAreRemoved() {
        val r = parse("submit assignment by friday 5pm")
        assertEquals("submit assignment", r.title.lowercase())
    }

    @Test
    fun veryLongInput_doesNotHang() {
        val long = "buy milk " + "and eggs ".repeat(2000) + "tomorrow"
        assertNotNull(QuickAddParser.parse(long, now))
    }
}
