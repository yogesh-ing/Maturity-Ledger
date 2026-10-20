package com.afec.app.domain

import com.afec.app.data.InstrumentType
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Regex Date Engine + Confidence Scorer (PRD §07 data flow, FR-01, FR-02, FR-09).
 *
 * Pure Kotlin / java.time only — unit-testable on the JVM without Android.
 * Routing thresholds (PRD §04/§07):
 *   score >= 85  -> auto-add to Dashboard
 *   score 50..84 -> SMS Verification Queue
 *   score <  50  -> logged silently, never shown
 *
 * Dates are parsed with India-locale rules (DD/MM, PRD §14) and normalised to
 * start-of-day epoch millis in the device zone.
 */
object SmsParser {

    val KEYWORDS = listOf(
        "matures", "maturity", "mature", "expiry", "expires", "expire",
        "value date", "lock-in", "renewal", "renew", "lapses", "due on"
    )

    /** Well-known Indian bank / service sender IDs (seed data for ParserPattern). */
    val KNOWN_SENDERS = mapOf(
        "HDFCBK" to "HDFC Bank", "SBIINB" to "SBI", "ICICIB" to "ICICI Bank",
        "AXISBK" to "Axis Bank", "KOTAKB" to "Kotak", "YESBNK" to "Yes Bank",
        "INDBNK" to "Indian Bank", "PNBSMS" to "PNB", "BARODA" to "Bank of Baroda",
        "AMAZON" to "Amazon", "PAYTM" to "Paytm", "JIO" to "Jio"
    )

    private val INSTITUTIONS = listOf(
        "HDFC" to "HDFC Bank", "SBI" to "SBI", "ICICI" to "ICICI Bank",
        "AXIS" to "Axis Bank", "KOTAK" to "Kotak", "YES BANK" to "Yes Bank",
        "INDUSIND" to "IndusInd Bank", "PNB" to "PNB", "BARODA" to "Bank of Baroda",
        "AMAZON" to "Amazon", "PRIME" to "Amazon Prime", "NETFLIX" to "Netflix",
        "PAYTM" to "Paytm", "JIO" to "Jio"
    )

    data class ParsedFields(
        val title: String,
        val institutionName: String,
        val instrumentType: InstrumentType,
        val principalAmount: Double?,
        val maturityDateEpoch: Long,
        val maturityDate: LocalDate
    )

    data class ParseOutcome(
        val fields: ParsedFields?,
        val confidence: Int,
        val reasons: List<String>,
        val hasKeyword: Boolean,
        val senderKnown: Boolean
    ) {
        val route: Route get() = when {
            fields == null -> Route.SILENT_LOG
            confidence >= 85 -> Route.AUTO_ADD
            confidence >= 50 -> Route.VERIFICATION_QUEUE
            else -> Route.SILENT_LOG
        }
    }

    enum class Route { AUTO_ADD, VERIFICATION_QUEUE, SILENT_LOG }

    // DD/MM/YYYY, DD-MM-YYYY, DD/MM/YY (India first — PRD §14)
    private val numericDate = Regex("""\b(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{2,4})\b""")
    // 12-Nov-2026, 12 Nov 2026, 12th November 2026, 05 December 26
    private val namedDate = Regex(
        """\b(\d{1,2})(?:st|nd|rd|th)?[\s\-](Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)[\s\-](\d{2,4})\b""",
        RegexOption.IGNORE_CASE
    )
    // 08/2027 (card expiry MM/YYYY) -> last day of that month
    private val monthYear = Regex("""\b(0[1-9]|1[0-2])/(\d{4})\b""")
    // "lapses in 5 days", "expires in 12 days"
    private val relativeDays = Regex("""\bin\s+(\d{1,3})\s*days?\b""", RegexOption.IGNORE_CASE)
    // Rs. 2,50,000 / ₹5,00,000 / INR 50000
    private val amountRegex = Regex(
        """(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE
    )

    private val monthNames = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )

    fun parse(
        sender: String,
        body: String,
        receivedDate: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        knownSenderIds: Set<String> = KNOWN_SENDERS.keys
    ): ParseOutcome {
        val reasons = mutableListOf<String>()
        var score = 0
        val upperSender = sender.uppercase(Locale.ROOT)
        val upperBody = body.uppercase(Locale.ROOT)

        val senderKey = knownSenderIds.firstOrNull { upperSender.contains(it) }
        val senderKnown = senderKey != null
        if (senderKnown) { score += 30; reasons += "known sender" }

        val hasKeyword = KEYWORDS.any { body.contains(it, ignoreCase = true) }
        if (hasKeyword) { score += 25; reasons += "financial keyword" }

        val date = extractDate(body, receivedDate)
        if (date != null) { score += 30; reasons += "valid date" }

        val amount = extractAmount(body)
        if (amount != null) { score += 10; reasons += "amount found" }

        val type = detectType(body)
        if (type != InstrumentType.OTHER) { score += 5; reasons += "instrument type" }

        if (date == null || !hasKeyword) {
            // Not a financial-expiry SMS (or unparseable): never surface a half-result.
            return ParseOutcome(null, minOf(score, 49), reasons, hasKeyword, senderKnown)
        }

        val institution = senderKey?.let { KNOWN_SENDERS[it] }
            ?: INSTITUTIONS.firstOrNull { upperBody.contains(it.first) }?.second
            ?: sender.take(24).ifBlank { "Unknown" }
        val fields = ParsedFields(
            title = buildTitle(type, institution, body),
            institutionName = institution,
            instrumentType = type,
            principalAmount = amount,
            maturityDateEpoch = date.atStartOfDay(zone).toInstant().toEpochMilli(),
            maturityDate = date
        )
        return ParseOutcome(fields, minOf(score, 100), reasons, hasKeyword, senderKnown)
    }

    fun extractDate(body: String, receivedDate: LocalDate): LocalDate? {
        namedDate.find(body)?.let { m ->
            val month = monthNames[m.groupValues[2].lowercase(Locale.ROOT).take(3)] ?: return@let
            runCatching {
                LocalDate.of(normaliseYear(m.groupValues[3]), month, m.groupValues[1].toInt())
            }.getOrNull()?.let { return it }
        }
        numericDate.find(body)?.let { m ->
            runCatching {
                LocalDate.of(
                    normaliseYear(m.groupValues[3]),
                    m.groupValues[2].toInt(),          // MM (second field, DD/MM per India locale)
                    m.groupValues[1].toInt()           // DD
                )
            }.getOrNull()?.let { return it }
        }
        relativeDays.find(body)?.let { m ->
            m.groupValues[1].toIntOrNull()?.let { return receivedDate.plusDays(it.toLong()) }
        }
        monthYear.find(body)?.let { m ->
            runCatching {
                val ym = java.time.YearMonth.of(m.groupValues[2].toInt(), m.groupValues[1].toInt())
                ym.atEndOfMonth()
            }.getOrNull()?.let { return it }
        }
        return null
    }

    fun extractAmount(body: String): Double? =
        amountRegex.find(body)?.groupValues?.get(1)
            ?.replace(",", "")?.toDoubleOrNull()

    fun detectType(body: String): InstrumentType {
        val t = body.lowercase(Locale.ROOT)
        return when {
            "fixed deposit" in t || Regex("""\bfd\b""").containsMatchIn(t) -> InstrumentType.FD
            "recurring deposit" in t || Regex("""\brd\b""").containsMatchIn(t) -> InstrumentType.RD
            "debit card" in t || "credit card" in t || "card ending" in t || "card" in t -> InstrumentType.CARD
            "subscription" in t || "membership" in t || "prime" in t || "netflix" in t -> InstrumentType.SUBSCRIPTION
            else -> InstrumentType.OTHER
        }
    }

    private fun buildTitle(type: InstrumentType, institution: String, body: String): String {
        val short = institution.replace(" Bank", "")
        return when (type) {
            InstrumentType.FD -> "$short FD Maturity"
            InstrumentType.RD -> "$short RD Maturity"
            InstrumentType.CARD -> "$short Card Expiry"
            InstrumentType.SUBSCRIPTION -> "$short Subscription Renewal"
            InstrumentType.OTHER -> "$short Expiry"
        }
    }

    private fun normaliseYear(y: String): Int {
        val v = y.toInt()
        return if (y.length == 2) (if (v >= 70) 1900 + v else 2000 + v) else v
    }
}

/** Day math + urgency tiers (PRD §05/§06: Urgent ≤7d, Approaching 8–30d, Comfortable 31d+). */
object DateMath {
    fun daysLeft(maturityDateEpoch: Long, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Long {
        val date = java.time.Instant.ofEpochMilli(maturityDateEpoch).atZone(zone).toLocalDate()
        return java.time.temporal.ChronoUnit.DAYS.between(today, date)
    }

    fun formatDate(epoch: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        java.time.Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate()
            .format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH))
}

enum class UrgencyTier { OVERDUE, URGENT, APPROACHING, COMFORTABLE, COMPLETED }

fun urgencyTier(daysLeft: Long, isCompleted: Boolean = false): UrgencyTier = when {
    isCompleted -> UrgencyTier.COMPLETED
    daysLeft < 0 -> UrgencyTier.OVERDUE
    daysLeft <= 7 -> UrgencyTier.URGENT
    daysLeft <= 30 -> UrgencyTier.APPROACHING
    else -> UrgencyTier.COMFORTABLE
}
