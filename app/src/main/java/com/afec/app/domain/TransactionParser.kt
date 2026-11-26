package com.afec.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class TxnKind { SPEND, REFUND }

enum class SpendCategory { FOOD, GROCERY, TRANSPORT, BILLS, SUBSCRIPTION, SHOPPING, CASH, OTHER }

data class Spend(
    val amountMinor: Long,
    val kind: TxnKind,
    val occurredOn: LocalDate,
    val merchant: String?,
    val category: SpendCategory
)

object TransactionParser {

    private val currency = Regex("""(?i)(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)""")

    private val refundKeywords = listOf("refund", "reversed", "reversal", "chargeback")
    private val billPaymentKeywords = listOf("payment received", "thank you for your payment", "bill paid", "bill payment")
    private val incomeKeywords = listOf("salary", "interest", "deposit", "cashback", "cheque", "amount credited", "credited to your", "transfer from", "transfer in", "received from", "received in your")
    private val intentKeywords = listOf("will be debited", "due on", "scheduled", "requested", "declined", "failed", "unsuccessful", "cancelled", "canceled", "otp", "one time password", "promo", "offer", "limit", "minimum due", "reminder")
    private val debitVerbs = listOf("debited", "spent", "paid", "withdrawn", "purchase", "charged", "deducted", "transaction", "dr ", "debit")

    private val balanceIndicators = listOf("avl", "avail", "available", "bal", "balance", "total")

    private val categoryRules: List<Pair<SpendCategory, List<String>>> = listOf(
        SpendCategory.FOOD to listOf("swiggy", "zomato", "restaurant", "food", "cafe", "dominos", "pizza", "kfc", "mcdonald", "starbucks", "burger", "dunzo", "eatfit"),
        SpendCategory.GROCERY to listOf("bigbasket", "dmart", "kirana", "grocery", "supermarket", "blinkit", "zepto", "instamart", "jiomart", "more", "reliance smart", "grofers"),
        SpendCategory.TRANSPORT to listOf("uber", "ola", "irctc", "fuel", "petrol", "diesel", "metro", "rapido", "indigo", "airasia", "spicejet", "makemytrip", "redbus", "fastag", "parking"),
        SpendCategory.BILLS to listOf("electricity", "broadband", "recharge", "jio", "airtel", "vodafone", "bsnl", "water bill", "gas", "tata power", "adani", "bescom"),
        SpendCategory.SUBSCRIPTION to listOf("netflix", "prime", "spotify", "subscription", "emandate", "e-mandate", "mandate", "hotstar", "youtube", "apple", "google play", "disney", "sony liv"),
        SpendCategory.SHOPPING to listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "tatacliq", "croma", "reliance digital"),
        SpendCategory.CASH to listOf("atm", "cash withdrawal", "cash wdl", "cash")
    )

    private val vpaRegex = Regex("""(?i)vpa\s+(\S+)""")
    private val atMerchantRegex = Regex("""(?i)\bat\s+([A-Za-z0-9 &'./-]{2,40})""")
    private val toMerchantRegex = Regex("""(?i)\bto\s+([A-Za-z0-9 &'./-]{2,40})""")

    fun parse(sender: String, body: String, receivedAt: Instant, zone: ZoneId = ZoneId.systemDefault()): Spend? {
        val lower = body.lowercase()
        val isRefund = refundKeywords.any { lower.contains(it) }

        if (!isRefund && billPaymentKeywords.any { lower.contains(it) }) return null
        if (!isRefund && incomeKeywords.any { lower.contains(it) }) return null
        if (intentKeywords.any { lower.contains(it) }) return null
        if (!isRefund && debitVerbs.none { lower.contains(it) }) return null

        val amountMinor = findAmountMinor(body) ?: return null
        val occurredOn = receivedAt.atZone(zone).toLocalDate()
        val merchant = extractMerchant(body)
        val category = categorize(lower, merchant)

        return Spend(
            amountMinor = amountMinor,
            kind = if (isRefund) TxnKind.REFUND else TxnKind.SPEND,
            occurredOn = occurredOn,
            merchant = merchant,
            category = category
        )
    }

    private fun findAmountMinor(body: String): Long? {
        for (m in currency.findAll(body)) {
            val start = m.range.first
            val prefix = body.substring((start - 20).coerceAtLeast(0), start).lowercase()
            if (balanceIndicators.any { prefix.contains(it) }) continue
            val raw = m.groupValues[1].replace(",", "")
            val minor = toMinor(raw) ?: continue
            return minor
        }
        return null
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

    private fun extractMerchant(body: String): String? {
        vpaRegex.find(body)?.groupValues?.get(1)?.let { return clean(it) }
        atMerchantRegex.find(body)?.groupValues?.get(1)?.let { return clean(it) }
        toMerchantRegex.find(body)?.groupValues?.get(1)?.let { return clean(it) }
        return null
    }

    private fun clean(s: String): String? {
        val t = s.trim().trimEnd('.', ',', ';', ':', ')').trim()
        if (t.isBlank()) return null
        return t.take(40)
    }

    private fun categorize(lower: String, merchant: String?): SpendCategory {
        val hay = (lower + " " + (merchant ?: "")).lowercase()
        for ((cat, keys) in categoryRules) {
            if (keys.any { hay.contains(it) }) return cat
        }
        return SpendCategory.OTHER
    }
}
