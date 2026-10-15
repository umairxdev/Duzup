package com.duzup.app

import com.duzup.app.core.model.Bill
import com.duzup.app.core.model.Occurrence
import com.duzup.app.core.model.OccurrenceItem
import com.duzup.app.core.model.OccurrenceStatus
import com.duzup.app.core.model.Recurrence
import com.duzup.app.core.model.ReminderOffsets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object TestData {
    val UTC: ZoneId = ZoneId.of("UTC")
    val epoch: Instant = Instant.parse("2025-01-01T00:00:00Z")

    fun bill(
        id: String = "bill-1",
        title: String = "Electricity",
        start: LocalDate = LocalDate.of(2025, 1, 31),
        recurrence: Recurrence = Recurrence.None,
        endDate: LocalDate? = null,
        amount: Long? = 850_000,
        currency: String = "PKR",
        time: LocalTime = LocalTime.of(9, 0),
        zone: ZoneId = UTC,
        offsets: List<Int> = ReminderOffsets.days(7, 3, 1, 0),
        categoryId: String = "utilities",
        archived: Boolean = false,
    ) = Bill(
        id = id, title = title, categoryId = categoryId, notes = null,
        amountMinorUnits = amount, currencyCode = currency, startDueDate = start, dueTime = time,
        zoneId = zone, recurrence = recurrence, endDate = endDate, reminderOffsets = offsets,
        isArchived = archived, createdAt = epoch, updatedAt = epoch,
    )

    fun occurrence(
        id: String = "occ-1",
        billId: String = "bill-1",
        date: LocalDate = LocalDate.of(2025, 1, 31),
        time: LocalTime = LocalTime.of(9, 0),
        status: OccurrenceStatus = OccurrenceStatus.PENDING,
        paidAt: Instant? = null,
        actual: Long? = null,
    ) = Occurrence(id, billId, date, time, status, paidAt, actual, null, epoch)

    fun item(bill: Bill = bill(), occ: Occurrence = occurrence(billId = bill.id)) = OccurrenceItem(occ, bill)
}
