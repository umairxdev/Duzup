package com.duzup.app.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzup.app.core.model.Bill
import com.duzup.app.core.model.Category
import com.duzup.app.core.model.DisplayStatus
import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.OccurrenceItem
import com.duzup.app.core.time.TimeProvider
import com.duzup.app.core.time.ticker
import com.duzup.app.data.repository.BillRepository
import com.duzup.app.domain.payments.PaymentRules
import com.duzup.app.feature.home.HomeRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

enum class HistoryMode { PAYMENTS, BILLS }
enum class StatusFilter { ALL, PENDING, OVERDUE, PAID, SKIPPED }
enum class DateFilter { ALL_TIME, THIS_MONTH, LAST_30_DAYS, NEXT_30_DAYS, THIS_YEAR }
enum class SortOrder { DUE_DATE_DESC, DUE_DATE_ASC, AMOUNT_DESC, NAME_ASC }

data class HistoryFilters(
    val mode: HistoryMode = HistoryMode.PAYMENTS,
    val query: String = "",
    val status: StatusFilter = StatusFilter.ALL,
    val categoryId: String? = null,
    /** Show only one kind of item (bills, events, ...); null shows everything. */
    val kind: ItemKind? = null,
    val date: DateFilter = DateFilter.ALL_TIME,
    val sort: SortOrder = SortOrder.DUE_DATE_DESC,
    val showArchived: Boolean = false,
)

data class HistoryUiState(
    val filters: HistoryFilters = HistoryFilters(),
    val payments: List<HomeRow> = emptyList(),
    val bills: List<Bill> = emptyList(),
    val categories: List<Category> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
)

/** Pure filtering/sorting, kept separate from the ViewModel so it is unit-testable. */
object HistoryQuery {
    fun matchesText(query: String, title: String, category: Category?): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return title.contains(q, ignoreCase = true) ||
            (category?.name?.contains(q, ignoreCase = true) == true) ||
            (category?.id?.contains(q, ignoreCase = true) == true)
    }

    fun inDateRange(filter: DateFilter, date: LocalDate, today: LocalDate): Boolean = when (filter) {
        DateFilter.ALL_TIME -> true
        DateFilter.THIS_MONTH -> date.year == today.year && date.month == today.month
        DateFilter.LAST_30_DAYS -> !date.isAfter(today) && !date.isBefore(today.minusDays(30))
        DateFilter.NEXT_30_DAYS -> !date.isBefore(today) && !date.isAfter(today.plusDays(30))
        DateFilter.THIS_YEAR -> date.year == today.year
    }

    fun matchesStatus(filter: StatusFilter, status: DisplayStatus): Boolean = when (filter) {
        StatusFilter.ALL -> true
        StatusFilter.PENDING -> status == DisplayStatus.UPCOMING || status == DisplayStatus.DUE_SOON || status == DisplayStatus.DUE_TODAY
        StatusFilter.OVERDUE -> status == DisplayStatus.OVERDUE
        StatusFilter.PAID -> status == DisplayStatus.PAID
        StatusFilter.SKIPPED -> status == DisplayStatus.SKIPPED
    }

    fun sortPayments(rows: List<HomeRow>, sort: SortOrder): List<HomeRow> = when (sort) {
        SortOrder.DUE_DATE_DESC -> rows.sortedByDescending { it.item.dueInstant }
        SortOrder.DUE_DATE_ASC -> rows.sortedBy { it.item.dueInstant }
        // Unknown amounts sort last in both amount orders.
        SortOrder.AMOUNT_DESC -> rows.sortedWith(
            compareByDescending<HomeRow> { it.item.effectiveAmountMinorUnits ?: Long.MIN_VALUE },
        )
        SortOrder.NAME_ASC -> rows.sortedBy { it.item.bill.title.lowercase() }
    }

    fun sortBills(bills: List<Bill>, sort: SortOrder): List<Bill> = when (sort) {
        SortOrder.DUE_DATE_DESC -> bills.sortedByDescending { it.startDueDate }
        SortOrder.DUE_DATE_ASC -> bills.sortedBy { it.startDueDate }
        SortOrder.AMOUNT_DESC -> bills.sortedByDescending { it.amountMinorUnits ?: Long.MIN_VALUE }
        SortOrder.NAME_ASC -> bills.sortedBy { it.title.lowercase() }
    }
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: BillRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val filters = MutableStateFlow(HistoryFilters())

    val uiState: StateFlow<HistoryUiState> = combine(
        repository.occurrenceItems,
        repository.bills,
        repository.categories,
        filters,
        time.ticker(),
    ) { items, bills, categories, f, now ->
        build(items, bills, categories, f, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun update(transform: (HistoryFilters) -> HistoryFilters) = filters.update(transform)

    fun setArchived(billId: String, archived: Boolean) {
        viewModelScope.launch { repository.setArchived(billId, archived) }
    }

    private fun build(
        items: List<OccurrenceItem>,
        bills: List<Bill>,
        categories: List<Category>,
        f: HistoryFilters,
        now: Instant,
    ): HistoryUiState {
        val today = now.atZone(time.zone()).toLocalDate()
        val categoryById = categories.associateBy { it.id }

        val payments = items
            .asSequence()
            .filter { f.kind == null || it.kind == f.kind }
            .filter { f.categoryId == null || it.bill.categoryId == f.categoryId }
            .filter { HistoryQuery.matchesText(f.query, it.bill.title, categoryById[it.bill.categoryId]) }
            .filter { HistoryQuery.inDateRange(f.date, it.occurrence.dueDate, today) }
            .map { HomeRow(it, PaymentRules.displayStatus(it, now, today)) }
            .filter { HistoryQuery.matchesStatus(f.status, it.status) }
            .toList()

        val billList = bills
            .filter { f.showArchived || !it.isArchived }
            .filter { f.kind == null || it.kind == f.kind }
            .filter { f.categoryId == null || it.categoryId == f.categoryId }
            .filter { HistoryQuery.matchesText(f.query, it.title, categoryById[it.categoryId]) }

        return HistoryUiState(
            filters = f,
            payments = HistoryQuery.sortPayments(payments, f.sort),
            bills = HistoryQuery.sortBills(billList, f.sort),
            categories = categories,
            today = today,
            isLoading = false,
        )
    }
}
