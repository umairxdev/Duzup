package com.duzup.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duzup.app.TestData
import com.duzup.app.core.model.Bill
import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.OccurrenceItem
import com.duzup.app.core.model.OccurrenceStatus
import com.duzup.app.core.model.Recurrence
import com.duzup.app.core.model.RecurrenceRule
import com.duzup.app.core.model.ReminderOffsets
import com.duzup.app.core.preferences.UserPreferences
import com.duzup.app.domain.payments.PaymentRules
import com.duzup.app.domain.summary.SummaryCalculator
import com.duzup.app.feature.addedit.AddEditCallbacks
import com.duzup.app.feature.addedit.AddEditScreen
import com.duzup.app.feature.addedit.AddEditUiState
import com.duzup.app.feature.addedit.DraftError
import com.duzup.app.feature.addedit.DraftField
import com.duzup.app.feature.addedit.ItemDraft
import com.duzup.app.feature.bills.DetailCallbacks
import com.duzup.app.feature.bills.DetailScreen
import com.duzup.app.feature.bills.DetailUiState
import com.duzup.app.feature.bills.OccurrenceRowUi
import com.duzup.app.feature.calendar.CalendarScreen
import com.duzup.app.feature.calendar.CalendarUiState
import com.duzup.app.feature.calendar.CalendarDot
import com.duzup.app.feature.calendar.DayCell
import com.duzup.app.feature.calendar.DotState
import com.duzup.app.feature.history.HistoryScreen
import com.duzup.app.feature.history.HistoryUiState
import com.duzup.app.feature.home.Greeting
import com.duzup.app.feature.home.HomeCallbacks
import com.duzup.app.feature.home.HomeRow
import com.duzup.app.feature.home.HomeScreen
import com.duzup.app.feature.home.HomeSection
import com.duzup.app.feature.home.HomeSectionKind
import com.duzup.app.feature.home.HomeUiState
import com.duzup.app.feature.intro.IntroOverlay
import com.duzup.app.feature.notes.NoteUi
import com.duzup.app.feature.notes.NotesCallbacks
import com.duzup.app.feature.notes.NotesScreen
import com.duzup.app.feature.notes.NotesUiState
import com.duzup.app.feature.onboarding.OnboardingScreen
import com.duzup.app.feature.settings.SettingsCallbacks
import com.duzup.app.feature.settings.SettingsScreen
import com.duzup.app.feature.stats.StatsScreen
import com.duzup.app.feature.stats.StatsUiState
import com.duzup.app.navigation.TopLevel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * Renders the real screens through the real theme into PNGs under app/build/screenshots so they can be reviewed
 * against the design. They double as smoke tests: a screen that crashes while composing fails here.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h891dp-port-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val today = Fixtures.today
    private val march = YearMonth.of(2025, 3)
    private val prefs = UserPreferences.defaults(java.util.Locale.US)

    // ---- fixtures: one of every kind ----
    private fun make(
        id: String, title: String, kind: ItemKind, date: LocalDate, time: LocalTime = LocalTime.of(9, 0),
        category: String = "other", amount: Long? = null, status: OccurrenceStatus = OccurrenceStatus.PENDING,
        configure: (Bill) -> Bill = { it },
    ): OccurrenceItem {
        val bill = configure(TestData.bill(id = "b-$id", title = title, categoryId = category, amount = amount, start = date, time = time).copy(kind = kind))
        val paidAt = if (status == OccurrenceStatus.PENDING) null else Fixtures.now
        return OccurrenceItem(
            TestData.occurrence(id = id, billId = bill.id, date = date, time = time, status = status, paidAt = paidAt),
            bill,
        )
    }

    private val rent = make("rent", "Rent", ItemKind.BILL, today.minusDays(2), category = "rent", amount = 4_500_000)
    private val electricity = make("el", "Electricity", ItemKind.BILL, today.plusDays(3), category = "utilities", amount = 850_000)
    private val dinner = make("dn", "Dinner with Sam", ItemKind.EVENT, today, LocalTime.of(19, 30)) { it.copy(location = "Cafe Aylanto") }
    private val assignment = make("as", "Submit assignment", ItemKind.TASK, today, LocalTime.of(17, 0)) { it.copy(priority = 2) }
    private val sara = make("sa", "Sara", ItemKind.BIRTHDAY, today.plusDays(4)) { it.copy(recurrence = Recurrence(RecurrenceRule.YEARLY)) }
    private val water = make("wa", "Drink water", ItemKind.ROUTINE, today, LocalTime.of(11, 0)) {
        it.copy(recurrence = Recurrence(RecurrenceRule.DAILY), routineTimes = listOf(LocalTime.of(11, 0), LocalTime.of(15, 0), LocalTime.of(19, 0)))
    }
    private val doneTask = make("dt", "Book flights", ItemKind.TASK, today.minusDays(1), status = OccurrenceStatus.PAID)

    private fun row(item: OccurrenceItem) = HomeRow(item, PaymentRules.displayStatus(item, Fixtures.now, today))

    private fun homeState(kind: com.duzup.app.core.model.ItemKind? = null) = HomeUiState(
        isLoading = false, greeting = Greeting.MORNING, month = march, isCurrentMonth = true,
        totals = SummaryCalculator.monthTotals(listOf(rent, electricity), march, "PKR"),
        showMoneyCard = true, kindFilter = kind,
        sections = listOf(
            HomeSection(HomeSectionKind.LATE, listOf(rent).map(::row)),
            HomeSection(HomeSectionKind.TODAY, listOf(dinner, assignment, water).map(::row)),
            HomeSection(HomeSectionKind.COMING_UP, listOf(electricity, sara).map(::row)),
            HomeSection(HomeSectionKind.DONE, listOf(doneTask).map(::row)),
        ),
        overdueCount = 1, hasAnyItems = true, categories = Fixtures.categoryMap, today = today,
    )

    private fun home(dark: Boolean, name: String, fontScale: Float = 1f) {
        rule.setContent {
            TestApp(dark = dark, fontScale = fontScale, bottomBar = TopLevel.HOME) { HomeScreen(homeState(), HomeCallbacks()) }
        }
        rule.onRoot().saveScreenshot(name)
    }

    @Test fun home_light() = home(false, "home_light")
    @Test fun home_dark() = home(true, "home_dark")
    @Test fun home_largeFont() = home(false, "home_light_font2x", fontScale = 2f)

    @Test
    fun home_empty() {
        val state = HomeUiState(isLoading = false, month = march, today = today)
        rule.setContent { TestApp(bottomBar = TopLevel.HOME) { HomeScreen(state, HomeCallbacks()) } }
        rule.onRoot().saveScreenshot("home_empty_light")
    }

    @Test
    fun home_eventsFilter() {
        val state = homeState(ItemKind.EVENT).copy(
            sections = listOf(HomeSection(HomeSectionKind.TODAY, listOf(row(dinner)))),
            showMoneyCard = false,
        )
        rule.setContent { TestApp(bottomBar = TopLevel.HOME) { HomeScreen(state, HomeCallbacks()) } }
        rule.onRoot().saveScreenshot("home_events_filter")
    }

    // ---- add / edit, one per kind ----
    private fun addEdit(kind: ItemKind, name: String, dark: Boolean = false, errors: Boolean = false, edit: (ItemDraft) -> ItemDraft = { it }) {
        val base = ItemDraft.defaults(prefs, kind, today, LocalTime.of(10, 0))
        val draft = edit(base)
        val state = AddEditUiState(
            isLoading = false, draft = draft, categories = Fixtures.categories, today = today,
            errors = if (errors) mapOf(DraftField.TITLE to DraftError.TITLE_REQUIRED, DraftField.DATE to DraftError.DATE_REQUIRED) else emptyMap(),
            showErrors = errors,
        )
        rule.setContent { TestApp(dark = dark) { AddEditScreen(state, AddEditCallbacks()) } }
        rule.onRoot().saveScreenshot(name)
    }

    @Test fun addEdit_bill() = addEdit(ItemKind.BILL, "add_bill_light") { it.copy(title = "Electricity Bill", amountText = "8500", currencyCode = "PKR", dueDate = today.plusDays(3)) }
    @Test fun addEdit_bill_dark() = addEdit(ItemKind.BILL, "add_bill_dark", dark = true) { it.copy(title = "Electricity Bill", amountText = "8500", currencyCode = "PKR", dueDate = today.plusDays(3)) }
    @Test fun addEdit_bill_errors() = addEdit(ItemKind.BILL, "add_bill_errors", errors = true) { it.copy(amountText = "abc", dueDate = null) }
    @Test fun addEdit_event() = addEdit(ItemKind.EVENT, "add_event_light") { it.copy(title = "Sam's party", location = "Cafe Aylanto", endTime = LocalTime.of(22, 0)) }
    @Test fun addEdit_task() = addEdit(ItemKind.TASK, "add_task_light") { it.copy(title = "Submit assignment", priority = 2) }
    @Test fun addEdit_note() = addEdit(ItemKind.MEMO, "add_note_light") { it.copy(title = "Wi-Fi password", notes = "It's on the fridge", remind = false) }
    @Test fun addEdit_birthday() = addEdit(ItemKind.BIRTHDAY, "add_birthday_light") { it.copy(title = "Sara", dueDate = LocalDate.of(2000, 3, 16)) }
    @Test fun addEdit_routine() = addEdit(ItemKind.ROUTINE, "add_routine_light") {
        it.copy(title = "Drink water", routineTimes = listOf(LocalTime.of(9, 0), LocalTime.of(13, 0)), weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
    }

    // ---- detail ----
    private fun detail(item: OccurrenceItem, name: String, dark: Boolean = false, history: List<OccurrenceItem> = emptyList(), todays: List<OccurrenceItem> = emptyList()) {
        fun ui(i: OccurrenceItem) = OccurrenceRowUi(i.occurrence, PaymentRules.displayStatus(i, Fixtures.now, today))
        val state = DetailUiState(
            isLoading = false, bill = item.bill, category = Fixtures.categoryMap[item.bill.categoryId],
            today = today, next = ui(item), pending = listOf(ui(item)), history = history.map(::ui), todays = todays.map(::ui),
        )
        rule.setContent { TestApp(dark = dark) { DetailScreen(state, DetailCallbacks()) } }
        rule.onRoot().saveScreenshot(name)
    }

    @Test fun detail_bill() = detail(electricity, "detail_bill_light", history = listOf(make("h", "Electricity", ItemKind.BILL, today.minusDays(30), amount = 850_000, status = OccurrenceStatus.PAID)))
    @Test fun detail_bill_late_dark() = detail(rent, "detail_bill_late_dark", dark = true)
    @Test fun detail_event() = detail(dinner, "detail_event_light")
    @Test fun detail_task() = detail(assignment, "detail_task_light")
    @Test fun detail_birthday() = detail(sara, "detail_birthday_light")
    @Test fun detail_routine() = detail(water, "detail_routine_light", todays = listOf(
        water, make("wa2", "Drink water", ItemKind.ROUTINE, today, LocalTime.of(15, 0), status = OccurrenceStatus.PAID) { water.bill },
    ))

    // ---- calendar ----
    @Test
    fun calendar_withMixedDots() {
        val first = march.atDay(1)
        val offset = (first.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
        val start = first.minusDays(offset.toLong())
        val cells = (0 until 42).map { i ->
            val date = start.plusDays(i.toLong())
            val dots = when (date.dayOfMonth) {
                10 -> listOf(CalendarDot(ItemKind.BILL, DotState.OVERDUE))
                12 -> listOf(CalendarDot(ItemKind.EVENT, DotState.PENDING), CalendarDot(ItemKind.TASK, DotState.PENDING), CalendarDot(ItemKind.ROUTINE, DotState.PENDING))
                16 -> listOf(CalendarDot(ItemKind.BIRTHDAY, DotState.PENDING))
                15 -> listOf(CalendarDot(ItemKind.BILL, DotState.PENDING))
                else -> emptyList()
            }.takeIf { date.month == march.month } ?: emptyList()
            DayCell(
                date = date, inMonth = date.month == march.month, isToday = date == today, isSelected = date == today,
                overdue = dots.count { it.state == DotState.OVERDUE }, pending = dots.count { it.state == DotState.PENDING },
                paid = dots.count { it.state == DotState.DONE }, dots = dots,
            )
        }
        val state = CalendarUiState(
            month = march, firstDay = DayOfWeek.MONDAY, cells = cells, selectedDate = today,
            dayRows = listOf(dinner, assignment, water).map(::row), categories = Fixtures.categoryMap, today = today,
        )
        rule.setContent { TestApp(bottomBar = TopLevel.CALENDAR) { CalendarScreen(state, {}, {}, {}, {}) } }
        rule.onRoot().saveScreenshot("calendar_light")
    }

    // ---- notes ----
    @Test
    fun notes_pinnedAndOthers() {
        fun note(id: String, title: String, pinned: Boolean, remind: Boolean) =
            NoteUi(
                TestData.bill(id = id, title = title, amount = null).copy(kind = ItemKind.MEMO, isPinned = pinned, notes = "Some detail goes here", scheduled = remind),
                if (remind) make("r$id", title, ItemKind.MEMO, today.plusDays(1)) else null,
            )
        val state = NotesUiState(
            isLoading = false,
            pinned = listOf(note("n1", "Wi-Fi password", true, false)),
            others = listOf(note("n2", "Call the landlord", false, true), note("n3", "Gift ideas for Sara", false, false)),
        )
        rule.setContent { TestApp(bottomBar = TopLevel.NOTES) { NotesScreen(state, NotesCallbacks()) } }
        rule.onRoot().saveScreenshot("notes_light")
    }

    @Test
    fun notes_empty_dark() {
        rule.setContent { TestApp(dark = true, bottomBar = TopLevel.NOTES) { NotesScreen(NotesUiState(isLoading = false), NotesCallbacks()) } }
        rule.onRoot().saveScreenshot("notes_empty_dark")
    }

    // ---- settings / stats / history / onboarding / intro ----
    @Test
    fun settings_light() {
        rule.setContent { TestApp(bottomBar = TopLevel.SETTINGS) { SettingsScreen(prefs.copy(quietHoursEnabled = true), false, null, SettingsCallbacks()) } }
        rule.onRoot().saveScreenshot("settings_light")
    }

    @Test
    fun settings_dark() {
        rule.setContent { TestApp(dark = true, bottomBar = TopLevel.SETTINGS) { SettingsScreen(prefs, false, null, SettingsCallbacks()) } }
        rule.onRoot().saveScreenshot("settings_dark")
    }

    @Test
    fun stats_withData() {
        val items = listOf(rent, electricity, make("p", "Gym", ItemKind.BILL, today.minusDays(5), category = "other", amount = 400_000, status = OccurrenceStatus.PAID))
        val state = StatsUiState(
            isLoading = false, month = march, isCurrentMonth = true,
            stats = SummaryCalculator.monthStats(items, march, Fixtures.now, "PKR"),
            categories = Fixtures.categoryMap, currencies = listOf("PKR"),
        )
        rule.setContent { TestApp { StatsScreen(state, {}, {}, {}) } }
        rule.onRoot().saveScreenshot("stats_light")
    }

    @Test
    fun history_items() {
        val state = HistoryUiState(isLoading = false, categories = Fixtures.categories, today = today, payments = listOf(rent, dinner, assignment, doneTask).map(::row))
        rule.setContent { TestApp { HistoryScreen(state, {}, {}, {}, { _, _ -> }) } }
        rule.onRoot().saveScreenshot("history_light")
    }

    @Test
    fun onboarding_pages() {
        rule.setContent { TestApp { OnboardingScreen(prefs, {}, {}, {}, {}, {}, {}) } }
        rule.onRoot().saveScreenshot("onboarding_1")
    }

    @Test
    fun intro_overlay() {
        rule.setContent { TestApp(dark = true) { IntroOverlay(onDone = {}) } }
        rule.onRoot().saveScreenshot("intro")
    }
}
