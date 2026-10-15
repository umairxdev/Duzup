package com.duzup.app.domain

import com.duzup.app.TestData
import com.duzup.app.core.model.DisplayStatus
import com.duzup.app.core.model.OccurrenceStatus
import com.duzup.app.domain.payments.PaymentRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PaymentRulesTest {
    private val paidAt = Instant.parse("2025-02-01T10:00:00Z")

    @Test
    fun markPaid_recordsDetails_andTrimsNote() {
        val paid = PaymentRules.markPaid(TestData.occurrence(), paidAt, 123_00, "  ref 42 ")
        assertEquals(OccurrenceStatus.PAID, paid.status)
        assertEquals(paidAt, paid.paidAt)
        assertEquals(12_300L, paid.actualAmountMinorUnits)
        assertEquals("ref 42", paid.paymentNote)
    }

    @Test
    fun markPaid_isIdempotent_secondCallKeepsOriginalPayment() {
        val first = PaymentRules.markPaid(TestData.occurrence(), paidAt, 100, "first")
        val second = PaymentRules.markPaid(first, paidAt.plusSeconds(500), 999, "second")
        assertSame(first, second)
    }

    @Test
    fun undoPaid_restoresPending_andClearsPayment() {
        val paid = PaymentRules.markPaid(TestData.occurrence(), paidAt, 100, "n")
        val undone = PaymentRules.undoPaid(paid)
        assertEquals(OccurrenceStatus.PENDING, undone.status)
        assertNull(undone.paidAt)
        assertNull(undone.actualAmountMinorUnits)
        assertNull(undone.paymentNote)
    }

    @Test
    fun undoPaid_onPending_isNoOp() {
        val pending = TestData.occurrence()
        assertSame(pending, PaymentRules.undoPaid(pending))
    }

    @Test
    fun skip_andRestore() {
        val skipped = PaymentRules.skip(TestData.occurrence())
        assertEquals(OccurrenceStatus.SKIPPED, skipped.status)
        assertSame(skipped, PaymentRules.skip(skipped))
        assertEquals(OccurrenceStatus.PENDING, PaymentRules.restoreSkipped(skipped).status)
    }

    @Test
    fun skip_doesNotTouchPaidOccurrences() {
        val paid = PaymentRules.markPaid(TestData.occurrence(), paidAt, null, null)
        assertSame(paid, PaymentRules.skip(paid))
    }

    @Test
    fun editPayment_onlyAppliesToPaid() {
        val pending = TestData.occurrence()
        assertSame(pending, PaymentRules.editPayment(pending, paidAt, 5, "x"))
        val paid = PaymentRules.markPaid(pending, paidAt, 5, null)
        val edited = PaymentRules.editPayment(paid, paidAt.plusSeconds(60), 7, "fixed")
        assertEquals(7L, edited.actualAmountMinorUnits)
        assertEquals("fixed", edited.paymentNote)
    }

    @Test
    fun marksOneOccurrencePaid_neverAffectsTheNextOne() {
        val october = TestData.occurrence(id = "oct", date = LocalDate.of(2025, 10, 31))
        val november = TestData.occurrence(id = "nov", date = LocalDate.of(2025, 11, 30))
        PaymentRules.markPaid(october, paidAt, null, null)
        assertEquals(OccurrenceStatus.PENDING, november.status)
    }

    @Test
    fun overdue_isDerivedFromDueInstant_notStored() {
        val bill = TestData.bill(start = LocalDate.of(2025, 3, 10))
        val item = TestData.item(bill, TestData.occurrence(date = LocalDate.of(2025, 3, 10)))
        val before = Instant.parse("2025-03-10T08:59:59Z")
        val after = Instant.parse("2025-03-10T09:00:01Z")
        assertFalse(PaymentRules.isOverdue(item, before))
        assertTrue(PaymentRules.isOverdue(item, after))
    }

    @Test
    fun displayStatus_coversAllStates() {
        val today = LocalDate.of(2025, 3, 10)
        val now = Instant.parse("2025-03-10T07:00:00Z")
        fun status(date: LocalDate, s: OccurrenceStatus = OccurrenceStatus.PENDING) =
            PaymentRules.displayStatus(TestData.item(occ = TestData.occurrence(date = date, status = s)), now, today)

        assertEquals(DisplayStatus.OVERDUE, status(today.minusDays(1)))
        assertEquals(DisplayStatus.DUE_TODAY, status(today))
        assertEquals(DisplayStatus.DUE_SOON, status(today.plusDays(3)))
        assertEquals(DisplayStatus.UPCOMING, status(today.plusDays(4)))
        assertEquals(DisplayStatus.PAID, status(today, OccurrenceStatus.PAID))
        assertEquals(DisplayStatus.SKIPPED, status(today, OccurrenceStatus.SKIPPED))
    }
}
