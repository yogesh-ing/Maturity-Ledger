package com.afec.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afec.app.data.TrackedInstrument
import com.afec.app.domain.DateMath
import com.afec.app.domain.UrgencyTier
import com.afec.app.domain.urgencyTier
import com.afec.app.notifications.NotificationHelper
import com.afec.app.ui.theme.ApproachingAmber
import com.afec.app.ui.theme.ComfortableGreen
import com.afec.app.ui.theme.UrgentRed

/** Tier -> accent colour (PRD §06 urgency signalling). */
fun tierColor(tier: UrgencyTier): Color = when (tier) {
    UrgencyTier.OVERDUE, UrgencyTier.URGENT -> UrgentRed
    UrgencyTier.APPROACHING -> ApproachingAmber
    UrgencyTier.COMFORTABLE -> ComfortableGreen
    UrgencyTier.COMPLETED -> ComfortableGreen
}

fun tierLabel(tier: UrgencyTier, days: Long): String = when (tier) {
    UrgencyTier.OVERDUE -> "Overdue · ${-days}d"
    UrgencyTier.URGENT -> "Urgent · ${days}d"
    UrgencyTier.APPROACHING -> "Approaching · ${days}d"
    UrgencyTier.COMFORTABLE -> "Comfortable · ${days}d"
    UrgencyTier.COMPLETED -> "✓ Completed"
}

/**
 * Dashboard card anatomy (PRD §06): padding 16dp, radius lg (16dp),
 * 4dp coloured left-accent bar, bold title, large day counter,
 * "Bank · ₹amount · Maturity dd MMM yyyy" sub-text. Min touch target 48dp (§09).
 */
@Composable
fun CountdownCard(item: TrackedInstrument, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val days = DateMath.daysLeft(item.maturityDateEpoch)
    val tier = urgencyTier(days, item.status.name == "COMPLETED")
    val accent = tierColor(tier)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
            .semantics { contentDescription = "${item.title}, ${tierLabel(tier, days)}" },
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(
                Modifier.weight(1f).padding(16.dp)
            ) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                val amount = item.principalAmount?.let { "₹${NotificationHelper.formatInr(it)} · " } ?: ""
                Text(
                    "${item.institutionName} · $amount Maturity ${DateMath.formatDate(item.maturityDateEpoch)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "● ${tierLabel(tier, days)}" + if (item.source.name == "SMS_PARSED") " · SMS Parsed" else "",
                    style = MaterialTheme.typography.labelSmall, color = accent
                )
            }
            Column(
                Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when {
                        tier == UrgencyTier.COMPLETED -> "✓"
                        days < 0 -> "${-days}"
                        else -> "$days"
                    },
                    style = MaterialTheme.typography.displayMedium, color = accent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        tier == UrgencyTier.COMPLETED -> "done"
                        days < 0 -> "days overdue"
                        days == 1L -> "day left"
                        else -> "days left"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
