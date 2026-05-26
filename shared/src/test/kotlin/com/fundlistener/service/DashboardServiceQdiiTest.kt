package com.fundlistener.service

import com.fundlistener.model.*
import com.fundlistener.repository.FundRepository
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import kotlin.test.assertNotNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DashboardServiceQdiiTest {

    private val baseFundCode = "006479"

    private val mockRepository = object : FundRepository {
        override suspend fun getAllPositions(): List<FundPosition> = listOf(
            FundPosition(baseFundCode, "Nasdaq 100 ETF", BigDecimal("1000.00"), BigDecimal("1000.00"), 0, 0)
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
            NavHistory(2, fundCode, "2026-05-17", BigDecimal("1.4900"), null, 0)
        )

        override suspend fun getLatestSnapshot(fundCode: String): ValuationSnapshot? {
            // Estimation date is 05-19, which is > 05-18
            return ValuationSnapshot(
                fundCode = fundCode,
                snapshotTime = java.time.Instant.parse("2026-05-19T07:00:00Z").toEpochMilli(),
                estimatedNav = BigDecimal("1.4625"),
                estimatedGrowthRate = BigDecimal("-2.50"),
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
                fundName = "Nasdaq 100 ETF",
                fundType = "QDII",
                fundManager = "Manager",
                topHoldings = null,
                lastUpdated = 0,
                assetType = "INDEX",
                linkedEtfCode = "QQQ",
                linkedEtfName = "Invesco QQQ"
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
        override suspend fun getStockMetadata(stockCode: String): StockMetadata? = null
        override suspend fun getStocksMetadata(stockCodes: List<String>): List<StockMetadata> = emptyList()
        override suspend fun deleteFundHoldingMappings(fundCode: String) {}
        override suspend fun getFundHoldingMappings(fundCode: String): List<FundHoldingMapping> = emptyList()
        override suspend fun upsertStockPriceHistory(history: StockPriceHistory) {}
        override suspend fun getLatestStockPrice(stockCode: String): StockPriceHistory? = null
    }

    // Dummy fundService just for metadata fallback if needed, but not heavily used here
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
    fun `TestCase 5 - State Machine isSettled remains false in realtime mode when official nav is delayed`() = runBlocking {
        val response = dashboardService.getDashboard()
        
        val qdiiFund = response.funds.find { it.fundCode == baseFundCode }
        assertNotNull(qdiiFund)
        
        // Even though we have estimatedNav from 05-19, the official navDate is 05-18.
        // In realtime mode, isSettled MUST be false so UI shows "估值中"
        assertFalse(qdiiFund!!.isSettled, "QDII fund should not be settled in realtime mode during intraday")
        assertEquals("1.4625", qdiiFund.estimatedNav)
        assertEquals("-2.50", qdiiFund.estimatedGrowthRate)
    }
}
