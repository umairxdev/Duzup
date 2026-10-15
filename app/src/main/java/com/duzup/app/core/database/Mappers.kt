package com.duzup.app.core.database

import com.duzup.app.core.model.Bill
import com.duzup.app.core.model.Category
import com.duzup.app.core.model.IntervalUnit
import com.duzup.app.core.model.ItemKind
import com.duzup.app.core.model.Occurrence
import com.duzup.app.core.model.OccurrenceItem
import com.duzup.app.core.model.OccurrenceStatus
import com.duzup.app.core.model.Recurrence
import com.duzup.app.core.model.RecurrenceRule
import com.duzup.app.core.model.ReminderKind
import com.duzup.app.core.model.ReminderRow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

fun encodeOffsets(offsets: List<Int>): String = offsets.distinct().sortedDescending().joinToString(",")

fun decodeOffsets(raw: String): List<Int> =
    raw.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it >= 0 }.distinct().sortedDescending()

private fun encodeTimes(times: List<LocalTime>): String = times.distinct().sorted().joinToString(",") { it.toString() }

private fun decodeTimes(raw: String): List<LocalTime> =
    raw.split(',').mapNotNull { runCatching { LocalTime.parse(it.trim()) }.getOrNull() }.distinct().sorted()

private fun encodeWeekdays(days: Set<DayOfWeek>): Int = days.fold(0) { mask, d -> mask or (1 shl (d.value - 1)) }

private fun decodeWeekdays(mask: Int): Set<DayOfWeek> =
    DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()

fun BillEntity.toDomain(): Bill = Bill(
    id = id,
    title = title,
    categoryId = categoryId,
    notes = notes,
    amountMinorUnits = amountMinorUnits,
    currencyCode = currencyCode,
    startDueDate = LocalDate.parse(startDueDate),
    dueTime = LocalTime.parse(dueLocalTime),
    zoneId = runCatching { ZoneId.of(timeZoneId) }.getOrDefault(ZoneId.systemDefault()),
    recurrence = Recurrence(
        rule = runCatching { RecurrenceRule.valueOf(recurrenceRule) }.getOrDefault(RecurrenceRule.NONE),
        interval = customInterval ?: 1,
        unit = customUnit?.let { runCatching { IntervalUnit.valueOf(it) }.getOrNull() } ?: IntervalUnit.MONTH,
    ),
    endDate = endDate?.let(LocalDate::parse),
    reminderOffsets = decodeOffsets(reminderOffsets),
    isArchived = isArchived,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    kind = runCatching { ItemKind.valueOf(kind) }.getOrDefault(ItemKind.BILL),
    scheduled = scheduled,
    endTime = endLocalTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
    allDay = allDay,
    location = location,
    priority = priority,
    isPinned = isPinned,
    includeAge = includeAge,
    routineTimes = decodeTimes(routineTimes),
    weekdays = decodeWeekdays(weekdaysMask),
)

fun Bill.toEntity(): BillEntity = BillEntity(
    id = id,
    title = title,
    categoryId = categoryId,
    notes = notes,
    amountMinorUnits = amountMinorUnits,
    currencyCode = currencyCode,
    startDueDate = startDueDate.toString(),
    dueLocalTime = dueTime.toString(),
    timeZoneId = zoneId.id,
    recurrenceRule = recurrence.rule.name,
    customInterval = if (recurrence.rule == RecurrenceRule.CUSTOM) recurrence.interval else null,
    customUnit = if (recurrence.rule == RecurrenceRule.CUSTOM) recurrence.unit.name else null,
    endDate = if (recurrence.isRecurring) endDate?.toString() else null,
    reminderOffsets = encodeOffsets(reminderOffsets),
    isArchived = isArchived,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    kind = kind.name,
    scheduled = scheduled,
    endLocalTime = endTime?.toString(),
    allDay = allDay,
    location = location,
    priority = priority,
    isPinned = isPinned,
    includeAge = includeAge,
    routineTimes = encodeTimes(routineTimes),
    weekdaysMask = encodeWeekdays(weekdays),
)

fun OccurrenceEntity.toDomain(): Occurrence = Occurrence(
    id = id,
    billId = billId,
    dueDate = LocalDate.parse(scheduledDueDate),
    dueTime = LocalTime.parse(scheduledDueTime),
    status = runCatching { OccurrenceStatus.valueOf(status) }.getOrDefault(OccurrenceStatus.PENDING),
    paidAt = paidAt?.let(Instant::ofEpochMilli),
    actualAmountMinorUnits = actualAmountMinorUnits,
    paymentNote = paymentNote,
    createdAt = Instant.ofEpochMilli(createdAt),
)

fun Occurrence.toEntity(): OccurrenceEntity = OccurrenceEntity(
    id = id,
    billId = billId,
    scheduledDueDate = dueDate.toString(),
    scheduledDueTime = dueTime.toString(),
    status = status.name,
    paidAt = paidAt?.toEpochMilli(),
    actualAmountMinorUnits = actualAmountMinorUnits,
    paymentNote = paymentNote,
    createdAt = createdAt.toEpochMilli(),
)

fun OccurrenceWithBill.toItem(): OccurrenceItem = OccurrenceItem(occurrence.toDomain(), bill.toDomain())

fun CategoryEntity.toDomain(): Category = Category(id, name, iconKey, colorArgb, builtIn, sortOrder)

fun Category.toEntity(): CategoryEntity = CategoryEntity(id, name, iconKey, colorArgb, builtIn, sortOrder)

fun ReminderEntity.toDomain(): ReminderRow = ReminderRow(
    id = id,
    occurrenceId = occurrenceId,
    kind = runCatching { ReminderKind.valueOf(kind) }.getOrDefault(ReminderKind.UPCOMING),
    offsetMinutes = offsetMinutes,
    scheduledAt = Instant.ofEpochMilli(scheduledAt),
    deliveredAt = deliveredAt?.let(Instant::ofEpochMilli),
    snoozeUntil = snoozeUntil?.let(Instant::ofEpochMilli),
    canceled = canceled,
)

fun ReminderRow.toEntity(): ReminderEntity = ReminderEntity(
    id = id,
    occurrenceId = occurrenceId,
    kind = kind.name,
    offsetMinutes = offsetMinutes,
    scheduledAt = scheduledAt.toEpochMilli(),
    deliveredAt = deliveredAt?.toEpochMilli(),
    snoozeUntil = snoozeUntil?.toEpochMilli(),
    canceled = canceled,
)
