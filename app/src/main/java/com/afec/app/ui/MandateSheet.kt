package com.afec.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.afec.app.data.CardMandate
import com.afec.app.data.MandateFrequency
import com.afec.app.domain.MandateMath
import com.afec.app.ui.theme.ButtonMinHeight
import java.time.Instant
import java.time.ZoneId

/**
 * Add / edit a recurring card mandate (e-mandate / standing instruction).
 *
 * The date the user picks is the *next* debit. Every later occurrence is derived
 * from it in [MandateMath], so a 31st-of-month mandate still lands on the 31st
 * after a short February.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MandateSheet(
    initial: CardMandate?,
    onDismiss: () -> Unit,
    onSave: (CardMandate) -> Unit,
    onDelete: (CardMandate) -> Unit = {}
) {
    var merchant by remember { mutableStateOf(initial?.merchantName ?: "") }
    var issuer by remember { mutableStateOf(initial?.cardIssuer ?: "") }
    var last4 by remember { mutableStateOf(initial?.cardLast4 ?: "") }
    var amount by remember { mutableStateOf(initial?.amount?.toString() ?: "") }
    var frequency by remember { mutableStateOf(initial?.frequency ?: MandateFrequency.MONTHLY) }
    var nextDebitEpoch by remember {
        mutableStateOf(
            initial?.nextDebitEpoch
                ?: java.time.LocalDate.now().plusDays(7)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
    }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    var frequencyExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = nextDebitEpoch)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { nextDebitEpoch = startOfDayLocal(it) }
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
                if (initial == null) "Add card mandate" else "Edit card mandate",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "Recurring auto-debits charged to a credit card — subscriptions, " +
                    "premiums, EMI-style SIs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("Merchant / purpose") },
                placeholder = { Text("e.g. Netflix, LIC premium") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = issuer,
                    onValueChange = { issuer = it },
                    label = { Text("Card issuer") },
                    placeholder = { Text("HDFC Bank") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = last4,
                    onValueChange = { v -> last4 = v.filter { it.isDigit() }.take(4) },
                    label = { Text("Last 4") },
                    placeholder = { Text("1234") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(108.dp),
                    singleLine = true
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { v -> amount = v.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (₹)") },
                    placeholder = { Text("Optional") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    supportingText = { Text("Leave blank if it varies") }
                )
                ExposedDropdownMenuBox(
                    frequencyExpanded,
                    { frequencyExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = frequency.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Repeats") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(frequencyExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(frequencyExpanded, { frequencyExpanded = false }) {
                        MandateFrequency.values().forEach { f ->
                            DropdownMenuItem(
                                text = { Text(f.label) },
                                onClick = { frequency = f; frequencyExpanded = false }
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonMinHeight)
            ) {
                Text(
                    (if (initial == null) "Next debit · " else "Next debit · ") +
                        MandateMath.toLocalDate(nextDebitEpoch)
                            .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"))
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                initial?.let { existing ->
                    TextButton(
                        onClick = { onDelete(existing) },
                        modifier = Modifier.heightIn(min = ButtonMinHeight)
                    ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = ButtonMinHeight)
                ) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val parsedAmount = amount.takeIf { it.isNotBlank() }?.toDoubleOrNull()
                        when {
                            merchant.isBlank() -> error = "Merchant is required"
                            issuer.isBlank() -> error = "Card issuer is required"
                            last4.isNotBlank() && last4.length != 4 -> error = "Last 4 must be 4 digits"
                            amount.isNotBlank() && (parsedAmount == null || parsedAmount <= 0.0) ->
                                error = "Amount must be a number above zero"
                            else -> onSave(
                                (initial ?: CardMandate(
                                    merchantName = merchant,
                                    cardIssuer = issuer,
                                    nextDebitEpoch = nextDebitEpoch
                                )).copy(
                                    merchantName = merchant.trim(),
                                    cardIssuer = issuer.trim(),
                                    cardLast4 = last4,
                                    amount = parsedAmount,
                                    frequency = frequency,
                                    nextDebitEpoch = nextDebitEpoch,
                                    notes = notes.takeIf { it.isNotBlank() }
                                )
                            )
                        }
                    },
                    modifier = Modifier.heightIn(min = ButtonMinHeight)
                ) { Text(if (initial == null) "Add" else "Save") }
            }
        }
    }
}

/** DatePicker works in UTC millis; normalise to local start-of-day. */
private fun startOfDayLocal(utcMillis: Long): Long {
    val date = Instant.ofEpochMilli(utcMillis).atZone(ZoneId.of("UTC")).toLocalDate()
    return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
