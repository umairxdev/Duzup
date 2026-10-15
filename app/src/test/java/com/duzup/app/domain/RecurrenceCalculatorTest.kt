package com.duzup.app.domain

import com.duzup.app.core.model.IntervalUnit
import com.duzup.app.core.model.Recurrence
import com.duzup.app.core.model.RecurrenceRule
import com.duzup.app.domain.recurrence.RecurrenceCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurrenceCalculatorTest {

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private val monthly = Recurrence(RecurrenceRule.MONTHLY)

    private fun dates(start: LocalDate, r: Recurrence, from: LocalDate, to: LocalDate, end: LocalDate? = null) =
        RecurrenceCalculator.datesBetween(start, r, end, from, to)

    @Test
    fun oneTime_returnsStartOnlyWhenInsideWindow() {
        val none = Recurrence.None
        assertEquals(listOf(d(2025, 3, 5)), dates(d(2025, 3, 5), none, d(2025, 1, 1), d(2025, 12, 31)))
        assertTrue(dates(d(2025, 3, 5), none, d(2025, 4, 1), d(2025, 12, 31)).isEmpty())
    }

    @Test
    fun monthlyOnThe31st_keepsAnchor_nonLeapYear() {
        val result = dates(d(2025, 1, 31), monthly, d(2025, 1, 1), d(2025, 5, 31))
        assertEquals(
            listOf(d(2025, 1, 31), d(2025, 2, 28), d(2025, 3, 31), d(2025, 4, 30), d(2025, 5, 31)),
            result,
        )
    }

    @Test
    fun monthlyOnThe31st_leapYearFebruary29() {
        val result = dates(d(2024, 1, 31), monthly, d(2024, 1, 1), d(2024, 3, 31))
        assertEquals(listOf(d(2024, 1, 31), d(2024, 2, 29), d(2024, 3, 31)), result)
    }

    @Test
    fun monthlyOnThe30th_clampsFebruary() {
        val result = dates(d(2025, 1, 30), monthly, d(2025, 1, 1), d(2025, 3, 31))
        assertEquals(listOf(d(2025, 1, 30), d(2025, 2, 28), d(2025, 3, 30)), result)
    }

    @Test
    fun yearlyLeapDay_fallsBackToFeb28_thenReturnsToFeb29() {
        val yearly = Recurrence(RecurrenceRule.YEARLY)
        val result = dates(d(2024, 2, 29), yearly, d(2024, 1, 1), d(2028, 12, 31))
        assertEquals(
            listOf(d(2024, 2, 29), d(2025, 2, 28), d(2026, 2, 28), d(2027, 2, 28), d(2028, 2, 29)),
            result,
        )
    }

    @Test
    fun quarterly_everyThreeMonths() {
        val result = dates(d(2025, 1, 15), Recurrence(RecurrenceRule.QUARTERLY), d(2025, 1, 1), d(2025, 12, 31))
        assertEquals(listOf(d(2025, 1, 15), d(2025, 4, 15), d(2025, 7, 15), d(2025, 10, 15)), result)
    }

    @Test
    fun biweekly_everyFourteenDays() {
        val result = dates(d(2025, 1, 1), Recurrence(RecurrenceRule.BIWEEKLY), d(2025, 1, 1), d(2025, 2, 12))
        assertEquals(listOf(d(2025, 1, 1), d(2025, 1, 15), d(2025, 1, 29), d(2025, 2, 12)), result)
    }

    @Test
    fun daily_acrossMonthBoundary() {
        val result = dates(d(2025, 2, 27), Recurrence(RecurrenceRule.DAILY), d(2025, 2, 27), d(2025, 3, 2))
        assertEquals(listOf(d(2025, 2, 27), d(2025, 2, 28), d(2025, 3, 1), d(2025, 3, 2)), result)
    }

    @Test
    fun customEveryTwoMonths() {
        val custom = Recurrence(RecurrenceRule.CUSTOM, 2, IntervalUnit.MONTH)
        val result = dates(d(2025, 1, 31), custom, d(2025, 1, 1), d(2025, 7, 31))
        assertEquals(listOf(d(2025, 1, 31), d(2025, 3, 31), d(2025, 5, 31), d(2025, 7, 31)), result)
    }

    @Test
    fun customEveryTenDays() {
        val custom = Recurrence(RecurrenceRule.CUSTOM, 10, IntervalUnit.DAY)
        val result = dates(d(2025, 1, 1), custom, d(2025, 1, 1), d(2025, 1, 31))
        assertEquals(listOf(d(2025, 1, 1), d(2025, 1, 11), d(2025, 1, 21), d(2025, 1, 31)), result)
    }

    @Test
    fun customEveryThreeWeeks_andEveryTwoYears() {
        val weeks = Recurrence(RecurrenceRule.CUSTOM, 3, IntervalUnit.WEEK)
        assertEquals(
            listOf(d(2025, 1, 1), d(2025, 1, 22), d(2025, 2, 12)),
            dates(d(2025, 1, 1), weeks, d(2025, 1, 1), d(2025, 2, 20)),
        )
        val years = Recurrence(RecurrenceRule.CUSTOM, 2, IntervalUnit.YEAR)
        assertEquals(
            listOf(d(2025, 6, 1), d(2027, 6, 1), d(2029, 6, 1)),
            dates(d(2025, 6, 1), years, d(2025, 1, 1), d(2030, 1, 1)),
        )
    }

    @Test
    fun windowStartingLate_skipsEarlierDates_withoutDrift() {
        val result = dates(d(2020, 1, 31), monthly, d(2025, 2, 1), d(2025, 4, 30))
        assertEquals(listOf(d(2025, 2, 28), d(2025, 3, 31), d(2025, 4, 30)), result)
    }

    @Test
    fun endDate_stopsGeneration_inclusive() {
        val result = dates(d(2025, 1, 10), monthly, d(2025, 1, 1), d(2025, 12, 31), end = d(2025, 3, 10))
        assertEquals(listOf(d(2025, 1, 10), d(2025, 2, 10), d(2025, 3, 10)), result)
    }

    @Test
    fun startAfterWindow_isEmpty() {
        assertTrue(dates(d(2026, 1, 1), monthly, d(2025, 1, 1), d(2025, 12, 31)).isEmpty())
    }

    @Test
    fun isScheduled_matchesGeneratedDates() {
        val start = d(2025, 1, 31)
        assertTrue(RecurrenceCalculator.isScheduled(start, monthly, d(2025, 2, 28)))
        assertTrue(RecurrenceCalculator.isScheduled(start, monthly, d(2025, 3, 31)))
        assertFalse(RecurrenceCalculator.isScheduled(start, monthly, d(2025, 3, 30)))
        assertFalse(RecurrenceCalculator.isScheduled(start, monthly, d(2024, 12, 31)))
        assertTrue(RecurrenceCalculator.isScheduled(start, Recurrence.None, start))
        assertFalse(RecurrenceCalculator.isScheduled(start, Recurrence.None, d(2025, 2, 28)))
        assertTrue(RecurrenceCalculator.isScheduled(d(2024, 2, 29), Recurrence(RecurrenceRule.YEARLY), d(2025, 2, 28)))
        assertFalse(RecurrenceCalculator.isScheduled(d(2025, 1, 1), Recurrence(RecurrenceRule.WEEKLY), d(2025, 1, 9)))
    }

    @Test
    fun isScheduled_agreesWithDatesBetween_forManyRules() {
        val rules = listOf(
            Recurrence(RecurrenceRule.DAILY), Recurrence(RecurrenceRule.WEEKLY), Recurrence(RecurrenceRule.BIWEEKLY),
            monthly, Recurrence(RecurrenceRule.QUARTERLY), Recurrence(RecurrenceRule.YEARLY),
            Recurrence(RecurrenceRule.CUSTOM, 5, IntervalUnit.MONTH),
        )
        val start = d(2023, 8, 31)
        val from = d(2023, 8, 1)
        val to = d(2025, 8, 31) // two years: stays under the per-call cap even for daily rules
        for (rule in rules) {
            val generated = dates(start, rule, from, to).toSet()
            var day = from
            while (!day.isAfter(to)) {
                assertEquals("$rule $day", day in generated, RecurrenceCalculator.isScheduled(start, rule, day))
                day = day.plusDays(1)
            }
        }
    }
}
