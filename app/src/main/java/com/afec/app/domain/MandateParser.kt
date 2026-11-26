package com.afec.app.domain

import com.afec.app.data.MandateFrequency
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Parser for card e-mandate SMS (SBI Card, HDFC, ICICI, Axis, Kotak, Amex …).
 *
 * Two shapes arrive, and they describe the *same* mandate:
 *  - registration: "your payment e-Mandate … has been registered … Merchant: …,
 *    e-Mandate Limit Amount (INR): 149.00, Frequency: As Presented, Start date:
 *    18/08/2026, End date:18/08/2036, Si-Hub ID: YbaOHVByiY"
 *  - debit: "Rs.149.00 has been debited to your SBI Credit Card XX9177 against
 *    recurring e-mandate … Merchant: … SiHub ID: YbaOHVByiY … Date: 19-Oct-2026"
 *
 * The Si-Hub ID / UMN is the identity: matching on it keeps one registration and
 * every later debit on a single mandate instead of creating duplicates.
 */
object MandateParser {

    data class ParsedMandate(
        val merchantName: String,
        val cardIssuer: String,
        val cardLast4: String,
        val amount: Double?,
        val frequency: MandateFrequency,
        val startEpoch: Long?,
        val endEpoch: Long?,
        val reference: String?,
        val lastDebitEpoch: Long?,
        val isRegistration: Boolean,
        val isDebit: Boolean
    ) {
        /** Where the recurrence is anchored when no explicit start date is given. */
        val anchorEpoch: Long? get() = startEpoch ?: lastDebitEpoch
    }

    private val mandateSignal = Regex("(?i)e[\\s-]?mandate|si[\\s-]?hub|standing instruction|autopay|auto[\\s-]?debit")
    private val registrationSignal = Regex("(?i)has been registered|registered with the following|has been set|successfully registered")
    private val debitSignal = Regex("(?i)has been debited|debited to your|has been charged|charged to your")

    private val cardEnding = Regex("(?i)card\\s+ending\\s*:?\\s*(\\d{4})\\b")
    private val cardXx = Regex("(?i)\\bxx\\s*(\\d{4})\\b")
    private val siHub = Regex("(?i)si[\\s-]?hub\\s*id\\s*:?\\s*([A-Za-z0-9]{5,})")
    private val umn = Regex("(?i)\\bUMN\\s*:?\\s*([A-Za-z0-9]{5,})")
    private val merchantField = Regex(
        "(?i)merchant\\s*:\\s*(.+?)(?=\\s*,|\\s+si[\\s-]?hub|\\s+trxn|\\s+e-?mandate|\\s+description|\\s*\\.\\s|$)"
    )
    private val limitAmount = Regex(
        "(?i)(?:e-?mandate\\s+)?limit\\s+amount\\s*\\(\\s*inr\\s*\\)\\s*:?\\s*([\\d,]+(?:\\.\\d{1,2})?)"
    )
    private val frequencyField = Regex("(?i)frequency\\s*:\\s*([A-Za-z][A-Za-z\\s-]*?)(?=\\s*,|\\s*\\.\\s|$)")
    private val startDate = Regex("(?i)start\\s*date\\s*:\\s*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})")
    private val endDate = Regex("(?i)end\\s*date\\s*:\\s*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})")
    private val debitDate = Regex("(?i)\\bdate\\s*:\\s*(\\d{1,2}-[A-Za-z]{3,9}-\\d{2,4})")

    private val issuers = listOf(
        "AMERICAN EXPRESS" to "American Express", "AMEX" to "American Express",
        "SBI" to "SBI Card", "HDFC" to "HDFC Bank", "ICICI" to "ICICI Bank",
        "AXIS" to "Axis Bank", "KOTAK" to "Kotak", "IDFC" to "IDFC FIRST Bank",
        "INDUSIND" to "IndusInd Bank", "YES BANK" to "Yes Bank", "RBL" to "RBL Bank",
        "FEDERAL" to "Federal Bank", "AIRTEL" to "Airtel Axis"
    )

    fun looksLikeMandate(body: String): Boolean = mandateSignal.containsMatchIn(body)

    fun parse(
        sender: String,
        body: String,
        receivedDate: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): ParsedMandate? {
        if (!looksLikeMandate(body)) return null
        val isRegistration = registrationSignal.containsMatchIn(body)
        val isDebit = debitSignal.containsMatchIn(body)
        if (!isRegistration && !isDebit) return null

        val reference = siHub.find(body)?.groupValues?.get(1)
            ?: umn.find(body)?.groupValues?.get(1)

        val merchant = merchantField.find(body)?.groupValues?.get(1)?.trim()
            ?.trimEnd(',', '.', ' ')
            ?.takeIf { it.isNotBlank() && !it.equals("null", true) }
            ?: reference?.let { "Card mandate $it" }
            ?: return null

        val last4 = cardEnding.find(body)?.groupValues?.get(1)
            ?: cardXx.find(body)?.groupValues?.get(1)
            ?: ""

        val amount = limitAmount.find(body)?.groupValues?.get(1)
            ?.replace(",", "")?.toDoubleOrNull()
            ?: SmsParser.extractAmount(body)

        val start = startDate.find(body)?.groupValues?.get(1)?.let { parseNumericDate(it) }
        val end = endDate.find(body)?.groupValues?.get(1)?.let { parseNumericDate(it) }
        val debited = if (isDebit) {
            debitDate.find(body)?.groupValues?.get(1)?.let { parseNamedDate(it) } ?: receivedDate
        } else null

        return ParsedMandate(
            merchantName = merchant,
            cardIssuer = detectIssuer(sender, body),
            cardLast4 = last4,
            amount = amount,
            frequency = parseFrequency(frequencyField.find(body)?.groupValues?.get(1)),
            startEpoch = start?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
            endEpoch = end?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
            reference = reference,
            lastDebitEpoch = debited?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
            isRegistration = isRegistration,
            isDebit = isDebit
        )
    }

    fun parseFrequency(raw: String?): MandateFrequency {
        val v = raw?.lowercase(Locale.ROOT)?.trim() ?: return MandateFrequency.AS_PRESENTED
        return when {
            "half" in v && "year" in v -> MandateFrequency.HALF_YEARLY
            "quarter" in v -> MandateFrequency.QUARTERLY
            "month" in v -> MandateFrequency.MONTHLY
            "year" in v || "annual" in v -> MandateFrequency.YEARLY
            // "As Presented", daily/weekly/one-time: no monthly equivalent to claim.
            else -> MandateFrequency.AS_PRESENTED
        }
    }

    private fun detectIssuer(sender: String, body: String): String {
        val text = "$body ${sender.replace("-", " ")}".uppercase(Locale.ROOT)
        issuers.firstOrNull { text.contains(it.first) }?.let { return it.second }
        return SmsParser.KNOWN_SENDERS.entries
            .firstOrNull { sender.uppercase(Locale.ROOT).contains(it.key) }
            ?.value ?: "Card"
    }

    /** DD/MM/YYYY (India order) as used on mandate registrations. */
    private fun parseNumericDate(raw: String): LocalDate? {
        val parts = raw.split('/', '-')
        if (parts.size != 3) return null
        val day = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        var year = parts[2].toIntOrNull() ?: return null
        if (parts[2].length == 2) year = if (year >= 70) 1900 + year else 2000 + year
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    /** 19-Oct-2026 */
    private fun parseNamedDate(raw: String): LocalDate? {
        val parts = raw.split('-')
        if (parts.size != 3) return null
        val day = parts[0].toIntOrNull() ?: return null
        val month = when (parts[1].lowercase(Locale.ROOT).take(3)) {
            "jan" -> 1; "feb" -> 2; "mar" -> 3; "apr" -> 4; "may" -> 5; "jun" -> 6
            "jul" -> 7; "aug" -> 8; "sep" -> 9; "oct" -> 10; "nov" -> 11; "dec" -> 12
            else -> return null
        }
        var year = parts[2].toIntOrNull() ?: return null
        if (parts[2].length == 2) year = if (year >= 70) 1900 + year else 2000 + year
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }
}
