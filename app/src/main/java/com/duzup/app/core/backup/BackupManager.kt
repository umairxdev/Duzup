package com.duzup.app.core.backup

import androidx.room.withTransaction
import com.duzup.app.core.database.BillDao
import com.duzup.app.core.database.BillEntity
import com.duzup.app.core.database.CategoryDao
import com.duzup.app.core.database.CategoryEntity
import com.duzup.app.core.database.OccurrenceDao
import com.duzup.app.core.database.OccurrenceEntity
import com.duzup.app.core.database.ReminderDao
import com.duzup.app.core.database.DuzupDatabase
import com.duzup.app.core.database.decodeOffsets
import com.duzup.app.core.database.encodeOffsets
import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.SnoozeChoice
import com.duzup.app.core.model.ThemeMode
import com.duzup.app.core.preferences.PreferencesRepository
import com.duzup.app.core.preferences.UserPreferences
import com.duzup.app.core.time.TimeProvider
import com.duzup.app.data.repository.BillRepository
import com.duzup.app.domain.reminders.ReminderEngine
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

enum class RestoreMode { REPLACE, MERGE }

data class RestoreResult(
    val bills: Int,
    val occurrences: Int,
    val categories: Int,
    val skippedExisting: Int,
)

@Singleton
class BackupManager @Inject constructor(
    private val db: DuzupDatabase,
    private val billDao: BillDao,
    private val occurrenceDao: OccurrenceDao,
    private val reminderDao: ReminderDao,
    private val categoryDao: CategoryDao,
    private val preferences: PreferencesRepository,
    private val repository: BillRepository,
    private val reminders: ReminderEngine,
    private val time: TimeProvider,
) {
    suspend fun export(): String {
        val prefs = preferences.current()
        val document = BackupDocument(
            exportedAt = time.now().toEpochMilli(),
            settings = BackupSettings(
                currencyCode = prefs.currencyCode,
                reminderTime = prefs.reminderTime.toString(),
                offsetsByKind = ItemKind.entries.associate { it.name to prefs.offsetsFor(it) },
                overdueReminders = prefs.overdueReminders,
                quietHoursEnabled = prefs.quietHoursEnabled,
                quietStart = prefs.quietStart.toString(),
                quietEnd = prefs.quietEnd.toString(),
                snooze = prefs.snooze.name,
                theme = prefs.theme.name,
                firstDayOfWeek = prefs.firstDayOfWeek.name,
                haptics = prefs.haptics,
                reduceMotion = prefs.reduceMotion,
                hideAmountsInNotifications = prefs.hideAmountsInNotifications,
            ),
            categories = categoryDao.getAll().map {
                BackupCategory(it.id, it.name, it.iconKey, it.colorArgb, it.builtIn, it.sortOrder)
            },
            items = billDao.getAll().map { b ->
                BackupItem(
                    id = b.id, kind = b.kind, title = b.title, categoryId = b.categoryId, notes = b.notes,
                    amountMinorUnits = b.amountMinorUnits, currencyCode = b.currencyCode,
                    startDueDate = b.startDueDate, dueLocalTime = b.dueLocalTime, timeZoneId = b.timeZoneId,
                    recurrenceRule = b.recurrenceRule, customInterval = b.customInterval, customUnit = b.customUnit,
                    endDate = b.endDate, reminderOffsets = decodeOffsets(b.reminderOffsets),
                    isArchived = b.isArchived, createdAt = b.createdAt, updatedAt = b.updatedAt,
                    scheduled = b.scheduled, endLocalTime = b.endLocalTime, allDay = b.allDay, location = b.location,
                    priority = b.priority, isPinned = b.isPinned, includeAge = b.includeAge,
                    routineTimes = b.routineTimes.split(',').filter { it.isNotBlank() },
                    weekdays = DayOfWeek.entries.filter { b.weekdaysMask and (1 shl (it.value - 1)) != 0 }.map { it.name },
                )
            },
            occurrences = occurrenceDao.getAll().map { o ->
                BackupOccurrence(
                    id = o.id, itemId = o.billId, scheduledDueDate = o.scheduledDueDate,
                    scheduledDueTime = o.scheduledDueTime, status = o.status, paidAt = o.paidAt,
                    actualAmountMinorUnits = o.actualAmountMinorUnits, paymentNote = o.paymentNote,
                    createdAt = o.createdAt,
                )
            },
        )
        return BackupCodec.encode(document)
    }

    /**
     * Validates first (throws [BackupException] before anything is touched), then writes inside ONE
     * Room transaction so a failure can never leave partial data. Alarms are re-planned afterwards.
     *
     * REPLACE: existing items/occurrences/categories/settings are wiped and replaced.
     * MERGE: existing records win on identical ids or on the same item+date+time; only missing records are added,
     *        and settings are left unchanged.
     */
    suspend fun restore(text: String, mode: RestoreMode): RestoreResult {
        val document = BackupCodec.decode(text) // may throw BackupException

        val categories = document.categories.map {
            CategoryEntity(it.id, it.name, it.iconKey, it.colorArgb, it.builtIn, it.sortOrder)
        }
        val items = document.items.map { b ->
            BillEntity(
                id = b.id, title = b.title.trim(), categoryId = b.categoryId, notes = b.notes,
                amountMinorUnits = b.amountMinorUnits, currencyCode = b.currencyCode,
                startDueDate = b.startDueDate, dueLocalTime = b.dueLocalTime, timeZoneId = b.timeZoneId,
                recurrenceRule = b.recurrenceRule, customInterval = b.customInterval, customUnit = b.customUnit,
                endDate = b.endDate, reminderOffsets = encodeOffsets(b.reminderOffsets),
                isArchived = b.isArchived, createdAt = b.createdAt, updatedAt = b.updatedAt,
                kind = b.kind, scheduled = b.scheduled, endLocalTime = b.endLocalTime, allDay = b.allDay,
                location = b.location, priority = b.priority, isPinned = b.isPinned, includeAge = b.includeAge,
                routineTimes = b.routineTimes.joinToString(","),
                weekdaysMask = b.weekdays.map { DayOfWeek.valueOf(it) }.fold(0) { m, d -> m or (1 shl (d.value - 1)) },
            )
        }
        val occurrences = document.occurrences.map { o ->
            OccurrenceEntity(
                id = o.id, billId = o.itemId, scheduledDueDate = o.scheduledDueDate,
                scheduledDueTime = o.scheduledDueTime, status = o.status, paidAt = o.paidAt,
                actualAmountMinorUnits = o.actualAmountMinorUnits, paymentNote = o.paymentNote, createdAt = o.createdAt,
            )
        }

        reminders.cancelAllAlarms()

        var insertedItems = 0
        var insertedOccurrences = 0
        var insertedCategories = 0
        try {
            db.withTransaction {
                if (mode == RestoreMode.REPLACE) {
                    billDao.deleteAll()          // cascades to occurrences and reminder rows
                    reminderDao.deleteAll()
                    categoryDao.deleteAll()
                }
                insertedCategories = categoryDao.insertIgnore(categories).count { it != -1L }
                insertedItems = billDao.insertIgnore(items).count { it != -1L }
                insertedOccurrences = occurrenceDao.insertIgnore(occurrences).count { it != -1L }
            }
        } catch (e: Exception) {
            repository.reconcileAll() // transaction rolled back: re-arm the alarms we cancelled above
            throw e
        }
        repository.ensureDefaultCategories()

        if (mode == RestoreMode.REPLACE) preferences.apply(document.settings.toPreferences())

        repository.reconcileAll() // regenerates horizon occurrences and reschedules future reminders
        return RestoreResult(
            bills = insertedItems,
            occurrences = insertedOccurrences,
            categories = insertedCategories,
            skippedExisting = (items.size - insertedItems) + (occurrences.size - insertedOccurrences),
        )
    }

    private fun BackupSettings.toPreferences(): UserPreferences = UserPreferences.defaults().let { d ->
        d.copy(
            currencyCode = currencyCode,
            reminderTime = LocalTime.parse(reminderTime),
            offsetsByKind = ItemKind.entries.associateWith { kind ->
                offsetsByKind[kind.name] ?: d.offsetsFor(kind)
            },
            overdueReminders = overdueReminders,
            quietHoursEnabled = quietHoursEnabled,
            quietStart = LocalTime.parse(quietStart),
            quietEnd = LocalTime.parse(quietEnd),
            snooze = SnoozeChoice.valueOf(snooze),
            theme = ThemeMode.valueOf(theme),
            firstDayOfWeek = DayOfWeek.valueOf(firstDayOfWeek),
            haptics = haptics,
            reduceMotion = reduceMotion,
            hideAmountsInNotifications = hideAmountsInNotifications,
        )
    }
}
