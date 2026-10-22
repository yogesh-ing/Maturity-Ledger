package com.afec.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.afec.app.data.AfecRepository
import com.afec.app.data.SmsMessage
import com.afec.app.data.TrackedInstrument
import com.afec.app.domain.DateMath
import com.afec.app.ui.theme.ComfortableGreen
import com.afec.app.ui.theme.ComfortableGreenContainer
import com.afec.app.ui.theme.ComfortableOnGreenContainer
import com.afec.app.ui.theme.UrgentRed
import com.afec.app.ui.theme.UrgentRedContainer
import com.afec.app.ui.theme.UrgentOnRedContainer

/**
 * Screen 02 — SMS Verification Queue (PRD §05, FR-09):
 * raw SMS beside parsed result, confidence badge, inline edit,
 * Approve & Add / Dismiss. Empty state: "You're all caught up".
 */
@Composable
fun QueueScreen(
    queue: List<SmsMessage>,
    onApprove: (SmsMessage, TrackedInstrument) -> Unit,
    onDismiss: (SmsMessage) -> Unit
) {
    var editing by remember { mutableStateOf<Pair<SmsMessage, TrackedInstrument>?>(null) }

    editing?.let { (msg, draft) ->
        ManualEntrySheet(
            initial = draft,
            onDismiss = { editing = null },
            onSave = { edited -> onApprove(msg, edited); editing = null }
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("SMS Verification Queue", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Medium-confidence parses wait here so false positives never reach your dashboard.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        if (queue.isEmpty()) {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(ComfortableGreenContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = ComfortableOnGreenContainer,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("You're all caught up ✓", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No SMS are waiting for review. New medium-confidence parses will appear here.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(queue, key = { it.id }) { msg ->
                    val draft = remember(msg.id) { AfecRepository.draftFromSms(msg) }
                    Card(
                        shape = MaterialTheme.shapes.large,
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(msg.sender, style = MaterialTheme.typography.titleMedium)
                                ConfidenceChip(msg.confidenceScore)
                            }
                            Spacer(Modifier.height(12.dp))
                            SectionLabel("Raw SMS")
                            Text(msg.rawBody, style = MaterialTheme.typography.bodyMedium)
                            if (draft != null) {
                                Spacer(Modifier.height(12.dp))
                                SectionLabel("Parsed")
                                Text(
                                    "${draft.title} · ${draft.institutionName} · " +
                                        DateMath.formatDate(draft.maturityDateEpoch) +
                                        (draft.principalAmount?.let { " · ₹$it" } ?: ""),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button({
                                    if (draft != null) onApprove(msg, draft)
                                }, enabled = draft != null) { Text("Approve & Add") }
                                if (draft != null) {
                                    FilledTonalButton({ editing = msg to draft }) { Text("Edit") }
                                }
                                TextButton({ onDismiss(msg) }) { Text("Dismiss") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun ConfidenceChip(score: Int) {
    val (bg, fg, icon) = when {
        score >= 85 -> Triple(ComfortableGreenContainer, ComfortableOnGreenContainer, Icons.Default.CheckCircle)
        score >= 65 -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, Icons.Default.Info)
        else -> Triple(UrgentRedContainer, UrgentOnRedContainer, Icons.Default.Warning)
    }
    val label = when {
        score >= 85 -> "High · $score"
        score >= 65 -> "Medium · $score"
        else -> "Low · $score"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}
