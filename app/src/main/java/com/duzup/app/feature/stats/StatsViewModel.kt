package com.duzup.app.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzup.app.core.model.Category
import com.duzup.app.core.preferences.PreferencesRepository
import com.duzup.app.core.time.TimeProvider
import com.duzup.app.core.time.ticker
import com.duzup.app.data.repository.BillRepository
import com.duzup.app.domain.summary.MonthStats
import com.duzup.app.domain.summary.SummaryCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.YearMonth
import javax.inject.Inject

data class StatsUiState(
    val isLoading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val isCurrentMonth: Boolean = true,
    val stats: MonthStats? = null,
    val categories: Map<String, Category> = emptyMap(),
    /** Currencies present in the month, preferred currency first. */
    val currencies: List<String> = emptyList(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    repository: BillRepository,
    preferences: PreferencesRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(time.today()))

    val uiState: StateFlow<StatsUiState> = combine(
        repository.occurrenceItems,
        repository.categories,
        preferences.preferences,
        month,
        time.ticker(),
    ) { items, categories, prefs, selected, now ->
        val stats = SummaryCalculator.monthStats(items, selected, now, prefs.currencyCode)
        val codes = (stats.paid + stats.pending + stats.overdue).map { it.currencyCode } +
            stats.categories.map { it.currencyCode }
        StatsUiState(
            isLoading = false,
            month = selected,
            isCurrentMonth = selected == YearMonth.from(now.atZone(time.zone())),
            stats = stats,
            categories = categories.associateBy { it.id },
            currencies = codes.distinct().sortedWith(compareBy<String> { it != prefs.currencyCode }.thenBy { it }),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun shiftMonth(delta: Long) = month.update { it.plusMonths(delta) }

    fun resetMonth() {
        month.value = YearMonth.from(time.today())
    }
}
