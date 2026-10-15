package com.duzup.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duzup.app.TestData
import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.OccurrenceItem
import com.duzup.app.core.preferences.UserPreferences
import com.duzup.app.domain.payments.PaymentRules
import com.duzup.app.feature.addedit.AddEditCallbacks
import com.duzup.app.feature.addedit.AddEditScreen
import com.duzup.app.feature.addedit.AddEditUiState
import com.duzup.app.feature.addedit.DraftError
import com.duzup.app.feature.addedit.DraftField
import com.duzup.app.feature.addedit.ItemDraft
import com.duzup.app.feature.home.HomeCallbacks
import com.duzup.app.feature.home.HomeRow
import com.duzup.app.feature.home.HomeScreen
import com.duzup.app.feature.home.HomeSection
import com.duzup.app.feature.home.HomeSectionKind
import com.duzup.app.feature.home.HomeUiState
import com.duzup.app.feature.notes.NotesCallbacks
import com.duzup.app.feature.notes.NotesScreen
import com.duzup.app.feature.notes.NotesUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h891dp-port-xxhdpi")
class UiBehaviorTest {
    @get:Rule val rule = createComposeRule()

    private val today = Fixtures.today
    private val prefs = UserPreferences.defaults(java.util.Locale.US)

    private fun item(id: String, title: String, kind: ItemKind): OccurrenceItem {
        val bill = TestData.bill(id = "b-$id", title = title, amount = null, start = today).copy(kind = kind)
        return OccurrenceItem(TestData.occurrence(id = id, billId = bill.id, date = today), bill)
    }

    private fun row(i: OccurrenceItem) = HomeRow(i, PaymentRules.displayStatus(i, Fixtures.now, today))

    private fun homeState(vararg items: OccurrenceItem) = HomeUiState(
        isLoading = false, month = YearMonth.of(2025, 3), today = today, hasAnyItems = true,
        categories = Fixtures.categoryMap,
        sections = listOf(HomeSection(HomeSectionKind.TODAY, items.map(::row))),
    )

    @Test
    fun home_showsRowsAndOpensTheTappedItem() {
        var opened: String? = null
        rule.setContent {
            TestApp { HomeScreen(homeState(item("a", "Call mom", ItemKind.TASK), item("b", "Team dinner", ItemKind.EVENT)), HomeCallbacks(onOpenItem = { opened = it })) }
        }
        rule.onNodeWithText("Call mom", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Team dinner", substring = true).performClick()
        assertEquals("b-b", opened)
    }

    @Test
    fun home_emptyState_offersAnAddAction() {
        var added = false
        rule.setContent {
            TestApp { HomeScreen(HomeUiState(isLoading = false, today = today), HomeCallbacks(onAdd = { added = true })) }
        }
        rule.onNodeWithText("Nothing here yet").assertIsDisplayed()
        rule.onNodeWithText("Add something").performClick()
        assertTrue(added)
    }

    @Test
    fun addEdit_showsInlineErrorsAndSaves() {
        var saved = false
        val state = AddEditUiState(
            isLoading = false, draft = ItemDraft.defaults(prefs, ItemKind.TASK, today, LocalTime.of(10, 0)),
            categories = Fixtures.categories, today = today,
            errors = mapOf(DraftField.TITLE to DraftError.TITLE_REQUIRED), showErrors = true,
        )
        rule.setContent { TestApp { AddEditScreen(state, AddEditCallbacks(onSave = { saved = true })) } }
        rule.onNodeWithText("Give it a name").assertIsDisplayed()
        rule.onNodeWithText("Save it").performScrollTo().performClick()
        assertTrue(saved)
    }

    @Test
    fun addEdit_reportsTypedTitle() {
        var title = ""
        val state = AddEditUiState(
            isLoading = false, draft = ItemDraft.defaults(prefs, ItemKind.EVENT, today, LocalTime.of(10, 0)),
            categories = Fixtures.categories, today = today,
        )
        rule.setContent { TestApp { AddEditScreen(state, AddEditCallbacks(onTitle = { title = it })) } }
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Movie night")
        assertEquals("Movie night", title)
    }

    @Test
    fun notes_emptyState_isShown() {
        rule.setContent { TestApp { NotesScreen(NotesUiState(isLoading = false), NotesCallbacks()) } }
        rule.onNodeWithText("No notes yet").assertIsDisplayed()
    }
}
