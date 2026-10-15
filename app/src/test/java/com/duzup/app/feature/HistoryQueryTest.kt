package com.duzup.app.feature

import com.duzup.app.TestData
import com.duzup.app.core.model.Category
import com.duzup.app.core.model.DisplayStatus
import com.duzup.app.feature.history.DateFilter
import com.duzup.app.feature.history.HistoryQuery
import com.duzup.app.feature.history.SortOrder
import com.duzup.app.feature.history.StatusFilter
import com.duzup.app.feature.home.HomeRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HistoryQueryTest {
    private val today = LocalDate.of(2025, 3, 15)
    private val utilities = Category("utilities", "Utilities", "bolt", null, true, 0)

    @Test
    fun textSearch_matchesTitleOrCategory_caseInsensitive() {
        assertTrue(HistoryQuery.matchesText("elec", "Electricity Bill", utilities))
        assertTrue(HistoryQuery.matchesText("UTIL", "Anything", utilities))
        assertTrue(HistoryQuery.matchesText("  ", "Anything", null))
        assertFalse(HistoryQuery.matchesText("netflix", "Electricity Bill", utilities))
    }

    @Test
    fun dateFilters_coverBoundaries() {
        assertTrue(HistoryQuery.inDateRange(DateFilter.THIS_MONTH, LocalDate.of(2025, 3, 1), today))
        assertFalse(HistoryQuery.inDateRange(DateFilter.THIS_MONTH, LocalDate.of(2025, 2, 28), today))
        assertTrue(HistoryQuery.inDateRange(DateFilter.LAST_30_DAYS, today.minusDays(30), today))
        assertFalse(HistoryQuery.inDateRange(DateFilter.LAST_30_DAYS, today.minusDays(31), today))
        assertFalse(HistoryQuery.inDateRange(DateFilter.LAST_30_DAYS, today.plusDays(1), today))
        assertTrue(HistoryQuery.inDateRange(DateFilter.NEXT_30_DAYS, today, today))
        assertFalse(HistoryQuery.inDateRange(DateFilter.NEXT_30_DAYS, today.minusDays(1), today))
        assertTrue(HistoryQuery.inDateRange(DateFilter.THIS_YEAR, LocalDate.of(2025, 12, 31), today))
        assertFalse(HistoryQuery.inDateRange(DateFilter.THIS_YEAR, LocalDate.of(2024, 12, 31), today))
    }

    @Test
    fun statusFilter_groupsPendingVariants() {
        assertTrue(HistoryQuery.matchesStatus(StatusFilter.PENDING, DisplayStatus.DUE_SOON))
        assertTrue(HistoryQuery.matchesStatus(StatusFilter.PENDING, DisplayStatus.DUE_TODAY))
        assertFalse(HistoryQuery.matchesStatus(StatusFilter.PENDING, DisplayStatus.OVERDUE))
        assertTrue(HistoryQuery.matchesStatus(StatusFilter.ALL, DisplayStatus.SKIPPED))
    }

    @Test
    fun sorting_amountPutsUnknownLast_andNameIsCaseInsensitive() {
        fun row(id: String, title: String, amount: Long?) = HomeRow(
            TestData.item(
                TestData.bill(id = id, amount = amount).copy(title = title),
                TestData.occurrence(id = id, billId = id),
            ),
            DisplayStatus.UPCOMING,
        )
        val rows = listOf(row("a", "zeta", null), row("b", "Alpha", 500), row("c", "beta", 900))
        assertEquals(listOf("c", "b", "a"), HistoryQuery.sortPayments(rows, SortOrder.AMOUNT_DESC).map { it.item.id })
        assertEquals(listOf("b", "c", "a"), HistoryQuery.sortPayments(rows, SortOrder.NAME_ASC).map { it.item.id })
    }
}
