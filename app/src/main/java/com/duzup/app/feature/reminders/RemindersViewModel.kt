package com.duzup.app.feature.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzup.app.core.model.ReminderRow
import com.duzup.app.data.repository.BillRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ReminderUi(
    val reminder: ReminderRow,
    val billId: String?,
    val title: String,
    val dueDate: java.time.LocalDate?,
)

data class RemindersUiState(
    val isLoading: Boolean = true,
    val scheduled: List<ReminderUi> = emptyList(),
    val delivered: List<ReminderUi> = emptyList(),
)

@HiltViewModel
class RemindersViewModel @Inject constructor(
    repository: BillRepository,
) : ViewModel() {

    val uiState: StateFlow<RemindersUiState> = combine(
        repository.pendingReminders,
        repository.recentlyDeliveredReminders(),
        repository.occurrenceItems,
    ) { pending, delivered, items ->
        val byOccurrence = items.associateBy { it.id }
        fun ui(row: ReminderRow): ReminderUi {
            val item = byOccurrence[row.occurrenceId]
            return ReminderUi(row, item?.bill?.id, item?.bill?.title.orEmpty(), item?.occurrence?.dueDate)
        }
        RemindersUiState(
            isLoading = false,
            // Rows whose bill is gone or archived never fire, so they are not listed as upcoming.
            scheduled = pending
                .filter { byOccurrence[it.occurrenceId]?.bill?.isArchived == false }
                .take(SCHEDULED_SHOWN)
                .map(::ui),
            delivered = delivered.map(::ui).filter { it.title.isNotEmpty() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersUiState())

    private companion object {
        const val SCHEDULED_SHOWN = 30
    }
}
