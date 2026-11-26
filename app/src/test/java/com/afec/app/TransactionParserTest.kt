package com.afec.app

import com.afec.app.domain.SpendCategory
import com.afec.app.domain.TransactionParser
import com.afec.app.domain.TxnKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TransactionParserTest {
    private val zone: ZoneId = ZoneId.systemDefault()
    private fun instant(d: LocalDate): Instant = d.atStartOfDay(zone).toInstant()

    @Test
    fun `debit with trailing balance takes the debit not the balance`() {
        val s = TransactionParser.parse("HDFCBK", "Rs.500 debited from A/c XX1234. Avl Bal Rs.10,000", instant(LocalDate.of(2026, 11, 20)))
        assertEquals(50000L, s!!.amountMinor)
        assertEquals(TxnKind.SPEND, s.kind)
    }

    @Test
    fun `balance only yields nothing`() {
        val s = TransactionParser.parse("HDFCBK", "Avl Bal Rs.50,000", instant(LocalDate.of(2026, 11, 20)))
        assertNull(s)
    }

    @Test
    fun `salary credit is not a spend`() {
        val s = TransactionParser.parse("SBIINB", "Rs.85,000 credited to your account. Salary for Nov", instant(LocalDate.of(2026, 11, 20)))
        assertNull(s)
    }

    @Test
    fun `credit card bill payment is not a spend`() {
        val s = TransactionParser.parse("HDFCBK", "Thank you for your payment of Rs.5,000 towards your credit card bill", instant(LocalDate.of(2026, 11, 20)))
        assertNull(s)
    }

    @Test
    fun `upi debit to swiggy categorises as food`() {
        val s = TransactionParser.parse("SBIINB", "Rs.450 debited via UPI. VPA swiggy@okaxis", instant(LocalDate.of(2026, 11, 20)))
        assertEquals(45000L, s!!.amountMinor)
        assertEquals(SpendCategory.FOOD, s.category)
    }

    @Test
    fun `card spend is a spend`() {
        val s = TransactionParser.parse("HDFCBK", "You spent Rs.1,299 on HDFC Credit Card xx1234", instant(LocalDate.of(2026, 11, 20)))
        assertEquals(TxnKind.SPEND, s!!.kind)
        assertEquals(129900L, s.amountMinor)
    }

    @Test
    fun `refund is a refund`() {
        val s = TransactionParser.parse("AMAZON", "Rs.499 credited to your account as refund against order 123", instant(LocalDate.of(2026, 11, 20)))
        assertEquals(TxnKind.REFUND, s!!.kind)
        assertEquals(49900L, s.amountMinor)
    }

    @Test
    fun `otp and intent and declined are not spends`() {
        assertNull(TransactionParser.parse("HDFCBK", "Your OTP is 123456. Rs.500 will be debited", instant(LocalDate.of(2026, 11, 20))))
        assertNull(TransactionParser.parse("HDFCBK", "Reminder: your EMI of Rs.5,000 is due on 05/12", instant(LocalDate.of(2026, 11, 20))))
        assertNull(TransactionParser.parse("HDFCBK", "Transaction of Rs.500 declined", instant(LocalDate.of(2026, 11, 20))))
    }

    @Test
    fun `indian grouping parses to minor units exactly`() {
        val s = TransactionParser.parse("SBIINB", "Rs.1,23,456.78 debited from your account", instant(LocalDate.of(2026, 11, 20)))
        assertEquals(12345678L, s!!.amountMinor)
    }

    @Test
    fun `date comes from the sms timestamp not today`() {
        val s = TransactionParser.parse("SBIINB", "Rs.100 debited from your account", instant(LocalDate.of(2025, 3, 14)))
        assertEquals(LocalDate.of(2025, 3, 14), s!!.occurredOn)
    }
}
