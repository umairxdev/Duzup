package com.duzup.app.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzup.app.core.model.Category
import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.OccurrenceItem
import com.duzup.app.core.model.OccurrenceStatus
import com.duzup.app.core.preferences.PreferencesRepository
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

enum class DotState { OVERDUE, PENDING, DONE }

/** One marker under a day number: what kind of item, and whether it is late, upcoming or done. */
data class CalendarDot(val kind: ItemKind, val state: DotState)

data class DayCell(
    val date: LocalDate,
    val inMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
    val overdue: Int,
    val pending: Int,
    val paid: Int,
    /** At most three markers, late first, then upcoming, then done. */
    val dots: List<CalendarDot> = emptyList(),
) {
    val total: Int get() = overdue + pending + paid
}

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val firstDay: DayOfWeek = DayOfWeek.MONDAY,
    val cells: List<DayCell> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val dayRows: List<HomeRow> = emptyList(),
    val categories: Map<String, Category> = emptyMap(),
    val today: LocalDate = LocalDate.now(),
)

private data class CalendarSelection(val month: YearMonth, val selected: LocalDate)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: BillRepository,
    preferences: PreferencesRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val selection = MutableStateFlow(
        time.today().let { CalendarSelection(YearMonth.from(it), it) },
    )

    val uiState: StateFlow<CalendarUiState> = combine(
        repository.occurrenceItems,
        repository.categories,
        preferences.preferences,
        selection,
        time.ticker(),
    ) { items, categories, prefs, sel, now ->
        build(items, categories, prefs.firstDayOfWeek, sel, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun shiftMonth(delta: Long) {
        val target = selection.value.month.plusMonths(delta)
        val today = time.today()
        selection.value = CalendarSelection(
            month = target,
            selected = if (YearMonth.from(today) == target) today else target.atDay(1),
        )
        ensureGenerated(target)
    }

    fun goToToday() {
        val today = time.today()
        selection.value = CalendarSelection(YearMonth.from(today), today)
    }

    fun selectDate(date: LocalDate) {
        selection.update { CalendarSelection(YearMonth.from(date), date) }
        ensureGenerated(YearMonth.from(date))
    }

    /** Browsing far into the future generates that month's occurrences on demand (idempotent). */
    private fun ensureGenerated(month: YearMonth) {
        viewModelScope.launch { repository.reconcileAll(extendThrough = month.atEndOfMonth()) }
    }

    private fun build(
        all: List<OccurrenceItem>,
        categories: List<Category>,
        firstDay: DayOfWeek,
        sel: CalendarSelection,
        now: Instant,
    ): CalendarUiState {
        val today = now.atZone(time.zone()).toLocalDate()
        val live = all.filter { !it.bill.isArchived && it.occurrence.status != OccurrenceStatus.SKIPPED }
        val byDate = live.groupBy { it.occurrence.dueDate }

        val first = sel.month.atDay(1)
        val lead = (first.dayOfWeek.value - firstDay.value + 7) % 7
        val weeks = (lead + sel.month.lengthOfMonth() + 6) / 7
        val start = first.minusDays(lead.toLong())

        val cells = List(weeks * 7) { index ->
            val date = start.plusDays(index.toLong())
            val items = byDate[date].orEmpty()
            val overdue = items.count { PaymentRules.isOverdue(it, now) }
            val paid = items.count { it.occurrence.status == OccurrenceStatus.PAID }
            val dots = items
                .map { item ->
                    CalendarDot(
                        item.kind,
                        when {
                            item.occurrence.status == OccurrenceStatus.PAID -> DotState.DONE
                            PaymentRules.isOverdue(item, now) -> DotState.OVERDUE
                            else -> DotState.PENDING
                        },
                    )
                }
                .sortedBy { it.state.ordinal }
                .take(3)
            DayCell(
                date = date,
                inMonth = YearMonth.from(date) == sel.month,
                isToday = date == today,
                isSelected = date == sel.selected,
                overdue = overdue,
                pending = items.size - overdue - paid,
                paid = paid,
                dots = dots,
            )
        }
        val dayRows = byDate[sel.selected].orEmpty()
            .sortedBy { it.dueInstant }
            .map { HomeRow(it, PaymentRules.displayStatus(it, now, today)) }

        return CalendarUiState(
            month = sel.month,
            firstDay = firstDay,
            cells = cells,
            selectedDate = sel.selected,
            dayRows = dayRows,
            categories = categories.associateBy { it.id },
            today = today,
        )
    }
}
