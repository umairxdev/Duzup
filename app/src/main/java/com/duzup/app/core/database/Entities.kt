package com.duzup.app.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iconKey: String,
    val colorArgb: Long?,
    val builtIn: Boolean,
    val sortOrder: Int,
)

@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey val id: String,
    val title: String,
    val categoryId: String,
    val notes: String?,
    val amountMinorUnits: Long?,
    val currencyCode: String,
    /** ISO-8601 local date; sorts lexicographically. */
    val startDueDate: String,
    val dueLocalTime: String,
    val timeZoneId: String,
    val recurrenceRule: String,
    val customInterval: Int?,
    val customUnit: String?,
    val endDate: String?,
    /** Comma separated minutes before the due moment, e.g. "10080,4320,1440,0". Empty = no reminders. */
    val reminderOffsets: String,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    /** BILL, EVENT, TASK, MEMO, BIRTHDAY or ROUTINE. */
    val kind: String,
    val scheduled: Boolean,
    val endLocalTime: String?,
    val allDay: Boolean,
    val location: String?,
    val priority: Int,
    val isPinned: Boolean,
    val includeAge: Boolean,
    /** Comma separated HH:mm values (routines). */
    val routineTimes: String,
    /** Bit i set = ISO day-of-week i+1 applies (routines); 0 = every day. */
    val weekdaysMask: Int,
)

@Entity(
    tableName = "occurrences",
    foreignKeys = [
        ForeignKey(
            entity = BillEntity::class,
            parentColumns = ["id"],
            childColumns = ["billId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["billId", "scheduledDueDate", "scheduledDueTime"], unique = true),
        Index(value = ["scheduledDueDate"]),
        Index(value = ["status"]),
    ],
)
data class OccurrenceEntity(
    @PrimaryKey val id: String,
    val billId: String,
    val scheduledDueDate: String,
    val scheduledDueTime: String,
    val status: String,
    val paidAt: Long?,
    val actualAmountMinorUnits: Long?,
    val paymentNote: String?,
    val createdAt: Long,
)

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = OccurrenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["occurrenceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["occurrenceId"]), Index(value = ["scheduledAt"])],
)
data class ReminderEntity(
    @PrimaryKey val id: String,
    val occurrenceId: String,
    val kind: String,
    val offsetMinutes: Int,
    @ColumnInfo(name = "scheduledAt") val scheduledAt: Long,
    val deliveredAt: Long?,
    val snoozeUntil: Long?,
    val canceled: Boolean,
)

data class OccurrenceWithBill(
    @Embedded val occurrence: OccurrenceEntity,
    @Relation(parentColumn = "billId", entityColumn = "id") val bill: BillEntity,
)
