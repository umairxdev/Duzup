package com.duzup.app.feature.addedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import com.duzup.app.R
import com.duzup.app.core.designsystem.DuzupSheet
import com.duzup.app.core.designsystem.DuzupTheme
import com.duzup.app.core.designsystem.InsetTextField
import com.duzup.app.core.designsystem.PrimaryButton
import com.duzup.app.core.designsystem.RaisedCard
import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.time.DateFormats
import com.duzup.app.domain.quickadd.QuickAddParser
import com.duzup.app.domain.quickadd.QuickAddResult
import com.duzup.app.feature.bills.label
import com.duzup.app.feature.bills.recurrenceSummary
import java.time.LocalDateTime

/**
 * The centre "+" button opens this: type a line and let Duzup guess, or tap one of the six kinds.
 * Nothing is saved here; both paths lead to the normal form so the user always reviews first.
 */
@Composable
fun AddPickerSheet(
    onPickKind: (ItemKind) -> Unit,
    onQuickAdd: (kind: ItemKind, text: String) -> Unit,
    onDismiss: () -> Unit,
    now: () -> LocalDateTime = { LocalDateTime.now() },
) {
    val colors = DuzupTheme.colors
    var text by remember { mutableStateOf("") }
    val parsed = remember(text) { QuickAddParser.parse(text, now()) }

    DuzupSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.picker_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp).semantics { heading() },
            )

            InsetTextField(
                value = text,
                onValueChange = { text = it.take(160) },
                label = stringResource(R.string.quick_add_label),
                placeholder = stringResource(R.string.quick_add_hint),
                imeAction = ImeAction.Go,
                onImeAction = { parsed?.let { onQuickAdd(it.kind, text) } },
            )
            if (parsed != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.quick_add_preview, describe(parsed)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.accentText,
                )
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    text = stringResource(R.string.quick_add_continue),
                    onClick = { onQuickAdd(parsed.kind, text) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.picker_or_pick),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
            )
            Spacer(Modifier.height(8.dp))

            ItemKind.entries.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { kind ->
                        KindTile(kind, { onPickKind(kind) }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun KindTile(kind: ItemKind, onClick: () -> Unit, modifier: Modifier) {
    val colors = DuzupTheme.colors
    val label = kind.label()
    RaisedCard(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = label },
        onClick = onClick,
        onClickLabel = label,
        elevation = 4.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.Start) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Text(
                stringResource(
                    when (kind) {
                        ItemKind.BILL -> R.string.tile_bill
                        ItemKind.EVENT -> R.string.tile_event
                        ItemKind.TASK -> R.string.tile_task
                        ItemKind.MEMO -> R.string.tile_note
                        ItemKind.BIRTHDAY -> R.string.tile_birthday
                        ItemKind.ROUTINE -> R.string.tile_routine
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
    }
}

/** "Event · Fri, Mar 14 · 4:30 PM": what the parser understood, in one line. */
@Composable
private fun describe(r: QuickAddResult): String {
    val parts = mutableListOf(r.kind.label())
    r.date?.let { parts += DateFormats.dayMonth(it) }
    r.time?.let { parts += DateFormats.time(it) }
    if (r.recurrence.isRecurring) parts += recurrenceSummary(r.recurrence)
    return parts.joinToString(" · ")
}
