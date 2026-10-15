package com.duzup.app.integration

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duzup.app.TestData
import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.ReminderKind
import com.duzup.app.core.model.ReminderRow
import com.duzup.app.core.notifications.NotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = android.app.Application::class)
class NotificationHelperTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val time = FakeTime(Instant.parse("2025-03-01T08:00:00Z"))
    private lateinit var helper: NotificationHelper

    @Before
    fun setUp() {
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        helper = NotificationHelper(context, time)
        helper.createChannels()
    }

    private fun reminder(offset: Int) = ReminderRow(
        id = "occ|$offset", occurrenceId = "occ", kind = if (offset == 0) ReminderKind.DUE else ReminderKind.UPCOMING,
        offsetMinutes = offset * 1440, scheduledAt = time.now(), deliveredAt = null, snoozeUntil = null, canceled = false,
    )

    private fun show(offset: Int) {
        val bill = TestData.bill(id = "b", title = "Electricity", start = java.time.LocalDate.of(2025, 3, 10))
        val occ = TestData.occurrence(id = "occ", billId = "b", date = java.time.LocalDate.of(2025, 3, 10))
        helper.show(occ, bill, reminder(offset), hideAmount = false)
    }

    private fun billNotifications() =
        shadowOf(manager).allNotifications.filter { it.group == NotificationHelper.GROUP_KEY && it.flags and Notification.FLAG_GROUP_SUMMARY == 0 }

    @Test
    fun everyReminder_alerts_evenWhenItReplacesAnIgnoredOne() {
        show(7)
        show(3)
        val notifications = billNotifications()
        assertEquals("same bill occurrence reuses one slot", 1, notifications.size)
        assertTrue(
            "a later reminder must alert (no ONLY_ALERT_ONCE flag)",
            notifications.single().flags and Notification.FLAG_ONLY_ALERT_ONCE == 0,
        )
    }

    @Test
    fun notificationCarriesThreeActions() {
        show(1)
        val titles = billNotifications().single().actions.map { it.title.toString() }
        assertEquals(listOf("Paid it", "Remind later", "Open"), titles)
    }

    @Test
    fun cancelRemovesTheNotification() {
        show(1)
        helper.cancel("occ")
        assertTrue(billNotifications().isEmpty())
    }

    @Test
    fun everyKindAndPhase_producesFinishedCopy_onTheRightChannel() {
        val expectedChannel = mapOf(
            ItemKind.EVENT to NotificationHelper.CHANNEL_EVENTS, ItemKind.TASK to NotificationHelper.CHANNEL_TASKS,
            ItemKind.MEMO to NotificationHelper.CHANNEL_NOTES, ItemKind.BIRTHDAY to NotificationHelper.CHANNEL_BIRTHDAYS,
            ItemKind.ROUTINE to NotificationHelper.CHANNEL_ROUTINES,
        )
        val reminders = listOf(
            ReminderKind.UPCOMING to 7 * 1440, ReminderKind.UPCOMING to 1440, ReminderKind.UPCOMING to 180,
            ReminderKind.UPCOMING to 15, ReminderKind.DUE to 0, ReminderKind.OVERDUE to 0, ReminderKind.SNOOZE to 0,
        )
        for (kind in ItemKind.entries) for ((rk, minutes) in reminders) for (includeAge in listOf(true, false)) {
            val bill = TestData.bill(id = "b", title = "Sara", start = java.time.LocalDate.of(2000, 3, 10), amount = null)
                .copy(kind = kind, includeAge = includeAge)
            val occ = TestData.occurrence(id = "occ", billId = "b", date = java.time.LocalDate.of(2025, 3, 10))
            val row = ReminderRow("occ|$rk$minutes", "occ", rk, minutes, time.now(), null, null, false)
            helper.show(occ, bill, row, hideAmount = false)
            val n = shadowOf(manager).allNotifications.first { it.group == NotificationHelper.GROUP_KEY && it.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            val label = "$kind $rk $minutes age=$includeAge"
            val text = n.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
            val title = n.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
            assertTrue("$label: $text", text.isNotBlank() && !text.contains("%") && !text.contains("{"))
            assertTrue("$label title: $title", title == "Sara")
            expectedChannel[kind]?.let { assertEquals(label, it, n.channelId) }
            assertEquals(label, 3, n.actions.size)
            helper.cancel("occ")
        }
    }
}
