package com.afec.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.afec.app.data.AfecRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles FIRE (from AlarmScheduler), MARK_DONE and SNOOZE_1_DAY (from notification actions). */
class AlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(NotificationHelper.EXTRA_INSTRUMENT_ID) ?: return
        val offset = intent.getIntExtra(NotificationHelper.EXTRA_OFFSET_DAYS, 0)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AfecRepository(context)
                val item = repo.allInstruments().firstOrNull { it.id == id } ?: return@launch
                when (intent.action) {
                    NotificationHelper.ACTION_FIRE -> {
                        NotificationHelper.showMilestone(context, item, offset)
                        repo.markMilestoneSent(item, offset)
                    }
                    NotificationHelper.ACTION_MARK_DONE -> {
                        repo.markCompleted(item)
                        NotificationHelper.cancel(context, item, offset)
                    }
                    NotificationHelper.ACTION_SNOOZE -> {
                        AlarmScheduler(context).scheduleSnooze(item, offset)
                        NotificationHelper.cancel(context, item, offset)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** RECEIVE_BOOT_COMPLETED: reschedule all alarms after reboot (PRD §07/§12). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AfecRepository(context)
                repo.seedPatternsIfEmpty()
                repo.rescheduleAll()
                repo.runPurge()
            } finally {
                pending.finish()
            }
        }
    }
}
