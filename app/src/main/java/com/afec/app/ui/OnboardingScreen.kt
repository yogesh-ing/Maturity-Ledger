package com.afec.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * First-time onboarding (PRD §04): 3-slide value carousel →
 * plain-language permission rationale → system permission dialog (handled by caller).
 * Denied → manual-entry fallback with guided CTA (see Dashboard banner).
 */
@Composable
fun OnboardingScreen(
    onRequestPermissions: () -> Unit,
    onSkipPermissions: () -> Unit
) {
    var slide by remember { mutableIntStateOf(0) }
    val slides = listOf(
        "Never miss a maturity again" to
            "AFEC turns scattered bank SMS into one visual countdown, so an FD never quietly rolls over at a bad rate.",
        "Private by design" to
            "Everything is parsed and stored only on this phone. No accounts, no cloud sync, no internet permission.",
        "Alerts before it matters" to
            "Cascading reminders 14, 7 and 3 days before — and on the day — each instrument matures or renews."
    )

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Spacer(Modifier.height(48.dp))
            Text("AFEC", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(24.dp))
            if (slide < slides.size) {
                Text(slides[slide].first, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text(slides[slide].second, style = MaterialTheme.typography.bodyLarge)
            } else {
                Text("Why AFEC asks for SMS access", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Bank SMS contain your FD maturity, card expiry and renewal dates. " +
                                "AFEC reads them only on this device to build your countdown — " +
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
        }

        Column {
            Text(
                "Step ${minOf(slide + 1, 4)} of 4",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            if (slide < slides.size) {
                Button({ slide++ }, Modifier.fillMaxWidth()) { Text("Continue") }
                if (slide == slides.lastIndex) {
                    TextButton(onSkipPermissions, Modifier.fillMaxWidth()) {
                        Text("Skip — I'll add instruments manually")
                    }
                }
            } else {
                Button(onRequestPermissions, Modifier.fillMaxWidth()) {
                    Text("Allow SMS & notifications")
                }
                OutlinedButton(onSkipPermissions, Modifier.fillMaxWidth()) {
                    Text("Continue without SMS access")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
