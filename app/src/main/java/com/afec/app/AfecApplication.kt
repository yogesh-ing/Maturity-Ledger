package com.afec.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.afec.app.data.AfecRepository
import com.afec.app.notifications.NotificationHelper
import com.afec.app.sms.PurgeWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class AfecApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        // Daily purge of raw SMS / archived items older than 30 days (PRD §11, FR-08).
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "afec_purge",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PurgeWorker>(1, TimeUnit.DAYS).build()
        )
        CoroutineScope(Dispatchers.IO).launch {
            val repo = AfecRepository(this@AfecApplication)
            repo.seedPatternsIfEmpty()
            repo.rescheduleAll() // also covers alarms lost to force-stop / updates (§09)
        }
    }
}
