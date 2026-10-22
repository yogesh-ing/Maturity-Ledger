package com.afec.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.afec.app.data.InstrumentSource
import com.afec.app.data.InstrumentType
import com.afec.app.data.TrackedInstrument
import com.afec.app.domain.DateMath
import com.afec.app.ui.theme.ButtonMinHeight
import java.time.Instant
import java.time.ZoneId

/**
 * Screen 03 — Manual Entry / Edit Sheet (PRD §05, FR-06/FR-07):
 * Title, Institution, Type, Amount, Maturity Date (picker), Notes,
 * per-item alert override toggle, Save / Cancel footer.
 *
 * Also reused by the Verification Queue. [heading] / [saveLabel] let the caller
 * describe what the sheet actually does there ("Review parsed SMS" /
 * "Approve & Add") instead of promising a plain edit that silently approves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntrySheet(
    initial: TrackedInstrument?,
    onDismiss: () -> Unit,
    onSave: (TrackedInstrument) -> Unit,
    heading: String? = null,
    saveLabel: String = "Save"
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var institution by remember { mutableStateOf(initial?.institutionName ?: "") }
    var type by remember { mutableStateOf(initial?.instrumentType ?: InstrumentType.FD) }
    var amount by remember { mutableStateOf(initial?.principalAmount?.toString() ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var dateEpoch by remember {
        mutableStateOf(
            initial?.maturityDateEpoch
                ?: java.time.LocalDate.now().plusDays(30)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
    }
    var customAlerts by remember { mutableStateOf(initial?.useCustomAlerts ?: false) }
    var offsetsText by remember {
        mutableStateOf(initial?.alertOverrides?.joinToString(", ") ?: "")
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dateEpoch)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { dateEpoch = startOfDayUtc(it) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                heading ?: if (initial == null) "Add instrument" else "Edit instrument",
                style = MaterialTheme.typography.titleLarge
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                placeholder = { Text("e.g. HDFC FD withdrawal") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = institution,
                onValueChange = { institution = it },
                label = { Text("Institution") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            ExposedDropdownMenuBox(typeExpanded, { typeExpanded = it }) {
                OutlinedTextField(
                    value = type.displayLabel(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(typeExpanded, { typeExpanded = false }) {
                    InstrumentType.values().forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t.displayLabel()) },
                            onClick = { type = t; typeExpanded = false }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Principal amount (₹, optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonMinHeight)
            ) {
                Text("Maturity date · ${DateMath.formatDate(dateEpoch)}")
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Custom alerts for this item", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (customAlerts) "Uses the days below instead of the global cascade."
                        else "Off — inherits your global milestones.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(customAlerts, { customAlerts = it })
            }
            if (customAlerts) {
                OutlinedTextField(
                    value = offsetsText,
                    onValueChange = { offsetsText = it },
                    label = { Text("Alert days before (e.g. 21, 14, 7, 0)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = ButtonMinHeight)
                ) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val parsedAmount = amount.takeIf { s -> s.isNotBlank() }?.toDoubleOrNull()
                        when {
                            title.isBlank() -> error = "Title is required"
                            institution.isBlank() -> error = "Institution is required"
                            amount.isNotBlank() && parsedAmount == null -> error = "Amount must be a number"
                            else -> onSave(
                                (initial ?: TrackedInstrument(
                                    title = title, institutionName = institution,
                                    maturityDateEpoch = dateEpoch
                                )).copy(
                                    title = title.trim(), institutionName = institution.trim(),
                                    instrumentType = type, principalAmount = parsedAmount,
                                    maturityDateEpoch = dateEpoch,
                                    notes = notes.takeIf { n -> n.isNotBlank() },
                                    useCustomAlerts = customAlerts,
                                    alertOverrides = if (customAlerts) {
                                        offsetsText.split(",").mapNotNull { s -> s.trim().toIntOrNull() }
                                            .distinct().sortedDescending()
                                    } else emptyList(),
                                    source = initial?.source ?: InstrumentSource.MANUAL_ENTRY
                                )
                            )
                        }
                    },
                    modifier = Modifier.heightIn(min = ButtonMinHeight)
                ) { Text(saveLabel) }
            }
        }
    }
}

/** DatePicker works in UTC millis; normalise to local start-of-day. */
private fun startOfDayUtc(utcMillis: Long): Long {
    val date = Instant.ofEpochMilli(utcMillis).atZone(ZoneId.of("UTC")).toLocalDate()
    return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
