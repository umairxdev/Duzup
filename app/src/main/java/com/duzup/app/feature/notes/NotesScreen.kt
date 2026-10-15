package com.duzup.app.feature.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.duzup.app.R
import com.duzup.app.core.designsystem.CenteredContent
import com.duzup.app.core.designsystem.DuzupTheme
import com.duzup.app.core.designsystem.EmptyState
import com.duzup.app.core.designsystem.FlatIconButton
import com.duzup.app.core.designsystem.InsetTextField
import com.duzup.app.core.designsystem.NeuIconButton
import com.duzup.app.core.designsystem.RaisedCard
import com.duzup.app.core.designsystem.SectionLabel
import com.duzup.app.core.designsystem.listBottomPadding
import com.duzup.app.core.designsystem.statusBarTop
import com.duzup.app.core.time.DateFormats

class NotesCallbacks(
    val onAddQuickNote: (String) -> Unit = {},
    val onOpen: (String) -> Unit = {},
    val onSetPinned: (String, Boolean) -> Unit = { _, _ -> },
)

@Composable
fun NotesScreen(state: NotesUiState, callbacks: NotesCallbacks) {
    val colors = DuzupTheme.colors
    var draft by remember { mutableStateOf("") }
    val submit = {
        if (draft.isNotBlank()) {
            callbacks.onAddQuickNote(draft)
            draft = ""
        }
    }

    CenteredContent {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = statusBarTop() + 12.dp,
                bottom = listBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                Column {
                    Text(
                        stringResource(R.string.notes_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimary,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(stringResource(R.string.notes_subtitle), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                }
            }
            item(key = "quick") {
                Row(verticalAlignment = Alignment.Bottom) {
                    InsetTextField(
                        value = draft,
                        onValueChange = { draft = it.take(80) },
                        label = stringResource(R.string.notes_quick_label),
                        placeholder = stringResource(R.string.notes_quick_hint),
                        imeAction = ImeAction.Done,
                        onImeAction = { submit() },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    NeuIconButton(
                        icon = Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.notes_save_quick),
                        onClick = { submit() },
                        enabled = draft.isNotBlank(),
                    )
                }
            }

            if (state.isEmpty && !state.isLoading) {
                item(key = "empty") {
                    EmptyState(
                        title = stringResource(R.string.notes_empty_title),
                        message = stringResource(R.string.notes_empty_message),
                    )
                }
            }
            if (state.pinned.isNotEmpty()) {
                item(key = "pinned-label") { SectionLabel(stringResource(R.string.notes_pinned)) }
                items(state.pinned, key = { "p-" + it.bill.id }) { NoteCard(it, callbacks) }
            }
            if (state.others.isNotEmpty()) {
                if (state.pinned.isNotEmpty()) item(key = "others-label") { SectionLabel(stringResource(R.string.notes_all)) }
                items(state.others, key = { "n-" + it.bill.id }) { NoteCard(it, callbacks) }
            }
        }
    }
}

@Composable
private fun NoteCard(note: NoteUi, callbacks: NotesCallbacks) {
    val colors = DuzupTheme.colors
    val bill = note.bill
    val reminder = note.nextReminder
    val reminderText = reminder?.let { stringResource(R.string.note_reminder_on, DateFormats.dayMonth(it.occurrence.dueDate), DateFormats.time(it.occurrence.dueTime)) }
    val pinLabel = stringResource(if (bill.isPinned) R.string.unpin_note else R.string.pin_note)
    RaisedCard(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = false) { contentDescription = bill.title },
        onClick = { callbacks.onOpen(bill.id) },
        onClickLabel = stringResource(R.string.open_item),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 4.dp)) {
                Text(bill.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                bill.notes?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                if (reminderText != null) {
                    Text(reminderText, style = MaterialTheme.typography.labelMedium, color = colors.accentText, modifier = Modifier.padding(top = 6.dp))
                }
            }
            FlatIconButton(
                Icons.Rounded.PushPin,
                pinLabel,
                { callbacks.onSetPinned(bill.id, !bill.isPinned) },
                tint = if (bill.isPinned) colors.accentText else colors.textMuted,
            )
        }
    }
}
