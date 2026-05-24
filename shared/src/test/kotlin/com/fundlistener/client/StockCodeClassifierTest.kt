package com.fundlistener.client

import com.fundlistener.model.MarketType
import com.fundlistener.model.StockQuote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StockCodeClassifierTest {

    // ── MarketType.classify() ──────────────────────────────────────

    @Test
    fun `classify A-share Shanghai 6xx codes`() {
        assertEquals(MarketType.A_SHARE, MarketType.classify("600519"))
        assertEquals(MarketType.A_SHARE, MarketType.classify("601633"))
        assertEquals(MarketType.A_SHARE, MarketType.classify("603129"))
        assertEquals(MarketType.A_SHARE, MarketType.classify("600809"))
    }

    @Test
    fun `classify A-share Shanghai 688xxx STAR Market codes`() {
        assertEquals(MarketType.A_SHARE, MarketType.classify("688002"))
        assertEquals(MarketType.A_SHARE, MarketType.classify("688981"))
    }

    @Test
    fun `classify A-share Shenzhen 00xxxx codes`() {
        assertEquals(MarketType.A_SHARE, MarketType.classify("000333"))
        assertEquals(MarketType.A_SHARE, MarketType.classify("000858"))
        assertEquals(MarketType.A_SHARE, MarketType.classify("000568"))
    }

    @Test
    fun `classify A-share Shenzhen 002xxx codes`() {
        assertEquals(MarketType.A_SHARE, MarketType.classify("002594"))
        assertEquals(MarketType.A_SHARE, MarketType.classify("002714"))
    }

    @Test
    fun `classify A-share Shenzhen 300xxx ChiNext codes`() {
        assertEquals(MarketType.A_SHARE, MarketType.classify("300750"))
        assertEquals(MarketType.A_SHARE, MarketType.classify("300604"))
    }

    @Test
    fun `classify HK stock 5-digit codes`() {
        assertEquals(MarketType.HK_STOCK, MarketType.classify("01398"))
        assertEquals(MarketType.HK_STOCK, MarketType.classify("00700"))
        assertEquals(MarketType.HK_STOCK, MarketType.classify("09988"))
    }

    @Test
    fun `classify US stock ticker symbols`() {
        assertEquals(MarketType.US_STOCK, MarketType.classify("NVDA"))
        assertEquals(MarketType.US_STOCK, MarketType.classify("AAPL"))
        assertEquals(MarketType.US_STOCK, MarketType.classify("GOOGL"))
        assertEquals(MarketType.US_STOCK, MarketType.classify("MSFT"))
        assertEquals(MarketType.US_STOCK, MarketType.classify("AMZN"))
        assertEquals(MarketType.US_STOCK, MarketType.classify("TSM"))
    }

    @Test
    fun `classify US stock with mixed case`() {
        assertEquals(MarketType.US_STOCK, MarketType.classify("nvda"))
        assertEquals(MarketType.US_STOCK, MarketType.classify("Nvda"))
    }

    @Test
    fun `classify empty code should throw`() {
        assertFailsWith<IllegalArgumentException> { MarketType.classify("") }
        assertFailsWith<IllegalArgumentException> { MarketType.classify("   ") }
    }

    @Test
    fun `classify unknown format should throw`() {
        assertFailsWith<IllegalArgumentException> { MarketType.classify("1234") }
        assertFailsWith<IllegalArgumentException> { MarketType.classify("1234567") }
    }

    // ── MarketType.toSecId() ───────────────────────────────────────

    @Test
    fun `toSecId A-share Shanghai uses prefix 0`() {
        assertEquals("0.600519", MarketType.toSecId("600519"))
        assertEquals("0.601633", MarketType.toSecId("601633"))
        assertEquals("0.688002", MarketType.toSecId("688002"))
    }

    @Test
    fun `toSecId A-share Shenzhen uses prefix 1`() {
        assertEquals("1.000333", MarketType.toSecId("000333"))
        assertEquals("1.002594", MarketType.toSecId("002594"))
        assertEquals("1.300750", MarketType.toSecId("300750"))
    }

    @Test
    fun `toSecId HK stock uses prefix 116`() {
        assertEquals("116.01398", MarketType.toSecId("01398"))
        assertEquals("116.00700", MarketType.toSecId("00700"))
    }

    @Test
    fun `toSecId US stock uses prefix 105`() {
        assertEquals("105.NVDA", MarketType.toSecId("NVDA"))
        assertEquals("105.AAPL", MarketType.toSecId("AAPL"))
        assertEquals("105.GOOGL", MarketType.toSecId("GOOGL"))
    }

    // ── Mixed batch secId construction ─────────────────────────────

    @Test
    fun `toSecId mixed markets A HK US`() {
        val codes = listOf("600519", "000333", "01398", "00700", "NVDA", "AAPL")
        val secIds = codes.map { MarketType.toSecId(it) }
        assertEquals(
            listOf("0.600519", "1.000333", "116.01398", "116.00700", "105.NVDA", "105.AAPL"),
            secIds
        )
    }

    // ── StockQuoteClient.parseQuotes() ─────────────────────────────

    @Test
    fun `parseQuotes returns empty for empty diff`() {
        val client = StockQuoteClient(io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp))
        val raw = """{"rc":0,"rt":1,"data":{"total":0,"diff":[]}}"""
        val codeToMarket = mapOf<String, MarketType>()
        val quotes = client.parseQuotes(raw, codeToMarket)
        assertTrue(quotes.isEmpty())
    }

    @Test
    fun `parseQuotes returns empty for null data`() {
        val client = StockQuoteClient(io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp))
        val raw = """{"rc":100,"rt":1,"data":null}"""
        val codeToMarket = mapOf<String, MarketType>()
        val quotes = client.parseQuotes(raw, codeToMarket)
        assertTrue(quotes.isEmpty())
    }

    @Test
    fun `parseQuotes extracts A-share quote correctly`() {
        val client = StockQuoteClient(io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp))
        val raw = """
        {
            "rc": 0,
            "data": {
                "total": 1,
                "diff": [
                    {"f2": "1523.50", "f3": "-1.20", "f4": "-18.50", "f9": "35.80",
                     "f12": "600519", "f14": "贵州茅台",
                     "f15": "1550.00", "f16": "1520.00", "f17": "1540.00",
                     "f18": "1542.00", "f23": "6.50"}
                ]
            }
        }
        """.trimIndent()
        val codeToMarket = mapOf("600519" to MarketType.A_SHARE)
        val quotes = client.parseQuotes(raw, codeToMarket)

        assertEquals(1, quotes.size)
        val q = quotes[0]
        assertEquals("600519", q.code)
        assertEquals("贵州茅台", q.name)
        assertEquals(MarketType.A_SHARE, q.market)
        assertNotNull(q.latestPrice)
        assertEquals("1523.50".toBigDecimal(), q.latestPrice)
        assertEquals("-1.20".toBigDecimal(), q.changePercent)
        assertEquals("35.80".toBigDecimal(), q.pe)
        assertEquals("6.50".toBigDecimal(), q.pb)
    }

    @Test
    fun `parseQuotes extracts HK stock quote correctly`() {
        val client = StockQuoteClient(io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp))
        val raw = """
        {
            "rc": 0,
            "data": {
                "diff": [
                    {"f2": "449.20", "f3": "-1.58", "f4": "-7.20", "f9": "15.58",
                     "f12": "00700", "f14": "腾讯控股",
                     "f15": "460.00", "f16": "448.00", "f17": "458.00",
                     "f18": "456.40", "f23": "3.21"}
                ]
            }
        }
        """.trimIndent()
        val codeToMarket = mapOf("00700" to MarketType.HK_STOCK)
        val quotes = client.parseQuotes(raw, codeToMarket)

        assertEquals(1, quotes.size)
        val q = quotes[0]
        assertEquals("00700", q.code)
        assertEquals("腾讯控股", q.name)
        assertEquals(MarketType.HK_STOCK, q.market)
        assertEquals("449.20".toBigDecimal(), q.latestPrice)
        assertEquals("15.58".toBigDecimal(), q.pe)
        assertEquals("3.21".toBigDecimal(), q.pb)
    }

    @Test
    fun `parseQuotes extracts US stock quote correctly`() {
        val client = StockQuoteClient(io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp))
        val raw = """
        {
            "rc": 0,
            "data": {
                "diff": [
                    {"f2": "875.28", "f3": "2.35", "f4": "20.12", "f9": "42.50",
                     "f12": "NVDA", "f14": "英伟达",
                     "f15": "880.00", "f16": "870.00", "f17": "872.00",
                     "f18": "855.16", "f23": "18.90"}
                ]
            }
        }
        """.trimIndent()
        val codeToMarket = mapOf("NVDA" to MarketType.US_STOCK)
        val quotes = client.parseQuotes(raw, codeToMarket)

        assertEquals(1, quotes.size)
        val q = quotes[0]
        assertEquals("NVDA", q.code)
        assertEquals("英伟达", q.name)
        assertEquals(MarketType.US_STOCK, q.market)
        assertEquals("875.28".toBigDecimal(), q.latestPrice)
        assertEquals("42.50".toBigDecimal(), q.pe)
    }

    @Test
    fun `parseQuotes handles mixed market batch response`() {
        val client = StockQuoteClient(io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp))
        val raw = """
        {
            "rc": 0,
            "data": {
                "diff": [
                    {"f2": "1523.50", "f12": "600519", "f14": "贵州茅台",
                     "f3": "-1.20", "f9": "35.80", "f18": "1542.00", "f23": "6.50",
                     "f4": "-18.50", "f15": "1550.00", "f16": "1520.00", "f17": "1540.00"},
                    {"f2": "449.20", "f12": "00700", "f14": "腾讯控股",
                     "f3": "-1.58", "f9": "15.58", "f18": "456.40", "f23": "3.21",
                     "f4": "-7.20", "f15": "460.00", "f16": "448.00", "f17": "458.00"},
                    {"f2": "875.28", "f12": "NVDA", "f14": "英伟达",
                     "f3": "2.35", "f9": "42.50", "f18": "855.16", "f23": "18.90",
                     "f4": "20.12", "f15": "880.00", "f16": "870.00", "f17": "872.00"}
                ]
            }
        }
        """.trimIndent()
        val codeToMarket = mapOf(
            "600519" to MarketType.A_SHARE,
            "00700" to MarketType.HK_STOCK,
            "NVDA" to MarketType.US_STOCK
        )
        val quotes = client.parseQuotes(raw, codeToMarket)

        assertEquals(3, quotes.size)
        val markets = quotes.map { it.market }.toSet()
        assertEquals(3, markets.size)
        assertTrue(markets.containsAll(listOf(MarketType.A_SHARE, MarketType.HK_STOCK, MarketType.US_STOCK)))

        // verify each
        val aShare = quotes.find { it.code == "600519" }!!
        assertEquals(MarketType.A_SHARE, aShare.market)

        val hk = quotes.find { it.code == "00700" }!!
        assertEquals(MarketType.HK_STOCK, hk.market)

        val us = quotes.find { it.code == "NVDA" }!!
        assertEquals(MarketType.US_STOCK, us.market)
    }

    @Test
    fun `parseQuotes marks suspended stock when f2 is dash`() {
        val client = StockQuoteClient(io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp))
        val raw = """
        {
            "rc": 0,
            "data": {
                "diff": [
                    {"f2": "-", "f3": "-", "f4": "-", "f9": "-",
                     "f12": "000002", "f14": "万科A",
                     "f15": "-", "f16": "-", "f17": "-", "f18": "12.50", "f23": "-"}
                ]
            }
        }
        """.trimIndent()
        val codeToMarket = mapOf("000002" to MarketType.A_SHARE)
        val quotes = client.parseQuotes(raw, codeToMarket)

        assertEquals(1, quotes.size)
        assertTrue(quotes[0].isSuspended)
        assertEquals(null, quotes[0].latestPrice)
        assertEquals(null, quotes[0].pe)
    }

    @Test
    fun `parseQuotes skips item without f12 code`() {
        val client = StockQuoteClient(io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp))
        val raw = """
        {
            "rc": 0,
            "data": {
                "diff": [
                    {"f2": "100.00", "f14": "Unknown"}
                ]
            }
        }
        """.trimIndent()
        val quotes = client.parseQuotes(raw, emptyMap())
        assertTrue(quotes.isEmpty())
    }

    @Test
    fun `parseQuotes skips item not in codeToMarket map`() {
        val client = StockQuoteClient(io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp))
        val raw = """
        {
            "rc": 0,
            "data": {
                "diff": [
                    {"f2": "100.00", "f12": "600519", "f14": "贵州茅台"}
                ]
            }
        }
        """.trimIndent()
        // codeToMarket is empty — should skip
        val quotes = client.parseQuotes(raw, emptyMap())
        assertTrue(quotes.isEmpty())
    }

    @Test
    fun `parseQuotes handles malformed JSON gracefully`() {
        val client = StockQuoteClient(io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp))
        val quotes = client.parseQuotes("not valid json", emptyMap())
        assertTrue(quotes.isEmpty())
    }
}
