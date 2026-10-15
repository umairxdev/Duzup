package com.duzup.app.feature.bills

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.duzup.app.core.model.Bill
import com.duzup.app.core.model.Category
import com.duzup.app.core.model.DisplayStatus
import com.duzup.app.core.model.Occurrence
import com.duzup.app.core.model.OccurrenceItem
import com.duzup.app.core.model.OccurrenceStatus
import com.duzup.app.core.model.SnoozeChoice
import com.duzup.app.core.time.TimeProvider
import com.duzup.app.core.time.ticker
import com.duzup.app.data.repository.BillRepository
import com.duzup.app.domain.payments.PaymentRules
import com.duzup.app.navigation.DetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class OccurrenceRowUi(val occurrence: Occurrence, val status: DisplayStatus)

data class DetailUiState(
    val isLoading: Boolean = true,
    val bill: Bill? = null,
    val category: Category? = null,
    val today: LocalDate = LocalDate.now(),
    /** The occurrence the primary actions apply to: the earliest unfinished one (late ones first). */
    val next: OccurrenceRowUi? = null,
    /** Unfinished occurrences: everything late plus the next few. */
    val pending: List<OccurrenceRowUi> = emptyList(),
    /** Done, skipped and (for kinds that cannot be late) passed occurrences, newest first. */
    val history: List<OccurrenceRowUi> = emptyList(),
    /** Occurrences happening today (routines have several). */
    val todays: List<OccurrenceRowUi> = emptyList(),
)

sealed interface DetailEvent {
    data object Deleted : DetailEvent
    data class Snoozed(val ok: Boolean) : DetailEvent
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: BillRepository,
    private val time: TimeProvider,
) : ViewModel() {

    val billId: String = savedStateHandle.toRoute<DetailRoute>().itemId

    val uiState: StateFlow<DetailUiState> = combine(
        repository.observeBill(billId),
        repository.observeOccurrences(billId),
        repository.categories,
        time.ticker(),
    ) { bill, occurrences, categories, now ->
        if (bill == null) {
            DetailUiState(isLoading = false)
        } else {
            val today = now.atZone(bill.zoneId).toLocalDate()
            fun statusOf(o: Occurrence) = PaymentRules.displayStatus(OccurrenceItem(o, bill), now, today)
            fun asItem(o: Occurrence) = OccurrenceItem(o, bill)

            val unfinished = occurrences.filter { it.status == OccurrenceStatus.PENDING }.sortedWith(compareBy({ it.dueDate }, { it.dueTime }))
            val late = unfinished.filter { PaymentRules.isOverdue(asItem(it), now) }
            val passed = unfinished.filter { PaymentRules.isPast(asItem(it), now) }
            val upcoming = unfinished - late.toSet() - passed.toSet()
            val shown = (late + upcoming.take(UPCOMING_SHOWN)).map { OccurrenceRowUi(it, statusOf(it)) }

            DetailUiState(
                isLoading = false,
                bill = bill,
                category = categories.firstOrNull { it.id == bill.categoryId },
                today = today,
                next = shown.firstOrNull(),
                pending = shown,
                history = (occurrences.filter { it.status != OccurrenceStatus.PENDING } + passed)
                    .sortedWith(compareByDescending<Occurrence> { it.dueDate }.thenByDescending { it.dueTime })
                    .map { OccurrenceRowUi(it, statusOf(it)) },
                todays = occurrences.filter { it.dueDate == today }.sortedBy { it.dueTime }.map { OccurrenceRowUi(it, statusOf(it)) },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    private val _events = Channel<DetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun markPaid(occurrenceId: String, amountMinorUnits: Long?, paidAt: Instant, note: String?) {
        viewModelScope.launch { repository.markPaid(occurrenceId, paidAt, amountMinorUnits, note) }
    }

    /** One-tap "done" for everything that is not a bill. */
    fun markDone(occurrenceId: String) {
        viewModelScope.launch { repository.markPaid(occurrenceId, time.now(), null, null) }
    }

    fun editPayment(occurrenceId: String, amountMinorUnits: Long?, paidAt: Instant, note: String?) {
        viewModelScope.launch { repository.editPayment(occurrenceId, paidAt, amountMinorUnits, note) }
    }

    fun undoPaid(occurrenceId: String) = viewModelScope.launch { repository.undoPaid(occurrenceId) }
    fun skip(occurrenceId: String) = viewModelScope.launch { repository.skip(occurrenceId) }
    fun restoreSkipped(occurrenceId: String) = viewModelScope.launch { repository.restoreSkipped(occurrenceId) }

    /** [choice] null means "use my snooze setting". */
    fun snooze(occurrenceId: String, choice: SnoozeChoice? = null) {
        viewModelScope.launch { _events.send(DetailEvent.Snoozed(repository.snoozeWith(occurrenceId, choice))) }
    }

    fun setPinned(pinned: Boolean) = viewModelScope.launch { repository.setPinned(billId, pinned) }

    fun setArchived(archived: Boolean) = viewModelScope.launch { repository.setArchived(billId, archived) }

    fun delete() {
        viewModelScope.launch {
            repository.deleteBill(billId)
            _events.send(DetailEvent.Deleted)
        }
    }

    /** Instant to store for a completion on [date]: now if today, otherwise midday on that day. */
    fun paidInstant(date: LocalDate, bill: Bill): Instant {
        val now = time.now()
        val today = now.atZone(bill.zoneId).toLocalDate()
        return if (date == today) now else date.atTime(LocalTime.NOON).atZone(bill.zoneId).toInstant()
    }

    private companion object {
        const val UPCOMING_SHOWN = 3
    }
}
