package com.afec.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.afec.app.data.InstrumentStatus
import com.afec.app.data.InstrumentType
import com.afec.app.data.TrackedInstrument
import com.afec.app.domain.DateMath
import com.afec.app.domain.UrgencyTier
import com.afec.app.domain.urgencyTier

private enum class SortMode { DATE, AMOUNT, URGENCY }

/**
 * Screen 01 — Countdown Dashboard (PRD §05): sticky header + portfolio summary chip,
 * urgency sections Urgent (≤7d) / Approaching (8–30d) / Comfortable (31d+) / Completed,
 * sorted by closest maturity first (FR-03), filter & sort (FR-11), FAB + Add Manual.
 */
@Composable
fun DashboardScreen(
    instruments: List<TrackedInstrument>,
    manualOnlyMode: Boolean,
    onAdd: () -> Unit,
    onOpenSettings: () -> Unit,
    onItemClick: (TrackedInstrument) -> Unit,
    onRestore: (TrackedInstrument) -> Unit,
    onDeleteForever: (TrackedInstrument) -> Unit
) {
    var typeFilter by remember { mutableStateOf<InstrumentType?>(null) }
    var institutionFilter by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(SortMode.DATE) }

    val visible = instruments.filter {
        (typeFilter == null || it.instrumentType == typeFilter) &&
            (institutionFilter == null || it.institutionName == institutionFilter)
    }
    val active = visible.filter {
        it.status == InstrumentStatus.ACTIVE || it.status == InstrumentStatus.OVERDUE
    }.let { list ->
        when (sort) {
            SortMode.DATE, SortMode.URGENCY -> list.sortedBy { it.maturityDateEpoch }
            SortMode.AMOUNT -> list.sortedByDescending { it.principalAmount ?: 0.0 }
        }
    }
    val completed = visible.filter { it.status == InstrumentStatus.COMPLETED }
        .sortedBy { it.maturityDateEpoch }
    val archived = visible.filter { it.status == InstrumentStatus.DISMISSED }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Add Manual")
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("AFEC", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Automated Financial Expiry Countdown",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
                Spacer(Modifier.height(8.dp))
                val nearest = active.minByOrNull { it.maturityDateEpoch }
                AssistChip(
                    onClick = { nearest?.let(onItemClick) ?: onAdd() },
                    label = {
                        Text(
                            if (nearest == null) "No instruments yet — add your first"
                            else "Nearest: ${nearest.title} · ${DateMath.daysLeft(nearest.maturityDateEpoch)} days"
                        )
                    }
                )
            }

            if (manualOnlyMode) {
                item {
                    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Manual-entry mode", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "SMS access is off, so automatic capture is paused. " +
                                    "Manual Entry remains fully available.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(typeFilter == null, { typeFilter = null }, { Text("All") })
                    }
                    items(InstrumentType.values()) { type ->
                        FilterChip(
                            typeFilter == type, { typeFilter = type },
                            { Text(type.name) }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                val institutions = instruments.map { it.institutionName }.distinct().sorted()
                if (institutions.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(institutionFilter == null, { institutionFilter = null }, { Text("All banks") })
                        }
                        items(institutions) { bank ->
                            FilterChip(institutionFilter == bank, { institutionFilter = bank }, { Text(bank) })
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SortMode.values()) { mode ->
                        FilterChip(sort == mode, { sort = mode }, { Text("Sort: ${mode.name}") })
                    }
                }
            }

            if (active.isEmpty() && completed.isEmpty() && archived.isEmpty()) {
                item { EmptyState(onAdd) }
            }

            val byTier = active.groupBy { urgencyTier(DateMath.daysLeft(it.maturityDateEpoch)) }
            dashboardSection(
                "🔴 Urgent (≤7 days)",
                (byTier[UrgencyTier.OVERDUE].orEmpty() + byTier[UrgencyTier.URGENT].orEmpty()),
                onItemClick
            )
            dashboardSection("🟠 Approaching (8–30 days)", byTier[UrgencyTier.APPROACHING].orEmpty(), onItemClick)
            dashboardSection("🟢 Comfortable (31+ days)", byTier[UrgencyTier.COMFORTABLE].orEmpty(), onItemClick)
            dashboardSection("✓ Completed", completed, onItemClick)

            if (archived.isNotEmpty()) {
                item { Text("Archived", style = MaterialTheme.typography.titleLarge) }
                items(archived, key = { it.id }) { item ->
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Recoverable for 30 days from archive date.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row {
                                TextButton({ onRestore(item) }) { Text("Restore") }
                                TextButton({ onDeleteForever(item) }) { Text("Delete forever") }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

/** One urgency-grouped section of the dashboard (header + cards). */
private fun androidx.compose.foundation.lazy.LazyListScope.dashboardSection(
    title: String,
    list: List<TrackedInstrument>,
    onItemClick: (TrackedInstrument) -> Unit
) {
    if (list.isEmpty()) return
    item { Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleLarge) }
    items(list, key = { it.id }) { item ->
        CountdownCard(item, onClick = { onItemClick(item) })
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Card {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text("Nothing tracked yet", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "AFEC watches FD maturities, card expiries and renewals from your " +
                    "financial SMS — all on-device, nothing leaves this phone.",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAdd) { Text("Add your first instrument") }
        }
    }
}
