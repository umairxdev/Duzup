package com.duzup.app.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.Money
import com.duzup.app.core.model.ReminderOffsets
import com.duzup.app.core.model.SnoozeChoice
import com.duzup.app.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.temporal.WeekFields
import java.util.Currency
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class UserPreferences(
    val currencyCode: String,
    /** Reminder time for things that only have a date (bills, birthdays, date-only tasks and notes). */
    val reminderTime: LocalTime,
    /** Default reminder offsets (minutes before) per kind; used to pre-fill new items. */
    val offsetsByKind: Map<ItemKind, List<Int>>,
    val overdueReminders: Boolean,
    val quietHoursEnabled: Boolean,
    val quietStart: LocalTime,
    val quietEnd: LocalTime,
    val snooze: SnoozeChoice,
    val theme: ThemeMode,
    val firstDayOfWeek: DayOfWeek,
    val haptics: Boolean,
    val reduceMotion: Boolean,
    val hideAmountsInNotifications: Boolean,
    val onboardingCompleted: Boolean,
    val notificationPermissionAsked: Boolean,
) {
    fun offsetsFor(kind: ItemKind): List<Int> = offsetsByKind[kind] ?: ReminderOffsets.defaultsFor(kind)

    companion object {
        val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)
        val DEFAULT_QUIET_START: LocalTime = LocalTime.of(22, 0)
        val DEFAULT_QUIET_END: LocalTime = LocalTime.of(8, 0)

        fun defaults(locale: Locale = Locale.getDefault()): UserPreferences {
            val localeCurrency = runCatching { Currency.getInstance(locale).currencyCode }.getOrNull()
            val first = WeekFields.of(locale).firstDayOfWeek
            return UserPreferences(
                currencyCode = localeCurrency?.takeIf { it in Money.supportedCurrencies } ?: "USD",
                reminderTime = DEFAULT_TIME,
                offsetsByKind = ItemKind.entries.associateWith { ReminderOffsets.defaultsFor(it) },
                overdueReminders = true,
                quietHoursEnabled = false,
                quietStart = DEFAULT_QUIET_START,
                quietEnd = DEFAULT_QUIET_END,
                snooze = SnoozeChoice.HOUR_1,
                theme = ThemeMode.SYSTEM,
                firstDayOfWeek = if (first == DayOfWeek.SUNDAY) DayOfWeek.SUNDAY else DayOfWeek.MONDAY,
                haptics = true,
                reduceMotion = false,
                hideAmountsInNotifications = false,
                onboardingCompleted = false,
                notificationPermissionAsked = false,
            )
        }
    }
}

@Singleton
class PreferencesRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) {
    private object Keys {
        val currency = stringPreferencesKey("currency")
        val reminderMinutes = intPreferencesKey("reminder_minutes")
        fun offsets(kind: ItemKind) = stringPreferencesKey("offsets_${kind.name}")
        val overdue = booleanPreferencesKey("overdue_reminders")
        val quietEnabled = booleanPreferencesKey("quiet_enabled")
        val quietStart = intPreferencesKey("quiet_start_minutes")
        val quietEnd = intPreferencesKey("quiet_end_minutes")
        val snooze = stringPreferencesKey("snooze")
        val theme = stringPreferencesKey("theme")
        val firstDay = stringPreferencesKey("first_day")
        val haptics = booleanPreferencesKey("haptics")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val hideAmounts = booleanPreferencesKey("hide_amounts_notifications")
        val onboarding = booleanPreferencesKey("onboarding_completed")
        val permAsked = booleanPreferencesKey("notification_permission_asked")
    }

    val preferences: Flow<UserPreferences> = store.data.map { p ->
        val d = UserPreferences.defaults()
        UserPreferences(
            currencyCode = p[Keys.currency]?.takeIf(Money::isValidCode) ?: d.currencyCode,
            reminderTime = p[Keys.reminderMinutes]?.let(::minutesToTime) ?: d.reminderTime,
            offsetsByKind = ItemKind.entries.associateWith { kind ->
                p[Keys.offsets(kind)]?.let(::parseOffsets) ?: ReminderOffsets.defaultsFor(kind)
            },
            overdueReminders = p[Keys.overdue] ?: d.overdueReminders,
            quietHoursEnabled = p[Keys.quietEnabled] ?: d.quietHoursEnabled,
            quietStart = p[Keys.quietStart]?.let(::minutesToTime) ?: d.quietStart,
            quietEnd = p[Keys.quietEnd]?.let(::minutesToTime) ?: d.quietEnd,
            snooze = p[Keys.snooze]?.let { runCatching { SnoozeChoice.valueOf(it) }.getOrNull() } ?: d.snooze,
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.theme,
            firstDayOfWeek = p[Keys.firstDay]?.let { runCatching { DayOfWeek.valueOf(it) }.getOrNull() } ?: d.firstDayOfWeek,
            haptics = p[Keys.haptics] ?: d.haptics,
            reduceMotion = p[Keys.reduceMotion] ?: d.reduceMotion,
            hideAmountsInNotifications = p[Keys.hideAmounts] ?: d.hideAmountsInNotifications,
            onboardingCompleted = p[Keys.onboarding] ?: d.onboardingCompleted,
            notificationPermissionAsked = p[Keys.permAsked] ?: d.notificationPermissionAsked,
        )
    }

    suspend fun current(): UserPreferences = preferences.first()

    suspend fun setCurrency(code: String) = store.edit { it[Keys.currency] = code }
    suspend fun setReminderTime(time: LocalTime) = store.edit { it[Keys.reminderMinutes] = timeToMinutes(time) }
    suspend fun setOffsets(kind: ItemKind, offsets: List<Int>) =
        store.edit { it[Keys.offsets(kind)] = offsets.distinct().sortedDescending().joinToString(",") }
    suspend fun setOverdueReminders(enabled: Boolean) = store.edit { it[Keys.overdue] = enabled }
    suspend fun setQuietHoursEnabled(enabled: Boolean) = store.edit { it[Keys.quietEnabled] = enabled }
    suspend fun setQuietStart(time: LocalTime) = store.edit { it[Keys.quietStart] = timeToMinutes(time) }
    suspend fun setQuietEnd(time: LocalTime) = store.edit { it[Keys.quietEnd] = timeToMinutes(time) }
    suspend fun setSnooze(choice: SnoozeChoice) = store.edit { it[Keys.snooze] = choice.name }
    suspend fun setTheme(mode: ThemeMode) = store.edit { it[Keys.theme] = mode.name }
    suspend fun setFirstDayOfWeek(day: DayOfWeek) = store.edit { it[Keys.firstDay] = day.name }
    suspend fun setHaptics(enabled: Boolean) = store.edit { it[Keys.haptics] = enabled }
    suspend fun setReduceMotion(enabled: Boolean) = store.edit { it[Keys.reduceMotion] = enabled }
    suspend fun setHideAmounts(enabled: Boolean) = store.edit { it[Keys.hideAmounts] = enabled }
    suspend fun setOnboardingCompleted(done: Boolean) = store.edit { it[Keys.onboarding] = done }
    suspend fun setNotificationPermissionAsked(asked: Boolean) = store.edit { it[Keys.permAsked] = asked }

    /** Restores a full set of preferences (used by backup restore). */
    suspend fun apply(prefs: UserPreferences) {
        setCurrency(prefs.currencyCode)
        setReminderTime(prefs.reminderTime)
        ItemKind.entries.forEach { setOffsets(it, prefs.offsetsFor(it)) }
        setOverdueReminders(prefs.overdueReminders)
        setQuietHoursEnabled(prefs.quietHoursEnabled)
        setQuietStart(prefs.quietStart)
        setQuietEnd(prefs.quietEnd)
        setSnooze(prefs.snooze)
        setTheme(prefs.theme)
        setFirstDayOfWeek(prefs.firstDayOfWeek)
        setHaptics(prefs.haptics)
        setReduceMotion(prefs.reduceMotion)
        setHideAmounts(prefs.hideAmountsInNotifications)
    }

    suspend fun clearAll() = store.edit { it.clear() }

    private fun timeToMinutes(time: LocalTime) = time.hour * 60 + time.minute

    private fun minutesToTime(minutes: Int): LocalTime =
        LocalTime.of((minutes / 60).coerceIn(0, 23), (minutes % 60).coerceIn(0, 59))

    private fun parseOffsets(raw: String): List<Int> =
        raw.split(',').mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 0..ReminderOffsets.MAX_MINUTES }.distinct().sortedDescending()
}
