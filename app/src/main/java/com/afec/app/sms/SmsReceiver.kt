package com.afec.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Lightweight SMS_RECEIVED receiver (PRD §07): extracts (sender, body, timestamp)
 * and enqueues a WorkManager job — no parsing or DB work on the broadcast thread.
 * Raw bodies are never logged (PRD §11).
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }
            .getOrNull() ?: return
        // Multi-part SMS: group by sender, concatenate body parts.
        messages.groupBy { it.originatingAddress ?: "unknown" }.forEach { (sender, parts) ->
            val body = parts.joinToString("") { it.messageBody ?: "" }
            if (body.isBlank()) return@forEach
            val data = Data.Builder()
                .putString(SmsParseWorker.KEY_SENDER, sender)
                .putString(SmsParseWorker.KEY_BODY, body)
                .putLong(
                    SmsParseWorker.KEY_RECEIVED_AT,
                    parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
                )
                .build()
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<SmsParseWorker>().setInputData(data).build()
            )
        }
    }
}
