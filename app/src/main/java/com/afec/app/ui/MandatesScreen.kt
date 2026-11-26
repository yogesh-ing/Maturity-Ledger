package com.afec.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afec.app.data.CardMandate
import com.afec.app.data.MandateStatus
import com.afec.app.domain.MandateMath
import com.afec.app.ui.theme.ButtonMinHeight
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Screen — Card mandates: every recurring auto-debit on the user's credit cards,
 * grouped by card, with the committed monthly total on top.
 *
 * Recurring debits are tracked separately from countdowns (they never "mature"),
 * so this screen owns its own grouping, totals and pause/resume actions.
 */
@Composable
fun MandatesScreen(
    mandates: List<CardMandate>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (CardMandate) -> Unit,
    onTogglePause: (CardMandate) -> Unit
) {
    val active = mandates.filter { it.status == MandateStatus.ACTIVE }
    val paused = mandates.filter { it.status == MandateStatus.PAUSED }
    val ended = mandates.filter { it.status == MandateStatus.REVOKED }

    val monthlyTotal = MandateMath.monthlyTotal(mandates)
    val variableCount = active.count { it.amount == null || !it.frequency.isFixedSchedule }
    val nextUp = active
        .mapNotNull { m -> MandateMath.nextDebit(m)?.let { m to it } }
        .minByOrNull { it.second }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(4.dp))
                Text("Card mandates", style = MaterialTheme.typography.headlineMedium)
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Committed each month",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        MandateMath.formatRupees(monthlyTotal),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    val parts = buildList {
                        add("${active.size} active")
                        if (variableCount > 0) add("$variableCount variable")
                        nextUp?.let { (m, date) ->
                            add(
                                "next: ${m.merchantName} on " +
                                    date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
                            )
                        }
                    }
                    Text(
                        parts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        if (mandates.isEmpty()) {
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No mandates yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Add the auto-debits already running on your cards — subscriptions, " +
                                "insurance premiums, SIP-style SIs — and this screen totals what " +
                                "they cost you every month.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Active mandates, grouped by the card they debit.
        active.groupBy { it.cardLabel }
            .toList()
            .sortedBy { (_, list) -> list.minOf { MandateMath.nextDebit(it)?.toEpochDay() ?: Long.MAX_VALUE } }
            .forEach { (cardLabel, list) ->
                item(key = "group-$cardLabel") {
                    val subtotal = MandateMath.monthlyTotal(list)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            cardLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (subtotal > 0) {
                            Text(
                                "${MandateMath.formatRupees(subtotal)}/mo",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                items(list, key = { it.id }) { mandate ->
                    MandateRow(
                        mandate = mandate,
                        onClick = { onEdit(mandate) },
                        onTogglePause = { onTogglePause(mandate) }
                    )
                }
            }

        if (paused.isNotEmpty()) {
            item(key = "paused-header") {
                Text(
                    "Paused",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(paused, key = { it.id }) { mandate ->
                MandateRow(
                    mandate = mandate,
                    onClick = { onEdit(mandate) },
                    onTogglePause = { onTogglePause(mandate) }
                )
            }
        }

        if (ended.isNotEmpty()) {
            item(key = "ended-header") {
                Text(
                    "Cancelled",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(ended, key = { it.id }) { mandate ->
                MandateRow(
                    mandate = mandate,
                    onClick = { onEdit(mandate) },
                    onTogglePause = { onTogglePause(mandate) }
                )
            }
        }

        item {
            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonMinHeight)
            ) { Text("Add mandate") }
        }
    }
}

@Composable
private fun MandateRow(
    mandate: CardMandate,
    onClick: () -> Unit,
    onTogglePause: () -> Unit
) {
    val next = MandateMath.nextDebit(mandate)
    val days = next?.let { MandateMath.daysUntil(it) }
    val amountLabel = mandate.amount?.let { MandateMath.formatRupees(it) } ?: "Variable amount"
    val schedule = MandateMath.scheduleLabel(mandate)
    val whenLabel = when {
        mandate.status == MandateStatus.REVOKED -> "Cancelled"
        mandate.status == MandateStatus.PAUSED -> "Paused"
        !mandate.frequency.isFixedSchedule -> mandate.lastDebitEpoch?.let {
            "Last debit " + MandateMath.toLocalDate(it)
                .format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
        } ?: "Debits as and when presented"
        days == null -> "No further debits"
        days < 0 -> "Overdue by ${-days} day${if (days == -1L) "" else "s"}"
        days == 0L -> "Debits today"
        days == 1L -> "Debits tomorrow"
        else -> "Next in $days days"
    }

    Card(onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    mandate.merchantName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$amountLabel · $schedule",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    whenLabel,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (mandate.status == MandateStatus.ACTIVE && (days ?: Long.MAX_VALUE) <= 3)
                        FontWeight.SemiBold else FontWeight.Normal,
                    color = if (mandate.status == MandateStatus.ACTIVE && (days ?: Long.MAX_VALUE) <= 3)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (mandate.status != MandateStatus.REVOKED) {
                TextButton(onClick = onTogglePause) {
                    Text(if (mandate.status == MandateStatus.ACTIVE) "Pause" else "Resume")
                }
            }
        }
    }
}
