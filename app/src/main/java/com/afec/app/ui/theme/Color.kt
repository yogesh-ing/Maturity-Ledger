package com.afec.app.ui.theme

import androidx.compose.ui.graphics.Color

// Design tokens — PRD §06 Design System (exact hex values from the HTML PRD).
val Primary50 = Color(0xFFEEF2FF)
val Primary100 = Color(0xFFE0E7FF)
val Primary300 = Color(0xFFA5B4FC)
val Primary500 = Color(0xFF6366F1)
val Primary600 = Color(0xFF4F46E5)
val Primary700 = Color(0xFF4338CA)
val Primary900 = Color(0xFF312E81)

val Accent50 = Color(0xFFF0FDFA)
val Accent300 = Color(0xFF5EEAD4)
val Accent500 = Color(0xFF14B8A6)
val Accent700 = Color(0xFF0F766E)

val Slate50 = Color(0xFFF8FAFC)
val Slate100 = Color(0xFFF1F5F9)
val Slate200 = Color(0xFFE2E8F0)
val Slate300 = Color(0xFFCBD5E1)
val Slate500 = Color(0xFF64748B)
val Slate700 = Color(0xFF334155)
val Slate900 = Color(0xFF0F172A)

val DarkBase = Color(0xFF0F172A)
val DarkRaised = Color(0xFF1E293B)
val DarkOverlay = Color(0xFF334155)
val DarkOnSurface = Color(0xFFE2E8F0)

// Urgency signalling (PRD §06): Critical red / Warning amber / Success green.
val UrgentRed = Color(0xFFDC2626)
val ApproachingAmber = Color(0xFFB45309) // amber-700: 5.0:1 on white, 4.6:1 on slate-100
val ComfortableGreen = Color(0xFF059669)

// M3 container tokens derived from urgency palette (tonal surfaces).
val UrgentRedContainer = Color(0xFFFEE2E2)
val UrgentOnRedContainer = Color(0xFF991B1B)
val ApproachingAmberContainer = Color(0xFFFEF3C7)
val ApproachingOnAmberContainer = Color(0xFF92400E)
val ComfortableGreenContainer = Color(0xFFD1FAE5)
val ComfortableOnGreenContainer = Color(0xFF065F46)

val DarkUrgentRedContainer = Color(0xFF7F1D1D)
val DarkApproachingAmberContainer = Color(0xFF78350F)
val DarkComfortableGreenContainer = Color(0xFF064E3B)

// Soft tints for hero / summary surfaces.

// ---------------------------------------------------------------------------
// Dark-mode urgency palette.
// The light containers above are pastel-on-light; reusing them on a dark surface
// produces a bright "sticker" chip and destroys the dark theme. These are the
// dark counterparts (same hue families, inverted luminance).
// ---------------------------------------------------------------------------
val UrgentRedDark = Color(0xFFF87171)
val ApproachingAmberDark = Color(0xFFFBBF24)
val ComfortableGreenDark = Color(0xFF34D399)

val DarkOnUrgentContainer = Color(0xFFFEE2E2)
val DarkOnApproachingContainer = Color(0xFFFEF3C7)
val DarkOnComfortableContainer = Color(0xFFD1FAE5)

// Error role for dark surfaces (UrgentRed is too dark to read on DarkBase).
val DarkError = Color(0xFFF87171)
val DarkOnError = Color(0xFF450A0A)
val DarkErrorContainer = Color(0xFF7F1D1D)

// Extra neutral steps needed for a complete M3 surface ramp.
val Slate600 = Color(0xFF475569)
val DarkContainerLowest = Color(0xFF0A101C)
val DarkContainerLow = Color(0xFF162034)
val DarkContainer = Color(0xFF1E293B)
val DarkContainerHigh = Color(0xFF273449)
val DarkContainerHighest = Color(0xFF334155)
val DarkSurfaceBright = Color(0xFF3B4A61)

/**
 * Hero gradient. The teal end is deliberately Accent700 rather than Accent500:
 * white 11sp labels on #14B8A6 measure 2.5:1 (WCAG AA fail), on #0F766E 5.5:1.
 */
val HeroGradientStart = Color(0xFF4F46E5)
val HeroGradientEnd = Color(0xFF0F766E)

// Summary numerals sitting on the gradient (all >= 3:1 against the darkest stop).
val HeroStatUrgent = Color(0xFFFFCDD2)
val HeroStatApproaching = Color(0xFFFDE68A)
val HeroStatComfortable = Color(0xFFBBF7D0)
