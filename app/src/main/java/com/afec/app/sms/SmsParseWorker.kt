package com.afec.app.sms

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.afec.app.data.AfecRepository
import com.afec.app.notifications.NotificationHelper

/** Background parse + route (PRD §07 flow): auto-add / queue / silent log. */
class SmsParseWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sender = inputData.getString(KEY_SENDER) ?: return Result.success()
        val body = inputData.getString(KEY_BODY) ?: return Result.success()
        val receivedAt = inputData.getLong(KEY_RECEIVED_AT, System.currentTimeMillis())
        val repo = AfecRepository(applicationContext)
        repo.seedPatternsIfEmpty()
        return try {
            when (val result = repo.ingestSms(sender, body, receivedAt)) {
                is AfecRepository.IngestResult.Queued ->
                    NotificationHelper.showQueued(applicationContext, 1)
                is AfecRepository.IngestResult.AutoAdded ->
                    NotificationHelper.showAutoAdded(applicationContext, result.item)
                else -> Unit // silent log stays silent (S07)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry() // zero data loss on transient failure (§09 reliability)
        }
    }

    companion object {
        const val KEY_SENDER = "sender"
        const val KEY_BODY = "body"
        const val KEY_RECEIVED_AT = "received_at"
    }
}

/** Periodic maintenance: purge raw SMS > 30 days + archived items > 30 days (§11/FR-08). */
class PurgeWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        AfecRepository(applicationContext).runPurge()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
