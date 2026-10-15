package com.duzup.app.integration

import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.ReminderOffsets
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duzup.app.core.backup.BackupException
import com.duzup.app.core.backup.RestoreMode
import com.duzup.app.core.model.OccurrenceStatus
import com.duzup.app.core.model.Recurrence
import com.duzup.app.core.model.RecurrenceRule
import com.duzup.app.core.model.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = android.app.Application::class)
class BackupRestoreTest {
    private lateinit var env: TestEnv

    @Before fun setUp() { env = TestEnv() }
    @After fun tearDown() { env.close() }

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    private suspend fun seed(): Triple<String, String, String> {
        env.repo.ensureDefaultCategories()
        val customId = env.repo.saveCategory(null, "Pets", "pets", 0xFF1E6FD9)
        val monthly = env.bill(title = "Rent", start = d(2025, 1, 31), recurrence = Recurrence(RecurrenceRule.MONTHLY))
            .copy(categoryId = customId)
        val oneTime = env.bill(title = "Insurance", start = d(2025, 3, 5), amount = null)
        env.repo.saveBill(monthly)
        env.repo.saveBill(oneTime)
        val january = env.occurrenceOn(monthly.id, d(2025, 1, 31))
        env.repo.markPaid(january.id, actualAmountMinorUnits = 777_00, note = "paid early")
        env.prefs.setCurrency("AED")
        env.prefs.setTheme(ThemeMode.DARK)
        env.prefs.setFirstDayOfWeek(DayOfWeek.SUNDAY)
        env.prefs.setOffsets(ItemKind.BILL, ReminderOffsets.days(14, 2))
        return Triple(monthly.id, oneTime.id, customId)
    }

    @Test
    fun exportThenReset_thenReplaceRestore_bringsEverythingBack() = runBlocking {
        val (monthlyId, oneTimeId, customId) = seed()
        val beforeOccurrences = env.db.occurrenceDao().getAll().size
        val json = env.backup.export()

        env.repo.resetAllData()
        assertTrue(env.db.billDao().getAll().isEmpty())
        assertTrue("alarms cancelled by reset", env.scheduler.active.isEmpty())

        val result = env.backup.restore(json, RestoreMode.REPLACE)

        assertEquals(2, result.bills)
        assertEquals(beforeOccurrences, env.db.occurrenceDao().getAll().size)
        val january = env.occurrenceOn(monthlyId, d(2025, 1, 31))
        assertEquals(OccurrenceStatus.PAID, january.status)
        assertEquals(777_00L, january.actualAmountMinorUnits)
        assertEquals("paid early", january.paymentNote)
        assertEquals(OccurrenceStatus.PENDING, env.occurrenceOn(monthlyId, d(2025, 2, 28)).status)
        assertEquals(customId, env.db.billDao().getById(monthlyId)!!.categoryId)
        assertTrue(env.db.categoryDao().getAll().any { it.id == customId && it.name == "Pets" })
        assertEquals(null, env.db.billDao().getById(oneTimeId)!!.amountMinorUnits)

        val prefs = env.prefs.current()
        assertEquals("AED", prefs.currencyCode)
        assertEquals(ThemeMode.DARK, prefs.theme)
        assertEquals(DayOfWeek.SUNDAY, prefs.firstDayOfWeek)
        assertEquals(ReminderOffsets.days(14, 2), prefs.offsetsFor(ItemKind.BILL))

        assertTrue("future reminders are rescheduled after restore", env.scheduler.active.isNotEmpty())
        val paidReminders = env.scheduler.active.keys.filter { it.startsWith(january.id) }
        assertTrue("no reminders for the paid occurrence", paidReminders.isEmpty())
    }

    @Test
    fun replaceRestore_discardsDataThatIsNotInTheBackup() = runBlocking {
        val first = env.bill(title = "Kept")
        env.repo.saveBill(first)
        val json = env.backup.export()

        val extra = env.bill(title = "Added later", start = d(2025, 4, 1))
        env.repo.saveBill(extra)
        assertEquals(2, env.db.billDao().getAll().size)

        env.backup.restore(json, RestoreMode.REPLACE)

        assertEquals(listOf("Kept"), env.db.billDao().getAll().map { it.title })
    }

    @Test
    fun mergeRestoreOfTheSameBackup_neverDuplicates() = runBlocking {
        seed()
        val json = env.backup.export()
        val bills = env.db.billDao().getAll().size
        val occurrences = env.db.occurrenceDao().getAll().size

        val result = env.backup.restore(json, RestoreMode.MERGE)

        assertEquals(bills, env.db.billDao().getAll().size)
        assertEquals(occurrences, env.db.occurrenceDao().getAll().size)
        assertEquals(0, result.bills)
        assertEquals(0, result.occurrences)
        assertTrue(result.skippedExisting > 0)
    }

    @Test
    fun mergeRestore_addsMissingBills_andKeepsExistingOnes() = runBlocking {
        val (monthlyId, oneTimeId, _) = seed()
        val json = env.backup.export()
        env.repo.deleteBill(oneTimeId)
        env.repo.saveBill(env.bill(title = "Local only", start = d(2025, 6, 1)))

        env.backup.restore(json, RestoreMode.MERGE)

        val titles = env.db.billDao().getAll().map { it.title }.sorted()
        assertEquals(listOf("Insurance", "Local only", "Rent"), titles)
        assertEquals(OccurrenceStatus.PAID, env.occurrenceOn(monthlyId, d(2025, 1, 31)).status)
    }

    @Test
    fun mergeRestore_existingRecordWinsOnConflict() = runBlocking {
        val (monthlyId, _, _) = seed()
        val json = env.backup.export()
        // The user pays February locally after taking the backup; the backup still says PENDING.
        val february = env.occurrenceOn(monthlyId, d(2025, 2, 28))
        env.repo.markPaid(february.id, actualAmountMinorUnits = 1_00, note = "local")

        env.backup.restore(json, RestoreMode.MERGE)

        val after = env.occurrenceOn(monthlyId, d(2025, 2, 28))
        assertEquals(OccurrenceStatus.PAID, after.status)
        assertEquals("local", after.paymentNote)
    }

    @Test
    fun rejectedBackup_touchesNothing_andCancelsNoAlarms() = runBlocking {
        seed()
        val bills = env.db.billDao().getAll().map { it.id }.sorted()
        val alarms = env.scheduler.active.toMap()
        val cancelCalls = env.scheduler.cancelCalls.size

        for (text in listOf("garbage", "{\"app\":\"duzup\",\"schemaVersion\":99}")) {
            try {
                env.backup.restore(text, RestoreMode.REPLACE)
                fail("accepted $text")
            } catch (_: BackupException) {
            }
        }

        assertEquals(bills, env.db.billDao().getAll().map { it.id }.sorted())
        assertEquals(alarms, env.scheduler.active)
        assertEquals("no alarm was cancelled", cancelCalls, env.scheduler.cancelCalls.size)
        assertEquals(0, env.notifier.cancelAllCalls)
    }

    @Test
    fun tamperedBackup_withDanglingOccurrence_isRejectedBeforeAnyChange() = runBlocking {
        seed()
        val json = env.backup.export()
        val tampered = json.replaceFirst(
            Regex("\"itemId\": \"[^\"]+\""),
            "\"itemId\": \"99999999-9999-9999-9999-999999999999\"",
        )
        val billsBefore = env.db.billDao().getAll().size

        try {
            env.backup.restore(tampered, RestoreMode.REPLACE)
            fail("accepted a dangling reference")
        } catch (e: BackupException) {
            assertEquals(BackupException.Reason.INVALID_DATA, e.reason)
        }
        assertEquals(billsBefore, env.db.billDao().getAll().size)
    }

    @Test
    fun exportedFile_containsNoReminderRows_butAllHistory() = runBlocking {
        seed()
        val json = env.backup.export()
        assertTrue(json.contains("\"schemaVersion\": 2"))
        assertTrue(json.contains("paid early"))
        assertTrue(!json.contains("|SNOOZE") && !json.contains("deliveredAt"))
    }

    @Test
    fun resetAllData_cancelsAlarms_clearsEverything_keepsOnboardingDone() = runBlocking {
        seed()
        env.prefs.setOnboardingCompleted(true)

        env.repo.resetAllData()

        assertTrue(env.db.billDao().getAll().isEmpty())
        assertTrue(env.db.occurrenceDao().getAll().isEmpty())
        assertTrue(env.db.reminderDao().getAll().isEmpty())
        assertTrue(env.scheduler.active.isEmpty())
        assertEquals(1, env.notifier.cancelAllCalls)
        assertTrue(env.prefs.current().onboardingCompleted)
        assertTrue("built-in categories are re-seeded", env.db.categoryDao().getAll().size >= 9)
        assertTrue("custom categories are gone", env.db.categoryDao().getAll().none { !it.builtIn })
    }
}
