package com.afec.app.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afec.app.data.InstrumentSource
import com.afec.app.data.InstrumentStatus
import com.afec.app.data.TrackedInstrument
import com.afec.app.domain.DateMath
import com.afec.app.domain.UrgencyTier
import com.afec.app.domain.urgencyTier
import com.afec.app.notifications.NotificationHelper
import com.afec.app.ui.theme.ButtonMinHeight
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Screen 05 — Instrument Detail (PRD §05): large countdown, maturity date,
 * source, collapsible raw-SMS audit trail, history timeline,
 * primary actions Renew / Edit / Delete (with confirmation).
 */
@Composable
fun DetailScreen(
    item: TrackedInstrument,
    viewModel: AfecViewModel,
    onBack: () -> Unit,
    onEdit: (TrackedInstrument) -> Unit
) {
    val days = DateMath.daysLeft(item.maturityDateEpoch)
    val completed = item.status == InstrumentStatus.COMPLETED
    val tier = urgencyTier(days, completed)
    val accent = tierColor(tier)
    val sourceSms by viewModel.selectedSms.collectAsState()
    var showRaw by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }

    LaunchedEffect(item.id) { viewModel.loadSourceSms(item) }

    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text("Archive this instrument?") },
            text = {
                Text(
                    "It moves to Archived and can be restored within 30 days. " +
                        "Its alarms are cancelled."
                )
            },
            confirmButton = {
                TextButton({ viewModel.archive(item); confirmArchive = false; onBack() }) {
                    Text("Archive")
                }
            },
            dismissButton = { TextButton({ confirmArchive = false }) { Text("Cancel") } }
        )
    }

    Column(Modifier.fillMaxSize()) {
        // Fixed header: the back affordance must not scroll away with the content.
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                item.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { countdownProgress(days) },
                            modifier = Modifier.size(180.dp),
                            color = accent,
                            trackColor = accent.copy(alpha = 0.15f),
                            strokeWidth = 12.dp,
                            strokeCap = StrokeCap.Round
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (completed) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(56.dp)
                                )
                                Text("done", style = MaterialTheme.typography.labelSmall)
                            } else {
                                Text(
                                    if (days < 0) "${-days}" else "$days",
                                    style = MaterialTheme.typography.displayMedium,
                                    color = accent
                                )
                                Text(
                                    when {
                                        days < 0 -> "days overdue"
                                        days == 0L -> "due today"
                                        days == 1L -> "day left"
                                        else -> "days left"
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Maturity: ${DateMath.formatDate(item.maturityDateEpoch)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${item.institutionName} · ${item.instrumentType.displayLabel()}" +
                            (item.principalAmount?.let {
                                " · ₹${NotificationHelper.formatInr(it)}"
                            } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Source: " +
                            (if (item.source == InstrumentSource.SMS_PARSED) "SMS-parsed"
                            else "Manual entry") +
                            (item.confidenceScore?.let { " · Confidence $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    item.notes?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("History", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "Created: ${formatTimestamp(item.createdAt)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Last edited: ${formatTimestamp(item.updatedAt)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (item.notificationsSent.isNotEmpty()) {
                        Text(
                            "Alerts already sent: " +
                                item.notificationsSent.joinToString { "${it}d" },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (sourceSms != null) {
                        TextButton({ showRaw = !showRaw }) {
                            Text(if (showRaw) "Hide raw SMS" else "Show raw SMS (audit trail)")
                        }
                        if (showRaw) {
                            Text(sourceSms!!.rawBody, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "From ${sourceSms!!.sender} · raw SMS is auto-purged after 30 days",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.renew(item) },
                    modifier = Modifier.weight(1f).heightIn(min = ButtonMinHeight)
                ) { Text("Renew (+1 yr)") }
                FilledTonalButton(
                    onClick = { onEdit(item) },
                    modifier = Modifier.weight(1f).heightIn(min = ButtonMinHeight)
                ) { Text("Edit") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.markCompleted(item); onBack() },
                    enabled = !completed,
                    modifier = Modifier.weight(1f).heightIn(min = ButtonMinHeight)
                ) { Text("Mark Completed") }
                TextButton(
                    onClick = { confirmArchive = true },
                    modifier = Modifier.weight(1f).heightIn(min = ButtonMinHeight)
                ) { Text("Archive", color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun formatTimestamp(epoch: Long): String =
    Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH))
