package com.afec.app.ui

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.afec.app.data.NotificationPreference
import com.afec.app.ui.theme.ThemeMode

/**
 * Screen 04 — Alert Settings (PRD §05, FR-04/FR-10) plus app-level settings:
 * milestone toggles (14d/7d/3d/day-of 9 AM), custom offsets, battery-optimization
 * warning card, theme (FR-14), SMS rescan, CSV export (FR-12), Reset Local Data (§11).
 */
@Composable
fun SettingsScreen(
    preferences: NotificationPreference,
    themeMode: ThemeMode,
    hasSmsPermission: Boolean,
    onSavePreferences: (NotificationPreference) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onRescanSms: () -> Unit,
    onExportCsv: () -> Unit,
    onExportPdf: () -> Unit,
    onResetAllData: () -> Unit
) {
    val context = LocalContext.current
    var customInput by remember { mutableStateOf("") }
    var showReset by remember { mutableStateOf(false) }
    var resetText by remember { mutableStateOf("") }

    val powerManager = context.getSystemService(PowerManager::class.java)
    val batteryOptimised = remember {
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == false
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Reset Local Data?") },
            text = {
                Column {
                    Text("This permanently erases every instrument, SMS audit record and alert on this device. Type RESET to confirm.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(resetText, { resetText = it }, label = { Text("Type RESET") })
                }
            },
            confirmButton = {
                TextButton(
                    { onResetAllData(); showReset = false; resetText = "" },
                    enabled = resetText == "RESET"
                ) { Text("Erase everything") }
            },
            dismissButton = { TextButton({ showReset = false }) { Text("Cancel") } }
        )
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Alert Settings", style = MaterialTheme.typography.headlineMedium)

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Milestone alerts (9:00 AM)", style = MaterialTheme.typography.titleMedium)
                ToggleRow("14 Days Out", preferences.alert14Days) {
                    onSavePreferences(preferences.copy(alert14Days = it))
                }
                ToggleRow("7 Days Out", preferences.alert7Days) {
                    onSavePreferences(preferences.copy(alert7Days = it))
                }
                ToggleRow("3 Days Out", preferences.alert3Days) {
                    onSavePreferences(preferences.copy(alert3Days = it))
                }
                ToggleRow("Day-Of (9:00 AM)", preferences.alertDayOf) {
                    onSavePreferences(preferences.copy(alertDayOf = it))
                }
                Spacer(Modifier.height(8.dp))
                Text("Custom day offsets", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    preferences.customOffsets.forEach { offset ->
                        AssistChip(
                            onClick = {
                                onSavePreferences(
                                    preferences.copy(customOffsets = preferences.customOffsets - offset)
                                )
                            },
                            label = { Text("$offset d  ✕") }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        customInput, { customInput = it.filter(Char::isDigit) },
                        label = { Text("Add custom day offset") },
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                    Button(onClick = {
                        customInput.toIntOrNull()?.takeIf { it in 1..365 }?.let { days ->
                            onSavePreferences(
                                preferences.copy(
                                    customOffsets = (preferences.customOffsets + days).distinct().sorted()
                                )
                            )
                            customInput = ""
                        }
                    }) { Text("Add") }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Channels: Urgent (day-of & ≤3d, chime + vibration) · " +
                        "Standard (7/14d) · Parsing Activity (silent). " +
                        "Same-day alerts are grouped into one summary.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (batteryOptimised) {
            Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Battery optimization may delay alerts", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Whitelist AFEC so 9:00 AM milestone alarms fire on time.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}")
                                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                            )
                        }.onFailure {
                            context.startActivity(
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                            )
                        }
                    }) { Text("Fix it") }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.values().forEach { mode ->
                        FilterChip(themeMode == mode, { onThemeMode(mode) }, { Text(mode.name) })
                    }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Data", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onRescanSms, enabled = hasSmsPermission) {
                    Text("Rescan last 90 days of SMS")
                }
                if (!hasSmsPermission) {
                    Text(
                        "SMS permission is off — AFEC is in manual-entry mode.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                OutlinedButton(onExportCsv) { Text("Export to CSV") }
                OutlinedButton(onExportPdf) { Text("Export to PDF") }
                Text(
                    "Fully offline: AFEC requests no internet permission and stores everything in a local Room database. Raw SMS are auto-purged after 30 days.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton({ showReset = true }) {
                    Text("Reset Local Data", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChange)
    }
}
