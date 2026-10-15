package com.duzup.app.domain.reminders

import com.duzup.app.core.database.BillDao
import com.duzup.app.core.database.BillEntity
import com.duzup.app.core.database.OccurrenceDao
import com.duzup.app.core.database.OccurrenceEntity
import com.duzup.app.core.database.ReminderDao
import com.duzup.app.core.database.ReminderEntity
import com.duzup.app.core.database.decodeOffsets
import com.duzup.app.core.database.toDomain
import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.OccurrenceStatus
import com.duzup.app.core.model.ReminderKind
import com.duzup.app.core.model.SnoozeChoice
import com.duzup.app.core.preferences.PreferencesRepository
import com.duzup.app.core.preferences.UserPreferences
import com.duzup.app.core.time.TimeProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the ReminderDelivery table and the alarms behind it.
 *
 * Everything is idempotent: [reconcile] can run on launch, boot, time change, permission change and
 * from WorkManager any number of times without producing duplicate alarms or notifications, because
 * reminder identity is stable (`occurrenceId|offset`) and delivery is claimed atomically in SQL.
 *
 * Quiet hours: a reminder that would fire inside the user's quiet window is held until it ends, unless that would
 * push it past the moment it is about (an event starting during quiet hours still reminds you on time).
 */
@Singleton
class ReminderEngine @Inject constructor(
    private val billDao: BillDao,
    private val occurrenceDao: OccurrenceDao,
    private val reminderDao: ReminderDao,
    private val preferences: PreferencesRepository,
    private val scheduler: AlarmScheduler,
    private val notifier: ReminderNotifier,
    private val time: TimeProvider,
) {
    private val mutex = Mutex()

    /** What quiet hours need to know about one occurrence. */
    private class DueInfo(val zone: ZoneId, val dueInstant: Instant)

    suspend fun reconcile() = mutex.withLock { reconcileLocked() }

    private fun zoneOf(bill: BillEntity): ZoneId = runCatching { ZoneId.of(bill.timeZoneId) }.getOrDefault(time.zone())

    private fun dueInfoOf(occ: OccurrenceEntity, bill: BillEntity): DueInfo {
        val zone = zoneOf(bill)
        return DueInfo(zone, ReminderPlanner.instantOf(LocalDate.parse(occ.scheduledDueDate), LocalTime.parse(occ.scheduledDueTime), zone))
    }

    /** Applies quiet hours to an alarm time. Post-due reminders (overdue, snooze) may be held without limit. */
    private fun quietAdjusted(atMs: Long, kind: String, info: DueInfo, prefs: UserPreferences): Long {
        val preDue = kind == ReminderKind.UPCOMING.name || kind == ReminderKind.DUE.name
        return QuietHours.nextAllowed(
            trigger = Instant.ofEpochMilli(atMs),
            zone = info.zone,
            enabled = prefs.quietHoursEnabled,
            start = prefs.quietStart,
            end = prefs.quietEnd,
            notLaterThan = if (preDue) info.dueInstant else null,
        ).toEpochMilli()
    }

    private suspend fun reconcileLocked() {
        val now = time.now()
        val nowMs = now.toEpochMilli()
        val windowEndMs = now.plus(Duration.ofDays(ALARM_WINDOW_DAYS)).toEpochMilli()
        val prefs = preferences.current()

        val bills = billDao.getAll().associateBy { it.id }
        val pending = occurrenceDao.getAllPending()
        val existing = reminderDao.getAll().associateBy { it.id }

        val desired = HashMap<String, PlannedReminder>()
        val validOccurrenceIds = HashSet<String>()
        val dueInfo = HashMap<String, DueInfo>()
        for (occ in pending) {
            val bill = bills[occ.billId] ?: continue
            if (bill.isArchived || !bill.scheduled) continue
            validOccurrenceIds += occ.id
            val info = dueInfoOf(occ, bill)
            dueInfo[occ.id] = info
            val kind = runCatching { ItemKind.valueOf(bill.kind) }.getOrDefault(ItemKind.BILL)
            ReminderPlanner.plan(
                occurrenceId = occ.id,
                dueDate = LocalDate.parse(occ.scheduledDueDate),
                dueTime = LocalTime.parse(occ.scheduledDueTime),
                zone = info.zone,
                offsets = decodeOffsets(bill.reminderOffsets),
                overdueReminder = prefs.overdueReminders && kind.canBeOverdue,
            ).forEach { desired[it.id] = it }
        }

        // Only the nearest reminders get alarms: AlarmManager caps concurrent alarms per app.
        val allowed = desired.values
            .filter { it.triggerAt.toEpochMilli() <= windowEndMs }
            .sortedBy { it.triggerAt }
            .take(MAX_ALARMS)
            .associateBy { it.id }

        val upserts = ArrayList<ReminderEntity>()
        val toSchedule = ArrayList<Pair<String, Long>>()
        val toCancel = ArrayList<String>()
        val canceledOccurrences = HashSet<String>()

        fun cancelRow(row: ReminderEntity) {
            if (row.canceled) return // already canceled earlier; its alarm is gone
            upserts += row.copy(canceled = true)
            canceledOccurrences += row.occurrenceId
            toCancel += row.id
        }

        fun arm(row: ReminderEntity, atMs: Long) {
            val info = dueInfo[row.occurrenceId]
            toSchedule += row.id to (if (info != null) quietAdjusted(atMs, row.kind, info, prefs) else atMs)
        }

        for (row in existing.values) {
            if (row.deliveredAt != null) continue // history: never touched, never re-sent

            if (row.occurrenceId !in validOccurrenceIds) {
                cancelRow(row)
                continue
            }

            if (row.kind == ReminderKind.SNOOZE.name) {
                when {
                    row.canceled -> Unit
                    nowMs - row.scheduledAt > ReminderPlanner.MISSED_GRACE.toMillis() -> cancelRow(row)
                    else -> arm(row, maxOf(row.scheduledAt, nowMs + FIRE_SOON_MS))
                }
                continue
            }

            val planned = allowed[row.id]
            if (planned == null) {
                cancelRow(row) // offset removed, overdue reminders disabled, or outside the alarm window
                continue
            }
            val trigger = planned.triggerAt.toEpochMilli()
            when {
                trigger > nowMs -> {
                    val updated = if (row.canceled || row.scheduledAt != trigger) {
                        row.copy(scheduledAt = trigger, canceled = false).also { upserts += it }
                    } else row
                    arm(updated, trigger)
                }
                !row.canceled && nowMs - trigger <= ReminderPlanner.MISSED_GRACE.toMillis() -> {
                    // Missed very recently (e.g. phone was off): deliver once, shortly.
                    val updated = if (row.scheduledAt != trigger) row.copy(scheduledAt = trigger).also { upserts += it } else row
                    arm(updated, nowMs + FIRE_SOON_MS)
                }
                !row.canceled -> cancelRow(row) // too stale to be useful
            }
        }

        for (planned in allowed.values) {
            if (planned.id in existing) continue
            val trigger = planned.triggerAt.toEpochMilli()
            if (trigger <= nowMs) continue // never create reminders in the past
            val row = ReminderEntity(
                id = planned.id,
                occurrenceId = planned.occurrenceId,
                kind = planned.kind.name,
                offsetMinutes = planned.offsetMinutes,
                scheduledAt = trigger,
                deliveredAt = null,
                snoozeUntil = null,
                canceled = false,
            )
            upserts += row
            arm(row, trigger)
        }

        if (upserts.isNotEmpty()) reminderDao.upsertAll(upserts)
        toCancel.forEach(scheduler::cancel)
        toSchedule.forEach { (id, at) -> scheduler.schedule(id, at) }
        canceledOccurrences.forEach(notifier::cancel)
    }

    /** Cancels remaining reminders and any visible notification for one occurrence (done / skipped). */
    suspend fun cancelForOccurrence(occurrenceId: String) = mutex.withLock {
        reminderDao.getForOccurrence(occurrenceId).forEach { scheduler.cancel(it.id) }
        reminderDao.cancelForOccurrence(occurrenceId)
        notifier.cancel(occurrenceId)
    }

    /** Cancels alarms (not rows) for occurrences about to be deleted; rows go with them via cascade. */
    suspend fun cancelAlarmsForOccurrences(occurrenceIds: Collection<String>) = mutex.withLock {
        occurrenceIds.forEach { occurrenceId ->
            reminderDao.getForOccurrence(occurrenceId).forEach { scheduler.cancel(it.id) }
            notifier.cancel(occurrenceId)
        }
    }

    suspend fun cancelAllAlarms() = mutex.withLock {
        reminderDao.getAll().forEach { scheduler.cancel(it.id) }
        notifier.cancelAll()
    }

    /** "Remind me later" after a fixed number of minutes; the occurrence's due date is never modified. */
    suspend fun snooze(occurrenceId: String, minutes: Long = DEFAULT_SNOOZE_MINUTES): Boolean =
        snoozeAt(occurrenceId) { _, _ -> time.now().plus(Duration.ofMinutes(minutes)) }

    /** "Remind me later" using a user-facing choice (defaults to the snooze setting). */
    suspend fun snoozeWith(occurrenceId: String, choice: SnoozeChoice? = null): Boolean {
        val prefs = preferences.current()
        val picked = choice ?: prefs.snooze
        return snoozeAt(occurrenceId) { zone, _ ->
            if (picked == SnoozeChoice.TOMORROW) {
                ReminderPlanner.instantOf(time.now().atZone(zone).toLocalDate().plusDays(1), prefs.reminderTime, zone)
            } else {
                time.now().plus(Duration.ofMinutes(picked.minutes))
            }
        }
    }

    private suspend fun snoozeAt(occurrenceId: String, whenFor: (ZoneId, BillEntity) -> Instant): Boolean = mutex.withLock {
        val occurrence = occurrenceDao.getById(occurrenceId) ?: return@withLock false
        if (occurrence.status != OccurrenceStatus.PENDING.name) return@withLock false
        val bill = billDao.getById(occurrence.billId) ?: return@withLock false
        if (bill.isArchived) return@withLock false

        val at = whenFor(zoneOf(bill), bill).toEpochMilli()
        val row = ReminderEntity(
            id = ReminderPlanner.idFor(occurrenceId, ReminderKind.SNOOZE, 0),
            occurrenceId = occurrenceId,
            kind = ReminderKind.SNOOZE.name,
            offsetMinutes = 0,
            scheduledAt = at,
            deliveredAt = null,
            snoozeUntil = at,
            canceled = false,
        )
        reminderDao.upsert(row)
        val prefs = preferences.current()
        scheduler.schedule(row.id, quietAdjusted(at, row.kind, dueInfoOf(occurrence, bill), prefs))
        notifier.cancel(occurrenceId)
        true
    }

    /**
     * Called by the alarm receiver. Returns true if a notification was shown.
     * The SQL claim in [ReminderDao.markDelivered] guarantees at-most-once delivery per reminder.
     */
    suspend fun deliver(reminderId: String): Boolean = mutex.withLock {
        val row = reminderDao.getById(reminderId) ?: return@withLock false
        if (row.canceled || row.deliveredAt != null) return@withLock false

        val occurrence = occurrenceDao.getById(row.occurrenceId)
        val bill = occurrence?.let { billDao.getById(it.billId) }
        if (occurrence == null || bill == null || bill.isArchived || occurrence.status != OccurrenceStatus.PENDING.name) {
            reminderDao.upsert(row.copy(canceled = true))
            return@withLock false
        }

        // Defensive quiet-hours check: if the alarm fired early (inexact alarms, clock changes), hold it.
        val prefs = preferences.current()
        val nowMs = time.now().toEpochMilli()
        val allowedAt = quietAdjusted(nowMs, row.kind, dueInfoOf(occurrence, bill), prefs)
        if (allowedAt > nowMs) {
            scheduler.schedule(row.id, allowedAt)
            return@withLock false
        }

        if (reminderDao.markDelivered(reminderId, nowMs) != 1) return@withLock false

        notifier.show(occurrence.toDomain(), bill.toDomain(), row.toDomain(), prefs.hideAmountsInNotifications)
        true
    }

    companion object {
        const val ALARM_WINDOW_DAYS = 60L
        const val MAX_ALARMS = 400
        const val DEFAULT_SNOOZE_MINUTES = 60L
        private const val FIRE_SOON_MS = 2_000L
    }
}
