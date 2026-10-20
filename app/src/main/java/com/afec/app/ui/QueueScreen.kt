package com.afec.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.afec.app.data.AfecRepository
import com.afec.app.data.SmsMessage
import com.afec.app.data.TrackedInstrument
import com.afec.app.domain.DateMath

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
            Card {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("You're all caught up ✓", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No SMS are waiting for review. New medium-confidence parses will appear here.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(queue, key = { it.id }) { msg ->
                    val draft = remember(msg.id) { AfecRepository.draftFromSms(msg) }
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text(msg.sender, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    confidenceBadge(msg.confidenceScore),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        msg.confidenceScore >= 85 -> MaterialTheme.colorScheme.secondary
                                        msg.confidenceScore >= 65 -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.error
                                    }
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Raw SMS", style = MaterialTheme.typography.labelSmall)
                            Text(msg.rawBody, style = MaterialTheme.typography.bodyMedium)
                            if (draft != null) {
                                Spacer(Modifier.height(8.dp))
                                Text("Parsed", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    "${draft.title} · ${draft.institutionName} · " +
                                        DateMath.formatDate(draft.maturityDateEpoch) +
                                        (draft.principalAmount?.let { " · ₹$it" } ?: ""),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button({
                                    if (draft != null) onApprove(msg, draft)
                                }, enabled = draft != null) { Text("Approve & Add") }
                                if (draft != null) {
                                    OutlinedButton({ editing = msg to draft }) { Text("Edit") }
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

private fun confidenceBadge(score: Int): String = when {
    score >= 85 -> "● High · $score"
    score >= 65 -> "● Medium · $score"
    else -> "● Low · $score"
}
