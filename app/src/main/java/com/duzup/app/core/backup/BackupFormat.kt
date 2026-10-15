package com.duzup.app.core.backup

import com.duzup.app.core.model.BuiltInCategories
import com.duzup.app.core.model.IntervalUnit
import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.Money
import com.duzup.app.core.model.OccurrenceStatus
import com.duzup.app.core.model.RecurrenceRule
import com.duzup.app.core.model.ReminderOffsets
import com.duzup.app.core.model.SnoozeChoice
import com.duzup.app.core.model.ThemeMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * Duzup backup file, schema version 2. Plain unencrypted JSON:
 *
 * { "app": "duzup", "schemaVersion": 2, "exportedAt": <epoch ms>,
 *   "settings": {...}, "categories": [...], "items": [...], "occurrences": [...] }
 *
 * Money is always integer minor units. Dates are ISO-8601 (yyyy-MM-dd), times HH:mm[:ss], instants are epoch
 * milliseconds, reminder offsets are minutes before the due moment. Reminder rows are derived data and are rebuilt
 * on restore. Version 1 files (the earlier bills-only app, "app": "remzup") are still readable.
 */
@Serializable
data class BackupDocument(
    val app: String = APP_ID,
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAt: Long,
    val settings: BackupSettings,
    val categories: List<BackupCategory>,
    val items: List<BackupItem>,
    val occurrences: List<BackupOccurrence>,
) {
    companion object {
        const val APP_ID = "duzup"
        const val LEGACY_APP_ID = "remzup"
        const val SCHEMA_VERSION = 2
    }
}

@Serializable
data class BackupSettings(
    val currencyCode: String,
    val reminderTime: String,
    /** Item kind name to default reminder offsets (minutes). */
    val offsetsByKind: Map<String, List<Int>>,
    val overdueReminders: Boolean,
    val quietHoursEnabled: Boolean,
    val quietStart: String,
    val quietEnd: String,
    val snooze: String,
    val theme: String,
    val firstDayOfWeek: String,
    val haptics: Boolean,
    val reduceMotion: Boolean,
    val hideAmountsInNotifications: Boolean,
)

@Serializable
data class BackupCategory(
    val id: String,
    val name: String,
    val iconKey: String,
    val colorArgb: Long? = null,
    val builtIn: Boolean,
    val sortOrder: Int,
)

@Serializable
data class BackupItem(
    val id: String,
    val kind: String = ItemKind.BILL.name,
    val title: String,
    val categoryId: String,
    val notes: String? = null,
    val amountMinorUnits: Long? = null,
    val currencyCode: String,
    val startDueDate: String,
    val dueLocalTime: String,
    val timeZoneId: String,
    val recurrenceRule: String,
    val customInterval: Int? = null,
    val customUnit: String? = null,
    val endDate: String? = null,
    val reminderOffsets: List<Int>,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val scheduled: Boolean = true,
    val endLocalTime: String? = null,
    val allDay: Boolean = false,
    val location: String? = null,
    val priority: Int = 1,
    val isPinned: Boolean = false,
    val includeAge: Boolean = true,
    val routineTimes: List<String> = emptyList(),
    val weekdays: List<String> = emptyList(),
)

@Serializable
data class BackupOccurrence(
    val id: String,
    val itemId: String,
    val scheduledDueDate: String,
    val scheduledDueTime: String,
    val status: String,
    val paidAt: Long? = null,
    val actualAmountMinorUnits: Long? = null,
    val paymentNote: String? = null,
    val createdAt: Long,
)

// ---- Version 1 (bills only), read-only -----------------------------------------------------------------

@Serializable
private data class LegacySettings(
    val currencyCode: String,
    val reminderTime: String,
    val defaultReminderOffsets: List<Int>,
    val overdueReminders: Boolean,
    val theme: String,
    val firstDayOfWeek: String,
    val haptics: Boolean,
    val reduceMotion: Boolean,
    val hideAmountsInNotifications: Boolean,
)

@Serializable
private data class LegacyBill(
    val id: String,
    val title: String,
    val categoryId: String,
    val notes: String? = null,
    val amountMinorUnits: Long? = null,
    val currencyCode: String,
    val startDueDate: String,
    val dueLocalTime: String,
    val timeZoneId: String,
    val recurrenceRule: String,
    val customInterval: Int? = null,
    val customUnit: String? = null,
    val endDate: String? = null,
    val reminderOffsets: List<Int>,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
private data class LegacyOccurrence(
    val id: String,
    val billId: String,
    val scheduledDueDate: String,
    val scheduledDueTime: String,
    val status: String,
    val paidAt: Long? = null,
    val actualAmountMinorUnits: Long? = null,
    val paymentNote: String? = null,
    val createdAt: Long,
)

@Serializable
private data class LegacyDocument(
    val app: String,
    val schemaVersion: Int,
    val exportedAt: Long,
    val settings: LegacySettings,
    val categories: List<BackupCategory>,
    val bills: List<LegacyBill>,
    val occurrences: List<LegacyOccurrence>,
)

/** Thrown for any unreadable or inconsistent backup. Messages never include user content. */
class BackupException(val reason: Reason, message: String) : Exception(message) {
    enum class Reason { NOT_JSON, WRONG_APP, UNSUPPORTED_VERSION, INVALID_DATA, TOO_LARGE }
}

object BackupCodec {
    const val MAX_BYTES = 25L * 1024 * 1024
    const val MAX_ITEMS = 20_000
    const val MAX_OCCURRENCES = 200_000
    const val MAX_CATEGORIES = 500

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun encode(document: BackupDocument): String = json.encodeToString(BackupDocument.serializer(), document)

    /** Parses and fully validates; returns a document that is safe to write to the database. */
    fun decode(text: String): BackupDocument {
        val root: JsonObject = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw BackupException(BackupException.Reason.NOT_JSON, "File is not a valid Duzup backup")
        }
        val app = root["app"]?.jsonPrimitive?.contentOrNull
        val version = root["schemaVersion"]?.jsonPrimitive?.intOrNull
        if (app == null || version == null) {
            throw BackupException(BackupException.Reason.NOT_JSON, "File is not a valid Duzup backup")
        }
        if (app != BackupDocument.APP_ID && app != BackupDocument.LEGACY_APP_ID) {
            throw BackupException(BackupException.Reason.WRONG_APP, "Not a Duzup backup")
        }
        val document = try {
            when (version) {
                1 -> migrateLegacy(json.decodeFromString(LegacyDocument.serializer(), text))
                BackupDocument.SCHEMA_VERSION -> json.decodeFromString(BackupDocument.serializer(), text)
                else -> throw BackupException(BackupException.Reason.UNSUPPORTED_VERSION, "Unsupported backup version $version")
            }
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException(BackupException.Reason.NOT_JSON, "File is not a valid Duzup backup")
        }
        validate(document)
        return document
    }

    private fun migrateLegacy(old: LegacyDocument): BackupDocument {
        val offsets = ItemKind.entries.associate { it.name to ReminderOffsets.defaultsFor(it) }.toMutableMap()
        offsets[ItemKind.BILL.name] = old.settings.defaultReminderOffsets.map { it * ReminderOffsets.MINUTES_PER_DAY }
        return BackupDocument(
            app = BackupDocument.APP_ID,
            schemaVersion = BackupDocument.SCHEMA_VERSION,
            exportedAt = old.exportedAt,
            settings = BackupSettings(
                currencyCode = old.settings.currencyCode,
                reminderTime = old.settings.reminderTime,
                offsetsByKind = offsets,
                overdueReminders = old.settings.overdueReminders,
                quietHoursEnabled = false,
                quietStart = "22:00",
                quietEnd = "08:00",
                snooze = SnoozeChoice.HOUR_1.name,
                theme = old.settings.theme,
                firstDayOfWeek = old.settings.firstDayOfWeek,
                haptics = old.settings.haptics,
                reduceMotion = old.settings.reduceMotion,
                hideAmountsInNotifications = old.settings.hideAmountsInNotifications,
            ),
            categories = old.categories,
            items = old.bills.map { b ->
                BackupItem(
                    id = b.id, kind = ItemKind.BILL.name, title = b.title, categoryId = b.categoryId, notes = b.notes,
                    amountMinorUnits = b.amountMinorUnits, currencyCode = b.currencyCode, startDueDate = b.startDueDate,
                    dueLocalTime = b.dueLocalTime, timeZoneId = b.timeZoneId, recurrenceRule = b.recurrenceRule,
                    customInterval = b.customInterval, customUnit = b.customUnit, endDate = b.endDate,
                    reminderOffsets = b.reminderOffsets.map { it * ReminderOffsets.MINUTES_PER_DAY },
                    isArchived = b.isArchived, createdAt = b.createdAt, updatedAt = b.updatedAt,
                )
            },
            occurrences = old.occurrences.map { o ->
                BackupOccurrence(
                    id = o.id, itemId = o.billId, scheduledDueDate = o.scheduledDueDate, scheduledDueTime = o.scheduledDueTime,
                    status = o.status, paidAt = o.paidAt, actualAmountMinorUnits = o.actualAmountMinorUnits,
                    paymentNote = o.paymentNote, createdAt = o.createdAt,
                )
            },
        )
    }

    fun validate(doc: BackupDocument) {
        fun fail(message: String): Nothing = throw BackupException(BackupException.Reason.INVALID_DATA, message)

        if (doc.app != BackupDocument.APP_ID && doc.app != BackupDocument.LEGACY_APP_ID) {
            throw BackupException(BackupException.Reason.WRONG_APP, "Not a Duzup backup")
        }
        if (doc.schemaVersion < 1 || doc.schemaVersion > BackupDocument.SCHEMA_VERSION) {
            throw BackupException(BackupException.Reason.UNSUPPORTED_VERSION, "Unsupported backup version ${doc.schemaVersion}")
        }
        if (doc.items.size > MAX_ITEMS || doc.occurrences.size > MAX_OCCURRENCES || doc.categories.size > MAX_CATEGORIES) {
            throw BackupException(BackupException.Reason.TOO_LARGE, "Backup is too large")
        }

        // Settings
        val s = doc.settings
        if (!Money.isValidCode(s.currencyCode)) fail("Invalid default currency")
        if (!isTime(s.reminderTime) || !isTime(s.quietStart) || !isTime(s.quietEnd)) fail("Invalid time setting")
        for ((kind, offsets) in s.offsetsByKind) {
            if (enumOrNull<ItemKind>(kind) == null) fail("Invalid default reminder kind")
            if (offsets.any { it !in 0..ReminderOffsets.MAX_MINUTES }) fail("Invalid default reminder offsets")
        }
        if (enumOrNull<SnoozeChoice>(s.snooze) == null) fail("Invalid snooze setting")
        if (enumOrNull<ThemeMode>(s.theme) == null) fail("Invalid theme")
        if (enumOrNull<DayOfWeek>(s.firstDayOfWeek) == null) fail("Invalid first day of week")

        // Categories
        val categoryIds = HashSet<String>()
        for (c in doc.categories) {
            if (c.id.isBlank() || c.name.isBlank() || c.iconKey.isBlank()) fail("Invalid category")
            if (!categoryIds.add(c.id)) fail("Duplicate category id")
        }
        val knownCategoryIds = categoryIds + BuiltInCategories.ids

        // Items
        val itemIds = HashSet<String>()
        for (b in doc.items) {
            if (!isUuid(b.id)) fail("Invalid item id")
            if (!itemIds.add(b.id)) fail("Duplicate item id")
            if (enumOrNull<ItemKind>(b.kind) == null) fail("Invalid item kind")
            if (b.title.isBlank()) fail("Item without a title")
            if (b.categoryId !in knownCategoryIds) fail("Item references an unknown category")
            if (b.amountMinorUnits != null && b.amountMinorUnits <= 0) fail("Invalid amount")
            if (!Money.isValidCode(b.currencyCode)) fail("Invalid currency")
            if (!isDate(b.startDueDate) || (b.endDate != null && !isDate(b.endDate))) fail("Invalid date")
            if (!isTime(b.dueLocalTime) || (b.endLocalTime != null && !isTime(b.endLocalTime))) fail("Invalid time")
            if (runCatching { ZoneId.of(b.timeZoneId) }.isFailure) fail("Invalid time zone")
            val rule = enumOrNull<RecurrenceRule>(b.recurrenceRule) ?: fail("Invalid recurrence")
            if (rule == RecurrenceRule.CUSTOM) {
                if (b.customInterval == null || b.customInterval !in 1..999) fail("Invalid custom interval")
                if (b.customUnit == null || enumOrNull<IntervalUnit>(b.customUnit) == null) fail("Invalid custom unit")
            }
            if (b.reminderOffsets.any { it !in 0..ReminderOffsets.MAX_MINUTES }) fail("Invalid reminder offsets")
            if (b.priority !in 0..2) fail("Invalid priority")
            if (b.routineTimes.any { !isTime(it) }) fail("Invalid routine time")
            if (b.weekdays.any { enumOrNull<DayOfWeek>(it) == null }) fail("Invalid weekday")
        }

        // Occurrences
        val occurrenceIds = HashSet<String>()
        val scheduleKeys = HashSet<Triple<String, String, String>>()
        for (o in doc.occurrences) {
            if (!isUuid(o.id)) fail("Invalid occurrence id")
            if (!occurrenceIds.add(o.id)) fail("Duplicate occurrence id")
            if (o.itemId !in itemIds) fail("Occurrence references an unknown item")
            if (!isDate(o.scheduledDueDate) || !isTime(o.scheduledDueTime)) fail("Invalid occurrence date")
            if (!scheduleKeys.add(Triple(o.itemId, o.scheduledDueDate, normalizedTime(o.scheduledDueTime)))) {
                fail("Duplicate occurrence for an item, date and time")
            }
            val status = enumOrNull<OccurrenceStatus>(o.status) ?: fail("Invalid occurrence status")
            if (status == OccurrenceStatus.PAID && o.paidAt == null) fail("Done occurrence without a completion date")
            if (status != OccurrenceStatus.PAID && (o.paidAt != null || o.actualAmountMinorUnits != null)) {
                fail("Completion details on an unfinished occurrence")
            }
            if (o.actualAmountMinorUnits != null && o.actualAmountMinorUnits <= 0) fail("Invalid payment amount")
        }
    }

    private fun normalizedTime(v: String): String = runCatching { LocalTime.parse(v).toString() }.getOrDefault(v)
    private fun isDate(v: String) = runCatching { LocalDate.parse(v) }.isSuccess
    private fun isTime(v: String) = runCatching { LocalTime.parse(v) }.isSuccess
    private fun isUuid(v: String) = runCatching { UUID.fromString(v) }.isSuccess

    private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? =
        runCatching { enumValueOf<E>(name) }.getOrNull()
}
