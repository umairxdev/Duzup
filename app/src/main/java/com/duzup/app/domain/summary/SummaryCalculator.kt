package com.duzup.app.domain.summary

import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.OccurrenceItem
import com.duzup.app.core.model.OccurrenceStatus
import com.duzup.app.domain.payments.PaymentRules
import java.time.Instant
import java.time.YearMonth

/** Sum for one currency. Occurrences without an amount are counted separately, never as zero. */
data class CurrencyAmount(val currencyCode: String, val minorUnits: Long, val unknownAmountCount: Int)

data class MonthTotals(
    val month: YearMonth,
    /** Unpaid (pending, including overdue) occurrences due inside [month]. */
    val pendingCount: Int,
    val perCurrency: List<CurrencyAmount>,
    /** Pending amount per week-of-month bucket (index 0..5) for [chartCurrency]. */
    val weeklyBuckets: List<Long>,
    val chartCurrency: String?,
)

data class CategorySlice(val categoryId: String, val currencyCode: String, val minorUnits: Long, val count: Int)

data class MonthStats(
    val month: YearMonth,
    val paid: List<CurrencyAmount>,
    val pending: List<CurrencyAmount>,
    val overdue: List<CurrencyAmount>,
    val paidCount: Int,
    val pendingCount: Int,
    val overdueCount: Int,
    val skippedCount: Int,
    val categories: List<CategorySlice>,
) {
    val isEmpty: Boolean get() = paidCount + pendingCount + overdueCount + skippedCount == 0
}

object SummaryCalculator {

    /** Home card: unpaid occurrences due within the selected calendar month, per currency. */
    fun monthTotals(
        items: List<OccurrenceItem>,
        month: YearMonth,
        preferredCurrency: String,
    ): MonthTotals {
        val pending = items.filter {
            it.kind == ItemKind.BILL && !it.bill.isArchived &&
                it.occurrence.status == OccurrenceStatus.PENDING &&
                YearMonth.from(it.occurrence.dueDate) == month
        }
        val perCurrency = sumByCurrency(pending, preferredCurrency)
        val chartCurrency = perCurrency.firstOrNull { it.minorUnits > 0 }?.currencyCode
        val buckets = LongArray(6)
        if (chartCurrency != null) {
            pending.filter { it.bill.currencyCode == chartCurrency }.forEach { item ->
                val amount = item.effectiveAmountMinorUnits ?: return@forEach
                buckets[(item.occurrence.dueDate.dayOfMonth - 1) / 7] += amount
            }
        }
        return MonthTotals(
            month = month,
            pendingCount = pending.size,
            perCurrency = perCurrency,
            weeklyBuckets = if (chartCurrency != null) buckets.toList() else emptyList(),
            chartCurrency = chartCurrency,
        )
    }

    /**
     * Statistics for [month]:
     * - paid: PAID occurrences whose payment date falls in the month (actual amount if recorded)
     * - pending: unpaid, due in the month, not yet overdue
     * - overdue: unpaid, due in the month, due instant already passed
     */
    fun monthStats(
        items: List<OccurrenceItem>,
        month: YearMonth,
        now: Instant,
        preferredCurrency: String,
    ): MonthStats {
        val live = items.filter { it.kind == ItemKind.BILL && !it.bill.isArchived }
        val paid = live.filter {
            it.occurrence.status == OccurrenceStatus.PAID &&
                it.occurrence.paidAt?.atZone(it.bill.zoneId)?.let { z -> YearMonth.from(z) } == month
        }
        val dueInMonth = live.filter { YearMonth.from(it.occurrence.dueDate) == month }
        val pendingAll = dueInMonth.filter { it.occurrence.status == OccurrenceStatus.PENDING }
        val overdue = pendingAll.filter { PaymentRules.isOverdue(it, now) }
        val pending = pendingAll - overdue.toSet()
        val skipped = dueInMonth.count { it.occurrence.status == OccurrenceStatus.SKIPPED }

        val categories = dueInMonth
            .filter { it.occurrence.status != OccurrenceStatus.SKIPPED }
            .filter { it.effectiveAmountMinorUnits != null }
            .groupBy { it.bill.categoryId to it.bill.currencyCode }
            .map { (key, list) ->
                CategorySlice(key.first, key.second, list.sumOf { it.effectiveAmountMinorUnits ?: 0L }, list.size)
            }
            .sortedByDescending { it.minorUnits }

        return MonthStats(
            month = month,
            paid = sumByCurrency(paid, preferredCurrency),
            pending = sumByCurrency(pending, preferredCurrency),
            overdue = sumByCurrency(overdue, preferredCurrency),
            paidCount = paid.size,
            pendingCount = pending.size,
            overdueCount = overdue.size,
            skippedCount = skipped,
            categories = categories,
        )
    }

    /** Never adds unlike currencies; preferred currency sorts first. */
    fun sumByCurrency(items: List<OccurrenceItem>, preferredCurrency: String): List<CurrencyAmount> =
        items.groupBy { it.bill.currencyCode }
            .map { (code, list) ->
                CurrencyAmount(
                    currencyCode = code,
                    minorUnits = list.sumOf { it.effectiveAmountMinorUnits ?: 0L },
                    unknownAmountCount = list.count { it.effectiveAmountMinorUnits == null },
                )
            }
            .sortedWith(compareBy<CurrencyAmount> { it.currencyCode != preferredCurrency }.thenBy { it.currencyCode })
}
