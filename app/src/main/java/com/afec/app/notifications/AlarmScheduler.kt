package com.afec.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.afec.app.data.InstrumentStatus
import com.afec.app.data.NotificationPreference
import com.afec.app.data.TrackedInstrument
import java.time.LocalDate
import java.time.ZoneId

/**
 * Milestone alarms at 9:00 AM local (PRD §07/§10): 14d / 7d / 3d / day-of + custom offsets.
 * Exact alarms when permitted (SCHEDULE_EXACT_ALARM); falls back to inexact
 * alarms otherwise (PRD §12: device lacks exact-alarm capability).
 */
class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun scheduleFor(item: TrackedInstrument, pref: NotificationPreference) {
        cancelFor(item, allKnownOffsets(item, pref))
        if (item.status != InstrumentStatus.ACTIVE && item.status != InstrumentStatus.OVERDUE) return
        val offsets = if (item.useCustomAlerts && item.alertOverrides.isNotEmpty()) item.alertOverrides else pref.enabledOffsets()
        val zone = ZoneId.systemDefault()
        val maturity = java.time.Instant.ofEpochMilli(item.maturityDateEpoch).atZone(zone).toLocalDate()
        offsets.forEach { offset ->
            val trigger = maturity.minusDays(offset.toLong())
                .atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
            if (trigger > System.currentTimeMillis()) {
                setAlarm(item, offset, trigger)
            } else if (offset == 0 && LocalDate.now(zone) == maturity) {
                // Day-of already reached before scheduling (e.g. just imported): fire at next 9 AM check
                // by scheduling one minute out so the user still gets the day-of alert.
                setAlarm(item, offset, System.currentTimeMillis() + 60_000)
            }
        }
    }

    /** Snooze: one-time alarm +1 day at 9 AM; stored maturity date is untouched (§10). */
    fun scheduleSnooze(item: TrackedInstrument, offsetDays: Int) {
        val trigger = LocalDate.now().plusDays(1).atTime(9, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        setAlarm(item, offsetDays, trigger)
    }

    fun cancelFor(item: TrackedInstrument) = cancelFor(item, DEFAULT_OFFSETS + item.alertOverrides)

    private fun cancelFor(item: TrackedInstrument, offsets: List<Int>) {
        offsets.distinct().forEach { offset ->
            alarmManager.cancel(pendingIntent(item, offset))
        }
    }

    private fun setAlarm(item: TrackedInstrument, offsetDays: Int, triggerAtMillis: Long) {
        val pi = pendingIntent(item, offsetDays)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager?.canScheduleExactAlarms() == true
        runCatching {
            if (canExact) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }.onFailure {
            runCatching { alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi) }
        }
    }

    private fun pendingIntent(item: TrackedInstrument, offsetDays: Int): PendingIntent {
        val intent = Intent(context, AlertActionReceiver::class.java).apply {
            action = NotificationHelper.ACTION_FIRE
            putExtra(NotificationHelper.EXTRA_INSTRUMENT_ID, item.id)
            putExtra(NotificationHelper.EXTRA_OFFSET_DAYS, offsetDays)
        }
        return PendingIntent.getBroadcast(
            context, item.id.hashCode() * 31 + offsetDays, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun allKnownOffsets(item: TrackedInstrument, pref: NotificationPreference): List<Int> =
        DEFAULT_OFFSETS + pref.enabledOffsets() + item.alertOverrides + item.notificationsSent

    companion object { val DEFAULT_OFFSETS = listOf(45, 30, 21, 14, 7, 3, 1, 0) }
}
