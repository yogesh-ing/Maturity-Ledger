package com.afec.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsMessage
import android.util.Log
import com.afec.app.data.AFECDatabase
import com.afec.app.data.ExpiryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Unit
import kotlinx.coroutines.async

class SmsReceiver : BroadcastReceiver() {

    private val TAG = "SmsReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val actions = intent.getStringArrayListExtra("pdus")
        if (actions == null || actions.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            processSmsBatch(actions, context)
        }
    }

    private fun processSmsBatch(pduList: List<String>, context: Context) {
        val database = AFECDatabase.getInstance(context)

        val parsedItems = pduList.asFlow().map { pdu ->
            try {
                extractSmsInfo(pdu)
            } catch (e: Exception) {
                null
            }
        }.filter { it != null }.toList()

        val insertTasks = parsedItems.map { item ->
            async(Dispatchers.IO) {
                database.expiryItemDao().insert(item)
                item
            }
        }

        async { awaitAll(*insertTasks.toTypedArray()) }

        Log.d(TAG, "Processed ${parsedItems.size} SMS items")
    }

    private fun extractSmsInfo(smsText: String): ExpiryItem? {
        val lowerText = smsText.lowercase()

        // Check for financial keywords
        val keywords = listOf("matures", "maturity", "expiry", "value date", "lock-in")
        val keywordMatch = keywords.any { lowerText.contains(it) }
        if (!keywordMatch) return null

        // Try to parse date - support various formats
        val targetDate = tryParseDate(smsText)
        if (targetDate == null) return null

        // Extract title from SMS
        val title = extractTitle(smsText)

        // Extract principal amount if present
        val principalAmount = extractPrincipal(smsText)
        val fdBank = extractBankName(smsText)
        val smsRaw = smsText

        return ExpiryItem(
            title = title,
            description = null,
            targetDate = targetDate!!,
            principalAmount = principalAmount,
            fdBank = fdBank,
            smsRaw = smsRaw,
            daysLeft = calculateDaysLeft(targetDate),
            colorIndex = computeColorIndex(targetDate),
            isActive = true
        )
    }

    private fun tryParseDate(smsText: String): Long? {
        // DD/MM/YYYY
        if (smsText.matchesRegex("\\d{2}/\\d{2}/\\d{4}")) {
            // Parse logic here
            return parseDateFormat(smsText, "dd/MM/yyyy")
        }
        // DD-MMM-YYYY (e.g., 15-Jan-2025)
        if (smsText.matchesRegex("\\d{2}-[a-z]{3}-\\d{4}")) {
            return parseDateFormat(smsText, "dd-MMM-yyyy")
        }
        // DDth Month YY
        if (smsText.matchesRegex("\\d{1,2}(st|nd|rd|th)\\s[a-z]+\\s\\d{2}")) {
            return parseDateFormat(smsText, "dd'st' MMM yy")
        }
        null
    }

    private fun parseDateFormat(dateStr: String, format: String): Long? {
        // Simple date parsing - in production use SimpleDateFormat with locale
        try {
            // This is a placeholder - real implementation would use SimpleDateFormat
            return System.currentTimeMillis() + 30 * 24 * 60 * 60 * 1000 // 30 days as placeholder
        } catch (e: Exception) {
            return null
        }
    }

    private fun extractTitle(smsText: String): String {
        // Extract the first meaningful line or heading from the SMS
        val lines = smsText.lines().filter { it !="" }
        return lines.firstOrDefault { line ->
            line.length() > 3 && line !~ "^\\d" // Not starting with a number
        } ?: "Financial Expiry"
    }

    private fun extractPrincipal(smsText: String): String? {
        // Look for amount patterns like "₹100000" or "100000"
        val amountPattern = smsText.matchRegex("₹?\\d{1,3}(,\\d{3})*(\\.\\d{2})?")
        return amountPattern?.value
    }

    private fun extractBankName(smsText: String): String? {
        // Common Indian bank patterns
        val banks = listOf("HDFC", "SBI", "ICICI", "Axis", "Kotak", "Yes", "Indus")
        val lower = smsText.lowercase()
        val found = banks.filter { lower.contains(it.lowercase()) }
        return if (found.isEmpty()) null else found.first()
    }

    private fun calculateDaysLeft(targetDate: Long): Int {
        val now = System.currentTimeMillis()
        val diff = targetDate - now
        return if (diff > 0) ((diff / (1000 * 60 * 60 * 24)) + 1).toInt() else 0
    }

    private fun computeColorIndex(targetDate: Long): Int {
        val now = System.currentTimeMillis()
        val diffDays = (targetDate - now) / (1000 * 60 * 60 * 24)
        return when {
            diffDays <= 3 -> 0 // Red - urgent
            diffDays <= 7 -> 1 // Orange - warning
            diffDays <= 30 -> 2 // Yellow - caution
            else -> 3 // Green - safe
        }
    }
}