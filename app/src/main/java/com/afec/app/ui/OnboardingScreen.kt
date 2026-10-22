package com.afec.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afec.app.ui.theme.ButtonMinHeight
import com.afec.app.ui.theme.HeroGradientEnd
import com.afec.app.ui.theme.HeroGradientStart

/**
 * First-time onboarding (PRD §04): 3-slide value carousel →
 * plain-language permission rationale → system permission dialog (handled by caller).
 * Denied → manual-entry fallback with guided CTA (see Dashboard banner).
 *
 * The slide column scrolls, so large font scales / small screens never clip the
 * rationale or hide the permission buttons.
 */
@Composable
fun OnboardingScreen(
    onRequestPermissions: () -> Unit,
    onSkipPermissions: () -> Unit
) {
    var slide by remember { mutableIntStateOf(0) }
    val slides = listOf(
        OnboardingSlide(
            Icons.Default.NotificationsActive,
            "Never miss a maturity again",
            "Dues turns scattered bank SMS into one visual countdown, so an FD never quietly " +
                "rolls over at a bad rate."
        ),
        OnboardingSlide(
            Icons.Default.Security,
            "Private by design",
            "Everything is parsed and stored only on this phone. No accounts, no cloud sync, " +
                "no internet permission."
        ),
        OnboardingSlide(
            Icons.Default.Info,
            "Alerts before it matters",
            "Cascading reminders 14, 7 and 3 days before — and on the day — each instrument " +
                "matures or renews."
        )
    )
    val totalSteps = slides.size + 1

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(Modifier.height(24.dp))
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(HeroGradientStart, HeroGradientEnd)
                            )
                        )
                        .padding(24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (slide < slides.size) slides[slide].icon
                            else Icons.Default.Security,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                "Dues",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )
                            Text(
                                "Every maturity, in one place",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            if (slide < slides.size) {
                Text(
                    slides[slide].title,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    slides[slide].body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text("Why Dues asks for SMS access", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Bank SMS contain your FD maturity, card expiry and renewal dates. " +
                                "Dues reads them only on this device to build your countdown — " +
                                "messages are never uploaded, shared or used for anything else. " +
                                "Raw SMS are deleted after 30 days.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "You can decline and use Manual Entry instead — it is fully supported.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        Column {
            // Page-indicator dots (3 slides + permission step)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(totalSteps) { index ->
                    val selected = index == slide
                    val width by animateDpAsState(
                        targetValue = if (selected) 24.dp else 8.dp,
                        animationSpec = tween(300),
                        label = "dotWidth"
                    )
                    val color by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        animationSpec = tween(300),
                        label = "dotColor"
                    )
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .height(8.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Step ${minOf(slide + 1, totalSteps)} of $totalSteps",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (slide > 0) {
                    TextButton(onClick = { slide-- }) { Text("Back") }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (slide < slides.size) {
                Button(
                    onClick = { slide++ },
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonMinHeight)
                ) { Text("Continue") }
                if (slide == slides.lastIndex) {
                    TextButton(onClick = onSkipPermissions, modifier = Modifier.fillMaxWidth()) {
                        Text("Skip — I'll add instruments manually")
                    }
                }
            } else {
                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonMinHeight)
                ) { Text("Allow SMS & notifications") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSkipPermissions,
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonMinHeight)
                ) { Text("Continue without SMS access") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private data class OnboardingSlide(
    val icon: ImageVector,
    val title: String,
    val body: String
)
