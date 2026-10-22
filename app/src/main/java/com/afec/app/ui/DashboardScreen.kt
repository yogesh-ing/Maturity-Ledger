package com.afec.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afec.app.data.InstrumentStatus
import com.afec.app.data.InstrumentType
import com.afec.app.data.TrackedInstrument
import com.afec.app.domain.DateMath
import com.afec.app.domain.UrgencyTier
import com.afec.app.domain.urgencyTier
import com.afec.app.ui.theme.HeroGradientEnd
import com.afec.app.ui.theme.HeroGradientStart
import com.afec.app.ui.theme.HeroStatApproaching
import com.afec.app.ui.theme.HeroStatComfortable
import com.afec.app.ui.theme.HeroStatUrgent

private enum class SortMode(val label: String) {
    DATE("By date"),
    AMOUNT("By amount")
}

/**
 * Screen 01 — Countdown Dashboard (PRD §05): portfolio summary hero, urgency
 * sections Urgent & overdue (≤7d) / Approaching (8–30d) / Comfortable (31d+) /
 * Completed, closest maturity first (FR-03), filter & sort (FR-11).
 *
 * The FAB and the snackbar host live in the app-level Scaffold (see AfecApp), so
 * this screen is pure content — no nested Scaffold and no double insets.
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
    }.sortedBy { it.maturityDateEpoch }
    val completed = visible.filter { it.status == InstrumentStatus.COMPLETED }
        .sortedByDescending { it.maturityDateEpoch }
    val archived = visible.filter { it.status == InstrumentStatus.DISMISSED }
        .sortedByDescending { it.deletedAtEpoch ?: 0L }
    val shown = when (sort) {
        SortMode.DATE -> active.sortedBy { it.maturityDateEpoch }
        SortMode.AMOUNT -> active.sortedByDescending { it.principalAmount ?: 0.0 }
    }

    val institutions = instruments.map { it.institutionName }
        .filter { it.isNotBlank() }.distinct().sorted()
    val nearest = active.minByOrNull { it.maturityDateEpoch }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DashboardHeader(
                active = active,
                completed = completed,
                onOpenSettings = onOpenSettings
            )
        }

        if (nearest != null) {
            val nearestDays = DateMath.daysLeft(nearest.maturityDateEpoch)
            item {
                AssistChip(
                    onClick = { onItemClick(nearest) },
                    label = {
                        Text(
                            "Nearest: ${nearest.title} · " + when {
                                nearestDays < 0 -> "${-nearestDays} days overdue"
                                nearestDays == 0L -> "due today"
                                nearestDays == 1L -> "1 day left"
                                else -> "$nearestDays days left"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
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

        if (instruments.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(typeFilter == null, { typeFilter = null }, { Text("All types") })
                        }
                        items(InstrumentType.values().toList()) { type ->
                            FilterChip(
                                typeFilter == type,
                                { typeFilter = if (typeFilter == type) null else type },
                                { Text(type.displayLabel()) }
                            )
                        }
                    }
                    if (institutions.size > 1) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    institutionFilter == null,
                                    { institutionFilter = null },
                                    { Text("All banks") }
                                )
                            }
                            items(institutions) { bank ->
                                FilterChip(
                                    institutionFilter == bank,
                                    { institutionFilter = if (institutionFilter == bank) null else bank },
                                    { Text(bank) }
                                )
                            }
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(SortMode.values().toList()) { mode ->
                            FilterChip(sort == mode, { sort = mode }, { Text(mode.label) })
                        }
                    }
                }
            }
        }

        when {
            instruments.isEmpty() -> item { EmptyState(onAdd) }
            visible.isEmpty() -> item {
                NoMatchesState { typeFilter = null; institutionFilter = null }
            }
            else -> {
                val byTier = shown.groupBy { urgencyTier(DateMath.daysLeft(it.maturityDateEpoch)) }
                val urgent = byTier[UrgencyTier.OVERDUE].orEmpty() +
                    byTier[UrgencyTier.URGENT].orEmpty()
                dashboardSection(UrgencyTier.URGENT, urgent, onItemClick)
                dashboardSection(
                    UrgencyTier.APPROACHING,
                    byTier[UrgencyTier.APPROACHING].orEmpty(), onItemClick
                )
                dashboardSection(
                    UrgencyTier.COMFORTABLE,
                    byTier[UrgencyTier.COMFORTABLE].orEmpty(), onItemClick
                )
                dashboardSection(UrgencyTier.COMPLETED, completed, onItemClick)
            }
        }

        if (archived.isNotEmpty()) {
            item {
                SectionHeader(
                    tier = UrgencyTier.COMPLETED,
                    title = "Archived",
                    count = archived.size,
                    tint = MaterialTheme.colorScheme.outline
                )
            }
            items(archived, key = { it.id }) { item ->
                ArchivedCard(item, onRestore = { onRestore(item) }, onDeleteForever = { onDeleteForever(item) })
            }
        }
    }
}

/** One urgency-grouped section of the dashboard (header + cards). */
private fun LazyListScope.dashboardSection(
    tier: UrgencyTier,
    list: List<TrackedInstrument>,
    onItemClick: (TrackedInstrument) -> Unit
) {
    if (list.isEmpty()) return
    item { SectionHeader(tier, tierSectionTitle(tier), list.size) }
    items(list, key = { it.id }) { item ->
        CountdownCard(item, onClick = { onItemClick(item) })
    }
}

/** Coloured dot + section title + count pill (replaces the old emoji headings). */
@Composable
private fun SectionHeader(
    tier: UrgencyTier,
    title: String,
    count: Int,
    tint: Color = tierColor(tier)
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(tint)
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(8.dp))
        Text(
            "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/** Dashboard header: gradient hero + summary stats row + settings icon. */
@Composable
private fun DashboardHeader(
    active: List<TrackedInstrument>,
    completed: List<TrackedInstrument>,
    onOpenSettings: () -> Unit
) {
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
                .padding(20.dp)
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(Modifier.weight(1f)) {
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
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Open settings",
                            tint = Color.White
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SummaryStat(
                        label = "Total",
                        spoken = "instruments tracked",
                        value = active.size + completed.size,
                        color = Color.White
                    )
                    SummaryStat(
                        label = "≤7d",
                        spoken = "urgent, due within 7 days",
                        value = active.count {
                            urgencyTier(DateMath.daysLeft(it.maturityDateEpoch)) in
                                listOf(UrgencyTier.OVERDUE, UrgencyTier.URGENT)
                        },
                        color = HeroStatUrgent
                    )
                    SummaryStat(
                        label = "8–30d",
                        spoken = "approaching, 8 to 30 days",
                        value = active.count {
                            urgencyTier(DateMath.daysLeft(it.maturityDateEpoch)) ==
                                UrgencyTier.APPROACHING
                        },
                        color = HeroStatApproaching
                    )
                    SummaryStat(
                        label = "31d+",
                        spoken = "comfortable, more than 31 days",
                        value = active.count {
                            urgencyTier(DateMath.daysLeft(it.maturityDateEpoch)) ==
                                UrgencyTier.COMFORTABLE
                        },
                        color = HeroStatComfortable
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, spoken: String, value: Int, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .clearAndSetSemantics { contentDescription = "$value $spoken" }
    ) {
        Text(
            "$value",
            style = MaterialTheme.typography.headlineMedium,
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
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
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("Nothing tracked yet", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Dues watches FD maturities, card expiries and renewals from your " +
                    "financial SMS — all on-device, nothing leaves this phone.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAdd) { Text("Add your first instrument") }
        }
    }
}

/**
 * Shown when filters hide every item — previously this reused "Nothing tracked
 * yet", which wrongly told users their portfolio was empty.
 */
@Composable
private fun NoMatchesState(onClearFilters: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("No instruments match these filters", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Try another type or bank, or clear the filters to see everything.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onClearFilters) { Text("Clear filters") }
        }
    }
}

/** Archived row: shows the actual recovery window instead of generic copy. */
@Composable
private fun ArchivedCard(
    item: TrackedInstrument,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            val archivedAt = item.deletedAtEpoch
            Text(
                if (archivedAt != null) {
                    "Archived ${DateMath.formatDate(archivedAt)} · restorable until " +
                        DateMath.formatDate(archivedAt + 30L * 24 * 60 * 60 * 1000)
                } else {
                    "Archived · restorable within 30 days"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
                TextButton(onClick = onRestore) { Text("Restore") }
                TextButton(onClick = onDeleteForever) {
                    Text("Delete forever", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
