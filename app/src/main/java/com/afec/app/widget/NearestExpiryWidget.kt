package com.afec.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.afec.app.MainActivity
import com.afec.app.R
import com.afec.app.data.AfecRepository
import com.afec.app.data.InstrumentStatus
import com.afec.app.domain.DateMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** FR-13: home-screen widget surfacing the single nearest upcoming expiry. */
class NearestExpiryWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nearest = AfecRepository(context).allInstruments()
                    .filter { it.status == InstrumentStatus.ACTIVE || it.status == InstrumentStatus.OVERDUE }
                    .minByOrNull { it.maturityDateEpoch }
                ids.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.widget_nearest_expiry)
                    if (nearest == null) {
                        views.setTextViewText(R.id.widget_title, "No upcoming expiries")
                        views.setTextViewText(R.id.widget_days, "—")
                        views.setTextViewText(R.id.widget_sub, "Open Dues to add your first instrument")
                    } else {
                        val days = DateMath.daysLeft(nearest.maturityDateEpoch)
                        views.setTextViewText(R.id.widget_title, nearest.title)
                        views.setTextViewText(
                            R.id.widget_days,
                            when {
                                days < 0 -> "${-days}d overdue"
                                days == 0L -> "Today"
                                days == 1L -> "1 day left"
                                else -> "$days days left"
                            }
                        )
                        views.setTextViewText(
                            R.id.widget_sub,
                            "${nearest.institutionName} · ${DateMath.formatDate(nearest.maturityDateEpoch)}"
                        )
                    }
                    views.setOnClickPendingIntent(
                        R.id.widget_root,
                        PendingIntent.getActivity(
                            context, 7001,
                            Intent(context, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                nearest?.let { putExtra("extra_instrument_id", it.id) }
                            },
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                    manager.updateAppWidget(id, views)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
