package com.afec.app

import com.afec.app.data.MandateFrequency
import com.afec.app.domain.MandateParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Real SBI Card e-mandate wording (card digits and Si-Hub ID substituted):
 * the registration SMS and the debit SMS describe one mandate and must agree.
 */
class MandateParserTest {

    private val received: LocalDate = LocalDate.of(2026, 10, 19)
    private fun epoch(y: Int, m: Int, d: Int): Long =
        LocalDate.of(y, m, d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val registration = "Dear Cardholder, your payment e-Mandate set at merchant " +
        "platform with your credit card ending 1234 has been registered with the following " +
        "details. Merchant: Zee Entertainment Enterprises Limited, Description: Adho Plan., " +
        "e-Mandate Limit Amount (INR): 149.00, Frequency: As Presented, Start date: 18/08/2026, " +
        "End date:18/08/2036, Si-Hub ID: AbCdEfGhIj. Please note that you have authorised debit " +
        "of 149.00 from your account towards the first Trxn. against this payment e-Mandate. " +
        "To manage e-Mandate(s), click: https://www.sbicard.com/emandates - SBI Card"

    private val debit = "Transaction Successful! Rs.149.00 has been debited to your SBI Credit " +
        "Card XX1234 against recurring e-mandate registered by you at merchant site. Details: " +
        "Merchant: ZEEENTERTAINMENTCYBSSI SiHub ID: AbCdEfGhIj Trxn. Ref. No.: 396291826780962 " +
        "Date: 19-Oct-2026. Time: 18:27:58 Manage E-mandate at www.sbicard.com/emandates or call " +
        "us at 18605001290, 18601801290 for any grievances. TnC apply"

    @Test
    fun `registration email-style SMS parses merchant, limit, dates and Si-Hub id`() {
        val parsed = MandateParser.parse("AD-SBICRD", registration, received)
            ?: error("registration SMS was not recognised as a mandate")
        assertNotNull(parsed)
        assertEquals("Zee Entertainment Enterprises Limited", parsed.merchantName)
        assertEquals("SBI Card", parsed.cardIssuer)
        assertEquals("1234", parsed.cardLast4)
        assertEquals(149.0, parsed.amount!!, 0.001)
        assertEquals(MandateFrequency.AS_PRESENTED, parsed.frequency)
        assertEquals(epoch(2026, 8, 18), parsed.startEpoch)
        assertEquals(epoch(2036, 8, 18), parsed.endEpoch)
        assertEquals("AbCdEfGhIj", parsed.reference)
        assertTrue(parsed.isRegistration)
        assertFalse(parsed.isDebit)
        assertNull(parsed.lastDebitEpoch)
    }

    @Test
    fun `debit SMS parses the same mandate and records the actual debit date`() {
        val parsed = MandateParser.parse("AD-SBICRD", debit, received)
            ?: error("debit SMS was not recognised as a mandate")
        assertNotNull(parsed)
        assertEquals("ZEEENTERTAINMENTCYBSSI", parsed.merchantName)
        assertEquals("SBI Card", parsed.cardIssuer)
        assertEquals("1234", parsed.cardLast4)
        assertEquals(149.0, parsed.amount!!, 0.001)
        assertEquals("AbCdEfGhIj", parsed.reference)
        assertEquals(epoch(2026, 10, 19), parsed.lastDebitEpoch)
        assertTrue(parsed.isDebit)
        assertFalse(parsed.isRegistration)
    }

    @Test
    fun `registration and debit share the Si-Hub id used to de-duplicate`() {
        val fromRegistration = MandateParser.parse("AD-SBICRD", registration, received)
        val fromDebit = MandateParser.parse("AD-SBICRD", debit, received)
        assertEquals(fromRegistration!!.reference, fromDebit!!.reference)
    }

    @Test
    fun `an ordinary card purchase is not a mandate`() {
        assertNull(
            MandateParser.parse(
                "AD-SBICRD",
                "Rs.499.00 spent on your SBI Credit Card XX1234 at AMAZON on 19-Oct-2026.",
                received
            )
        )
    }

    @Test
    fun `frequency wording maps to a schedule`() {
        assertEquals(MandateFrequency.MONTHLY, MandateParser.parseFrequency("Monthly"))
        assertEquals(MandateFrequency.QUARTERLY, MandateParser.parseFrequency("Quarterly"))
        assertEquals(MandateFrequency.HALF_YEARLY, MandateParser.parseFrequency("Half Yearly"))
        assertEquals(MandateFrequency.YEARLY, MandateParser.parseFrequency("Yearly"))
        assertEquals(MandateFrequency.AS_PRESENTED, MandateParser.parseFrequency("As Presented"))
        assertEquals(MandateFrequency.AS_PRESENTED, MandateParser.parseFrequency(null))
    }
}
