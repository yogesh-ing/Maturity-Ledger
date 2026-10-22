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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afec.app.data.AfecRepository
import com.afec.app.data.InstrumentSource
import com.afec.app.data.SmsMessage
import com.afec.app.data.TrackedInstrument
import com.afec.app.domain.DateMath
import com.afec.app.notifications.NotificationHelper
import com.afec.app.ui.theme.ButtonMinHeight
import java.time.LocalDate
import java.time.ZoneId

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
        // Reused form, but framed as a review step: the CTA actually approves.
        ManualEntrySheet(
            initial = draft,
            heading = "Review parsed SMS",
            saveLabel = "Approve & Add",
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
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("You're all caught up", style = MaterialTheme.typography.titleLarge)
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
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(queue, key = { it.id }) { msg ->
                    val draft = remember(msg.id) { AfecRepository.draftFromSms(msg) }
                    QueueCard(
                        msg = msg,
                        draft = draft,
                        onApprove = { onApprove(msg, it) },
                        onEdit = { editing = msg to it },
                        onDismiss = { onDismiss(msg) }
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueCard(
    msg: SmsMessage,
    draft: TrackedInstrument?,
    onApprove: (TrackedInstrument) -> Unit,
    onEdit: (TrackedInstrument) -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        msg.sender,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Received ${DateMath.formatDate(msg.receivedAtEpoch)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ConfidenceChip(msg.confidenceScore)
            }
            Spacer(Modifier.height(12.dp))
            SectionLabel("Raw SMS")
            Text(msg.rawBody, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            SectionLabel("Parsed")
            if (draft != null) {
                Text(
                    "${draft.title} · ${draft.institutionName} · " +
                        DateMath.formatDate(draft.maturityDateEpoch) +
                        (draft.principalAmount?.let { " · ₹${NotificationHelper.formatInr(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                Text(
                    "No reliable date found — add the details manually or dismiss.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(16.dp))
            if (draft != null) {
                Button(
                    onClick = { onApprove(draft) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonMinHeight)
                ) { Text("Approve & Add") }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { onEdit(draft) },
                        modifier = Modifier.weight(1f).heightIn(min = ButtonMinHeight)
                    ) { Text("Edit") }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).heightIn(min = ButtonMinHeight)
                    ) { Text("Dismiss") }
                }
            } else {
                Button(
                    onClick = { onEdit(manualDraft(msg)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonMinHeight)
                ) { Text("Add manually") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonMinHeight)
                ) { Text("Dismiss") }
            }
        }
    }
}

/** Fallback draft for SMS the parser could not read (never a dead end). */
private fun manualDraft(msg: SmsMessage): TrackedInstrument = TrackedInstrument(
    title = "Review ${msg.sender}",
    institutionName = msg.sender,
    maturityDateEpoch = LocalDate.now().plusDays(30)
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    source = InstrumentSource.SMS_PARSED,
    sourceSmsId = msg.id,
    confidenceScore = msg.confidenceScore
)

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
    val style = confidenceStyle(score)
    val icon = when {
        score >= 85 -> Icons.Default.CheckCircle
        score >= 65 -> Icons.Default.Info
        else -> Icons.Default.Warning
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(style.background)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = style.foreground, modifier = Modifier.size(12.dp))
        Text(style.label, style = MaterialTheme.typography.labelSmall, color = style.foreground)
    }
}
