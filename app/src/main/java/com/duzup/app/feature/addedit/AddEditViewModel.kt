package com.duzup.app.feature.addedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.duzup.app.core.model.Bill
import com.duzup.app.core.model.BuiltInCategories
import com.duzup.app.core.model.Category
import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.Recurrence
import com.duzup.app.core.model.RecurrenceRule
import com.duzup.app.core.preferences.PreferencesRepository
import com.duzup.app.core.time.TimeProvider
import com.duzup.app.data.repository.BillRepository
import com.duzup.app.domain.quickadd.QuickAddParser
import com.duzup.app.navigation.AddEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class AddEditUiState(
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val draft: ItemDraft = ItemDraft(),
    val categories: List<Category> = emptyList(),
    val errors: Map<DraftField, DraftError> = emptyMap(),
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val today: LocalDate = LocalDate.now(),
)

sealed interface AddEditEvent {
    /** [askNotificationPermission]: reminders were configured and the user was never asked before. */
    data class Saved(val askNotificationPermission: Boolean) : AddEditEvent
    data object Missing : AddEditEvent
}

@HiltViewModel
class AddEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: BillRepository,
    private val preferences: PreferencesRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<AddEditRoute>()
    private val itemId: String? = route.itemId
    private var original: Bill? = null

    private val _state = MutableStateFlow(AddEditUiState(isEditing = itemId != null, today = time.today()))
    val state: StateFlow<AddEditUiState> = _state.asStateFlow()

    private val _events = Channel<AddEditEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.categories.collect { list -> _state.update { it.copy(categories = list) } }
        }
        viewModelScope.launch {
            val draft = if (itemId != null) {
                val bill = repository.getBill(itemId)
                if (bill == null) {
                    _events.send(AddEditEvent.Missing)
                    return@launch
                }
                original = bill
                ItemDraft.from(bill)
            } else {
                val prefs = preferences.current()
                val now = time.now().atZone(time.zone()).toLocalDateTime()
                val parsed = route.quick?.let { QuickAddParser.parse(it, now) }
                val kind = parsed?.kind ?: runCatching { ItemKind.valueOf(route.kind) }.getOrDefault(ItemKind.BILL)
                val base = ItemDraft.defaults(prefs, kind, time.today(), now.toLocalTime())
                if (parsed != null) base.withQuickAdd(parsed, prefs) else base
            }
            _state.update { it.copy(isLoading = false, draft = draft) }
        }
    }

    fun update(transform: (ItemDraft) -> ItemDraft) {
        _state.update { current ->
            val draft = transform(current.draft)
            current.copy(
                draft = draft,
                errors = if (current.showErrors) ItemDraftValidator.validate(draft) else current.errors,
            )
        }
    }

    fun setTitle(value: String) = update { it.copy(title = value) }
    fun setAmount(value: String) = update { it.copy(amountText = value) }
    fun setCurrency(code: String) = update { it.copy(currencyCode = code) }
    fun setCategory(id: String) = update { it.copy(categoryId = id) }
    fun setDueDate(date: LocalDate) = update { it.copy(dueDate = date) }
    fun setTime(value: LocalTime) = update { it.copy(time = value) }
    fun setEndTime(value: LocalTime?) = update { it.copy(endTime = value) }
    fun setAllDay(value: Boolean) = update { it.copy(allDay = value, endTime = if (value) null else it.endTime) }
    fun setLocation(value: String) = update { it.copy(location = value) }
    fun setPriority(value: Int) = update { it.copy(priority = value) }
    fun setPinned(value: Boolean) = update { it.copy(pinned = value) }
    fun setIncludeAge(value: Boolean) = update { it.copy(includeAge = value) }
    fun setRemind(value: Boolean) = update { it.copy(remind = value) }
    fun setNotes(value: String) = update { it.copy(notes = value) }
    fun setEndDate(date: LocalDate?) = update { it.copy(endDate = date) }

    fun setRecurrence(recurrence: Recurrence) = update {
        it.copy(recurrence = recurrence, endDate = if (recurrence.isRecurring) it.endDate else null)
    }

    fun toggleWeekday(day: DayOfWeek) = update {
        it.copy(weekdays = if (day in it.weekdays) it.weekdays - day else it.weekdays + day)
    }

    fun addRoutineTime(value: LocalTime) = update {
        it.copy(routineTimes = (it.routineTimes + value).distinct().sorted())
    }

    fun removeRoutineTime(value: LocalTime) = update { it.copy(routineTimes = it.routineTimes - value) }

    fun toggleOffset(minutes: Int) = update {
        it.copy(offsets = if (minutes in it.offsets) it.offsets - minutes else it.offsets + minutes)
    }

    fun addCustomOffset(minutes: Int) = update { it.copy(offsets = it.offsets + minutes) }

    fun saveCategory(id: String?, name: String, iconKey: String, colorArgb: Long?) {
        viewModelScope.launch {
            val savedId = repository.saveCategory(id, name, iconKey, colorArgb)
            if (id == null) setCategory(savedId)
        }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch {
            repository.deleteCategory(id)
            if (_state.value.draft.categoryId == id) setCategory(BuiltInCategories.OTHER)
        }
    }

    fun markPermissionAsked() {
        viewModelScope.launch { preferences.setNotificationPermissionAsked(true) }
    }

    fun save() {
        val current = _state.value
        if (current.saving) return
        val errors = ItemDraftValidator.validate(current.draft)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors, showErrors = true) }
            return
        }
        _state.update { it.copy(saving = true, errors = emptyMap(), showErrors = true) }
        viewModelScope.launch {
            val draft = current.draft
            val prefs = preferences.current()
            val now = time.now()
            val kind = draft.kind
            val routine = kind == ItemKind.ROUTINE
            val repeats = kind != ItemKind.ROUTINE && kind != ItemKind.BIRTHDAY && kind != ItemKind.MEMO
            val recurrence = when (kind) {
                ItemKind.BIRTHDAY -> Recurrence(RecurrenceRule.YEARLY)
                ItemKind.ROUTINE -> Recurrence(RecurrenceRule.DAILY)
                ItemKind.MEMO -> Recurrence.None
                else -> draft.recurrence
            }
            val dueTime = when {
                routine -> draft.routineTimes.min()
                kind == ItemKind.EVENT && draft.allDay -> prefs.reminderTime
                else -> draft.time
            }
            val bill = Bill(
                id = original?.id ?: repository.newId(),
                title = draft.title.trim(),
                categoryId = if (kind == ItemKind.BILL) draft.categoryId else BuiltInCategories.OTHER,
                notes = draft.notes.trim().ifEmpty { null },
                amountMinorUnits = ItemDraftValidator.amountMinorUnits(draft),
                currencyCode = draft.currencyCode,
                startDueDate = draft.dueDate ?: time.today(),
                dueTime = dueTime,
                zoneId = original?.zoneId ?: time.zone(),
                recurrence = recurrence,
                endDate = draft.endDate.takeIf { repeats && recurrence.isRecurring },
                reminderOffsets = if (draft.isScheduled) draft.offsets.sortedDescending() else emptyList(),
                isArchived = original?.isArchived ?: false,
                createdAt = original?.createdAt ?: now,
                updatedAt = now,
                kind = kind,
                scheduled = draft.isScheduled,
                endTime = draft.endTime.takeIf { kind == ItemKind.EVENT && !draft.allDay },
                allDay = kind == ItemKind.EVENT && draft.allDay,
                location = draft.location.trim().ifEmpty { null }.takeIf { kind == ItemKind.EVENT },
                priority = if (kind == ItemKind.TASK) draft.priority else 1,
                isPinned = kind == ItemKind.MEMO && draft.pinned,
                includeAge = kind != ItemKind.BIRTHDAY || draft.includeAge,
                routineTimes = if (routine) draft.routineTimes.distinct().sorted() else emptyList(),
                weekdays = if (routine) draft.weekdays else emptySet(),
            )
            repository.saveBill(bill)
            val asked = preferences.current().notificationPermissionAsked
            _events.send(AddEditEvent.Saved(askNotificationPermission = bill.scheduled && bill.reminderOffsets.isNotEmpty() && !asked))
        }
    }
}
