package com.duzup.app.integration

import com.duzup.app.core.model.ReminderOffsets
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.duzup.app.core.backup.BackupManager
import com.duzup.app.core.database.DuzupDatabase
import com.duzup.app.core.model.Bill
import com.duzup.app.core.model.Occurrence
import com.duzup.app.core.model.OccurrenceStatus
import com.duzup.app.core.model.Recurrence
import com.duzup.app.core.model.Bill as DomainBill
import com.duzup.app.core.model.ReminderRow
import com.duzup.app.core.preferences.PreferencesRepository
import com.duzup.app.core.time.TimeProvider
import com.duzup.app.core.database.toDomain
import com.duzup.app.data.repository.BillRepository
import com.duzup.app.domain.reminders.AlarmScheduler
import com.duzup.app.domain.reminders.ReminderEngine
import com.duzup.app.domain.reminders.ReminderNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class FakeTime(var instant: Instant, var zoneId: ZoneId = ZoneOffset.UTC) : TimeProvider {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zoneId
}

/** Records alarms the way AlarmManager would hold them: scheduling the same id replaces the earlier alarm. */
class FakeAlarmScheduler : AlarmScheduler {
    val active = linkedMapOf<String, Long>()
    val cancelCalls = mutableListOf<String>()
    var exact = true

    override fun schedule(reminderId: String, triggerAtMillis: Long) {
        active[reminderId] = triggerAtMillis
    }

    override fun cancel(reminderId: String) {
        active.remove(reminderId)
        cancelCalls += reminderId
    }

    override fun canScheduleExact(): Boolean = exact

    /** Simulates a reboot: all alarms are lost until something re-plans them. */
    fun wipe() = active.clear()
}

class FakeNotifier : ReminderNotifier {
    data class Shown(val occurrenceId: String, val billTitle: String, val reminder: ReminderRow, val hideAmount: Boolean)

    val shown = mutableListOf<Shown>()
    val canceled = mutableListOf<String>()
    var cancelAllCalls = 0

    override fun show(occurrence: Occurrence, bill: Bill, reminder: ReminderRow, hideAmount: Boolean) {
        shown += Shown(occurrence.id, bill.title, reminder, hideAmount)
    }

    override fun cancel(occurrenceId: String) {
        canceled += occurrenceId
    }

    override fun cancelAll() {
        cancelAllCalls++
    }
}

/** Real Room (in memory) + real engine/repository/backup, with the clock, alarms and notifications faked. */
class TestEnv(start: String = "2025-01-10T08:00:00Z") {
    val context: Context = ApplicationProvider.getApplicationContext()
    val db: DuzupDatabase = Room.inMemoryDatabaseBuilder(context, DuzupDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val time = FakeTime(Instant.parse(start))
    val scheduler = FakeAlarmScheduler()
    val notifier = FakeNotifier()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefsDir: File = Files.createTempDirectory("duzup-prefs").toFile()
    val prefs = PreferencesRepository(
        PreferenceDataStoreFactory.create(scope = scope) { File(prefsDir, "prefs.preferences_pb") },
    )

    val engine = ReminderEngine(db.billDao(), db.occurrenceDao(), db.reminderDao(), prefs, scheduler, notifier, time)
    val repo = BillRepository(
        db, db.billDao(), db.occurrenceDao(), db.reminderDao(), db.categoryDao(), engine, prefs, time, scope,
    )
    val backup = BackupManager(
        db, db.billDao(), db.occurrenceDao(), db.reminderDao(), db.categoryDao(), prefs, repo, engine, time,
    )

    fun close() {
        db.close()
        scope.cancel()
        prefsDir.deleteRecursively()
    }

    fun bill(
        title: String = "Electricity",
        start: LocalDate = LocalDate.of(2025, 1, 31),
        recurrence: Recurrence = Recurrence.None,
        offsets: List<Int> = ReminderOffsets.days(7, 3, 1, 0),
        amount: Long? = 850_000,
        currency: String = "PKR",
        time: LocalTime = LocalTime.of(9, 0),
        id: String = repo.newId(),
    ): DomainBill = DomainBill(
        id = id, title = title, categoryId = "utilities", notes = null, amountMinorUnits = amount,
        currencyCode = currency, startDueDate = start, dueTime = time, zoneId = ZoneOffset.UTC,
        recurrence = recurrence, endDate = null, reminderOffsets = offsets, isArchived = false,
        createdAt = this.time.now(), updatedAt = this.time.now(),
    )

    suspend fun occurrences(billId: String): List<Occurrence> =
        db.occurrenceDao().getForBill(billId).map { it.toDomain() }.sortedBy { it.dueDate }

    suspend fun occurrenceOn(billId: String, date: LocalDate): Occurrence =
        occurrences(billId).first { it.dueDate == date }

    fun alarmMillis(date: String, time: String = "09:00"): Long = Instant.parse("${date}T$time:00Z").toEpochMilli()

    fun status(o: Occurrence): OccurrenceStatus = o.status
}
