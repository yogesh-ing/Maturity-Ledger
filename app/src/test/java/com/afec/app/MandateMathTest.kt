package com.afec.app

import com.afec.app.data.CardMandate
import com.afec.app.data.MandateFrequency
import com.afec.app.data.MandateStatus
import com.afec.app.domain.MandateMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Recurrence rules for card mandates, especially month-end handling. */
class MandateMathTest {

    private fun epoch(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun mandate(
        merchant: String = "Netflix",
        amount: Double? = 649.0,
        frequency: MandateFrequency = MandateFrequency.MONTHLY,
        next: LocalDate = LocalDate.of(2026, 11, 5),
        status: MandateStatus = MandateStatus.ACTIVE,
        end: LocalDate? = null
    ) = CardMandate(
        merchantName = merchant,
        cardIssuer = "HDFC Bank",
        cardLast4 = "1234",
        amount = amount,
        frequency = frequency,
        nextDebitEpoch = epoch(next),
        mandateEndEpoch = end?.let { epoch(it) },
        status = status
    )

    @Test
    fun `a 31st mandate clamps to February but returns to the 31st in March`() {
        val anchor = epoch(LocalDate.of(2026, 1, 31))
        assertEquals(
            LocalDate.of(2026, 2, 28),
            MandateMath.nextDebit(anchor, MandateFrequency.MONTHLY, LocalDate.of(2026, 2, 1))
        )
        // Chaining +1 month would leave it stuck on the 28th; anchoring must not drift.
        assertEquals(
            LocalDate.of(2026, 3, 31),
            MandateMath.nextDebit(anchor, MandateFrequency.MONTHLY, LocalDate.of(2026, 3, 1))
        )
    }

    @Test
    fun `next debit is today when the debit falls today`() {
        val anchor = epoch(LocalDate.of(2026, 11, 5))
        assertEquals(
            LocalDate.of(2026, 11, 5),
            MandateMath.nextDebit(anchor, MandateFrequency.MONTHLY, LocalDate.of(2026, 11, 5))
        )
    }

    @Test
    fun `a stale anchor rolls forward by whole cycles`() {
        val anchor = epoch(LocalDate.of(2026, 1, 5))
        assertEquals(
            LocalDate.of(2026, 4, 5),
            MandateMath.nextDebit(anchor, MandateFrequency.MONTHLY, LocalDate.of(2026, 3, 10))
        )
    }

    @Test
    fun `quarterly and yearly skip the intervening months`() {
        val quarterly = epoch(LocalDate.of(2026, 1, 15))
        assertEquals(
            LocalDate.of(2026, 4, 15),
            MandateMath.nextDebit(quarterly, MandateFrequency.QUARTERLY, LocalDate.of(2026, 2, 1))
        )
        val yearly = epoch(LocalDate.of(2026, 6, 5))
        assertEquals(
            LocalDate.of(2027, 6, 5),
            MandateMath.nextDebit(yearly, MandateFrequency.YEARLY, LocalDate.of(2027, 1, 1))
        )
    }

    @Test
    fun `a mandate past its end date has no next debit`() {
        val anchor = epoch(LocalDate.of(2026, 1, 5))
        val end = epoch(LocalDate.of(2026, 2, 1))
        assertNull(
            MandateMath.nextDebit(anchor, MandateFrequency.MONTHLY, LocalDate.of(2026, 3, 1), end)
        )
    }

    @Test
    fun `as-presented mandates have no invented schedule or monthly figure`() {
        val m = mandate(frequency = MandateFrequency.AS_PRESENTED)
        assertNull(MandateMath.nextDebit(m, LocalDate.of(2026, 1, 1)))
        assertNull(MandateMath.monthlyEquivalent(149.0, MandateFrequency.AS_PRESENTED))
        assertEquals("As presented by the merchant", MandateMath.scheduleLabel(m))
    }

    @Test
    fun `a debit rolls the next date forward from the observed debit`() {
        val observed = LocalDate.of(2026, 10, 19)
        assertEquals(
            epoch(LocalDate.of(2026, 11, 19)),
            MandateMath.nextAfterDebit(epoch(observed), MandateFrequency.MONTHLY)
        )
        assertEquals(
            epoch(LocalDate.of(2027, 1, 19)),
            MandateMath.nextAfterDebit(epoch(observed), MandateFrequency.QUARTERLY)
        )
    }

    @Test
    fun `monthly total normalises frequency and ignores paused mandates`() {
        val mandates = listOf(
            mandate("Netflix", 649.0, MandateFrequency.MONTHLY),
            mandate("Insurance", 3000.0, MandateFrequency.QUARTERLY),
            mandate("Cloud storage", 1200.0, MandateFrequency.YEARLY),
            mandate("Paused gym", 500.0, MandateFrequency.MONTHLY, status = MandateStatus.PAUSED),
            mandate("Electricity", null, MandateFrequency.MONTHLY)
        )
        assertEquals(649.0 + 1000.0 + 100.0, MandateMath.monthlyTotal(mandates), 0.001)
    }

    @Test
    fun `ordinals read like a bank statement`() {
        assertEquals("1st", MandateMath.ordinal(1))
        assertEquals("2nd", MandateMath.ordinal(2))
        assertEquals("3rd", MandateMath.ordinal(3))
        assertEquals("11th", MandateMath.ordinal(11))
        assertEquals("22nd", MandateMath.ordinal(22))
        assertEquals("31st", MandateMath.ordinal(31))
    }

    @Test
    fun `schedule label describes the recurring day`() {
        val m = mandate(next = LocalDate.of(2026, 11, 5))
        assertEquals("5th of every month", MandateMath.scheduleLabel(m))
        assertEquals(
            "5th of every quarter",
            MandateMath.scheduleLabel(m.copy(frequency = MandateFrequency.QUARTERLY))
        )
    }
}
