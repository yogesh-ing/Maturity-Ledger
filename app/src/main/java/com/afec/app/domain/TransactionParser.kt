package com.afec.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

enum class TxnKind { SPEND, REFUND }

enum class SpendCategory { FOOD, GROCERY, TRANSPORT, BILLS, SUBSCRIPTION, SHOPPING, CASH, OTHER }

data class Spend(
    val amountMinor: Long,
    val kind: TxnKind,
    val occurredOn: LocalDate,
    val merchant: String?,
    val category: SpendCategory
)

/**
 * Read-only, debit-only SMS expense parser (docs/expense_tracking_v2.md §7).
 *
 * Returns null for: balance figures, income, credit-card bill payments, intents,
 * failures, OTPs, non-INR amounts and anything unparseable. Never throws.
 *
 * Deliberately independent of SmsParser: that one drives maturity tracking and
 * takes the *first* amount token, which on a debit SMS is usually the balance.
 */
object TransactionParser {

    private val currency = Regex("""(?i)(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)""")

    /** Any amount whose 30-char prefix names a balance is a balance, not a spend. */
    private val balanceTokens = listOf(
        "avl bal", "avail bal", "available balance", "bal:", "total bal",
        "closing bal", "opening bal", "current bal", "current balance",
        "balance is", "balance of"
    )

    private val refundKeywords = listOf("refund", "reversed", "reversal", "chargeback")

    private val spendKeywords = listOf(
        "spent", "spend", "debited", "debit", "paid", "withdrawn", "purchase",
        "charged", "upi", "imps", "neft", "rtgs", "atm", "card", "mandate", "emi"
    )

    private val incomeKeywords = listOf(
        "salary", "interest credited", "deposit", "transfer in", "cashback",
        "cheque", "amount credited", "credited to your", "received from"
    )

    private val billPaymentKeywords = listOf(
        "payment received", "thank you for your payment", "bill paid", "bill payment"
    )

    private val intentKeywords = listOf(
        "will be debited", "scheduled", "due on", "requested", "declined",
        "failed", "unsuccessful", "cancelled", "canceled", "otp",
        "one time password", "promo", "limit"
    )

    private val categoryRules: List<Pair<SpendCategory, List<String>>> = listOf(
        SpendCategory.FOOD to listOf(
            "swiggy", "zomato", "domino", "pizza", "burger", "restaurant",
            "cafe", "bar", "hotel", "food", "dine", "kitchen", "kfc", "starbucks"
        ),
        SpendCategory.GROCERY to listOf(
            "big basket", "bigbasket", "blinkit", "zepto", "grofers", "dmart",
            "grocery", "kirana", "supermarket", "fresh", "vegetable", "instamart"
        ),
        SpendCategory.TRANSPORT to listOf(
            "uber", "ola", "rapido", "metro", "bus", "train", "flight", "irctc",
            "indigo", "air india", "fuel", "petrol", "diesel", "highway", "toll", "fastag"
        ),
        SpendCategory.BILLS to listOf(
            "electricity", "water", "gas", "broadband", "internet", "phone",
            "bill", "mobile recharge", "recharge", "rent", "property tax", "insurance"
        ),
        SpendCategory.SUBSCRIPTION to listOf(
            "spotify", "netflix", "prime", "hotstar", "subscription",
            "monthly", "annual", "plan", "youtube"
        ),
        SpendCategory.SHOPPING to listOf(
            "amazon", "flipkart", "myntra", "ajio", "shopping", "store", "mall", "meesho"
        ),
        SpendCategory.CASH to listOf("atm", "cash", "withdrawal")
    )

    private val vpaRegex = Regex("""(?i)vpa\s*:?\s*(\S+)""")
    private val atMerchantRegex = Regex("""(?i)\bat\s+([A-Za-z0-9 &'./-]{2,40})""")
    private val toMerchantRegex = Regex("""(?i)\bto\s+([A-Za-z0-9 &'./-]{2,40})""")

    private val numericDate = Regex("""\b(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})\b""")
    private val namedDate = Regex("""\b(\d{1,2})[\s-]([A-Za-z]{3,9})[\s-](\d{2,4})\b""")
    private val monthNames = listOf(
        "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"
    )

    fun parse(
        sender: String,
        body: String,
        receivedAt: Instant,
        zone: ZoneId = ZoneId.systemDefault()
    ): Spend? {
        // 1. Normalise.
        val text = body.replace('\u00A0', ' ').replace('\u200B', ' ').trim()
        if (text.isEmpty()) return null
        val lower = text.lowercase(Locale.ROOT)

        // 4. Direction — ambiguous (both keyword sets) is a refusal, not a guess.
        val isRefund = refundKeywords.any { lower.contains(it) }
        val isSpend = spendKeywords.any { lower.contains(it) }
        val kind = when {
            isRefund && !isSpend -> TxnKind.REFUND
            isSpend && !isRefund -> TxnKind.SPEND
            else -> return null
        }

        // 5. Intent, failure and — for outflows only — income and bill payments.
        //    A refund legitimately says "credited to your a/c", so it skips those.
        if (intentKeywords.any { lower.contains(it) }) return null
        if (kind == TxnKind.SPEND) {
            if (incomeKeywords.any { lower.contains(it) }) return null
            if (billPaymentKeywords.any { lower.contains(it) }) return null
        }

        // 2 + 3. All amount tokens, dropping balance figures.
        val tokens = currency.findAll(text).mapNotNull { m ->
            val from = (m.range.first - 30).coerceAtLeast(0)
            val prefix = text.substring(from, m.range.first).lowercase(Locale.ROOT)
            if (balanceTokens.any { prefix.contains(it) }) return@mapNotNull null
            val minor = toMinor(m.groupValues[1].replace(",", "")) ?: return@mapNotNull null
            if (minor <= 0L) return@mapNotNull null
            minor to m.range.first
        }.toList()
        if (tokens.isEmpty()) return null

        // 6. Nearest token to the direction keyword; a tie means we cannot tell.
        val anchor = (if (kind == TxnKind.SPEND) spendKeywords else refundKeywords)
            .mapNotNull { k -> lower.indexOf(k).takeIf { it >= 0 } }
            .minOrNull() ?: 0
        val ranked = tokens.sortedBy { kotlin.math.abs(it.second - anchor) }
        if (ranked.size > 1 &&
            kotlin.math.abs(ranked[0].second - anchor) == kotlin.math.abs(ranked[1].second - anchor)
        ) return null
        val amountMinor = ranked.first().first

        // 7. Date from the body, else the SMS timestamp (not today).
        val occurredOn = parseDate(text) ?: receivedAt.atZone(zone).toLocalDate()

        // 8 + 9. Merchant, then category.
        val merchant = extractMerchant(text)
        return Spend(
            amountMinor = amountMinor,
            kind = kind,
            occurredOn = occurredOn,
            merchant = merchant,
            category = categorize(lower, merchant)
        )
    }

    private fun toMinor(raw: String): Long? {
        val parts = raw.split(".")
        val whole = parts.getOrNull(0)?.toLongOrNull() ?: return null
        val frac = parts.getOrNull(1)
        val minorFrac = when (frac) {
            null, "" -> 0L
            else -> (frac + "00").take(2).toLongOrNull() ?: return null
        }
        return whole * 100 + minorFrac
    }

    private fun parseDate(text: String): LocalDate? {
        for (m in numericDate.findAll(text)) {
            val date = buildDate(
                year = m.groupValues[3].toIntOrNull(),
                month = m.groupValues[2].toIntOrNull(),
                day = m.groupValues[1].toIntOrNull()
            )
            if (date != null) return date
        }
        for (m in namedDate.findAll(text)) {
            val idx = monthNames.indexOf(m.groupValues[2].take(3).lowercase(Locale.ROOT))
            if (idx < 0) continue
            val date = buildDate(
                year = m.groupValues[3].toIntOrNull(),
                month = idx + 1,
                day = m.groupValues[1].toIntOrNull()
            )
            if (date != null) return date
        }
        return null
    }

    private fun buildDate(year: Int?, month: Int?, day: Int?): LocalDate? {
        if (year == null || month == null || day == null) return null
        val fullYear = if (year < 100) 2000 + year else year
        return runCatching { LocalDate.of(fullYear, month, day) }.getOrNull()
    }

    private fun extractMerchant(text: String): String? {
        vpaRegex.find(text)?.groupValues?.get(1)?.let { return clean(it) }
        atMerchantRegex.find(text)?.groupValues?.get(1)?.let { return clean(it) }
        toMerchantRegex.find(text)?.groupValues?.get(1)?.let { return clean(it) }
        return null
    }

    private fun clean(s: String): String? {
        val t = s.trim().trimEnd('.', ',', ';', ':', ')').trim()
        return if (t.isBlank()) null else t.take(40)
    }

    private fun categorize(lower: String, merchant: String?): SpendCategory {
        val hay = lower + " " + (merchant ?: "").lowercase(Locale.ROOT)
        for ((cat, keys) in categoryRules) {
            if (keys.any { hay.contains(it) }) return cat
        }
        return SpendCategory.OTHER
    }
}
