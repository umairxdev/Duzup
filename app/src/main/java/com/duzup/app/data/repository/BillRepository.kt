package com.duzup.app.data.repository

import androidx.room.withTransaction
import com.duzup.app.core.database.BillDao
import com.duzup.app.core.database.CategoryDao
import com.duzup.app.core.database.CategoryEntity
import com.duzup.app.core.database.OccurrenceDao
import com.duzup.app.core.database.ReminderDao
import com.duzup.app.core.database.DuzupDatabase
import com.duzup.app.core.database.toDomain
import com.duzup.app.core.database.toEntity
import com.duzup.app.core.database.toItem
import com.duzup.app.core.model.Bill
import com.duzup.app.core.model.BuiltInCategories
import com.duzup.app.core.model.Category
import com.duzup.app.core.model.Occurrence
import com.duzup.app.core.model.OccurrenceItem
import com.duzup.app.core.model.OccurrenceStatus
import com.duzup.app.core.model.ReminderRow
import com.duzup.app.core.model.SnoozeChoice
import com.duzup.app.core.preferences.PreferencesRepository
import com.duzup.app.core.preferences.UserPreferences
import com.duzup.app.core.time.TimeProvider
import com.duzup.app.di.ApplicationScope
import com.duzup.app.domain.payments.PaymentRules
import com.duzup.app.domain.recurrence.OccurrencePlanner
import com.duzup.app.domain.reminders.ReminderEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillRepository @Inject constructor(
    private val db: DuzupDatabase,
    private val billDao: BillDao,
    private val occurrenceDao: OccurrenceDao,
    private val reminderDao: ReminderDao,
    private val categoryDao: CategoryDao,
    private val reminders: ReminderEngine,
    private val preferences: PreferencesRepository,
    private val time: TimeProvider,
    @ApplicationScope scope: CoroutineScope,
) {
    // ---- Observation ---------------------------------------------------------------------------

    val bills: Flow<List<Bill>> = billDao.observeAll().map { list -> list.map { it.toDomain() } }

    val categories: Flow<List<Category>> = categoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Every occurrence joined to its bill. Shared so several screens reuse one Room subscription. */
    val occurrenceItems: Flow<List<OccurrenceItem>> = occurrenceDao.observeAllWithBill()
        .map { list -> list.map { it.toItem() } }
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    fun observeBill(id: String): Flow<Bill?> = billDao.observeById(id).map { it?.toDomain() }

    fun observeOccurrences(billId: String): Flow<List<Occurrence>> =
        occurrenceDao.observeForBill(billId).map { list -> list.map { it.toDomain() } }

    val pendingReminders: Flow<List<ReminderRow>> = reminderDao.observePending().map { l -> l.map { it.toDomain() } }

    fun recentlyDeliveredReminders(limit: Int = 10): Flow<List<ReminderRow>> =
        reminderDao.observeRecentlyDelivered(limit).map { l -> l.map { it.toDomain() } }

    suspend fun getBill(id: String): Bill? = billDao.getById(id)?.toDomain()

    suspend fun getOccurrence(id: String): Occurrence? = occurrenceDao.getById(id)?.toDomain()

    // ---- Bills ---------------------------------------------------------------------------------

    fun newId(): String = UUID.randomUUID().toString()

    /**
     * Inserts or updates a bill template, reconciles its occurrences (history untouched) and
     * re-plans reminders. Safe to call repeatedly.
     */
    suspend fun saveBill(bill: Bill) {
        val stamped = bill.copy(updatedAt = time.now())
        reminders.cancelAlarmsForOccurrences(
            occurrenceDao.getForBill(stamped.id).filter { it.status == OccurrenceStatus.PENDING.name }.map { it.id },
        )
        db.withTransaction {
            billDao.upsert(stamped.toEntity())
            if (!stamped.isArchived) reconcileOccurrences(stamped, OccurrencePlanner.defaultHorizon(time.today()))
        }
        reminders.reconcile()
    }

    suspend fun setArchived(id: String, archived: Boolean) {
        billDao.setArchived(id, archived, time.now().toEpochMilli())
        if (!archived) {
            billDao.getById(id)?.toDomain()?.let { bill ->
                db.withTransaction { reconcileOccurrences(bill, OccurrencePlanner.defaultHorizon(time.today())) }
            }
        }
        reminders.reconcile()
    }

    suspend fun deleteBill(id: String) {
        reminders.cancelAlarmsForOccurrences(occurrenceDao.getForBill(id).map { it.id })
        billDao.delete(id) // occurrences and reminder rows follow through ON DELETE CASCADE
    }

    // ---- Occurrence reconciliation -------------------------------------------------------------

    /** Generates occurrences lazily and idempotently for every active bill, then re-plans reminders. */
    suspend fun reconcileAll(extendThrough: LocalDate? = null) {
        val today = time.today()
        val through = listOfNotNull(OccurrencePlanner.defaultHorizon(today), extendThrough).max()
        db.withTransaction {
            billDao.getAll().map { it.toDomain() }.filter { !it.isArchived }.forEach { reconcileOccurrences(it, through) }
        }
        reminders.reconcile()
    }

    private suspend fun reconcileOccurrences(bill: Bill, through: LocalDate) {
        val existing = occurrenceDao.getForBill(bill.id).map { it.toDomain() }
        val diff = OccurrencePlanner.plan(bill, existing, time.today(), through)
        if (diff.deleteIds.isNotEmpty()) occurrenceDao.deleteByIds(diff.deleteIds)
        if (diff.insertSlots.isNotEmpty()) {
            val now = time.now()
            occurrenceDao.insertIgnore(
                diff.insertSlots.map { slot ->
                    Occurrence(
                        id = newId(),
                        billId = bill.id,
                        dueDate = slot.date,
                        dueTime = slot.time,
                        status = OccurrenceStatus.PENDING,
                        paidAt = null,
                        actualAmountMinorUnits = null,
                        paymentNote = null,
                        createdAt = now,
                    ).toEntity()
                },
            )
        }
    }

    // ---- Payment actions (idempotent) ----------------------------------------------------------

    suspend fun markPaid(
        occurrenceId: String,
        paidAt: Instant = time.now(),
        actualAmountMinorUnits: Long? = null,
        note: String? = null,
    ) {
        db.withTransaction {
            val current = occurrenceDao.getById(occurrenceId)?.toDomain() ?: return@withTransaction
            val updated = PaymentRules.markPaid(current, paidAt, actualAmountMinorUnits, note)
            if (updated !== current) occurrenceDao.update(updated.toEntity())
        }
        reminders.cancelForOccurrence(occurrenceId)
    }

    suspend fun editPayment(occurrenceId: String, paidAt: Instant, actualAmountMinorUnits: Long?, note: String?) {
        db.withTransaction {
            val current = occurrenceDao.getById(occurrenceId)?.toDomain() ?: return@withTransaction
            val updated = PaymentRules.editPayment(current, paidAt, actualAmountMinorUnits, note)
            if (updated !== current) occurrenceDao.update(updated.toEntity())
        }
    }

    suspend fun undoPaid(occurrenceId: String) {
        transition(occurrenceId, PaymentRules::undoPaid)
        reminders.reconcile()
    }

    suspend fun skip(occurrenceId: String) {
        transition(occurrenceId, PaymentRules::skip)
        reminders.cancelForOccurrence(occurrenceId)
    }

    suspend fun restoreSkipped(occurrenceId: String) {
        transition(occurrenceId, PaymentRules::restoreSkipped)
        reminders.reconcile()
    }

    private suspend fun transition(occurrenceId: String, rule: (Occurrence) -> Occurrence) {
        db.withTransaction {
            val current = occurrenceDao.getById(occurrenceId)?.toDomain() ?: return@withTransaction
            val updated = rule(current)
            if (updated !== current) occurrenceDao.update(updated.toEntity())
        }
    }

    suspend fun snooze(occurrenceId: String, minutes: Long = ReminderEngine.DEFAULT_SNOOZE_MINUTES): Boolean =
        reminders.snooze(occurrenceId, minutes)

    /** "Remind me later" using a choice (10 min / 1 h / 3 h / tomorrow morning), defaulting to the snooze setting. */
    suspend fun snoozeWith(occurrenceId: String, choice: SnoozeChoice? = null): Boolean =
        reminders.snoozeWith(occurrenceId, choice)

    suspend fun setPinned(id: String, pinned: Boolean) {
        billDao.setPinned(id, pinned, time.now().toEpochMilli())
    }

    // ---- Categories ----------------------------------------------------------------------------

    /** Seeds built-in categories exactly once; INSERT OR IGNORE makes this safe to repeat. */
    suspend fun ensureDefaultCategories() {
        categoryDao.insertIgnore(
            BuiltInCategories.all.mapIndexed { index, (id, icon) ->
                CategoryEntity(id = id, name = defaultName(id), iconKey = icon, colorArgb = null, builtIn = true, sortOrder = index)
            },
        )
    }

    suspend fun saveCategory(id: String?, name: String, iconKey: String, colorArgb: Long?): String {
        val categoryId = id ?: "custom-${UUID.randomUUID()}"
        val existing = categoryDao.getAll()
        val order = existing.maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
        categoryDao.upsert(
            CategoryEntity(
                id = categoryId,
                name = name.trim(),
                iconKey = iconKey,
                colorArgb = colorArgb,
                builtIn = false,
                sortOrder = existing.firstOrNull { it.id == categoryId }?.sortOrder ?: order,
            ),
        )
        return categoryId
    }

    /** Bills in a deleted custom category move to "Other"; built-in categories cannot be deleted. */
    suspend fun deleteCategory(id: String) {
        if (id in BuiltInCategories.ids) return
        db.withTransaction {
            billDao.reassignCategory(id, BuiltInCategories.OTHER)
            categoryDao.deleteCustom(id)
        }
    }

    // ---- Reset ---------------------------------------------------------------------------------

    /** Wipes bills, occurrences, reminders, custom categories and preferences (keeps onboarding done). */
    suspend fun resetAllData() {
        reminders.cancelAllAlarms()
        db.withTransaction {
            billDao.deleteAll()
            reminderDao.deleteAll()
            categoryDao.deleteAll()
        }
        ensureDefaultCategories()
        preferences.clearAll()
        preferences.setOnboardingCompleted(true)
    }

    suspend fun currentPreferences(): UserPreferences = preferences.current()

    private fun defaultName(id: String): String = id.replaceFirstChar { it.uppercase() }
}
