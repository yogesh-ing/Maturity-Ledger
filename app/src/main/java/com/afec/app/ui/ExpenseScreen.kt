package com.afec.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afec.app.domain.CategoryTotal
import com.afec.app.domain.MonthlyTotal
import com.afec.app.domain.SpendCategory
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Screen 04 — Expenses (docs/expense_tracking_v2.md §10). Read-only, debit-only. */
sealed interface ExpenseState {
    data object Loading : ExpenseState
    data class Ready(val total: MonthlyTotal, val month: YearMonth) : ExpenseState
    data class Empty(val month: YearMonth) : ExpenseState
}

@Composable
fun ExpenseScreen(state: ExpenseState, onEnableSms: () -> Unit) {
    val month = when (state) {
        is ExpenseState.Ready -> state.month
        is ExpenseState.Empty -> state.month
        ExpenseState.Loading -> YearMonth.now()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Expenses",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                monthLabel(month),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when (state) {
            ExpenseState.Loading -> ExpenseSkeleton()
            is ExpenseState.Empty -> ExpenseEmpty(onEnableSms)
            is ExpenseState.Ready -> {
                TotalCard(state.total)
                if (state.total.categoryTotals.isNotEmpty()) {
                    CategoryCard(state.total.categoryTotals)
                }
                if (state.total.refundCount > 0) {
                    RefundCard(state.total)
                }
            }
        }
    }
}

@Composable
private fun TotalCard(total: MonthlyTotal) {
    val amount = formatRupees(total.spentMinor)
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "This month",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
            Text(
                amount,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.semantics {
                    contentDescription = "Total spent this month: $amount"
                }
            )
            Text(
                if (total.transactionCount == 1) "1 transaction"
                else "${total.transactionCount} transactions",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "May exclude transactions from the 1st",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun CategoryCard(totals: List<CategoryTotal>) {
    val max = totals.maxOf { it.totalMinor }.coerceAtLeast(1L)
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "By category",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            totals.forEach { bar ->
                val label = categoryLabel(bar.category)
                val amount = formatRupees(bar.totalMinor)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            contentDescription = "$label, $amount"
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        Modifier.width(94.dp),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((bar.totalMinor.toFloat() / max).coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                    Text(
                        amount,
                        Modifier.width(88.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
private fun RefundCard(total: MonthlyTotal) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .semantics(mergeDescendants = true) {
                        contentDescription =
                            "Refunds and reversals, ${total.refundCount}, " +
                                "${formatRupees(total.refundedMinor)}"
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Refunds & reversals",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${total.refundCount} · ${formatRupees(total.refundedMinor)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Credited back for something already spent this month. " +
                        "Subtracted from the total above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ExpenseEmpty(onEnableSms: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "No expenses detected this month",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Make sure SMS permission is enabled — spends are read from your bank " +
                    "messages on this device only.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onEnableSms) { Text("Check SMS access") }
        }
    }
}

@Composable
private fun ExpenseSkeleton() {
    val grey = MaterialTheme.colorScheme.surfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(grey)
        )
        repeat(4) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(grey)
            )
        }
    }
}

internal fun formatRupees(minor: Long): String {
    val rupees = minor / 100
    val paise = (minor % 100).toInt()
    val grouped = NumberFormat
        .getIntegerInstance(Locale.forLanguageTag("en-IN"))
        .format(rupees)
    return if (paise == 0) "₹$grouped" else "₹$grouped.${paise.toString().padStart(2, '0')}"
}

private fun monthLabel(month: YearMonth): String =
    month.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))

private fun categoryLabel(category: SpendCategory): String = when (category) {
    SpendCategory.FOOD -> "Food & Dining"
    SpendCategory.GROCERY -> "Groceries"
    SpendCategory.TRANSPORT -> "Transport"
    SpendCategory.BILLS -> "Bills"
    SpendCategory.SUBSCRIPTION -> "Subscriptions"
    SpendCategory.SHOPPING -> "Shopping"
    SpendCategory.CASH -> "Cash"
    SpendCategory.OTHER -> "Other"
}
