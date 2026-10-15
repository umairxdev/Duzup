package com.duzup.app.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duzup.app.core.model.Bill
import com.duzup.app.core.model.BuiltInCategories
import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.OccurrenceItem
import com.duzup.app.core.model.OccurrenceStatus
import com.duzup.app.core.model.Recurrence
import com.duzup.app.core.preferences.PreferencesRepository
import com.duzup.app.core.time.TimeProvider
import com.duzup.app.data.repository.BillRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A note plus its next pending reminder, if it has one. */
data class NoteUi(val bill: Bill, val nextReminder: OccurrenceItem?)

data class NotesUiState(
    val isLoading: Boolean = true,
    val pinned: List<NoteUi> = emptyList(),
    val others: List<NoteUi> = emptyList(),
) {
    val isEmpty: Boolean get() = pinned.isEmpty() && others.isEmpty()
}

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val repository: BillRepository,
    private val preferences: PreferencesRepository,
    private val time: TimeProvider,
) : ViewModel() {

    val uiState: StateFlow<NotesUiState> = combine(repository.bills, repository.occurrenceItems) { bills, occurrences ->
        val nextByNote = occurrences
            .filter { it.kind == ItemKind.MEMO && it.occurrence.status == OccurrenceStatus.PENDING }
            .groupBy { it.bill.id }
            .mapValues { (_, list) -> list.minBy { it.dueInstant } }
        val notes = bills
            .filter { it.kind == ItemKind.MEMO && !it.isArchived }
            .sortedByDescending { it.updatedAt }
            .map { NoteUi(it, nextByNote[it.id]) }
        NotesUiState(
            isLoading = false,
            pinned = notes.filter { it.bill.isPinned },
            others = notes.filter { !it.bill.isPinned },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    /** Quick capture: a title-only note without a reminder. Longer notes go through the full form. */
    fun addQuickNote(title: String) {
        val text = title.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val prefs = preferences.current()
            val now = time.now()
            repository.saveBill(
                Bill(
                    id = repository.newId(),
                    title = text.take(80),
                    categoryId = BuiltInCategories.OTHER,
                    notes = null,
                    amountMinorUnits = null,
                    currencyCode = prefs.currencyCode,
                    startDueDate = time.today(),
                    dueTime = prefs.reminderTime,
                    zoneId = time.zone(),
                    recurrence = Recurrence.None,
                    endDate = null,
                    reminderOffsets = emptyList(),
                    isArchived = false,
                    createdAt = now,
                    updatedAt = now,
                    kind = ItemKind.MEMO,
                    scheduled = false,
                ),
            )
        }
    }

    fun setPinned(id: String, pinned: Boolean) {
        viewModelScope.launch { repository.setPinned(id, pinned) }
    }
}
