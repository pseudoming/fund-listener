package com.fundlistener.service

import com.fundlistener.model.ValuationSnapshot
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PercentileCalculatorTest {

    private fun snapshot(pe: BigDecimal?, pb: BigDecimal? = null) = ValuationSnapshot(
        fundCode = "110022",
        snapshotTime = System.currentTimeMillis(),
        estimatedNav = BigDecimal("3.0"),
        estimatedGrowthRate = BigDecimal.ZERO,
        weightedPe = pe,
        weightedPb = pb,
        pePercentile = null,
        pbPercentile = null,
        coverageRate = null,
        createdAt = System.currentTimeMillis()
    )

    // ═══════════════════════════════════════════════
    //  PE Percentile
    // ═══════════════════════════════════════════════

    @Test
    fun `PE percentile with 20 valid data points`() {
        val history = (1..20).map { i ->
            snapshot(pe = BigDecimal(i)) // PE from 1 to 20
        }
        val result = PercentileCalculator.calcPEPercentile(BigDecimal("15.0"), history)
        assertTrue(result is PercentileCalculator.PercentileResult.Valid)
        // 14 values are less than 15 (1..14), so percentile = 14/20 * 100 = 70.00
        assertEquals("70.00", (result as PercentileCalculator.PercentileResult.Valid).percentile.toPlainString())
    }

    @Test
    fun `PE percentile at minimum — all historical higher`() {
        val history = (1..20).map { i ->
            snapshot(pe = BigDecimal(10 + i)) // PE from 11 to 30
        }
        val result = PercentileCalculator.calcPEPercentile(BigDecimal("5.0"), history)
        assertTrue(result is PercentileCalculator.PercentileResult.Valid)
        assertEquals("0.00", (result as PercentileCalculator.PercentileResult.Valid).percentile.toPlainString())
    }

    @Test
    fun `PE percentile at maximum — all historical lower`() {
        val history = (1..20).map { i ->
            snapshot(pe = BigDecimal(i)) // PE from 1 to 20
        }
        val result = PercentileCalculator.calcPEPercentile(BigDecimal("100.0"), history)
        assertTrue(result is PercentileCalculator.PercentileResult.Valid)
        assertEquals("100.00", (result as PercentileCalculator.PercentileResult.Valid).percentile.toPlainString())
    }

    @Test
    fun `PE percentile with exact match — current equals some historical`() {
        val batch = listOf(
            snapshot(pe = BigDecimal("10.00")),
            snapshot(pe = BigDecimal("15.00")),
            snapshot(pe = BigDecimal("20.00")),
            snapshot(pe = BigDecimal("15.00")),
            snapshot(pe = BigDecimal("10.00"))
        )
        val history = (1..4).flatMap { batch } // 4 * 5 = 20 items
        val result = PercentileCalculator.calcPEPercentile(BigDecimal("15.00"), history)
        // Values < 15: 2 out of 5 = 40% per group → 8 out of 20 = 40%
        assertTrue(result is PercentileCalculator.PercentileResult.Valid)
    }

    // ═══════════════════════════════════════════════
    //  Degradation
    // ═══════════════════════════════════════════════

    @Test
    fun `returns Degraded when less than 20 days`() {
        val history = (1..19).map { i ->
            snapshot(pe = BigDecimal(i))
        }
        val result = PercentileCalculator.calcPEPercentile(BigDecimal("10.0"), history)
        assertTrue(result is PercentileCalculator.PercentileResult.Degraded)
        val degraded = result as PercentileCalculator.PercentileResult.Degraded
        assertEquals(19, degraded.currentDays)
        assertEquals(20, degraded.requiredDays)
    }

    @Test
    fun `returns Degraded with 5 days`() {
        val history = (1..5).map { i ->
            snapshot(pe = BigDecimal(i))
        }
        val result = PercentileCalculator.calcPEPercentile(BigDecimal("3.0"), history)
        assertTrue(result is PercentileCalculator.PercentileResult.Degraded)
        assertEquals(5, (result as PercentileCalculator.PercentileResult.Degraded).currentDays)
    }

    @Test
    fun `returns Degraded for empty history`() {
        val result = PercentileCalculator.calcPEPercentile(BigDecimal("10.0"), emptyList())
        assertTrue(result is PercentileCalculator.PercentileResult.Degraded)
        assertEquals(0, (result as PercentileCalculator.PercentileResult.Degraded).currentDays)
    }

    // ═══════════════════════════════════════════════
    //  NoValue
    // ═══════════════════════════════════════════════

    @Test
    fun `returns NoValue when current PE is null`() {
        val history = (1..30).map { i ->
            snapshot(pe = BigDecimal(i))
        }
        val result = PercentileCalculator.calcPEPercentile(null, history)
        assertTrue(result is PercentileCalculator.PercentileResult.NoValue)
    }

    // ═══════════════════════════════════════════════
    //  PB Percentile
    // ═══════════════════════════════════════════════

    @Test
    fun `PB percentile calculation`() {
        val history = (1..25).map { i ->
            snapshot(pe = null, pb = BigDecimal(i))
        }
        val result = PercentileCalculator.calcPBPercentile(BigDecimal("12.5"), history)
        assertTrue(result is PercentileCalculator.PercentileResult.Valid)
        // 12 values < 12.5 → 12/25*100 = 48.00
        assertEquals("48.00", (result as PercentileCalculator.PercentileResult.Valid).percentile.toPlainString())
    }

    @Test
    fun `PB percentile returns NoValue when current is null`() {
        val history = (1..25).map { i ->
            snapshot(pe = null, pb = BigDecimal(i))
        }
        val result = PercentileCalculator.calcPBPercentile(null, history)
        assertTrue(result is PercentileCalculator.PercentileResult.NoValue)
    }

    // ═══════════════════════════════════════════════
    //  Mixed nulls in history
    // ═══════════════════════════════════════════════

    @Test
    fun `ignores null PEs in history`() {
        val history = (1..25).map { i ->
            if (i % 3 == 0) snapshot(pe = null) // every 3rd has no PE
            else snapshot(pe = BigDecimal(i))
        }
        val result = PercentileCalculator.calcPEPercentile(BigDecimal("10.0"), history)
        // Valid entries: 1,2,4,5,7,8,10,11,13,14,16,17,19,20,22,23,25 = 17 entries
        // Values < 10: 1,2,4,5,7,8 = 6 entries → 6/17*100 ≈ 35.29
        assertTrue(result is PercentileCalculator.PercentileResult.Degraded)
        assertEquals(17, (result as PercentileCalculator.PercentileResult.Degraded).currentDays)
    }

    @Test
    fun `Degraded when valid entries less than 20 even if total histories are more`() {
        val history = (1..30).map { i ->
            if (i <= 12) snapshot(pe = null) // first 12 have no PE
            else snapshot(pe = BigDecimal(i)) // only 18 valid
        }
        val result = PercentileCalculator.calcPEPercentile(BigDecimal("20.0"), history)
        assertTrue(result is PercentileCalculator.PercentileResult.Degraded)
        assertEquals(18, (result as PercentileCalculator.PercentileResult.Degraded).currentDays)
    }

    @Test
    fun `MIN_DAYS constant is 20`() {
        assertEquals(20, PercentileCalculator.MIN_DAYS)
    }
}
