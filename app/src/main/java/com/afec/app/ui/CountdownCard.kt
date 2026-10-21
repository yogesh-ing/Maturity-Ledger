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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
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
import com.afec.app.ui.theme.ApproachingAmberContainer
import com.afec.app.ui.theme.ApproachingOnAmberContainer
import com.afec.app.ui.theme.ComfortableGreen
import com.afec.app.ui.theme.ComfortableGreenContainer
import com.afec.app.ui.theme.ComfortableOnGreenContainer
import com.afec.app.ui.theme.UrgentRed
import com.afec.app.ui.theme.UrgentRedContainer
import com.afec.app.ui.theme.UrgentOnRedContainer

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

/** Tier -> container background for the status chip. */
@Composable
fun tierContainer(tier: UrgencyTier): Pair<Color, Color> = when (tier) {
    UrgencyTier.OVERDUE, UrgencyTier.URGENT ->
        UrgentRedContainer to UrgentOnRedContainer
    UrgencyTier.APPROACHING ->
        ApproachingAmberContainer to ApproachingOnAmberContainer
    UrgencyTier.COMFORTABLE, UrgencyTier.COMPLETED ->
        ComfortableGreenContainer to ComfortableOnGreenContainer
}

/** Progress of the instrument term elapsed (0..1) based on created → maturity. */
private fun termProgress(item: TrackedInstrument, days: Long): Float {
    val created = item.createdAt
    val maturity = item.maturityDateEpoch
    val total = maturity - created
    if (total <= 0L) return 1f
    val elapsed = System.currentTimeMillis() - created
    return (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

/**
 * Dashboard card anatomy (PRD §06): padding 16dp, radius lg (18dp),
 * 4dp coloured left-accent bar, bold title, large day counter,
 * "Bank · ₹amount · Maturity dd MMM yyyy" sub-text. Min touch target 48dp (§09).
 *
 * v2 enhancements (mobile-android-design skill):
 *  - Tier-tinted status chip with icon
 *  - Animated term-progress bar at bottom
 *  - Surface-container elevated background
 *  - 6dp accent bar with rounded ends
 */
@Composable
fun CountdownCard(item: TrackedInstrument, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val days = DateMath.daysLeft(item.maturityDateEpoch)
    val tier = urgencyTier(days, item.status.name == "COMPLETED")
    val accent = tierColor(tier)
    val (chipBg, chipFg) = tierContainer(tier)
    val progress by animateFloatAsState(
        targetValue = termProgress(item, days),
        animationSpec = tween(durationMillis = 800),
        label = "termProgress"
    )
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
            .semantics { contentDescription = "${item.title}, ${tierLabel(tier, days)}" },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // Accent bar — slightly wider for visual weight, rounded to match card
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(accent, RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
            )
            Column(Modifier.weight(1f).padding(16.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                val amount = item.principalAmount?.let { "₹${NotificationHelper.formatInr(it)} · " } ?: ""
                Text(
                    "${item.institutionName} · $amount Maturity ${DateMath.formatDate(item.maturityDateEpoch)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                StatusChip(tier, days, item.source.name == "SMS_PARSED")
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
        // Term-progress bar at card bottom
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)),
            color = accent,
            trackColor = accent.copy(alpha = 0.15f),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

/** Compact status chip: icon + tier label + optional SMS-parsed tag. */
@Composable
private fun StatusChip(tier: UrgencyTier, days: Long, fromSms: Boolean) {
    val (bg, fg) = tierContainer(tier)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = when (tier) {
                UrgencyTier.COMPLETED -> Icons.Default.CheckCircle
                UrgencyTier.OVERDUE, UrgencyTier.URGENT -> Icons.Default.NotificationsActive
                else -> Icons.Default.Info
            },
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(12.dp)
        )
        Text(
            tierLabel(tier, days),
            style = MaterialTheme.typography.labelSmall,
            color = fg
        )
        if (fromSms) {
            Text(
                "· SMS",
                style = MaterialTheme.typography.labelSmall,
                color = fg.copy(alpha = 0.7f)
            )
        }
    }
}
