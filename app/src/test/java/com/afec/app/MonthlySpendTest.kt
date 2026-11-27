package com.afec.app

import com.afec.app.domain.MonthlySpend
import com.afec.app.domain.Spend
import com.afec.app.domain.SpendCategory
import com.afec.app.domain.TxnKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthlySpendTest {

    private val month = YearMonth.of(2025, 11)

    @Test
    fun `spend plus refund in one month are netted`() {
        val spends = listOf(
            Spend(100_000L, TxnKind.SPEND, LocalDate.of(2025, 11, 5), "A", SpendCategory.FOOD),
            Spend(50_000L, TxnKind.REFUND, LocalDate.of(2025, 11, 10), "A", SpendCategory.FOOD)
        )
        val total = MonthlySpend.summarize(spends, month)
        assertEquals(50_000L, total.spentMinor)
        assertEquals(1, total.transactionCount)
        assertEquals(1, total.refundCount)
        assertEquals(50_000L, total.refundedMinor)
    }

    @Test
    fun `same SMS present twice is counted once`() {
        val s = Spend(100_000L, TxnKind.SPEND, LocalDate.of(2025, 11, 5), "A", SpendCategory.FOOD)
        val total = MonthlySpend.summarize(listOf(s, s.copy()), month)
        assertEquals(100_000L, total.spentMinor)
        assertEquals(1, total.transactionCount)
    }

    @Test
    fun `message from another month is excluded`() {
        val spends = listOf(
            Spend(100_000L, TxnKind.SPEND, LocalDate.of(2025, 12, 1), "A", SpendCategory.FOOD),
            Spend(50_000L, TxnKind.SPEND, LocalDate.of(2025, 11, 15), "B", SpendCategory.CASH)
        )
        val total = MonthlySpend.summarize(spends, month)
        assertEquals(50_000L, total.spentMinor)
        assertEquals(1, total.transactionCount)
    }

    @Test
    fun `more than 4 categories merge into Other`() {
        val spends = listOf(
            Spend(100_000L, TxnKind.SPEND, LocalDate.of(2025, 11, 1), "A", SpendCategory.FOOD),
            Spend(80_000L, TxnKind.SPEND, LocalDate.of(2025, 11, 2), "B", SpendCategory.TRANSPORT),
            Spend(60_000L, TxnKind.SPEND, LocalDate.of(2025, 11, 3), "C", SpendCategory.BILLS),
            Spend(40_000L, TxnKind.SPEND, LocalDate.of(2025, 11, 4), "D", SpendCategory.SHOPPING),
            Spend(30_000L, TxnKind.SPEND, LocalDate.of(2025, 11, 5), "E", SpendCategory.SUBSCRIPTION),
            Spend(20_000L, TxnKind.SPEND, LocalDate.of(2025, 11, 6), "F", SpendCategory.GROCERY)
        )
        val total = MonthlySpend.summarize(spends, month)
        assertEquals(5, total.categoryTotals.size)
        val other = total.categoryTotals.last()
        assertEquals(SpendCategory.OTHER, other.category)
        assertEquals(50_000L, other.totalMinor)
        assertEquals(2, other.count)
    }

    @Test
    fun `fewer than 4 categories shows only those`() {
        val spends = listOf(
            Spend(100_000L, TxnKind.SPEND, LocalDate.of(2025, 11, 1), "A", SpendCategory.FOOD),
            Spend(50_000L, TxnKind.SPEND, LocalDate.of(2025, 11, 2), "B", SpendCategory.CASH)
        )
        val total = MonthlySpend.summarize(spends, month)
        assertEquals(2, total.categoryTotals.size)
    }

    @Test
    fun `refund exceeds spend floors at zero`() {
        val spends = listOf(
            Spend(10_000L, TxnKind.SPEND, LocalDate.of(2025, 11, 1), "A", SpendCategory.FOOD),
            Spend(20_000L, TxnKind.REFUND, LocalDate.of(2025, 11, 2), "A", SpendCategory.FOOD)
        )
        val total = MonthlySpend.summarize(spends, month)
        assertEquals(0L, total.spentMinor)
    }

    @Test
    fun `empty list returns zero total`() {
        val total = MonthlySpend.summarize(emptyList(), month)
        assertEquals(0L, total.spentMinor)
        assertEquals(0, total.transactionCount)
        assertTrue(total.categoryTotals.isEmpty())
    }
}
