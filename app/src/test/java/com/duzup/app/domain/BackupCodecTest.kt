package com.duzup.app.domain

import com.duzup.app.core.backup.BackupItem
import com.duzup.app.core.backup.BackupCategory
import com.duzup.app.core.backup.BackupCodec
import com.duzup.app.core.backup.BackupDocument
import com.duzup.app.core.backup.BackupException
import com.duzup.app.core.backup.BackupException.Reason
import com.duzup.app.core.backup.BackupOccurrence
import com.duzup.app.core.backup.BackupSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCodecTest {
    private val billId = "11111111-1111-1111-1111-111111111111"
    private val occId = "22222222-2222-2222-2222-222222222222"

    private fun settings() = BackupSettings(
        currencyCode = "PKR", reminderTime = "09:00", offsetsByKind = mapOf("BILL" to listOf(10080, 4320, 1440, 0)),
        overdueReminders = true, quietHoursEnabled = false, quietStart = "22:00", quietEnd = "08:00", snooze = "HOUR_1", theme = "SYSTEM", firstDayOfWeek = "MONDAY",
        haptics = true, reduceMotion = false, hideAmountsInNotifications = false,
    )

    private fun bill(id: String = billId) = BackupItem(
        id = id, title = "Electricity", categoryId = "utilities", amountMinorUnits = 850_000,
        currencyCode = "PKR", startDueDate = "2025-01-31", dueLocalTime = "09:00", timeZoneId = "Asia/Karachi",
        recurrenceRule = "MONTHLY", reminderOffsets = listOf(10080, 4320, 1440, 0), isArchived = false,
        createdAt = 1L, updatedAt = 2L,
    )

    private fun occurrence(id: String = occId, date: String = "2025-01-31", status: String = "PENDING", paidAt: Long? = null) =
        BackupOccurrence(id, billId, date, "09:00", status, paidAt, null, null, 1L)

    private fun doc(
        items: List<BackupItem> = listOf(bill()),
        occurrences: List<BackupOccurrence> = listOf(occurrence()),
        categories: List<BackupCategory> = emptyList(),
        settings: BackupSettings = settings(),
        app: String = BackupDocument.APP_ID,
        version: Int = BackupDocument.SCHEMA_VERSION,
    ) = BackupDocument(app, version, 1_700_000_000_000, settings, categories, items, occurrences)

    private fun assertRejected(reason: Reason, document: BackupDocument) {
        try {
            BackupCodec.validate(document)
            fail("expected rejection with $reason")
        } catch (e: BackupException) {
            assertEquals(reason, e.reason)
        }
    }

    @Test
    fun roundTrip_preservesEverything() {
        val original = doc(
            occurrences = listOf(occurrence(), occurrence("33333333-3333-3333-3333-333333333333", "2025-02-28", "PAID", paidAt = 5L)),
            categories = listOf(BackupCategory("custom-1", "Pets", "pets", 0xFF1E6FD9, false, 3)),
        )
        val decoded = BackupCodec.decode(BackupCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun unknownJsonKeys_areIgnored_forForwardCompatibility() {
        val json = BackupCodec.encode(doc()).replaceFirst("{", "{ \"futureField\": 42,")
        assertEquals(1, BackupCodec.decode(json).items.size)
    }

    @Test
    fun garbageAndWrongFiles_areRejected() {
        for (text in listOf("", "not json", "[]", "{\"hello\": 1}", "{\"app\": \"duzup\"}")) {
            try {
                BackupCodec.decode(text)
                fail("accepted: $text")
            } catch (e: BackupException) {
                assertEquals(Reason.NOT_JSON, e.reason)
            }
        }
    }

    @Test
    fun wrongApp_andUnsupportedVersions_areRejected() {
        assertRejected(Reason.WRONG_APP, doc(app = "other"))
        assertRejected(Reason.UNSUPPORTED_VERSION, doc(version = 99))
        assertRejected(Reason.UNSUPPORTED_VERSION, doc(version = 0))
    }

    @Test
    fun duplicateIds_areRejected() {
        assertRejected(Reason.INVALID_DATA, doc(items = listOf(bill(), bill())))
        assertRejected(
            Reason.INVALID_DATA,
            doc(occurrences = listOf(occurrence(), occurrence(date = "2025-02-28"))),
        )
    }

    @Test
    fun duplicateOccurrenceForSameBillAndDate_isRejected() {
        assertRejected(
            Reason.INVALID_DATA,
            doc(occurrences = listOf(occurrence(), occurrence("33333333-3333-3333-3333-333333333333"))),
        )
    }

    @Test
    fun danglingReferences_areRejected() {
        assertRejected(Reason.INVALID_DATA, doc(occurrences = listOf(occurrence().copy(itemId = "99999999-9999-9999-9999-999999999999"))))
        assertRejected(Reason.INVALID_DATA, doc(items = listOf(bill().copy(categoryId = "no-such-category"))))
    }

    @Test
    fun customCategoryReferences_areAccepted_whenDefinedInTheFile() {
        val custom = BackupCategory("custom-1", "Pets", "pets", null, false, 0)
        BackupCodec.validate(doc(items = listOf(bill().copy(categoryId = "custom-1")), categories = listOf(custom)))
    }

    @Test
    fun malformedValues_areRejected() {
        val bad = listOf(
            doc(items = listOf(bill().copy(id = "not-a-uuid"))),
            doc(items = listOf(bill().copy(title = "  "))),
            doc(items = listOf(bill().copy(startDueDate = "31/01/2025"))),
            doc(items = listOf(bill().copy(dueLocalTime = "25:99"))),
            doc(items = listOf(bill().copy(timeZoneId = "Mars/Olympus"))),
            doc(items = listOf(bill().copy(currencyCode = "ZZZZ"))),
            doc(items = listOf(bill().copy(amountMinorUnits = -5))),
            doc(items = listOf(bill().copy(amountMinorUnits = 0))),
            doc(items = listOf(bill().copy(recurrenceRule = "SOMETIMES"))),
            doc(items = listOf(bill().copy(recurrenceRule = "CUSTOM", customInterval = null, customUnit = "DAY"))),
            doc(items = listOf(bill().copy(recurrenceRule = "CUSTOM", customInterval = 0, customUnit = "DAY"))),
            doc(items = listOf(bill().copy(recurrenceRule = "CUSTOM", customInterval = 5, customUnit = "FORTNIGHT"))),
            doc(items = listOf(bill().copy(reminderOffsets = listOf(600_000)))),
            doc(items = listOf(bill().copy(reminderOffsets = listOf(-1)))),
            doc(occurrences = listOf(occurrence().copy(scheduledDueDate = "garbage"))),
            doc(occurrences = listOf(occurrence().copy(status = "DONE"))),
            doc(settings = settings().copy(currencyCode = "??")),
            doc(settings = settings().copy(reminderTime = "noon")),
            doc(settings = settings().copy(snooze = "NEVER")),
            doc(settings = settings().copy(offsetsByKind = mapOf("PARTY" to listOf(0)))),
            doc(items = listOf(bill().copy(kind = "PARTY"))),
            doc(items = listOf(bill().copy(priority = 7))),
            doc(items = listOf(bill().copy(routineTimes = listOf("99:99")))),
            doc(items = listOf(bill().copy(weekdays = listOf("FUNDAY")))),
            doc(settings = settings().copy(theme = "NEON")),
            doc(settings = settings().copy(firstDayOfWeek = "FUNDAY")),
            doc(settings = settings().copy(offsetsByKind = mapOf("BILL" to listOf(999_999)))),
        )
        bad.forEachIndexed { index, document ->
            try {
                BackupCodec.validate(document)
                fail("case $index was accepted")
            } catch (e: BackupException) {
                assertEquals("case $index", Reason.INVALID_DATA, e.reason)
            }
        }
    }

    @Test
    fun paymentDetails_mustMatchStatus() {
        assertRejected(Reason.INVALID_DATA, doc(occurrences = listOf(occurrence(status = "PAID", paidAt = null))))
        assertRejected(Reason.INVALID_DATA, doc(occurrences = listOf(occurrence(status = "PENDING", paidAt = 5L))))
        assertRejected(Reason.INVALID_DATA, doc(occurrences = listOf(occurrence(status = "SKIPPED", paidAt = 5L))))
        BackupCodec.validate(doc(occurrences = listOf(occurrence(status = "PAID", paidAt = 5L))))
        BackupCodec.validate(doc(occurrences = listOf(occurrence(status = "SKIPPED"))))
    }

    @Test
    fun errorMessages_neverContainUserContent() {
        try {
            BackupCodec.validate(doc(items = listOf(bill().copy(title = "Secret Landlord", startDueDate = "bad"))))
            fail()
        } catch (e: BackupException) {
            assertTrue(e.message!!.none { false } && !e.message!!.contains("Secret"))
        }
    }
}
