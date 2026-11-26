package com.afec.app

import com.afec.app.domain.MonthlySpend
import com.afec.app.domain.Spend
import com.afec.app.domain.SpendCategory
import com.afec.app.domain.TxnKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MonthlySpendTest {
    private val nov = java.time.YearMonth.of(2026, 11)
    private val mar = java.time.YearMonth.of(2025, 3)

    @Test
    fun `spend and refund in one month are netted`() {
        val spends = listOf(
            Spend(50000L, TxnKind.SPEND, LocalDate.of(2026, 11, 5), "Swiggy", SpendCategory.FOOD),
            Spend(20000L, TxnKind.REFUND, LocalDate.of(2026, 11, 6), "Swiggy", SpendCategory.FOOD)
        )
        val t = MonthlySpend.summarize(spends, nov)
        assertEquals(30000L, t.spentMinor)
        assertEquals(2, t.count)
    }

    @Test
    fun `duplicate spend rows are counted once`() {
        val s = Spend(10000L, TxnKind.SPEND, LocalDate.of(2026, 11, 5), "Tea", SpendCategory.FOOD)
        val t = MonthlySpend.summarize(listOf(s, s), nov)
        assertEquals(1, t.count)
        assertEquals(10000L, t.spentMinor)
    }

    @Test
    fun `spends from another month are excluded`() {
        val spends = listOf(
            Spend(10000L, TxnKind.SPEND, LocalDate.of(2025, 3, 14), "Tea", SpendCategory.FOOD)
        )
        val t = MonthlySpend.summarize(spends, nov)
        assertEquals(0L, t.spentMinor)
        assertEquals(0, t.count)
    }
}
