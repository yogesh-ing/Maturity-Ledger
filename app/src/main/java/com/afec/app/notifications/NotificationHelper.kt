package com.afec.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.afec.app.MainActivity
import com.afec.app.R
import com.afec.app.data.TrackedInstrument
import com.afec.app.domain.DateMath

/** Notification channels + builders (PRD §10 Notification Specification). */
object NotificationHelper {
    const val CHANNEL_URGENT = "afec_urgent"       // High: day-of & <=3-day
    const val CHANNEL_STANDARD = "afec_standard"   // Default: 7/14-day
    const val CHANNEL_PARSING = "afec_parsing"     // Low: verification-queue activity

    const val EXTRA_INSTRUMENT_ID = "extra_instrument_id"
    const val EXTRA_OFFSET_DAYS = "extra_offset_days"
    const val ACTION_FIRE = "com.afec.app.action.FIRE_ALERT"
    const val ACTION_MARK_DONE = "com.afec.app.action.MARK_DONE"
    const val ACTION_SNOOZE = "com.afec.app.action.SNOOZE_1_DAY"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_URGENT, "Urgent Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Day-of and ≤3-day milestone reminders"
                    enableVibration(true)
                },
                NotificationChannel(CHANNEL_STANDARD, "Standard Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "7 / 14-day milestone reminders"
                },
                NotificationChannel(CHANNEL_PARSING, "Parsing Activity", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "New item added to verification queue"
                    setShowBadge(true)
                }
            )
        )
    }

    private fun openDetailIntent(context: Context, instrumentId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_INSTRUMENT_ID, instrumentId)
        }
        return PendingIntent.getActivity(
            context, instrumentId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showMilestone(context: Context, item: TrackedInstrument, offsetDays: Int) {
        val days = DateMath.daysLeft(item.maturityDateEpoch)
        val urgent = offsetDays <= 3
        val title = when {
            offsetDays == 0 -> "Matures today: ${item.title}"
            days < 0 -> "Overdue: ${item.title}"
            else -> "${item.title} — $days days left"
        }
        val text = "${item.institutionName} · matures ${DateMath.formatDate(item.maturityDateEpoch)}" +
            (item.principalAmount?.let { " · ₹${formatInr(it)}" } ?: "")

        val doneIntent = PendingIntent.getBroadcast(
            context, (item.id + "_done").hashCode(),
            Intent(context, AlertActionReceiver::class.java).apply {
                action = ACTION_MARK_DONE; putExtra(EXTRA_INSTRUMENT_ID, item.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozeIntent = PendingIntent.getBroadcast(
            context, (item.id + "_snooze").hashCode(),
            Intent(context, AlertActionReceiver::class.java).apply {
                action = ACTION_SNOOZE; putExtra(EXTRA_INSTRUMENT_ID, item.id)
                putExtra(EXTRA_OFFSET_DAYS, offsetDays)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, if (urgent) CHANNEL_URGENT else CHANNEL_STANDARD)
            .setSmallIcon(R.drawable.ic_stat_dues)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openDetailIntent(context, item.id))   // tap -> Instrument Detail (§10)
            .setAutoCancel(true)
            .setGroup("afec_milestones")                            // same-day alerts group (§10)
            .addAction(0, "Mark Done", doneIntent)
            .addAction(0, "Snooze 1 day", snoozeIntent)
            .build()
        notifySafe(context, (item.id.hashCode() * 31 + offsetDays), notification)
    }

    /** S10: multiple same-day alerts collapse into one expandable summary. Call after showMilestone. */
    fun showGroupSummary(context: Context, urgent: Boolean) {
        val notification = NotificationCompat.Builder(context, if (urgent) CHANNEL_URGENT else CHANNEL_STANDARD)
            .setSmallIcon(R.drawable.ic_stat_dues)
            .setContentTitle("Dues maturity alerts")
            .setContentText("You have instruments maturing soon — tap an alert for details")
            .setStyle(NotificationCompat.InboxStyle().setSummaryText("Maturity alerts"))
            .setGroup("afec_milestones")
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        notifySafe(context, 8000, notification)
    }

    /** S04 happy path: background equivalent of the toast confirmation for an auto-added item. */
    fun showAutoAdded(context: Context, item: TrackedInstrument) {
        val notification = NotificationCompat.Builder(context, CHANNEL_PARSING)
            .setSmallIcon(R.drawable.ic_stat_dues)
            .setContentTitle("Added to Dashboard")
            .setContentText("${item.title} - ${DateMath.formatDate(item.maturityDateEpoch)}")
            .setContentIntent(openDetailIntent(context, item.id))
            .setAutoCancel(true)
            .build()
        notifySafe(context, (item.id.hashCode() * 31 + 99), notification)
    }

    fun showQueued(context: Context, count: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_PARSING)
            .setSmallIcon(R.drawable.ic_stat_dues)
            .setContentTitle("New item in Verification Queue")
            .setContentText("$count parsed SMS need your review")
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 9001,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("extra_open_queue", true)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setAutoCancel(true)
            .build()
        notifySafe(context, 9001, notification)
    }

    fun cancel(context: Context, item: TrackedInstrument, offsetDays: Int) {
        NotificationManagerCompat.from(context).cancel(item.id.hashCode() * 31 + offsetDays)
    }

    private fun notifySafe(context: Context, id: Int, notification: android.app.Notification) {
        // POST_NOTIFICATIONS is requested at runtime (Android 13+); guard SecurityException.
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    fun formatInr(amount: Double): String =
        java.text.NumberFormat.getNumberInstance(java.util.Locale("en", "IN")).format(amount)
}
