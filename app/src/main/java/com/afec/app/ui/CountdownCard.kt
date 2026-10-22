package com.afec.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afec.app.data.InstrumentSource
import com.afec.app.data.InstrumentStatus
import com.afec.app.data.TrackedInstrument
import com.afec.app.domain.DateMath
import com.afec.app.domain.UrgencyTier
import com.afec.app.domain.urgencyTier
import com.afec.app.notifications.NotificationHelper

/**
 * Dashboard card anatomy (PRD §06): padding 16dp, radius lg (16dp),
 * 4dp coloured left-accent bar, bold title, large day counter,
 * "Bank · ₹amount · Maturity dd MMM yyyy" sub-text.
 *
 * The accent bar and the progress strip are clipped by the card itself, so no
 * corner-radius constants are duplicated here.
 */
@Composable
fun CountdownCard(item: TrackedInstrument, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val days = DateMath.daysLeft(item.maturityDateEpoch)
    val tier = urgencyTier(days, item.status == InstrumentStatus.COMPLETED)
    val accent = tierColor(tier)
    val progress by animateFloatAsState(
        targetValue = countdownProgress(days),
        animationSpec = tween(durationMillis = 800),
        label = "countdownProgress"
    )
    val amount = item.principalAmount?.let { "₹${NotificationHelper.formatInr(it)}" }
    val meta = listOfNotNull(
        item.institutionName.takeIf { it.isNotBlank() },
        amount,
        "Maturity ${DateMath.formatDate(item.maturityDateEpoch)}"
    ).joinToString(" · ")
    val description = listOfNotNull(
        item.title,
        item.institutionName.takeIf { it.isNotBlank() },
        amount,
        "matures ${DateMath.formatDate(item.maturityDateEpoch)}",
        tierLabel(tier, days)
    ).joinToString(", ")

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // PRD §06: 4dp coloured left-accent bar indicating urgency tier.
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(Modifier.weight(1f).padding(16.dp)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                StatusChip(tier, days, item.source == InstrumentSource.SMS_PARSED)
            }
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (tier == UrgencyTier.COMPLETED) {
                    Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                } else {
                    Text(
                        text = if (days < 0) "${-days}" else "$days",
                        style = MaterialTheme.typography.displayMedium,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                Text(
                    text = when {
                        tier == UrgencyTier.COMPLETED -> "done"
                        days < 0 -> "days overdue"
                        days == 0L -> "due today"
                        days == 1L -> "day left"
                        else -> "days left"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // Countdown strip: fills as maturity approaches (same metaphor as Detail).
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = accent,
            trackColor = accent.copy(alpha = 0.15f),
            strokeCap = StrokeCap.Round
        )
    }
}

/**
 * Compact status chip (PRD §06: dot indicator + Label Small + full radius).
 * The dot carries the urgency colour; the chip surface is the tier container.
 */
@Composable
private fun StatusChip(tier: UrgencyTier, days: Long, fromSms: Boolean) {
    val (bg, fg) = tierContainer(tier)
    val dot = tierColor(tier)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Text(
            tierLabel(tier, days),
            style = MaterialTheme.typography.labelSmall,
            color = fg
        )
        if (fromSms) {
            Text(
                "· SMS",
                style = MaterialTheme.typography.labelSmall,
                color = fg.copy(alpha = 0.75f)
            )
        }
    }
}
