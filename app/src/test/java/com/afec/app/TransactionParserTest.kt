package com.afec.app

import com.afec.app.domain.SpendCategory
import com.afec.app.domain.TransactionParser
import com.afec.app.domain.TxnKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TransactionParserTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    // ── Amount extraction ──────────────────────────────

    @Test
    fun `debit with trailing Avl Bal returns the debit not the balance`() {
        val spend = TransactionParser.parse(
            "HDFCBANK", TestFixtures.SWIGGY_DEBIT,
            TestFixtures.instant("2025-11-26"), zone
        )
        assertNotNull(spend)
        assertEquals(34_000L, spend!!.amountMinor)
        assertEquals(TxnKind.SPEND, spend.kind)
    }

    @Test
    fun `balance-only message returns null`() {
        assertNull(
            TransactionParser.parse(
                "HDFCBANK", TestFixtures.BALANCE_ONLY,
                TestFixtures.instant("2025-11-26"), zone
            )
        )
    }

    @Test
    fun `Indian format lakh grouping parses to 12345678 paise`() {
        val spend = TransactionParser.parse(
            "DMART", TestFixtures.INDIAN_FORMAT_AMOUNT,
            TestFixtures.instant("2025-11-20"), zone
        )
        assertNotNull(spend)
        assertEquals(12_345_678L, spend!!.amountMinor)
    }

    @Test
    fun `voucher applied returns the gross spend not the net`() {
        val spend = TransactionParser.parse(
            "SWIGGY", TestFixtures.VOUCHER_APPLIED,
            TestFixtures.instant("2025-11-26"), zone
        )
        assertNotNull(spend)
        assertEquals(50_000L, spend!!.amountMinor)
    }

    // ── Direction ──────────────────────────────────────

    @Test
    fun `UPI debit with VPA is SPEND`() {
        val spend = TransactionParser.parse(
            "OKICICI", TestFixtures.UPI_DEBIT_VPA,
            TestFixtures.instant("2025-11-26"), zone
        )
        assertNotNull(spend)
        assertEquals(TxnKind.SPEND, spend!!.kind)
        assertEquals(SpendCategory.FOOD, spend.category)
    }

    @Test
    fun `card spend is SPEND`() {
        val spend = TransactionParser.parse(
            "HDFCCARD", TestFixtures.CARD_SPEND,
            TestFixtures.instant("2025-11-26"), zone
        )
        assertNotNull(spend)
        assertEquals(TxnKind.SPEND, spend!!.kind)
    }

    @Test
    fun `refund is REFUND`() {
        val spend = TransactionParser.parse(
            "AMAZON", TestFixtures.REFUND,
            TestFixtures.instant("2025-11-24"), zone
        )
        assertNotNull(spend)
        assertEquals(TxnKind.REFUND, spend!!.kind)
        assertEquals(49_900L, spend.amountMinor)
    }

    @Test
    fun `ATM withdrawal is SPEND CASH`() {
        val spend = TransactionParser.parse(
            "HDFCBANK", TestFixtures.ATM_WITHDRAWAL,
            TestFixtures.instant("2025-11-25"), zone
        )
        assertNotNull(spend)
        assertEquals(TxnKind.SPEND, spend!!.kind)
        assertEquals(SpendCategory.CASH, spend.category)
    }

    // ── Exclusions ─────────────────────────────────────

    @Test
    fun `salary credit returns null`() {
        assertNull(
            TransactionParser.parse(
                "SALARY", TestFixtures.SALARY_CREDIT,
                TestFixtures.instant("2025-11-01"), zone
            )
        )
    }

    @Test
    fun `credit card bill payment returns null`() {
        assertNull(
            TransactionParser.parse(
                "HDFCCARD", TestFixtures.CREDIT_CARD_PAYMENT,
                TestFixtures.instant("2025-11-26"), zone
            )
        )
    }

    @Test
    fun `scheduled mandate returns null`() {
        assertNull(
            TransactionParser.parse(
                "HDFCBANK", TestFixtures.SCHEDULED_DEBIT,
                TestFixtures.instant("2025-11-26"), zone
            )
        )
    }

    @Test
    fun `OTP returns null`() {
        assertNull(
            TransactionParser.parse(
                "OKICICI", TestFixtures.OTP_SMS,
                TestFixtures.instant("2025-11-26"), zone
            )
        )
    }

    @Test
    fun `declined transaction returns null`() {
        assertNull(
            TransactionParser.parse(
                "OKICICI", TestFixtures.DECLINED,
                TestFixtures.instant("2025-11-26"), zone
            )
        )
    }

    @Test
    fun `settled mandate is SPEND`() {
        val spend = TransactionParser.parse(
            "HDFCBANK", TestFixtures.SETTLED_MANDATE,
            TestFixtures.instant("2025-11-05"), zone
        )
        assertNotNull(spend)
        assertEquals(TxnKind.SPEND, spend!!.kind)
        assertEquals(500_000L, spend.amountMinor)
    }

    // ── Date ───────────────────────────────────────────

    @Test
    fun `date from SMS body is used not receivedAt`() {
        val spend = TransactionParser.parse(
            "HDFCBANK", TestFixtures.SWIGGY_DEBIT,
            TestFixtures.instant("2025-12-01"),
            zone
        )
        assertNotNull(spend)
        assertEquals(LocalDate.of(2025, 11, 26), spend!!.occurredOn)
    }

    @Test
    fun `receivedAt in 2025 gives occurredOn in 2025 not today`() {
        val body = "You have spent Rs.100.00 at CAFE. 15/06/2025."
        val spend = TransactionParser.parse(
            "Cafe", body,
            TestFixtures.instant("2025-06-15"), zone
        )
        assertNotNull(spend)
        assertEquals(2025, spend!!.occurredOn.year)
    }

    // ── Safety ─────────────────────────────────────────

    @Test
    fun `empty body returns null`() {
        assertNull(TransactionParser.parse("X", "", Instant.now(), zone))
    }

    @Test
    fun `no amount token returns null`() {
        assertNull(
            TransactionParser.parse(
                "X", "Thank you for banking with us.",
                Instant.now(), zone
            )
        )
    }

    @Test
    fun `non-INR currency returns null`() {
        val body = "USD 50.00 spent at GLOBAL STORE. 26/11/2025."
        assertNull(TransactionParser.parse("X", body, Instant.now(), zone))
    }
}
