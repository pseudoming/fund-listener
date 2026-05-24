package com.fundlistener.service

import com.fundlistener.model.FundHolding
import com.fundlistener.model.FundHoldingsResponse
import com.fundlistener.model.MarketType
import com.fundlistener.model.StockQuote
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ValuationEngineTest {

    // ── Helpers ───────────────────────────────────────────────────

    private fun holding(code: String, name: String, ratio: String) =
        FundHolding(code, name, ratio)

    private fun quote(
        code: String,
        name: String,
        changePercent: String? = null,
        pe: String? = null,
        pb: String? = null,
        suspended: Boolean = false
    ) = StockQuote(
        code = code,
        name = name,
        market = MarketType.A_SHARE,
        latestPrice = BigDecimal("100"),
        changePercent = changePercent?.toBigDecimal(),
        changeAmount = null,
        pe = pe?.toBigDecimal(),
        pb = pb?.toBigDecimal(),
        high = null, low = null, open = null, prevClose = null,
        isSuspended = suspended
    )

    private fun holdingsResponse(code: String, date: String, vararg h: FundHolding) =
        FundHoldingsResponse(code, date, h.toList())

    // ── Coverage ──────────────────────────────────────────────────

    @Test
    fun `full coverage when all stocks have quotes`() {
        val holdings = holdingsResponse("110022", "2026-03-31",
            holding("600519", "贵州茅台", "9.90"),
            holding("000333", "美的集团", "9.64")
        )
        val quotes = listOf(
            quote("600519", "贵州茅台", "-1.20", "35.80", "6.50"),
            quote("000333", "美的集团", "0.50", "12.30", "2.80")
        )
        val result = ValuationEngine.calculate(holdings, quotes)

        assertEquals("100.00", result.coveragePercent)
        assertEquals(0, result.missingCount)
    }

    @Test
    fun `zero coverage when no quotes`() {
        val holdings = holdingsResponse("110022", "2026-03-31",
            holding("600519", "贵州茅台", "9.90")
        )
        val result = ValuationEngine.calculate(holdings, emptyList())

        assertEquals("0.00", result.coveragePercent)
        assertEquals(1, result.missingCount)
        assertNull(result.weightedChangePercent)
        assertNull(result.weightedPE)
    }

    @Test
    fun `partial coverage when some stocks suspended`() {
        val holdings = holdingsResponse("110022", "2026-03-31",
            holding("600519", "贵州茅台", "9.90"),
            holding("000333", "美的集团", "9.64")
        )
        val quotes = listOf(
            quote("600519", "贵州茅台", "-1.20", "35.80", "6.50"),
            quote("000333", "美的集团", suspended = true)
        )
        val result = ValuationEngine.calculate(holdings, quotes)

        assertTrue(result.coveragePercent.toDouble() in 45.0..55.0)
        assertEquals(1, result.missingCount)
    }

    // ── Weighted Change Percent ───────────────────────────────────

    @Test
    fun `weighted change simple average`() {
        val holdings = holdingsResponse("F1", "2026-03-31",
            holding("A", "StockA", "10.00"),
            holding("B", "StockB", "10.00")
        )
        val quotes = listOf(
            quote("A", "StockA", "2.00"),
            quote("B", "StockB", "-2.00")
        )
        val result = ValuationEngine.calculate(holdings, quotes)

        assertEquals("0.00", result.weightedChangePercent)
    }

    @Test
    fun `weighted change with different weights`() {
        // StockA: ratio=5%, change=+4%  → 0.20
        // StockB: ratio=5%, change=-2%  → -0.10
        // weighted = (0.20 + (-0.10)) / 10 = 0.10 / 10 = 1.00%
        val holdings = holdingsResponse("F1", "2026-03-31",
            holding("A", "StockA", "5.00"),
            holding("B", "StockB", "5.00")
        )
        val quotes = listOf(
            quote("A", "StockA", "4.00"),
            quote("B", "StockB", "-2.00")
        )
        val result = ValuationEngine.calculate(holdings, quotes)

        assertEquals("1.00", result.weightedChangePercent)
    }

    @Test
    fun `weighted change single stock`() {
        val holdings = holdingsResponse("F1", "2026-03-31",
            holding("A", "StockA", "8.50")
        )
        val quotes = listOf(
            quote("A", "StockA", "3.50")
        )
        val result = ValuationEngine.calculate(holdings, quotes)

        assertEquals("3.50", result.weightedChangePercent)
    }

    // ── Weighted PE ───────────────────────────────────────────────

    @Test
    fun `weighted PE with all data present`() {
        // PE_A=20, ratio_A=5 → 100
        // PE_B=10, ratio_B=5 → 50
        // weighted PE = 150 / 10 = 15.00
        val holdings = holdingsResponse("F1", "2026-03-31",
            holding("A", "StockA", "5.00"),
            holding("B", "StockB", "5.00")
        )
        val quotes = listOf(
            quote("A", "StockA", pe = "20.00"),
            quote("B", "StockB", pe = "10.00")
        )
        val result = ValuationEngine.calculate(holdings, quotes)

        assertEquals("15.00", result.weightedPE)
    }

    @Test
    fun `weighted PE skips null PE`() {
        // Only StockB has PE data
        val holdings = holdingsResponse("F1", "2026-03-31",
            holding("A", "StockA", "5.00"),
            holding("B", "StockB", "5.00")
        )
        val quotes = listOf(
            quote("A", "StockA", pe = null),
            quote("B", "StockB", pe = "10.00")
        )
        val result = ValuationEngine.calculate(holdings, quotes)

        assertEquals("10.00", result.weightedPE)
    }

    @Test
    fun `weighted PE null when no PE data`() {
        val holdings = holdingsResponse("F1", "2026-03-31",
            holding("A", "StockA", "5.00")
        )
        val quotes = listOf(
            quote("A", "StockA", pe = null)
        )
        val result = ValuationEngine.calculate(holdings, quotes)

        assertNull(result.weightedPE)
    }

    // ── Weighted PB ───────────────────────────────────────────────

    @Test
    fun `weighted PB calculation`() {
        // PB_A=2, ratio_A=7 → 14
        // PB_B=3, ratio_B=3 → 9
        // weighted PB = 23 / 10 = 2.30
        val holdings = holdingsResponse("F1", "2026-03-31",
            holding("A", "StockA", "7.00"),
            holding("B", "StockB", "3.00")
        )
        val quotes = listOf(
            quote("A", "StockA", pb = "2.00"),
            quote("B", "StockB", pb = "3.00")
        )
        val result = ValuationEngine.calculate(holdings, quotes)

        assertEquals("2.30", result.weightedPB)
    }

    // ── Detail rows ───────────────────────────────────────────────

    @Test
    fun `detail rows contain all holdings`() {
        val holdings = holdingsResponse("F1", "2026-03-31",
            holding("A", "StockA", "10.00"),
            holding("B", "StockB", "5.00"),
            holding("C", "StockC", "3.00")
        )
        val quotes = listOf(
            quote("A", "StockA", "1.00", "15.00", "2.00"),
            quote("C", "StockC", "-0.50", null, null)
        )
        val result = ValuationEngine.calculate(holdings, quotes)

        assertEquals(3, result.stockDetails.size)

        val a = result.stockDetails.find { it.stockCode == "A" }!!
        assertTrue(a.hasQuote)
        assertEquals("1.00", a.changePercent)

        val b = result.stockDetails.find { it.stockCode == "B" }!!
        assertEquals(false, b.hasQuote)

        val c = result.stockDetails.find { it.stockCode == "C" }!!
        assertTrue(c.hasQuote)
        assertEquals("-0.50", c.changePercent)
    }

    // ── Edge Cases ────────────────────────────────────────────────

    @Test
    fun `empty holdings returns valid empty result`() {
        val holdings = FundHoldingsResponse("F1", "2026-03-31", emptyList())
        val result = ValuationEngine.calculate(holdings, emptyList())

        assertEquals("F1", result.fundCode)
        assertEquals("0.00", result.coveragePercent)
        assertEquals(0, result.missingCount)
        assertNull(result.weightedChangePercent)
        assertTrue(result.stockDetails.isEmpty())
    }

    @Test
    fun `total ratio reflects all holdings`() {
        val holdings = holdingsResponse("F1", "2026-03-31",
            holding("A", "StockA", "9.90"),
            holding("B", "StockB", "8.50"),
            holding("C", "StockC", "6.33")
        )
        val quotes = listOf(
            quote("A", "StockA", "1.00")
        )
        val result = ValuationEngine.calculate(holdings, quotes)

        assertEquals("24.73", result.totalRatio)
        assertEquals("9.90", result.totalRatioCovered)
    }

    @Test
    fun `report date is forwarded correctly`() {
        val holdings = holdingsResponse("110022", "2026-03-31",
            holding("A", "StockA", "10.00")
        )
        val quotes = listOf(quote("A", "StockA", "1.00"))
        val result = ValuationEngine.calculate(holdings, quotes)

        assertEquals("2026-03-31", result.reportDate)
    }
}
