package com.duzup.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY builtIn DESC, sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun getAll(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(categories: List<CategoryEntity>): List<Long>

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id AND builtIn = 0")
    suspend fun deleteCustom(id: String)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE id = :id")
    fun observeById(id: String): Flow<BillEntity?>

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun getById(id: String): BillEntity?

    @Query("SELECT * FROM bills")
    suspend fun getAll(): List<BillEntity>

    @Upsert
    suspend fun upsert(bill: BillEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(bills: List<BillEntity>): List<Long>

    @Query("UPDATE bills SET isArchived = :archived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, updatedAt: Long)

    @Query("UPDATE bills SET isPinned = :pinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, updatedAt: Long)

    @Query("UPDATE bills SET categoryId = :toId WHERE categoryId = :fromId")
    suspend fun reassignCategory(fromId: String, toId: String)

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM bills")
    suspend fun deleteAll()
}

@Dao
interface OccurrenceDao {
    @Transaction
    @Query("SELECT * FROM occurrences")
    fun observeAllWithBill(): Flow<List<OccurrenceWithBill>>

    @Query("SELECT * FROM occurrences WHERE billId = :billId ORDER BY scheduledDueDate DESC")
    fun observeForBill(billId: String): Flow<List<OccurrenceEntity>>

    @Query("SELECT * FROM occurrences WHERE id = :id")
    suspend fun getById(id: String): OccurrenceEntity?

    @Query("SELECT * FROM occurrences WHERE billId = :billId")
    suspend fun getForBill(billId: String): List<OccurrenceEntity>

    @Query("SELECT * FROM occurrences WHERE status = 'PENDING'")
    suspend fun getAllPending(): List<OccurrenceEntity>

    @Query("SELECT * FROM occurrences")
    suspend fun getAll(): List<OccurrenceEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(occurrences: List<OccurrenceEntity>): List<Long>

    @Update
    suspend fun update(occurrence: OccurrenceEntity)

    @Query("UPDATE occurrences SET scheduledDueTime = :time WHERE id IN (:ids)")
    suspend fun updateTime(ids: List<String>, time: String)

    @Query("DELETE FROM occurrences WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM occurrences")
    suspend fun deleteAll()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders")
    suspend fun getAll(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE occurrenceId = :occurrenceId")
    suspend fun getForOccurrence(occurrenceId: String): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE canceled = 0 AND deliveredAt IS NULL ORDER BY scheduledAt ASC")
    fun observePending(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE deliveredAt IS NOT NULL ORDER BY deliveredAt DESC LIMIT :limit")
    fun observeRecentlyDelivered(limit: Int): Flow<List<ReminderEntity>>

    @Upsert
    suspend fun upsertAll(reminders: List<ReminderEntity>)

    @Upsert
    suspend fun upsert(reminder: ReminderEntity)

    /** Atomic claim: returns 1 only for the single caller that delivers this reminder. */
    @Query("UPDATE reminders SET deliveredAt = :now WHERE id = :id AND deliveredAt IS NULL AND canceled = 0")
    suspend fun markDelivered(id: String, now: Long): Int

    @Query("UPDATE reminders SET canceled = 1 WHERE occurrenceId = :occurrenceId AND deliveredAt IS NULL")
    suspend fun cancelForOccurrence(occurrenceId: String)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()
}
