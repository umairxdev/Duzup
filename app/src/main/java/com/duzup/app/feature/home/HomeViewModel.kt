package com.duzup.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzup.app.core.model.Category
import com.duzup.app.core.model.DisplayStatus
import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.OccurrenceItem
import com.duzup.app.core.model.OccurrenceStatus
import com.duzup.app.core.preferences.PreferencesRepository
import com.duzup.app.core.time.TimeProvider
import com.duzup.app.core.time.ticker
import com.duzup.app.data.repository.BillRepository
import com.duzup.app.domain.payments.PaymentRules
import com.duzup.app.domain.summary.MonthTotals
import com.duzup.app.domain.summary.SummaryCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

enum class HomeTab { UPCOMING, PAID, OVERDUE }
enum class Greeting { MORNING, AFTERNOON, EVENING }
enum class HomeSectionKind { LATE, TODAY, COMING_UP, DONE }

data class HomeRow(val item: OccurrenceItem, val status: DisplayStatus)

data class HomeSection(val kind: HomeSectionKind, val rows: List<HomeRow>)

data class HomeUiState(
    val isLoading: Boolean = true,
    val greeting: Greeting = Greeting.MORNING,
    val month: YearMonth = YearMonth.now(),
    val isCurrentMonth: Boolean = true,
    val totals: MonthTotals? = null,
    /** The money card only appears when bills are in view. */
    val showMoneyCard: Boolean = false,
    val tab: HomeTab = HomeTab.UPCOMING,
    val kindFilter: ItemKind? = null,
    val sections: List<HomeSection> = emptyList(),
    val overdueCount: Int = 0,
    val hasAnyItems: Boolean = false,
    val categories: Map<String, Category> = emptyMap(),
    val today: LocalDate = LocalDate.now(),
) {
    val isEmpty: Boolean get() = sections.all { it.rows.isEmpty() }
}

private data class Selection(val month: YearMonth, val tab: HomeTab, val kind: ItemKind?)

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: BillRepository,
    preferences: PreferencesRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val selection = MutableStateFlow(Selection(YearMonth.from(time.today()), HomeTab.UPCOMING, null))

    val uiState: StateFlow<HomeUiState> = combine(
        repository.occurrenceItems,
        repository.categories,
        preferences.preferences,
        selection,
        time.ticker(),
    ) { items, categories, prefs, sel, now ->
        build(items, categories, prefs.currencyCode, sel, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun selectTab(tab: HomeTab) = selection.update { it.copy(tab = tab) }

    fun selectKind(kind: ItemKind?) = selection.update { it.copy(kind = kind) }

    fun shiftMonth(delta: Long) = selection.update { it.copy(month = it.month.plusMonths(delta)) }

    fun resetMonth() = selection.update { it.copy(month = YearMonth.from(time.today())) }

    private fun build(
        all: List<OccurrenceItem>,
        categories: List<Category>,
        preferredCurrency: String,
        sel: Selection,
        now: Instant,
    ): HomeUiState {
        val zone = time.zone()
        val today = now.atZone(zone).toLocalDate()
        val live = all.filter { !it.bill.isArchived }
        val filtered = live.filter { sel.kind == null || it.kind == sel.kind }

        fun row(item: OccurrenceItem) = HomeRow(item, PaymentRules.displayStatus(item, now, today))
        val overdue = filtered.filter { PaymentRules.isOverdue(it, now) }.sortedBy { it.dueInstant }

        val sections: List<HomeSection> = when (sel.tab) {
            HomeTab.UPCOMING -> {
                val pending = filtered.filter { it.occurrence.status == OccurrenceStatus.PENDING }
                // Everything still to happen today, in time order (a routine can appear several times).
                val todayRows = pending
                    .filter { it.occurrence.dueDate == today && !PaymentRules.isOverdue(it, now) && !PaymentRules.isPast(it, now) }
                    .sortedBy { it.dueInstant }
                // After today: just the next occurrence of each item ("what is coming next").
                val comingUp = pending
                    .filter { it.occurrence.dueDate.isAfter(today) }
                    .groupBy { it.bill.id }
                    .map { (_, list) -> list.minBy { it.dueInstant } }
                    .sortedBy { it.dueInstant }
                listOf(
                    HomeSection(HomeSectionKind.TODAY, todayRows.map(::row)),
                    HomeSection(HomeSectionKind.COMING_UP, comingUp.map(::row)),
                )
            }
            HomeTab.PAID -> listOf(
                HomeSection(
                    HomeSectionKind.DONE,
                    filtered
                        .filter {
                            it.occurrence.status == OccurrenceStatus.PAID &&
                                it.occurrence.paidAt?.atZone(it.bill.zoneId)?.let { z -> YearMonth.from(z) } == sel.month
                        }
                        .sortedByDescending { it.occurrence.paidAt }
                        .map { HomeRow(it, DisplayStatus.PAID) },
                ),
            )
            HomeTab.OVERDUE -> listOf(HomeSection(HomeSectionKind.LATE, overdue.map { HomeRow(it, DisplayStatus.OVERDUE) }))
        }

        val hour = now.atZone(zone).hour
        return HomeUiState(
            isLoading = false,
            greeting = when {
                hour < 12 -> Greeting.MORNING
                hour < 17 -> Greeting.AFTERNOON
                else -> Greeting.EVENING
            },
            month = sel.month,
            isCurrentMonth = sel.month == YearMonth.from(today),
            totals = SummaryCalculator.monthTotals(all, sel.month, preferredCurrency),
            showMoneyCard = (sel.kind == null || sel.kind == ItemKind.BILL) && live.any { it.kind == ItemKind.BILL },
            tab = sel.tab,
            kindFilter = sel.kind,
            sections = sections,
            overdueCount = overdue.size,
            hasAnyItems = live.isNotEmpty(),
            categories = categories.associateBy { it.id },
            today = today,
        )
    }
}
