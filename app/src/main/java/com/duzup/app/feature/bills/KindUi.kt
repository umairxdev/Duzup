package com.duzup.app.feature.bills

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.duzup.app.R
import com.duzup.app.core.model.Category
import com.duzup.app.core.model.ItemKind

/** Presentation facts about each kind of reminder item. */
val ItemKind.iconKey: String
    get() = when (this) {
        ItemKind.BILL -> "card"
        ItemKind.EVENT -> "event"
        ItemKind.TASK -> "task"
        ItemKind.MEMO -> "note"
        ItemKind.BIRTHDAY -> "cake"
        ItemKind.ROUTINE -> "routine"
    }

/** A distinct accent per kind (ARGB); bills keep the neutral default. */
val ItemKind.accentArgb: Long?
    get() = when (this) {
        ItemKind.BILL -> null
        ItemKind.EVENT -> 0xFF7B61FF
        ItemKind.TASK -> 0xFF00A3BF
        ItemKind.MEMO -> 0xFF8A6D3B
        ItemKind.BIRTHDAY -> 0xFFE0457B
        ItemKind.ROUTINE -> 0xFF1E6FD9
    }

/** Singular name: "Bill", "Event", "Task", "Note", "Birthday", "Routine". */
@Composable
fun ItemKind.label(): String = stringResource(
    when (this) {
        ItemKind.BILL -> R.string.kind_bill
        ItemKind.EVENT -> R.string.kind_event
        ItemKind.TASK -> R.string.kind_task
        ItemKind.MEMO -> R.string.kind_note
        ItemKind.BIRTHDAY -> R.string.kind_birthday
        ItemKind.ROUTINE -> R.string.kind_routine
    },
)

/** Plural filter-chip text, e.g. "Bills". */
@Composable
fun ItemKind.chipLabel(): String = stringResource(
    when (this) {
        ItemKind.BILL -> R.string.chip_bills
        ItemKind.EVENT -> R.string.chip_events
        ItemKind.TASK -> R.string.chip_tasks
        ItemKind.MEMO -> R.string.chip_notes
        ItemKind.BIRTHDAY -> R.string.chip_birthdays
        ItemKind.ROUTINE -> R.string.chip_routines
    },
)

/** Non-bill kinds have no category; their badge uses a stand-in built from the kind itself. */
fun kindBadge(kind: ItemKind): Category = Category(
    id = "kind-${kind.name.lowercase()}",
    name = "",
    iconKey = kind.iconKey,
    colorArgb = kind.accentArgb,
    builtIn = true,
    sortOrder = 0,
)
