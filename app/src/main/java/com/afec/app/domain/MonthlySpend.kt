package com.afec.app.domain

import java.time.YearMonth

data class CategoryTotal(
    val category: SpendCategory,
    val totalMinor: Long,
    val count: Int
)

data class MonthlyTotal(
    val spentMinor: Long,
    val categoryTotals: List<CategoryTotal>,
    val transactionCount: Int,
    val refundedMinor: Long = 0L,
    val refundCount: Int = 0
)

/**
 * Aggregator for the spend overview (docs/expense_tracking_v2.md §8).
 * Pure Kotlin: no Android, no I/O, no storage.
 */
object MonthlySpend {

    const val CATEGORY_BARS = 4

    fun summarize(spends: List<Spend>, month: YearMonth): MonthlyTotal {
        // Duplicate rows are expected: sms_messages has a random UUID PK and
        // @Insert(REPLACE), so a re-scan inserts copies. Collapse the natural key.
        val inMonth = spends
            .filter { YearMonth.from(it.occurredOn) == month }
            .distinct()

        val spendEntries = inMonth.filter { it.kind == TxnKind.SPEND }
        val refundEntries = inMonth.filter { it.kind == TxnKind.REFUND }

        val totalSpend = spendEntries.sumOf { it.amountMinor }
        val totalRefund = refundEntries.sumOf { it.amountMinor }

        return MonthlyTotal(
            spentMinor = (totalSpend - totalRefund).coerceAtLeast(0L),
            categoryTotals = topCategories(spendEntries),
            transactionCount = spendEntries.size,
            refundedMinor = totalRefund,
            refundCount = refundEntries.size
        )
    }

    /** Top 4 categories keep their own bar; the remainder merges into a single OTHER bar. */
    private fun topCategories(entries: List<Spend>): List<CategoryTotal> {
        val grouped = entries
            .groupBy { it.category }
            .map { (category, list) ->
                CategoryTotal(category, list.sumOf { it.amountMinor }, list.size)
            }
            .sortedWith(
                compareByDescending<CategoryTotal> { it.totalMinor }
                    .thenBy { it.category.ordinal }
            )

        val bars = grouped.take(CATEGORY_BARS).toMutableList()
        val rest = grouped.drop(CATEGORY_BARS)
        if (rest.isNotEmpty()) {
            val otherIndex = bars.indexOfFirst { it.category == SpendCategory.OTHER }
            if (otherIndex >= 0) {
                bars[otherIndex] = bars[otherIndex].copy(
                    totalMinor = bars[otherIndex].totalMinor + rest.sumOf { it.totalMinor },
                    count = bars[otherIndex].count + rest.sumOf { it.count }
                )
            } else {
                bars.add(
                    CategoryTotal(
                        SpendCategory.OTHER,
                        rest.sumOf { it.totalMinor },
                        rest.sumOf { it.count }
                    )
                )
            }
        }
        return bars
    }
}
