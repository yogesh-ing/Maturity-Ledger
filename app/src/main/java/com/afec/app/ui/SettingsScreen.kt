package com.afec.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.afec.app.data.NotificationPreference
import com.afec.app.ui.theme.ButtonMinHeight
import com.afec.app.ui.theme.ThemeMode
import com.afec.app.ui.theme.displayLabel

/**
 * Screen 04 — Settings (PRD §05, FR-04/FR-10/FR-12/FR-14):
 * milestone toggles (14d/7d/3d/day-of 9 AM), custom offsets, battery-optimization
 * warning, appearance (theme + optional Material You), SMS rescan, CSV/PDF export
 * and Reset Local Data (§11).
 */
@Composable
fun SettingsScreen(
    preferences: NotificationPreference,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    hasSmsPermission: Boolean,
    onSavePreferences: (NotificationPreference) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
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
    val batteryRestricted = remember {
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == false
    }
    val parsedOffset = customInput.toIntOrNull()
    val offsetInvalid = customInput.isNotEmpty() && (parsedOffset == null || parsedOffset !in 1..365)
    // Declared explicitly: Compose cannot infer a @Composable lambda inside an if-expression.
    val offsetSupporting: (@Composable () -> Unit)? =
        if (offsetInvalid) { { Text("Enter a value between 1 and 365") } } else null

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false; resetText = "" },
            title = { Text("Reset Local Data?") },
            text = {
                Column {
                    Text(
                        "This permanently erases every instrument, SMS audit record and alert " +
                            "on this device. Type RESET to confirm."
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = resetText,
                        onValueChange = { resetText = it },
                        label = { Text("Type RESET") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onResetAllData(); showReset = false; resetText = "" },
                    enabled = resetText == "RESET",
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Erase everything") }
            },
            dismissButton = {
                TextButton({ showReset = false; resetText = "" }) { Text("Cancel") }
            }
        )
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SettingsSectionHeader(Icons.Default.Notifications, "Milestone alerts (9:00 AM)")
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
                Text("Custom day offsets", style = MaterialTheme.typography.titleSmall)
                if (preferences.customOffsets.isEmpty()) {
                    Text(
                        "None yet — add an interval below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(preferences.customOffsets) { offset ->
                            AssistChip(
                                onClick = {
                                    onSavePreferences(
                                        preferences.copy(
                                            customOffsets = preferences.customOffsets - offset
                                        )
                                    )
                                },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("$offset days")
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove $offset day alert",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customInput,
                        onValueChange = { customInput = it.filter(Char::isDigit) },
                        label = { Text("Add custom day offset") },
                        isError = offsetInvalid,
                        supportingText = offsetSupporting,
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            parsedOffset?.takeIf { it in 1..365 }?.let { days ->
                                onSavePreferences(
                                    preferences.copy(
                                        customOffsets = (preferences.customOffsets + days)
                                            .distinct().sorted()
                                    )
                                )
                                customInput = ""
                            }
                        },
                        enabled = parsedOffset != null && parsedOffset in 1..365,
                        modifier = Modifier.heightIn(min = ButtonMinHeight)
                    ) { Text("Add") }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Channels: Urgent (day-of & ≤3d, chime + vibration) · " +
                        "Standard (7/14d) · Parsing Activity (silent). " +
                        "Same-day alerts are grouped into one summary.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (batteryRestricted) {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.BatteryAlert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Battery optimization may delay alerts",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Whitelist Dues so 9:00 AM milestone alarms fire on time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
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
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier.heightIn(min = ButtonMinHeight)
                    ) { Text("Fix it") }
                }
            }
        }

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsSectionHeader(Icons.Default.Palette, "Appearance")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.values().forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { onThemeMode(mode) },
                            label = { Text(mode.displayLabel()) }
                        )
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Use wallpaper colours", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Material You — replaces the indigo/teal palette.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = dynamicColor, onCheckedChange = onDynamicColor)
                    }
                }
            }
        }

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsSectionHeader(Icons.Default.Storage, "Data")
                OutlinedButton(
                    onClick = onRescanSms,
                    enabled = hasSmsPermission,
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonMinHeight)
                ) { Text("Rescan last 90 days of SMS") }
                if (!hasSmsPermission) {
                    Text(
                        "SMS permission is off — Dues is in manual-entry mode.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onExportCsv,
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonMinHeight)
                ) { Text("Export to CSV") }
                OutlinedButton(
                    onClick = onExportPdf,
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonMinHeight)
                ) { Text("Export to PDF") }
                Text(
                    "Fully offline: Dues requests no internet permission and stores everything " +
                        "in a local Room database. Raw SMS are auto-purged after 30 days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = { showReset = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Reset Local Data") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsSectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
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
