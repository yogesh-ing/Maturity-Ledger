package com.afec.app.sms

import android.content.Context
import android.provider.Telephony
import com.afec.app.data.AfecRepository

/**
 * Onboarding step 4 (PRD §04): after READ_SMS is granted, scan the last 90 days
 * of the SMS inbox for existing instruments. Requires READ_SMS at call time.
 */
object HistoricalSmsScanner {
    suspend fun scanLast90Days(context: Context): Int {
        val repo = AfecRepository(context)
        val cutoff = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
        var ingested = 0
        val cursor = runCatching {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(cutoff.toString()),
                "${Telephony.Sms.DATE} ASC"
            )
        }.getOrNull() ?: return 0
        cursor.use {
            val addr = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
            while (it.moveToNext()) {
                val sender = if (addr >= 0) it.getString(addr) ?: continue else continue
                val body = if (bodyIdx >= 0) it.getString(bodyIdx) ?: continue else continue
                val date = if (dateIdx >= 0) it.getLong(dateIdx) else System.currentTimeMillis()
                // Cheap pre-filter mirrors FR-01 keywords; full scoring happens in ingest.
                if (com.afec.app.domain.SmsParser.KEYWORDS.any { k -> body.contains(k, true) }) {
                    repo.ingestSms(sender, body, date)
                    ingested++
                }
            }
        }
        return ingested
    }
}
