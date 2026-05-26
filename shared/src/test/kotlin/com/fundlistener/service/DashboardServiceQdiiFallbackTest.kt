package com.fundlistener.service

import com.fundlistener.model.*
import com.fundlistener.repository.FundRepository
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DashboardServiceQdiiFallbackTest {

    private val baseFundCode = "040046" // 华安纳斯达克100

    private val mockRepository = object : FundRepository {
        override suspend fun getAllPositions(): List<FundPosition> = listOf(
            FundPosition(baseFundCode, "华安纳斯达克100", BigDecimal("1000.00"), BigDecimal("1000.00"), 0, 0)
        )
        override suspend fun getPosition(fundCode: String): FundPosition? = null
        override suspend fun upsertPosition(position: FundPosition) {}
        override suspend fun deletePosition(fundCode: String) {}
        override suspend fun insertTransaction(transaction: FundTransaction) {}
        override suspend fun getTransactionsByFund(fundCode: String): List<FundTransaction> = emptyList()
        override suspend fun getAllTransactions(): List<FundTransaction> = emptyList()
        override suspend fun deleteTransaction(id: String) {}
        
        override suspend fun getLatestNav(fundCode: String): NavHistory? {
            // Official NAV is delayed by 1 day
            return NavHistory(1, fundCode, "2026-05-18", BigDecimal("1.5000"), null, 0)
        }
        override suspend fun upsertNavHistory(record: NavHistory) {}
        override suspend fun batchUpsertNavHistory(records: List<NavHistory>) {}
        override suspend fun getNavHistory(fundCode: String, limit: Int): List<NavHistory> = listOf(
            NavHistory(1, fundCode, "2026-05-18", BigDecimal("1.5000"), null, 0),
            NavHistory(2, fundCode, "2026-05-17", BigDecimal("1.5000"), null, 0)
        )

        override suspend fun getLatestSnapshot(fundCode: String): ValuationSnapshot? {
            // Primary API returned missing or 0 estimated NAV
            return ValuationSnapshot(
                fundCode = fundCode,
                snapshotTime = java.time.Instant.parse("2026-05-19T07:00:00Z").toEpochMilli(),
                estimatedNav = BigDecimal("0.00"), // Simulating missing/0
                estimatedGrowthRate = BigDecimal.ZERO,
                pePercentile = null,
                pbPercentile = null,
                createdAt = 0L
            )
        }
        override suspend fun insertSnapshot(snapshot: ValuationSnapshot) {}
        override suspend fun getSnapshots(fundCode: String, limit: Int): List<ValuationSnapshot> = emptyList()
        override suspend fun getSnapshotCount(fundCode: String): Int = 0

        override suspend fun getValuationRules(fundCode: String): List<FundValuationRule> = emptyList()
        override suspend fun insertValuationRule(rule: FundValuationRule) {}
        override suspend fun deleteValuationRules(fundCode: String) {}
        override suspend fun deleteValuationRule(id: Long) {}
        override suspend fun saveValuationRules(fundCode: String, rules: List<FundValuationRule>) {}

        override suspend fun searchFunds(keyword: String, limit: Int): List<FundSearchResult> = emptyList()
        override suspend fun batchInsertFunds(funds: List<FundSearchResult>) {}
        override suspend fun getFundCount(): Int = 0

        override suspend fun getFundMetadata(fundCode: String): FundMetadata? {
            return FundMetadata(
                fundCode = baseFundCode,
                fundName = "华安纳斯达克100",
                fundType = "QDII",
                fundManager = "Manager",
                topHoldings = null,
                lastUpdated = 0,
                assetType = "INDEX",
                linkedEtfCode = "159941", // The secondary data source
                linkedEtfName = "纳指ETF"
            )
        }
        override suspend fun upsertFundMetadata(metadata: FundMetadata) {}
        override suspend fun batchUpsertFundHoldingMappings(mappings: List<FundHoldingMapping>) {}
        override suspend fun upsertStockMetadata(metadata: StockMetadata) {}
        
        override suspend fun getConfig(key: String): String? = "realtime" // Switch to realtime to check isSettled = false
        override suspend fun setConfig(key: String, value: String) {}
        override suspend fun getAllWatchlist(): List<String> = emptyList()
        override suspend fun getWatchlistItems(): List<Pair<String, Long>> = emptyList()
        override suspend fun addToWatchlist(fundCode: String) {}
        override suspend fun removeFromWatchlist(fundCode: String) {}
        override suspend fun isInWatchlist(fundCode: String): Boolean = false
        override suspend fun getAllFundMetadata(): List<FundMetadata> = emptyList()
        override suspend fun getStockMetadata(stockCode: String): StockMetadata? {
            if (stockCode == "159941") {
                return StockMetadata(
                    stockCode = stockCode,
                    stockName = "纳指ETF",
                    marketType = "SH",
                    currentPrice = "1.020",
                    growthRate = "2.00",
                    updatedAt = 0L
                )
            }
            return null
        }
        override suspend fun getStocksMetadata(stockCodes: List<String>): List<StockMetadata> = emptyList()
        override suspend fun deleteFundHoldingMappings(fundCode: String) {}
        override suspend fun getFundHoldingMappings(fundCode: String): List<FundHoldingMapping> = emptyList()
        
        override suspend fun getLatestStockPrice(stockCode: String): StockPriceHistory? = null
        override suspend fun upsertStockPriceHistory(history: StockPriceHistory) {}
    }

    private val dummyFundService = FundService(
        com.fundlistener.client.TianTianFundClient(io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp)),
        mockRepository,
        CustomValuationEngine(mockRepository, object : com.fundlistener.client.QuoteClient(io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp)) {
            override suspend fun fetchQuotes(targets: List<Pair<String, String>>): Map<String, Quote> = emptyMap()
        }, org.mockito.kotlin.mock(), com.fundlistener.client.TianTianFundClient(io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp)))
    )

    private val dashboardService = DashboardService(
        dummyFundService, 
        mockRepository,
        object : com.fundlistener.client.QuoteClient(io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp)) {
            override suspend fun fetchQuotes(targets: List<Pair<String, String>>): Map<String, Quote> = emptyMap()
        },
        ValuationDisplayNormalizer(mockRepository)
    )

    @Test
    fun `test QDII fallback valuation using linked ETF when primary API returns 0`() = runBlocking {
        val response = dashboardService.getDashboard()
        
        val qdiiFund = response.funds.find { it.fundCode == baseFundCode }
        assertTrue(qdiiFund != null)
        
        // isSettled should be false because we are in realtime mode and intraday
        assertFalse(qdiiFund.isSettled)
        
        // Since primary API gave 0.00, it should fallback to linked ETF 159941
        // Yesterday NAV is 1.5000. 159941 growth is 2.0% (1.020 / 1.000 - 1).
        // So new estimatedNav should be 1.5000 * 1.02 = 1.5300
        assertEquals("1.5300", qdiiFund.estimatedNav, "Should calculate fallback NAV using linked ETF")
        
        // Growth rate should match the ETF's growth rate: 2.00
        assertEquals("2.00", qdiiFund.estimatedGrowthRate)
        
        // PNL for 1000 shares: (1.5300 - 1.5000) * 1000 = 30.00
        assertEquals("30.00", qdiiFund.latestPnl)
    }

    @Test
    fun `test extreme fallback throws exception when nav is 0 and no history`() = runBlocking {
        val noHistoryRepo = object : FundRepository by mockRepository {
            override suspend fun getLatestSnapshot(fundCode: String): ValuationSnapshot? = ValuationSnapshot(
                fundCode = fundCode,
                snapshotTime = java.time.Instant.parse("2026-05-19T07:00:00Z").toEpochMilli(),
                estimatedNav = BigDecimal("0.00"), 
                estimatedGrowthRate = BigDecimal.ZERO,
                pePercentile = null, pbPercentile = null, createdAt = 0L
            )
            override suspend fun getLatestNav(fundCode: String): NavHistory? = null
            override suspend fun getNavHistory(fundCode: String, limit: Int): List<NavHistory> = emptyList()
            override suspend fun getFundMetadata(fundCode: String): FundMetadata? = null // No linked ETF
            override suspend fun getAllPositions(): List<FundPosition> = listOf(
                FundPosition(baseFundCode, "华安纳斯达克100", BigDecimal("1000.00"), BigDecimal.ZERO, 0, 0)
            )
        }
        val service = DashboardService(dummyFundService, noHistoryRepo)
        
        try {
            service.getDashboard()
            kotlin.test.fail("Should throw IllegalStateException when currentNav falls to 0")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("持仓市值为0异常"), "Message should complain about 0 market value")
        }
    }

    @Test
    fun `test QDII delayed alipay mode shifts date and uses official nav`() = runBlocking {
        val delayedRepo = object : FundRepository by mockRepository {
            override suspend fun getConfig(key: String): String? = "alipay" // Force alipay mode
            override suspend fun getLatestNav(fundCode: String): NavHistory? {
                // Official date is 17th, but estimation time is 18th
                return NavHistory(1, fundCode, "2026-05-17", BigDecimal("1.4000"), null, 0)
            }
            override suspend fun getNavHistory(fundCode: String, limit: Int): List<NavHistory> = listOf(
                NavHistory(1, fundCode, "2026-05-17", BigDecimal("1.4000"), null, 0),
                NavHistory(2, fundCode, "2026-05-16", BigDecimal("1.3000"), null, 0)
            )
            override suspend fun getLatestSnapshot(fundCode: String): ValuationSnapshot? = ValuationSnapshot(
                fundCode = fundCode,
                snapshotTime = java.time.Instant.parse("2026-05-18T07:00:00Z").toEpochMilli(), // 18th
                estimatedNav = BigDecimal("0.00"), 
                estimatedGrowthRate = BigDecimal.ZERO,
                pePercentile = null, pbPercentile = null, createdAt = 0L
            )
        }
        val service = DashboardService(dummyFundService, delayedRepo)
        val response = service.getDashboard()
        val qdiiFund = response.funds.find { it.fundCode == baseFundCode }
        assertTrue(qdiiFund != null)
        
        // isSettled must be true in alipay mode
        assertTrue(qdiiFund.isSettled)
        
        // Date should be shifted from 17th to 18th
        assertEquals("2026-05-18", qdiiFund.navDate)
        
        // Nav should use the official nav from 17th (1.4000)
        assertEquals("1.4000", qdiiFund.estimatedNav)
        
        // PNL should be calculated against 16th (1.3000): (1.4000 - 1.3000) * 1000 = 100.00
        assertEquals("100.00", qdiiFund.latestPnl)
    }
}
