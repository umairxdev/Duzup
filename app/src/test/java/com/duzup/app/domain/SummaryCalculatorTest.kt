package com.duzup.app.domain

import com.duzup.app.TestData
import com.duzup.app.core.model.OccurrenceStatus
import com.duzup.app.domain.summary.SummaryCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class SummaryCalculatorTest {
    private val march = YearMonth.of(2025, 3)
    private val now = Instant.parse("2025-03-15T12:00:00Z")

    private fun item(
        id: String,
        day: Int,
        amount: Long?,
        currency: String = "PKR",
        status: OccurrenceStatus = OccurrenceStatus.PENDING,
        paidAt: Instant? = null,
        actual: Long? = null,
        category: String = "utilities",
        archived: Boolean = false,
        month: Int = 3,
    ) = TestData.item(
        TestData.bill(id = "b$id", amount = amount, currency = currency, categoryId = category, archived = archived),
        TestData.occurrence(id = id, billId = "b$id", date = LocalDate.of(2025, month, day), status = status, paidAt = paidAt, actual = actual),
    )

    @Test
    fun monthTotals_sumsUnpaidOccurrencesInMonth_includingOverdue() {
        val items = listOf(
            item("a", 5, 100_00),   // overdue (due Mar 5, now Mar 15)
            item("b", 20, 250_00),  // upcoming
            item("c", 25, 50_00, status = OccurrenceStatus.PAID),
            item("d", 28, 70_00, month = 4), // other month
        )
        val totals = SummaryCalculator.monthTotals(items, march, "PKR")
        assertEquals(2, totals.pendingCount)
        assertEquals(350_00L, totals.perCurrency.single().minorUnits)
    }

    @Test
    fun monthTotals_neverMixesCurrencies_andPutsPreferredFirst() {
        val items = listOf(item("a", 5, 100_00, "PKR"), item("b", 6, 40_00, "USD"), item("c", 7, 10_00, "USD"))
        val totals = SummaryCalculator.monthTotals(items, march, "USD")
        assertEquals(listOf("USD", "PKR"), totals.perCurrency.map { it.currencyCode })
        assertEquals(50_00L, totals.perCurrency[0].minorUnits)
        assertEquals(100_00L, totals.perCurrency[1].minorUnits)
    }

    @Test
    fun unknownAmounts_areCountedSeparately_notAsZeroTotals() {
        val items = listOf(item("a", 5, null), item("b", 6, 10_00))
        val total = SummaryCalculator.monthTotals(items, march, "PKR").perCurrency.single()
        assertEquals(10_00L, total.minorUnits)
        assertEquals(1, total.unknownAmountCount)
    }

    @Test
    fun archivedBills_areExcluded() {
        val items = listOf(item("a", 5, 100_00, archived = true))
        val totals = SummaryCalculator.monthTotals(items, march, "PKR")
        assertEquals(0, totals.pendingCount)
        assertTrue(totals.perCurrency.isEmpty())
    }

    @Test
    fun recurringTemplate_isNotDoubleCounted_onlyOccurrencesAreSummed() {
        // One monthly bill with an occurrence in March and in April: March total counts exactly one.
        val bill = TestData.bill(id = "m", amount = 100_00)
        val items = listOf(
            TestData.item(bill, TestData.occurrence(id = "m1", billId = "m", date = LocalDate.of(2025, 3, 31))),
            TestData.item(bill, TestData.occurrence(id = "m2", billId = "m", date = LocalDate.of(2025, 4, 30))),
        )
        assertEquals(100_00L, SummaryCalculator.monthTotals(items, march, "PKR").perCurrency.single().minorUnits)
    }

    @Test
    fun noAmountsAtAll_hasNoChart() {
        val totals = SummaryCalculator.monthTotals(listOf(item("a", 5, null)), march, "PKR")
        assertNull(totals.chartCurrency)
        assertTrue(totals.weeklyBuckets.isEmpty())
    }

    @Test
    fun weeklyBuckets_groupPendingAmountByWeekOfMonth() {
        val items = listOf(item("a", 3, 10_00), item("b", 9, 20_00), item("c", 10, 5_00), item("d", 31, 7_00))
        val buckets = SummaryCalculator.monthTotals(items, march, "PKR").weeklyBuckets
        assertEquals(listOf(10_00L, 25_00L, 0L, 0L, 7_00L, 0L), buckets)
    }

    @Test
    fun monthStats_splitsPaidPendingOverdue() {
        val paidAt = Instant.parse("2025-03-02T10:00:00Z")
        val items = listOf(
            item("paid", 1, 100_00, status = OccurrenceStatus.PAID, paidAt = paidAt, actual = 90_00),
            item("over", 5, 40_00),
            item("pend", 25, 60_00),
            item("skip", 26, 30_00, status = OccurrenceStatus.SKIPPED),
            item("early", 4, 15_00, month = 4, status = OccurrenceStatus.PAID, paidAt = paidAt), // paid in March for April
        )
        val stats = SummaryCalculator.monthStats(items, march, now, "PKR")
        assertEquals(1 + 1, stats.paidCount)
        assertEquals(90_00L + 15_00L, stats.paid.single().minorUnits) // actual amount wins over bill amount
        assertEquals(40_00L, stats.overdue.single().minorUnits)
        assertEquals(60_00L, stats.pending.single().minorUnits)
        assertEquals(1, stats.skippedCount)
    }

    @Test
    fun categoryBreakdown_excludesSkipped_andGroupsByCurrency() {
        val items = listOf(
            item("a", 5, 100_00, category = "utilities"),
            item("b", 6, 50_00, category = "utilities"),
            item("c", 7, 30_00, category = "rent", currency = "USD"),
            item("d", 8, 99_00, category = "rent", status = OccurrenceStatus.SKIPPED),
        )
        val slices = SummaryCalculator.monthStats(items, march, now, "PKR").categories
        assertEquals(2, slices.size)
        assertEquals(150_00L, slices.first { it.categoryId == "utilities" }.minorUnits)
        assertEquals("USD", slices.first { it.categoryId == "rent" }.currencyCode)
    }

    @Test
    fun emptyMonth_isEmpty() {
        assertTrue(SummaryCalculator.monthStats(emptyList(), march, now, "PKR").isEmpty)
    }
}
