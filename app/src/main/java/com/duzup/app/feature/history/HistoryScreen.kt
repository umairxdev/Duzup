package com.duzup.app.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.duzup.app.R
import com.duzup.app.core.designsystem.BackButton
import com.duzup.app.core.designsystem.CenteredContent
import com.duzup.app.core.designsystem.EmptyState
import com.duzup.app.core.designsystem.FilterPill
import com.duzup.app.core.designsystem.FlatIconButton
import com.duzup.app.core.designsystem.InsetTextField
import com.duzup.app.core.designsystem.RaisedCard
import com.duzup.app.core.designsystem.DuzupSheet
import com.duzup.app.core.designsystem.DuzupTheme
import com.duzup.app.core.designsystem.ScreenTitle
import com.duzup.app.core.designsystem.listBottomPadding
import com.duzup.app.core.designsystem.statusBarTop
import com.duzup.app.core.model.Bill
import com.duzup.app.core.model.Category
import com.duzup.app.core.model.Money
import com.duzup.app.feature.bills.ItemRow
import com.duzup.app.feature.bills.chipLabel
import com.duzup.app.feature.bills.kindBadge
import com.duzup.app.feature.bills.label
import com.duzup.app.core.model.ItemKind
import com.duzup.app.feature.bills.CategoryBadge
import com.duzup.app.feature.bills.displayName
import com.duzup.app.feature.bills.recurrenceSummary

private enum class Sheet { NONE, CATEGORY, DATE, SORT }

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onBack: () -> Unit,
    onUpdate: ((HistoryFilters) -> HistoryFilters) -> Unit,
    onOpenBill: (String) -> Unit,
    onSetArchived: (String, Boolean) -> Unit,
) {
    val colors = DuzupTheme.colors
    val f = state.filters
    var sheet by rememberSaveable { mutableStateOf(Sheet.NONE) }
    val categoryById = state.categories.associateBy { it.id }

    CenteredContent {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = statusBarTop() + 4.dp,
                bottom = listBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "top") {
                Column {
                    BackButton(onBack)
                    ScreenTitle(
                        title = stringResource(R.string.all_bills_title),
                        subtitle = stringResource(R.string.all_bills_subtitle),
                    )
                }
            }
            item(key = "mode") {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterPill(stringResource(R.string.mode_activity), f.mode == HistoryMode.PAYMENTS, { onUpdate { it.copy(mode = HistoryMode.PAYMENTS) } })
                    FilterPill(stringResource(R.string.mode_items), f.mode == HistoryMode.BILLS, { onUpdate { it.copy(mode = HistoryMode.BILLS) } })
                }
            }
            item(key = "search") {
                InsetTextField(
                    value = f.query,
                    onValueChange = { text -> onUpdate { it.copy(query = text) } },
                    label = stringResource(R.string.search),
                    placeholder = stringResource(R.string.search_hint),
                    imeAction = ImeAction.Search,
                )
            }
            item(key = "filters") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (f.mode == HistoryMode.PAYMENTS) {
                        StatusFilter.entries.forEach { status ->
                            FilterPill(
                                label = stringResource(statusLabel(status)),
                                selected = f.status == status,
                                onClick = { onUpdate { it.copy(status = status) } },
                                role = Role.RadioButton,
                            )
                        }
                    } else {
                        FilterPill(
                            label = stringResource(R.string.show_archived),
                            selected = f.showArchived,
                            onClick = { onUpdate { it.copy(showArchived = !it.showArchived) } },
                            role = Role.Checkbox,
                        )
                    }
                }
            }
            item(key = "kinds") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterPill(stringResource(R.string.chip_all), f.kind == null, { onUpdate { it.copy(kind = null) } }, role = Role.RadioButton)
                    ItemKind.entries.forEach { kind ->
                        FilterPill(kind.chipLabel(), f.kind == kind, { onUpdate { it.copy(kind = kind) } }, role = Role.RadioButton)
                    }
                }
            }
            item(key = "refine") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterPill(
                        label = f.categoryId?.let { categoryById[it].displayName() } ?: stringResource(R.string.filter_category_all),
                        selected = f.categoryId != null,
                        onClick = { sheet = Sheet.CATEGORY },
                        role = Role.Button,
                    )
                    if (f.mode == HistoryMode.PAYMENTS) {
                        FilterPill(
                            label = stringResource(dateLabel(f.date)),
                            selected = f.date != DateFilter.ALL_TIME,
                            onClick = { sheet = Sheet.DATE },
                            role = Role.Button,
                        )
                    }
                    FilterPill(
                        label = stringResource(R.string.sort_prefix, stringResource(sortLabel(f.sort))),
                        selected = false,
                        onClick = { sheet = Sheet.SORT },
                        role = Role.Button,
                    )
                }
            }

            if (f.mode == HistoryMode.PAYMENTS) {
                if (state.payments.isEmpty() && !state.isLoading) {
                    item(key = "empty-payments") {
                        EmptyState(
                            title = stringResource(R.string.history_none_title),
                            message = stringResource(R.string.history_none_message),
                        )
                    }
                }
                items(state.payments, key = { it.item.id }) { row ->
                    ItemRow(
                        item = row.item,
                        category = categoryById[row.item.bill.categoryId],
                        status = row.status,
                        today = state.today,
                        onClick = { onOpenBill(row.item.bill.id) },
                    )
                }
            } else {
                if (state.bills.isEmpty() && !state.isLoading) {
                    item(key = "empty-bills") {
                        EmptyState(
                            title = stringResource(R.string.bills_none_title),
                            message = stringResource(R.string.bills_none_message),
                        )
                    }
                }
                items(state.bills, key = { it.id }) { bill ->
                    BillTemplateRow(
                        bill = bill,
                        category = categoryById[bill.categoryId],
                        onClick = { onOpenBill(bill.id) },
                        onRestore = { onSetArchived(bill.id, false) },
                    )
                }
            }
        }
    }

    when (sheet) {
        Sheet.NONE -> Unit
        Sheet.CATEGORY -> ChoiceSheet(
            title = stringResource(R.string.choose_category),
            options = listOf<Pair<String?, String>>(null to stringResource(R.string.filter_category_all)) +
                state.categories.map { it.id to it.displayName() },
            selected = f.categoryId,
            onSelect = { choice -> onUpdate { it.copy(categoryId = choice) }; sheet = Sheet.NONE },
            onDismiss = { sheet = Sheet.NONE },
        )
        Sheet.DATE -> ChoiceSheet(
            title = stringResource(R.string.filter_date),
            options = DateFilter.entries.map { it to stringResource(dateLabel(it)) },
            selected = f.date,
            onSelect = { choice -> onUpdate { it.copy(date = choice) }; sheet = Sheet.NONE },
            onDismiss = { sheet = Sheet.NONE },
        )
        Sheet.SORT -> ChoiceSheet(
            title = stringResource(R.string.sort_title),
            options = SortOrder.entries.map { it to stringResource(sortLabel(it)) },
            selected = f.sort,
            onSelect = { choice -> onUpdate { it.copy(sort = choice) }; sheet = Sheet.NONE },
            onDismiss = { sheet = Sheet.NONE },
        )
    }
}

private fun statusLabel(status: StatusFilter): Int = when (status) {
    StatusFilter.ALL -> R.string.filter_all
    StatusFilter.PENDING -> R.string.stats_pending
    StatusFilter.OVERDUE -> R.string.overdue
    StatusFilter.PAID -> R.string.status_paid
    StatusFilter.SKIPPED -> R.string.status_skipped
}

private fun dateLabel(filter: DateFilter): Int = when (filter) {
    DateFilter.ALL_TIME -> R.string.date_all_time
    DateFilter.THIS_MONTH -> R.string.date_this_month
    DateFilter.LAST_30_DAYS -> R.string.date_last_30
    DateFilter.NEXT_30_DAYS -> R.string.date_next_30
    DateFilter.THIS_YEAR -> R.string.date_this_year
}

private fun sortLabel(sort: SortOrder): Int = when (sort) {
    SortOrder.DUE_DATE_DESC -> R.string.sort_date_newest
    SortOrder.DUE_DATE_ASC -> R.string.sort_date_oldest
    SortOrder.AMOUNT_DESC -> R.string.sort_amount
    SortOrder.NAME_ASC -> R.string.sort_name
}

@Composable
private fun <T> ChoiceSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DuzupTheme.colors
    DuzupSheet(onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary, modifier = Modifier.padding(bottom = 12.dp))
        Column(Modifier.then(Modifier.fillMaxWidth())) {
            options.forEach { (value, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.RadioButton) { onSelect(value) }
                        .semantics(mergeDescendants = true) { this.selected = value == selected },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    if (value == selected) Icon(Icons.Rounded.Check, null, tint = colors.accentText, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun BillTemplateRow(bill: Bill, category: Category?, onClick: () -> Unit, onRestore: () -> Unit) {
    val colors = DuzupTheme.colors
    val amount = if (bill.kind == ItemKind.BILL) {
        bill.amountMinorUnits?.let { Money.format(it, bill.currencyCode) } ?: stringResource(R.string.no_amount)
    } else {
        bill.kind.label()
    }
    val repeat = recurrenceSummary(bill.recurrence)
    val archivedLabel = stringResource(R.string.archived_label)
    RaisedCard(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(bill.title).append(", ").append(amount).append(", ").append(repeat)
                    if (bill.isArchived) append(", ").append(archivedLabel)
                }
            },
        onClick = onClick,
        onClickLabel = stringResource(R.string.open_item),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(if (bill.kind == ItemKind.BILL) category else kindBadge(bill.kind))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(bill.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$amount · $repeat", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (bill.isArchived) Text(archivedLabel, style = MaterialTheme.typography.labelSmall, color = colors.dueSoon)
            }
            if (bill.isArchived) {
                FlatIconButton(Icons.Rounded.Unarchive, stringResource(R.string.restore_bill), onRestore, tint = colors.accentText)
            }
        }
    }
}
