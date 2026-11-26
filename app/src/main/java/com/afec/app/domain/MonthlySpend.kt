package com.afec.app.domain

import java.time.YearMonth

data class MonthlyTotal(
    val spentMinor: Long,
    val categories: List<Pair<SpendCategory, Long>>,
    val count: Int
)

object MonthlySpend {
    fun summarize(spends: List<Spend>, month: YearMonth): MonthlyTotal {
        val inMonth = spends.filter { YearMonth.from(it.occurredOn) == month }.distinct()
        val spent = inMonth.filter { it.kind == TxnKind.SPEND }.sumOf { it.amountMinor }
        val refunded = inMonth.filter { it.kind == TxnKind.REFUND }.sumOf { it.amountMinor }
        val cats = inMonth.filter { it.kind == TxnKind.SPEND }
            .groupBy { it.category }
            .mapValues { (_, v) -> v.sumOf { it.amountMinor } }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
        return MonthlyTotal(spent - refunded, cats, inMonth.size)
    }
}
