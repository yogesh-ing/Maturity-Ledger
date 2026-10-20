package com.afec.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.unit.dp
import com.afec.app.data.InstrumentSource
import com.afec.app.data.InstrumentType
import com.afec.app.data.TrackedInstrument
import com.afec.app.domain.DateMath
import java.time.Instant
import java.time.ZoneId

/**
 * Screen 03 — Manual Entry / Edit Sheet (PRD §05, FR-06/FR-07):
 * Title, Institution, Type, Amount, Maturity Date (picker), Notes,
 * per-item alert override toggle, Save / Cancel footer.
 * Also reused to edit a Verification-Queue parse before approving it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntrySheet(
    initial: TrackedInstrument?,
    onDismiss: () -> Unit,
    onSave: (TrackedInstrument) -> Unit
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
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (initial == null) "Add instrument" else "Edit instrument",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge
            )
            OutlinedTextField(title, { title = it }, label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(institution, { institution = it }, label = { Text("Institution") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)

            ExposedDropdownMenuBox(typeExpanded, { typeExpanded = it }) {
                OutlinedTextField(
                    type.name, {}, readOnly = true, label = { Text("Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(typeExpanded, { typeExpanded = false }) {
                    InstrumentType.values().forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t.name) },
                            onClick = { type = t; typeExpanded = false }
                        )
                    }
                }
            }

            OutlinedTextField(
                amount, { amount = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Principal amount (₹, optional)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )

            OutlinedButton({ showDatePicker = true }, Modifier.fillMaxWidth()) {
                Text("Maturity Date: ${DateMath.formatDate(dateEpoch)}")
            }

            OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth())

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Custom alerts for this item")
                Switch(customAlerts, { customAlerts = it })
            }
            if (customAlerts) {
                OutlinedTextField(
                    offsetsText, { offsetsText = it },
                    label = { Text("Alert days before (e.g. 21, 14, 7, 0)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }

            error?.let {
                Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onDismiss) { Text("Cancel") }
                Button(onClick = {
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
                }) { Text("Save") }
            }
        }
    }
}

/** DatePicker works in UTC millis; normalise to local start-of-day. */
private fun startOfDayUtc(utcMillis: Long): Long {
    val date = Instant.ofEpochMilli(utcMillis).atZone(ZoneId.of("UTC")).toLocalDate()
    return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
