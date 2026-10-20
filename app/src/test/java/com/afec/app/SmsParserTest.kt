package com.afec.app

import com.afec.app.data.InstrumentType
import com.afec.app.domain.DateMath
import com.afec.app.domain.SmsParser
import com.afec.app.domain.UrgencyTier
import com.afec.app.domain.urgencyTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Unit tests (PRD §13): validated against the Appendix sample bank SMS templates. */
class SmsParserTest {

    private val received: LocalDate = LocalDate.of(2026, 10, 20)

    @Test
    fun `hdfc appendix sample auto-adds`() {
        val out = SmsParser.parse(
            "V-HDFCBK",
            "Your Fixed Deposit No. 908123 expires on 12-Nov-2026. Auto-renew is disabled. Visit netbanking.",
            received
        )
        assertNotNull(out.fields)
        assertEquals(LocalDate.of(2026, 11, 12), out.fields!!.maturityDate)
        assertEquals(InstrumentType.FD, out.fields!!.instrumentType)
        assertEquals(SmsParser.Route.AUTO_ADD, out.route)
    }

    @Test
    fun `sbi appendix sample parses DD-MM-YYYY and Indian amount`() {
        val out = SmsParser.parse(
            "VM-SBIINB",
            "Dear Customer, your SBI FD A/c XX4532 will mature on 05/12/2026 for Rs. 2,50,000. Value date noted.",
            received
        )
        assertNotNull(out.fields)
        assertEquals(LocalDate.of(2026, 12, 5), out.fields!!.maturityDate)
        assertEquals(250000.0, out.fields!!.principalAmount!!, 0.001)
        assertEquals(SmsParser.Route.AUTO_ADD, out.route)
    }

    @Test
    fun `amazon relative-days subscription parses`() {
        val out = SmsParser.parse(
            "VM-AMAZON",
            "Alert: Your Prime membership subscription lapses in 5 days. Renew with your registered card.",
            received
        )
        assertNotNull(out.fields)
        assertEquals(received.plusDays(5), out.fields!!.maturityDate)
        assertEquals(InstrumentType.SUBSCRIPTION, out.fields!!.instrumentType)
    }

    @Test
    fun `icici card MM-YYYY expiry uses last day of month`() {
        val out = SmsParser.parse(
            "AD-ICICIB",
            "Your ICICI Debit Card ending 7723 expires 08/2027. Reissue request can be raised via iMobile.",
            received
        )
        assertNotNull(out.fields)
        assertEquals(LocalDate.of(2027, 8, 31), out.fields!!.maturityDate)
        assertEquals(InstrumentType.CARD, out.fields!!.instrumentType)
    }

    @Test
    fun `ordinal named date parses`() {
        assertEquals(
            LocalDate.of(2026, 12, 5),
            SmsParser.extractDate("Your FD matures on 5th December 2026.", received)
        )
    }

    @Test
    fun `unknown sender with keyword and date goes to verification queue`() {
        val out = SmsParser.parse(
            "VM-XXXXXX",
            "Reminder: your deposit maturity date is 20/01/2027.",
            received
        )
        assertNotNull(out.fields)
        assertEquals(SmsParser.Route.VERIFICATION_QUEUE, out.route)
    }

    @Test
    fun `non-financial SMS is silently logged, never surfaced`() {
        val out = SmsParser.parse("VM-OFFER", "Big sale! 50% off everything this weekend only.", received)
        assertNull(out.fields)
        assertEquals(SmsParser.Route.SILENT_LOG, out.route)
    }

    @Test
    fun `urgency tiers follow PRD thresholds`() {
        assertEquals(UrgencyTier.OVERDUE, urgencyTier(-1))
        assertEquals(UrgencyTier.URGENT, urgencyTier(0))
        assertEquals(UrgencyTier.URGENT, urgencyTier(7))
        assertEquals(UrgencyTier.APPROACHING, urgencyTier(8))
        assertEquals(UrgencyTier.APPROACHING, urgencyTier(30))
        assertEquals(UrgencyTier.COMFORTABLE, urgencyTier(31))
        assertEquals(UrgencyTier.COMPLETED, urgencyTier(3, isCompleted = true))
    }

    @Test
    fun `leap year and month-end date math`() {
        val leap = LocalDate.of(2024, 2, 29)
        val epoch = leap.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(0L, DateMath.daysLeft(epoch, leap))
        assertTrue(DateMath.formatDate(epoch).contains("29 Feb 2024"))
    }
}
