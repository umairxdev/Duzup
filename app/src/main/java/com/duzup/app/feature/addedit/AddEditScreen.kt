package com.duzup.app.feature.addedit

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.duzup.app.R
import com.duzup.app.core.designsystem.BackButton
import com.duzup.app.core.designsystem.CategoryColors
import com.duzup.app.core.designsystem.CategoryIcons
import com.duzup.app.core.designsystem.CenteredContent
import com.duzup.app.core.designsystem.ConfirmDialog
import com.duzup.app.core.designsystem.DuzupDatePickerDialog
import com.duzup.app.core.designsystem.DuzupSheet
import com.duzup.app.core.designsystem.DuzupTheme
import com.duzup.app.core.designsystem.DuzupTimePickerDialog
import com.duzup.app.core.designsystem.FieldError
import com.duzup.app.core.designsystem.FilterPill
import com.duzup.app.core.designsystem.FlatIconButton
import com.duzup.app.core.designsystem.InsetTextField
import com.duzup.app.core.designsystem.PixelMark
import com.duzup.app.core.designsystem.PrimaryButton
import com.duzup.app.core.designsystem.ScreenTitle
import com.duzup.app.core.designsystem.SecondaryButton
import com.duzup.app.core.designsystem.SectionLabel
import com.duzup.app.core.designsystem.SelectorField
import com.duzup.app.core.designsystem.SwitchRow
import com.duzup.app.core.designsystem.navigationBarBottom
import com.duzup.app.core.designsystem.statusBarTop
import com.duzup.app.core.model.BuiltInCategories
import com.duzup.app.core.model.Category
import com.duzup.app.core.model.IntervalUnit
import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.Money
import com.duzup.app.core.model.Recurrence
import com.duzup.app.core.model.RecurrenceRule
import com.duzup.app.core.model.ReminderOffsets
import com.duzup.app.core.time.DateFormats
import com.duzup.app.domain.reminders.OffsetUnit
import com.duzup.app.domain.reminders.ReminderPlanner
import com.duzup.app.feature.bills.CategoryBadge
import com.duzup.app.feature.bills.displayName
import com.duzup.app.feature.bills.priorityLabel
import com.duzup.app.feature.bills.recurrenceSummary
import com.duzup.app.feature.bills.reminderOffsetLabel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Currency

@Composable
fun errorText(error: DraftError?): String? = when (error) {
    null -> null
    DraftError.TITLE_REQUIRED -> stringResource(R.string.error_title_required)
    DraftError.TITLE_TOO_LONG -> stringResource(R.string.error_title_too_long, ItemDraftValidator.MAX_TITLE)
    DraftError.AMOUNT_INVALID -> stringResource(R.string.error_amount_invalid)
    DraftError.AMOUNT_TOO_MANY_DECIMALS -> stringResource(R.string.error_amount_decimals)
    DraftError.AMOUNT_NOT_POSITIVE -> stringResource(R.string.error_amount_positive)
    DraftError.AMOUNT_TOO_LARGE -> stringResource(R.string.error_amount_large)
    DraftError.DATE_REQUIRED -> stringResource(R.string.error_date_required)
    DraftError.END_TIME_BEFORE_START -> stringResource(R.string.error_end_time_before_start)
    DraftError.INTERVAL_INVALID -> stringResource(R.string.error_interval_invalid)
    DraftError.END_BEFORE_START -> stringResource(R.string.error_end_before_start)
    DraftError.OFFSET_INVALID -> stringResource(R.string.error_offset_invalid)
    DraftError.NOTES_TOO_LONG -> stringResource(R.string.error_notes_too_long, ItemDraftValidator.MAX_NOTES)
    DraftError.LOCATION_TOO_LONG -> stringResource(R.string.error_location_too_long, ItemDraftValidator.MAX_LOCATION)
    DraftError.ROUTINE_TIMES_REQUIRED -> stringResource(R.string.error_routine_times)
}

/** Everything the form can do, with no-op defaults so tests can supply only what they care about. */
class AddEditCallbacks(
    val onBack: () -> Unit = {},
    val onTitle: (String) -> Unit = {},
    val onAmount: (String) -> Unit = {},
    val onCurrency: (String) -> Unit = {},
    val onCategory: (String) -> Unit = {},
    val onDueDate: (LocalDate) -> Unit = {},
    val onTime: (LocalTime) -> Unit = {},
    val onEndTime: (LocalTime?) -> Unit = {},
    val onAllDay: (Boolean) -> Unit = {},
    val onLocation: (String) -> Unit = {},
    val onPriority: (Int) -> Unit = {},
    val onPinned: (Boolean) -> Unit = {},
    val onIncludeAge: (Boolean) -> Unit = {},
    val onRemind: (Boolean) -> Unit = {},
    val onRecurrence: (Recurrence) -> Unit = {},
    val onEndDate: (LocalDate?) -> Unit = {},
    val onToggleWeekday: (DayOfWeek) -> Unit = {},
    val onAddRoutineTime: (LocalTime) -> Unit = {},
    val onRemoveRoutineTime: (LocalTime) -> Unit = {},
    val onToggleOffset: (Int) -> Unit = {},
    val onAddOffset: (Int) -> Unit = {},
    val onNotes: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onSaveCategory: (String?, String, String, Long?) -> Unit = { _, _, _, _ -> },
    val onDeleteCategory: (String) -> Unit = {},
)

private enum class Dialog { NONE, CATEGORY, CURRENCY, DATE, TIME, END_TIME, REPEAT, END_DATE, CUSTOM_OFFSET, ROUTINE_TIME }

@Composable
fun AddEditScreen(state: AddEditUiState, callbacks: AddEditCallbacks) {
    val colors = DuzupTheme.colors
    val draft = state.draft
    val kind = draft.kind
    var dialog by rememberSaveable { mutableStateOf(Dialog.NONE) }
    val context = LocalContext.current
    val open: (Dialog) -> Unit = { dialog = it }

    CenteredContent {
        Column(
            Modifier
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(top = statusBarTop() + 4.dp, bottom = navigationBarBottom() + 24.dp)
                .padding(horizontal = 20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BackButton(callbacks.onBack)
                Spacer(Modifier.weight(1f))
                PixelMark(Modifier.padding(end = 8.dp))
            }
            Spacer(Modifier.height(8.dp))
            ScreenTitle(
                title = if (state.isEditing) stringResource(R.string.edit_title, kindWord(kind)) else stringResource(newTitleRes(kind)),
                subtitle = stringResource(subtitleRes(kind)),
            )
            Spacer(Modifier.height(16.dp))

            if (state.isEditing) {
                InfoNote(stringResource(R.string.edit_future_note))
                Spacer(Modifier.height(12.dp))
            }

            when (kind) {
                ItemKind.BILL -> BillForm(state, callbacks, open)
                ItemKind.EVENT -> EventForm(state, callbacks, open)
                ItemKind.TASK -> TaskForm(state, callbacks, open)
                ItemKind.MEMO -> NoteForm(state, callbacks, open)
                ItemKind.BIRTHDAY -> BirthdayForm(state, callbacks, open)
                ItemKind.ROUTINE -> RoutineForm(state, callbacks, open)
            }

            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                text = stringResource(R.string.save_item),
                onClick = callbacks.onSave,
                enabled = !state.saving && !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = callbacks.onBack, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(stringResource(R.string.action_cancel), color = colors.textSecondary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    // ---- Pickers and sheets ----
    when (dialog) {
        Dialog.NONE -> Unit
        Dialog.CATEGORY -> CategorySheet(
            categories = state.categories,
            selectedId = draft.categoryId,
            onSelect = { callbacks.onCategory(it); dialog = Dialog.NONE },
            onSave = callbacks.onSaveCategory,
            onDelete = callbacks.onDeleteCategory,
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.CURRENCY -> CurrencySheet(
            selected = draft.currencyCode,
            onSelect = { callbacks.onCurrency(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.DATE -> DuzupDatePickerDialog(
            initial = draft.dueDate ?: state.today,
            onConfirm = { callbacks.onDueDate(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.TIME -> DuzupTimePickerDialog(
            initial = draft.time,
            is24Hour = DateFormat.is24HourFormat(context),
            onConfirm = { callbacks.onTime(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.END_TIME -> DuzupTimePickerDialog(
            initial = draft.endTime ?: draft.time.plusHours(1),
            is24Hour = DateFormat.is24HourFormat(context),
            onConfirm = { callbacks.onEndTime(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.ROUTINE_TIME -> DuzupTimePickerDialog(
            initial = draft.routineTimes.maxOrNull()?.plusHours(4) ?: LocalTime.of(9, 0),
            is24Hour = DateFormat.is24HourFormat(context),
            onConfirm = { callbacks.onAddRoutineTime(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.REPEAT -> RepeatSheet(
            current = draft.recurrence,
            onSelect = { callbacks.onRecurrence(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.END_DATE -> EndDateSheet(
            current = draft.endDate,
            initial = draft.dueDate ?: state.today,
            onSelect = { callbacks.onEndDate(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.CUSTOM_OFFSET -> CustomOffsetDialog(
            onAdd = { callbacks.onAddOffset(it); dialog = Dialog.NONE },
            onDismiss = { dialog = Dialog.NONE },
        )
    }
}

@Composable
private fun kindWord(kind: ItemKind): String = stringResource(
    when (kind) {
        ItemKind.BILL -> R.string.word_bill
        ItemKind.EVENT -> R.string.word_event
        ItemKind.TASK -> R.string.word_task
        ItemKind.MEMO -> R.string.word_note
        ItemKind.BIRTHDAY -> R.string.word_birthday
        ItemKind.ROUTINE -> R.string.word_routine
    },
)

private fun newTitleRes(kind: ItemKind): Int = when (kind) {
    ItemKind.BILL -> R.string.new_bill_title
    ItemKind.EVENT -> R.string.new_event_title
    ItemKind.TASK -> R.string.new_task_title
    ItemKind.MEMO -> R.string.new_note_title
    ItemKind.BIRTHDAY -> R.string.new_birthday_title
    ItemKind.ROUTINE -> R.string.new_routine_title
}

private fun subtitleRes(kind: ItemKind): Int = when (kind) {
    ItemKind.BILL -> R.string.new_bill_subtitle
    ItemKind.EVENT -> R.string.new_event_subtitle
    ItemKind.TASK -> R.string.new_task_subtitle
    ItemKind.MEMO -> R.string.new_note_subtitle
    ItemKind.BIRTHDAY -> R.string.new_birthday_subtitle
    ItemKind.ROUTINE -> R.string.new_routine_subtitle
}

// ---- Shared form building blocks ---------------------------------------------------------------------

@Composable
private fun FieldGap() = Spacer(Modifier.height(6.dp))

@Composable
private fun TitleField(state: AddEditUiState, c: AddEditCallbacks, label: Int, hint: Int) {
    InsetTextField(
        value = state.draft.title,
        onValueChange = c.onTitle,
        label = stringResource(label),
        placeholder = stringResource(hint),
        error = errorText(state.errors[DraftField.TITLE]),
    )
}

@Composable
private fun DateField(state: AddEditUiState, open: (Dialog) -> Unit, label: Int) {
    SelectorField(
        label = stringResource(label),
        value = state.draft.dueDate?.let { DateFormats.fullDate(it) },
        placeholder = stringResource(R.string.field_due_date_hint),
        leadingIcon = Icons.Rounded.CalendarToday,
        onClick = { open(Dialog.DATE) },
        error = errorText(state.errors[DraftField.DATE]),
    )
}

@Composable
private fun TimeField(state: AddEditUiState, open: (Dialog) -> Unit, label: Int) {
    SelectorField(
        label = stringResource(label),
        value = DateFormats.time(state.draft.time),
        placeholder = "",
        leadingIcon = Icons.Rounded.Schedule,
        onClick = { open(Dialog.TIME) },
    )
}

@Composable
private fun RepeatFields(state: AddEditUiState, open: (Dialog) -> Unit) {
    val draft = state.draft
    SelectorField(
        label = stringResource(R.string.field_repeat),
        value = recurrenceSummary(draft.recurrence),
        placeholder = "",
        leadingIcon = Icons.Rounded.Repeat,
        onClick = { open(Dialog.REPEAT) },
        error = errorText(state.errors[DraftField.RECURRENCE]),
    )
    if (draft.recurrence.isRecurring) {
        FieldGap()
        SelectorField(
            label = stringResource(R.string.field_ends),
            value = draft.endDate?.let { DateFormats.date(it) } ?: stringResource(R.string.ends_never),
            placeholder = "",
            leadingIcon = Icons.Rounded.EventBusy,
            onClick = { open(Dialog.END_DATE) },
            error = errorText(state.errors[DraftField.END_DATE]),
        )
    }
}

@Composable
private fun RemindersBlock(state: AddEditUiState, c: AddEditCallbacks, open: (Dialog) -> Unit) {
    val draft = state.draft
    ReminderChips(draft.kind, draft.offsets, c.onToggleOffset, onCustom = { open(Dialog.CUSTOM_OFFSET) })
    errorText(state.errors[DraftField.REMINDERS])?.let { FieldError(it) }
    if (draft.offsets.isEmpty()) {
        Spacer(Modifier.height(6.dp))
        InfoNote(stringResource(R.string.no_reminders_note))
    }
}

@Composable
private fun NotesField(state: AddEditUiState, c: AddEditCallbacks, label: Int, hint: Int, minLines: Int = 1) {
    InsetTextField(
        value = state.draft.notes,
        onValueChange = c.onNotes,
        label = stringResource(label),
        placeholder = stringResource(hint),
        singleLine = false,
        minLines = minLines,
        maxLines = 6,
        imeAction = ImeAction.Default,
        error = errorText(state.errors[DraftField.NOTES]),
    )
}

@Composable
private fun PastDueNote(state: AddEditUiState) {
    val date = state.draft.dueDate
    if (date != null && date < state.today && state.draft.kind.canBeOverdue) {
        Spacer(Modifier.height(6.dp))
        InfoNote(stringResource(R.string.past_due_note))
    }
}

// ---- Per-kind forms --------------------------------------------------------------------------------

@Composable
private fun BillForm(state: AddEditUiState, c: AddEditCallbacks, open: (Dialog) -> Unit) {
    val draft = state.draft
    val selectedCategory = state.categories.firstOrNull { it.id == draft.categoryId }

    // Four categories plus "More" always fit the screen width: equal columns, no scrolling.
    val visible = remember(state.categories, draft.categoryId) {
        val firstFour = state.categories.take(4)
        if (firstFour.any { it.id == draft.categoryId }) firstFour
        else firstFour.take(3) + listOfNotNull(selectedCategory)
    }
    Row(Modifier.fillMaxWidth()) {
        visible.forEach { category ->
            CategoryChoice(
                category, category.id == draft.categoryId,
                onClick = { c.onCategory(category.id) },
                modifier = Modifier.weight(1f),
            )
        }
        MoreChoice(Modifier.weight(1f)) { open(Dialog.CATEGORY) }
    }
    Spacer(Modifier.height(10.dp))

    TitleField(state, c, R.string.field_bill_name, R.string.field_bill_name_hint)
    FieldGap()
    InsetTextField(
        value = draft.amountText,
        onValueChange = c.onAmount,
        label = stringResource(R.string.field_amount_optional),
        placeholder = stringResource(R.string.field_amount_hint),
        prefix = Money.symbolFor(draft.currencyCode),
        keyboardType = KeyboardType.Decimal,
        error = errorText(state.errors[DraftField.AMOUNT]),
    )
    FieldGap()
    SelectorField(
        label = stringResource(R.string.field_currency),
        value = "${draft.currencyCode} · ${currencyName(draft.currencyCode)}",
        placeholder = "",
        leadingIcon = Icons.Rounded.Payments,
        onClick = { open(Dialog.CURRENCY) },
    )
    FieldGap()
    DateField(state, open, R.string.field_due_date)
    PastDueNote(state)
    FieldGap()
    TimeField(state, open, R.string.field_reminder_time)
    FieldGap()
    RepeatFields(state, open)
    Spacer(Modifier.height(10.dp))
    RemindersBlock(state, c, open)
    Spacer(Modifier.height(10.dp))
    NotesField(state, c, R.string.field_notes_optional, R.string.field_notes_hint)
}

@Composable
private fun EventForm(state: AddEditUiState, c: AddEditCallbacks, open: (Dialog) -> Unit) {
    val draft = state.draft
    TitleField(state, c, R.string.field_event_name, R.string.field_event_name_hint)
    FieldGap()
    DateField(state, open, R.string.field_event_date)
    FieldGap()
    SwitchRow(stringResource(R.string.field_all_day), stringResource(R.string.field_all_day_desc), draft.allDay, c.onAllDay)
    if (!draft.allDay) {
        FieldGap()
        TimeField(state, open, R.string.field_starts_at)
        FieldGap()
        SelectorField(
            label = stringResource(R.string.field_ends_at),
            value = draft.endTime?.let { DateFormats.time(it) } ?: stringResource(R.string.no_end_time),
            placeholder = "",
            leadingIcon = Icons.Rounded.Schedule,
            onClick = { open(Dialog.END_TIME) },
            error = errorText(state.errors[DraftField.END_TIME]),
        )
        if (draft.endTime != null) {
            TextButton(onClick = { c.onEndTime(null) }, modifier = Modifier.height(44.dp)) {
                Text(stringResource(R.string.remove_end_time), color = DuzupTheme.colors.accentText, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    FieldGap()
    InsetTextField(
        value = draft.location,
        onValueChange = c.onLocation,
        label = stringResource(R.string.field_place_optional),
        placeholder = stringResource(R.string.field_place_hint),
        error = errorText(state.errors[DraftField.LOCATION]),
    )
    FieldGap()
    RepeatFields(state, open)
    Spacer(Modifier.height(10.dp))
    RemindersBlock(state, c, open)
    Spacer(Modifier.height(10.dp))
    NotesField(state, c, R.string.field_notes_optional, R.string.field_notes_hint)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskForm(state: AddEditUiState, c: AddEditCallbacks, open: (Dialog) -> Unit) {
    val draft = state.draft
    TitleField(state, c, R.string.field_task_name, R.string.field_task_name_hint)
    FieldGap()
    DateField(state, open, R.string.field_due_date)
    PastDueNote(state)
    FieldGap()
    TimeField(state, open, R.string.field_due_time)
    Spacer(Modifier.height(10.dp))
    Text(stringResource(R.string.field_priority), style = MaterialTheme.typography.labelMedium, color = DuzupTheme.colors.textPrimary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(0, 1, 2).forEach { level ->
            FilterPill(priorityLabel(level), draft.priority == level, { c.onPriority(level) }, role = Role.RadioButton)
        }
    }
    FieldGap()
    RepeatFields(state, open)
    Spacer(Modifier.height(10.dp))
    RemindersBlock(state, c, open)
    Spacer(Modifier.height(10.dp))
    NotesField(state, c, R.string.field_notes_optional, R.string.field_notes_hint)
}

@Composable
private fun NoteForm(state: AddEditUiState, c: AddEditCallbacks, open: (Dialog) -> Unit) {
    val draft = state.draft
    TitleField(state, c, R.string.field_note_title, R.string.field_note_title_hint)
    FieldGap()
    NotesField(state, c, R.string.field_note_body, R.string.field_note_body_hint, minLines = 4)
    FieldGap()
    SwitchRow(stringResource(R.string.field_pin), stringResource(R.string.field_pin_desc), draft.pinned, c.onPinned)
    SwitchRow(stringResource(R.string.field_note_remind), stringResource(R.string.field_note_remind_desc), draft.remind, c.onRemind)
    if (draft.remind) {
        FieldGap()
        DateField(state, open, R.string.field_note_date)
        FieldGap()
        TimeField(state, open, R.string.field_note_time)
        Spacer(Modifier.height(10.dp))
        RemindersBlock(state, c, open)
    }
}

@Composable
private fun BirthdayForm(state: AddEditUiState, c: AddEditCallbacks, open: (Dialog) -> Unit) {
    val draft = state.draft
    TitleField(state, c, R.string.field_birthday_name, R.string.field_birthday_name_hint)
    FieldGap()
    DateField(state, open, R.string.field_birthday_date)
    Spacer(Modifier.height(6.dp))
    InfoNote(stringResource(R.string.birthday_year_hint))
    FieldGap()
    SwitchRow(stringResource(R.string.field_show_age), stringResource(R.string.field_show_age_desc), draft.includeAge, c.onIncludeAge)
    FieldGap()
    TimeField(state, open, R.string.field_remind_at)
    Spacer(Modifier.height(10.dp))
    RemindersBlock(state, c, open)
    Spacer(Modifier.height(10.dp))
    NotesField(state, c, R.string.field_notes_optional, R.string.field_gift_ideas_hint)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutineForm(state: AddEditUiState, c: AddEditCallbacks, open: (Dialog) -> Unit) {
    val draft = state.draft
    val locale = LocalConfiguration.current.locales[0]
    TitleField(state, c, R.string.field_routine_name, R.string.field_routine_name_hint)
    Spacer(Modifier.height(10.dp))

    Text(stringResource(R.string.field_routine_times), style = MaterialTheme.typography.labelMedium, color = DuzupTheme.colors.textPrimary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        draft.routineTimes.forEach { t ->
            FilterPill(
                label = "${DateFormats.time(t)}  ×",
                selected = true,
                onClick = { c.onRemoveRoutineTime(t) },
                role = Role.Button,
            )
        }
        if (draft.routineTimes.size < ItemDraftValidator.MAX_ROUTINE_TIMES) {
            FilterPill(
                label = stringResource(R.string.add_time),
                selected = false,
                onClick = { open(Dialog.ROUTINE_TIME) },
                role = Role.Button,
                leadingIcon = Icons.Rounded.Add,
            )
        }
    }
    errorText(state.errors[DraftField.ROUTINE_TIMES])?.let { FieldError(it) }
    Spacer(Modifier.height(10.dp))

    Text(stringResource(R.string.field_routine_days), style = MaterialTheme.typography.labelMedium, color = DuzupTheme.colors.textPrimary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DayOfWeek.entries.forEach { day ->
            FilterPill(
                label = day.getDisplayName(TextStyle.SHORT, locale),
                selected = day in draft.weekdays,
                onClick = { c.onToggleWeekday(day) },
                role = Role.Checkbox,
            )
        }
    }
    InfoNote(stringResource(if (draft.weekdays.isEmpty()) R.string.routine_every_day else R.string.routine_picked_days))
    FieldGap()
    DateField(state, open, R.string.field_routine_start)
    Spacer(Modifier.height(10.dp))
    RemindersBlock(state, c, open)
    Spacer(Modifier.height(10.dp))
    NotesField(state, c, R.string.field_notes_optional, R.string.field_notes_hint)
}

@Composable
private fun InfoNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = DuzupTheme.colors.textMuted,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun currencyName(code: String): String =
    runCatching { Currency.getInstance(code).displayName }.getOrDefault(code)

@Composable
private fun CategoryChoice(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelOverride: String? = null,
) {
    val name = labelOverride ?: category.displayName()
    Column(
        modifier
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                contentDescription = name
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CategoryBadge(category, size = 40.dp, selected = selected, modifier = Modifier.padding(3.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) DuzupTheme.colors.textPrimary else DuzupTheme.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
        )
    }
}

@Composable
private fun MoreChoice(modifier: Modifier = Modifier, onClick: () -> Unit) {
    CategoryChoice(
        category = Category(BuiltInCategories.OTHER, "", "more", null, true, 99),
        selected = false,
        onClick = onClick,
        modifier = modifier,
        labelOverride = stringResource(R.string.category_more),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReminderChips(
    kind: ItemKind,
    offsets: Set<Int>,
    onToggle: (Int) -> Unit,
    onCustom: () -> Unit,
    title: String = stringResource(R.string.field_reminders),
) {
    val options = (ReminderOffsets.choicesFor(kind) + offsets).distinct().sortedDescending()
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = DuzupTheme.colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { minutes ->
                FilterPill(
                    label = reminderOffsetLabel(minutes, kind),
                    selected = minutes in offsets,
                    onClick = { onToggle(minutes) },
                    role = Role.Checkbox,
                )
            }
            FilterPill(
                label = stringResource(R.string.reminder_custom),
                selected = false,
                onClick = onCustom,
                role = Role.Button,
                leadingIcon = Icons.Rounded.Add,
            )
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = DuzupTheme.colors.textPrimary,
        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
    )
}

@Composable
internal fun CurrencySheet(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = DuzupTheme.colors
    DuzupSheet(onDismiss) {
        SheetTitle(stringResource(R.string.field_currency))
        LazyColumn(Modifier.height(420.dp)) {
            items(Money.supportedCurrencies, key = { it }) { code ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn48()
                        .clickable(role = Role.RadioButton) { onSelect(code) }
                        .semantics(mergeDescendants = true) { this.selected = code == selected }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(code, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, modifier = Modifier.width(56.dp))
                    Text(
                        "${Money.symbolFor(code)}  ${currencyNameOf(code)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (code == selected) Icon(Icons.Rounded.Check, null, tint = colors.accentText, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

private fun Modifier.heightIn48(): Modifier = this.then(Modifier.height(48.dp))

private fun currencyNameOf(code: String): String =
    runCatching { Currency.getInstance(code).displayName }.getOrDefault(code)

@Composable
private fun RepeatSheet(current: Recurrence, onSelect: (Recurrence) -> Unit, onDismiss: () -> Unit) {
    val colors = DuzupTheme.colors
    var customOpen by rememberSaveable { mutableStateOf(current.rule == RecurrenceRule.CUSTOM) }
    var intervalText by rememberSaveable { mutableStateOf(if (current.rule == RecurrenceRule.CUSTOM) current.interval.toString() else "2") }
    var unit by rememberSaveable { mutableStateOf(if (current.rule == RecurrenceRule.CUSTOM) current.unit else IntervalUnit.MONTH) }
    val interval = intervalText.toIntOrNull()
    val intervalError = interval == null || interval !in 1..Recurrence.MAX_INTERVAL

    val presets = listOf(
        RecurrenceRule.NONE to R.string.repeat_none,
        RecurrenceRule.DAILY to R.string.repeat_daily,
        RecurrenceRule.WEEKLY to R.string.repeat_weekly,
        RecurrenceRule.BIWEEKLY to R.string.repeat_biweekly,
        RecurrenceRule.MONTHLY to R.string.repeat_monthly,
        RecurrenceRule.QUARTERLY to R.string.repeat_quarterly,
        RecurrenceRule.YEARLY to R.string.repeat_yearly,
    )
    DuzupSheet(onDismiss) {
        SheetTitle(stringResource(R.string.field_repeat))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            presets.forEach { (rule, label) ->
                val isSelected = current.rule == rule && !customOpen
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn48()
                        .clickable(role = Role.RadioButton) { onSelect(Recurrence(rule)) }
                        .semantics(mergeDescendants = true) { this.selected = isSelected },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    if (isSelected) Icon(Icons.Rounded.Check, null, tint = colors.accentText, modifier = Modifier.size(20.dp))
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn48()
                    .clickable(role = Role.RadioButton) { customOpen = true }
                    .semantics(mergeDescendants = true) { this.selected = customOpen },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.repeat_custom), style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
                if (customOpen) Icon(Icons.Rounded.Check, null, tint = colors.accentText, modifier = Modifier.size(20.dp))
            }
            if (customOpen) {
                Spacer(Modifier.height(8.dp))
                InsetTextField(
                    value = intervalText,
                    onValueChange = { intervalText = it.filter(Char::isDigit).take(3) },
                    label = stringResource(R.string.custom_every),
                    keyboardType = KeyboardType.Number,
                    error = if (intervalError) stringResource(R.string.error_interval_invalid) else null,
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IntervalUnit.entries.forEach { u ->
                        FilterPill(
                            label = stringResource(
                                when (u) {
                                    IntervalUnit.DAY -> R.string.unit_days
                                    IntervalUnit.WEEK -> R.string.unit_weeks
                                    IntervalUnit.MONTH -> R.string.unit_months
                                    IntervalUnit.YEAR -> R.string.unit_years
                                },
                            ),
                            selected = unit == u,
                            onClick = { unit = u },
                            role = Role.RadioButton,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = stringResource(R.string.action_apply),
                    onClick = { if (!intervalError) onSelect(Recurrence(RecurrenceRule.CUSTOM, interval!!, unit)) },
                    enabled = !intervalError,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun EndDateSheet(
    current: java.time.LocalDate?,
    initial: java.time.LocalDate,
    onSelect: (java.time.LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DuzupTheme.colors
    var picking by rememberSaveable { mutableStateOf(false) }
    if (picking) {
        DuzupDatePickerDialog(
            initial = current ?: initial.plusYears(1),
            onConfirm = { onSelect(it) },
            onDismiss = { picking = false },
        )
        return
    }
    DuzupSheet(onDismiss) {
        SheetTitle(stringResource(R.string.field_ends))
        Row(
            Modifier.fillMaxWidth().heightIn48().clickable(role = Role.RadioButton) { onSelect(null) }
                .semantics(mergeDescendants = true) { selected = current == null },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.ends_never), style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
            if (current == null) Icon(Icons.Rounded.Check, null, tint = colors.accentText, modifier = Modifier.size(20.dp))
        }
        Row(
            Modifier.fillMaxWidth().heightIn48().clickable(role = Role.RadioButton) { picking = true }
                .semantics(mergeDescendants = true) { selected = current != null },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                current?.let { stringResource(R.string.ends_on, DateFormats.date(it)) } ?: stringResource(R.string.ends_pick_date),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (current != null) Icon(Icons.Rounded.Check, null, tint = colors.accentText, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CustomOffsetDialog(onAdd: (Int) -> Unit, onDismiss: () -> Unit) {
    val colors = DuzupTheme.colors
    var text by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(OffsetUnit.MINUTES) }
    val minutes = ReminderPlanner.parseOffset(text, unit)
    val valid = minutes != null
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        title = { Text(stringResource(R.string.custom_reminder_title), color = colors.textPrimary, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                InsetTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(4) },
                    label = stringResource(R.string.custom_reminder_amount),
                    placeholder = stringResource(R.string.custom_reminder_hint),
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                    onImeAction = { if (minutes != null) onAdd(minutes) },
                    error = if (text.isNotEmpty() && !valid) stringResource(R.string.error_offset_invalid) else null,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OffsetUnit.entries.forEach { u ->
                        FilterPill(
                            label = stringResource(
                                when (u) {
                                    OffsetUnit.MINUTES -> R.string.unit_minutes
                                    OffsetUnit.HOURS -> R.string.unit_hours
                                    OffsetUnit.DAYS -> R.string.unit_days
                                },
                            ),
                            selected = unit == u,
                            onClick = { unit = u },
                            role = Role.RadioButton,
                        )
                    }
                }
                Text(
                    stringResource(R.string.custom_reminder_before),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (minutes != null) onAdd(minutes) }, enabled = valid) {
                Text(stringResource(R.string.action_add), color = if (valid) colors.accentText else colors.textMuted)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = colors.textSecondary) } },
    )
}

@Composable
private fun CategorySheet(
    categories: List<Category>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onSave: (String?, String, String, Long?) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DuzupTheme.colors
    var editing by remember { mutableStateOf<Category?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Category?>(null) }

    DuzupSheet(onDismiss) {
        SheetTitle(stringResource(R.string.choose_category))
        LazyColumn(Modifier.height(360.dp)) {
            items(categories, key = { it.id }) { category ->
                val name = category.displayName()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn48()
                        .clickable(role = Role.RadioButton) { onSelect(category.id) }
                        .semantics(mergeDescendants = false) { this.selected = category.id == selectedId }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryBadge(category, size = 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(name, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (category.id == selectedId) Icon(Icons.Rounded.Check, stringResource(R.string.a11y_selected), tint = colors.accentText, modifier = Modifier.size(20.dp))
                    if (!category.builtIn) {
                        com.duzup.app.core.designsystem.FlatIconButton(Icons.Rounded.Edit, stringResource(R.string.edit_category_named, name), { editing = category })
                        com.duzup.app.core.designsystem.FlatIconButton(Icons.Rounded.Delete, stringResource(R.string.delete_category_named, name), { deleting = category }, tint = colors.overdue)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        com.duzup.app.core.designsystem.SecondaryButton(
            text = stringResource(R.string.new_category),
            onClick = { creating = true },
            leadingIcon = Icons.Rounded.Add,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (creating || editing != null) {
        CategoryEditorDialog(
            existing = editing,
            onSave = { name, icon, color ->
                onSave(editing?.id, name, icon, color)
                creating = false
                editing = null
            },
            onDismiss = { creating = false; editing = null },
        )
    }
    deleting?.let { category ->
        ConfirmDialog(
            title = stringResource(R.string.delete_category_title),
            message = stringResource(R.string.delete_category_message),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { onDelete(category.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditorDialog(
    existing: Category?,
    onSave: (name: String, iconKey: String, colorArgb: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DuzupTheme.colors
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var icon by rememberSaveable { mutableStateOf(existing?.iconKey ?: CategoryIcons.pickable.first()) }
    var color by rememberSaveable { mutableStateOf(existing?.colorArgb ?: CategoryColors.options.first()) }
    val valid = name.trim().isNotEmpty() && name.trim().length <= 30

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        title = {
            Text(
                stringResource(if (existing == null) R.string.new_category else R.string.edit_category),
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                InsetTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = stringResource(R.string.category_name),
                    imeAction = ImeAction.Done,
                )
                Spacer(Modifier.height(16.dp))
                SectionLabel(stringResource(R.string.category_icon))
                Spacer(Modifier.height(4.dp))
                FlowRow {
                    CategoryIcons.pickable.forEach { key ->
                        Box(
                            Modifier
                                .size(48.dp)
                                .clickable(role = Role.RadioButton) { icon = key }
                                .semantics { selected = key == icon; contentDescription = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            CategoryBadge(
                                Category("preview", "", key, color, false, 0),
                                size = 40.dp,
                                selected = key == icon,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SectionLabel(stringResource(R.string.category_color))
                Spacer(Modifier.height(4.dp))
                FlowRow {
                    CategoryColors.options.forEach { option ->
                        Box(
                            Modifier
                                .size(48.dp)
                                .clickable(role = Role.RadioButton) { color = option }
                                .semantics { selected = option == color; contentDescription = "#%06X".format(option and 0xFFFFFF) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(if (option == color) 34.dp else 28.dp)
                                    .clip(CircleShape)
                                    .background(Color(option)),
                            )
                            if (option == color) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (valid) onSave(name.trim(), icon, color) }, enabled = valid) {
                Text(stringResource(R.string.action_save), color = if (valid) colors.accentText else colors.textMuted)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = colors.textSecondary) } },
    )
}

