package com.afec.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.afec.app.data.InstrumentStatus
import com.afec.app.data.InstrumentType
import com.afec.app.domain.UrgencyTier
import com.afec.app.ui.theme.ApproachingAmber
import com.afec.app.ui.theme.ApproachingAmberContainer
import com.afec.app.ui.theme.ApproachingAmberDark
import com.afec.app.ui.theme.ApproachingOnAmberContainer
import com.afec.app.ui.theme.ComfortableGreen
import com.afec.app.ui.theme.ComfortableGreenContainer
import com.afec.app.ui.theme.ComfortableGreenDark
import com.afec.app.ui.theme.ComfortableOnGreenContainer
import com.afec.app.ui.theme.DarkApproachingAmberContainer
import com.afec.app.ui.theme.DarkComfortableGreenContainer
import com.afec.app.ui.theme.DarkOnApproachingContainer
import com.afec.app.ui.theme.DarkOnComfortableContainer
import com.afec.app.ui.theme.DarkOnUrgentContainer
import com.afec.app.ui.theme.DarkUrgentRedContainer
import com.afec.app.ui.theme.UrgentOnRedContainer
import com.afec.app.ui.theme.UrgentRed
import com.afec.app.ui.theme.UrgentRedContainer
import com.afec.app.ui.theme.UrgentRedDark

/**
 * Single source of truth for urgency presentation (PRD §06 "Semantic — Urgency
 * Signaling"). Both the dashboard card and the detail screen read from here so
 * the two screens can never drift apart.
 *
 * Dark mode is derived from the *rendered* surface, not from the system setting,
 * so it stays correct when the user forces Light/Dark or enables Material You.
 */
@Composable
@ReadOnlyComposable
fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

fun tierColor(tier: UrgencyTier, dark: Boolean): Color = if (dark) {
    when (tier) {
        UrgencyTier.OVERDUE, UrgencyTier.URGENT -> UrgentRedDark
        UrgencyTier.APPROACHING -> ApproachingAmberDark
        UrgencyTier.COMFORTABLE, UrgencyTier.COMPLETED -> ComfortableGreenDark
    }
} else {
    when (tier) {
        UrgencyTier.OVERDUE, UrgencyTier.URGENT -> UrgentRed
        UrgencyTier.APPROACHING -> ApproachingAmber
        UrgencyTier.COMFORTABLE, UrgencyTier.COMPLETED -> ComfortableGreen
    }
}

@Composable
@ReadOnlyComposable
fun tierColor(tier: UrgencyTier): Color = tierColor(tier, isDarkSurface())

/** Container background + readable foreground for a tier chip. */
@Composable
@ReadOnlyComposable
fun tierContainer(tier: UrgencyTier): Pair<Color, Color> = if (isDarkSurface()) {
    when (tier) {
        UrgencyTier.OVERDUE, UrgencyTier.URGENT ->
            DarkUrgentRedContainer to DarkOnUrgentContainer
        UrgencyTier.APPROACHING ->
            DarkApproachingAmberContainer to DarkOnApproachingContainer
        UrgencyTier.COMFORTABLE, UrgencyTier.COMPLETED ->
            DarkComfortableGreenContainer to DarkOnComfortableContainer
    }
} else {
    when (tier) {
        UrgencyTier.OVERDUE, UrgencyTier.URGENT ->
            UrgentRedContainer to UrgentOnRedContainer
        UrgencyTier.APPROACHING ->
            ApproachingAmberContainer to ApproachingOnAmberContainer
        UrgencyTier.COMFORTABLE, UrgencyTier.COMPLETED ->
            ComfortableGreenContainer to ComfortableOnGreenContainer
    }
}

/** Chip / accessibility label, e.g. "Urgent · 3d". */
fun tierLabel(tier: UrgencyTier, days: Long): String = when (tier) {
    UrgencyTier.OVERDUE -> "Overdue · ${-days}d"
    UrgencyTier.URGENT -> "Urgent · ${days}d"
    UrgencyTier.APPROACHING -> "Approaching · ${days}d"
    UrgencyTier.COMFORTABLE -> "Comfortable · ${days}d"
    UrgencyTier.COMPLETED -> "Completed"
}

/** Section heading used by the dashboard groups. */
fun tierSectionTitle(tier: UrgencyTier): String = when (tier) {
    UrgencyTier.OVERDUE, UrgencyTier.URGENT -> "Urgent & overdue (≤7 days)"
    UrgencyTier.APPROACHING -> "Approaching (8–30 days)"
    UrgencyTier.COMFORTABLE -> "Comfortable (31+ days)"
    UrgencyTier.COMPLETED -> "Completed"
}

/** Days of runway represented by a full countdown ring / progress bar. */
const val COUNTDOWN_HORIZON_DAYS = 90f

/**
 * 0 → plenty of runway, 1 → due (or overdue). Used by the card bar and the
 * detail ring so both indicators describe the same thing.
 */
fun countdownProgress(daysLeft: Long): Float =
    (1f - (daysLeft.coerceIn(0, COUNTDOWN_HORIZON_DAYS.toLong()) / COUNTDOWN_HORIZON_DAYS))
        .coerceIn(0f, 1f)

/** User-facing instrument type (the enum constants are not UI copy). */
fun InstrumentType.displayLabel(): String = when (this) {
    InstrumentType.FD -> "Fixed Deposit"
    InstrumentType.RD -> "Recurring Deposit"
    InstrumentType.CARD -> "Card"
    InstrumentType.SUBSCRIPTION -> "Subscription"
    InstrumentType.OTHER -> "Other"
}

fun InstrumentStatus.displayLabel(): String = when (this) {
    InstrumentStatus.ACTIVE -> "Active"
    InstrumentStatus.COMPLETED -> "Completed"
    InstrumentStatus.DISMISSED -> "Archived"
    InstrumentStatus.OVERDUE -> "Overdue"
}

/** Confidence badge styling for the verification queue (FR-09). */
data class ConfidenceStyle(val background: Color, val foreground: Color, val label: String)

@Composable
@ReadOnlyComposable
fun confidenceStyle(score: Int): ConfidenceStyle {
    val dark = isDarkSurface()
    val high = if (dark) {
        DarkComfortableGreenContainer to DarkOnComfortableContainer
    } else {
        ComfortableGreenContainer to ComfortableOnGreenContainer
    }
    val low = if (dark) {
        DarkUrgentRedContainer to DarkOnUrgentContainer
    } else {
        UrgentRedContainer to UrgentOnRedContainer
    }
    val (bg, fg) = when {
        score >= 85 -> high
        score >= 65 -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
        else -> low
    }
    val label = when {
        score >= 85 -> "High · $score"
        score >= 65 -> "Medium · $score"
        else -> "Low · $score"
    }
    return ConfidenceStyle(bg, fg, label)
}
