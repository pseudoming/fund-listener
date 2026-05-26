package com.fundlistener.service

import com.fundlistener.client.QuoteClient
import com.fundlistener.client.TianTianFundClient
import com.fundlistener.model.*
import com.fundlistener.repository.FundRepository
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class QdiiBackupValuationTest {

    private val baseFundCode = "006479" // Example QDII Fund

    private val mockRepository = object : FundRepository {
        override suspend fun getAllPositions(): List<FundPosition> = listOf(
            FundPosition(baseFundCode, "Nasdaq 100 ETF", BigDecimal("10000000.00"), BigDecimal("10000000.00"), 0, 0)
        )
        override suspend fun getPosition(fundCode: String): FundPosition? = null
        override suspend fun upsertPosition(position: FundPosition) {}
        override suspend fun deletePosition(fundCode: String) {}
        override suspend fun insertTransaction(transaction: FundTransaction) {}
        override suspend fun getTransactionsByFund(fundCode: String): List<FundTransaction> = emptyList()
        override suspend fun getAllTransactions(): List<FundTransaction> = emptyList()
        override suspend fun deleteTransaction(id: String) {}
        override suspend fun upsertNavHistory(record: NavHistory) {}
        override suspend fun batchUpsertNavHistory(records: List<NavHistory>) {}
        override suspend fun getNavHistory(fundCode: String, limit: Int): List<NavHistory> = emptyList()

        override suspend fun getLatestNav(fundCode: String): NavHistory? {
            return NavHistory(1, fundCode, "2026-05-19", BigDecimal("1.5000"), null, 0)
        }

        override suspend fun insertSnapshot(snapshot: ValuationSnapshot) {}
        override suspend fun getSnapshots(fundCode: String, limit: Int): List<ValuationSnapshot> = emptyList()
        override suspend fun getLatestSnapshot(fundCode: String): ValuationSnapshot? = null
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
        
        override suspend fun getConfig(key: String): String? = "alipay"
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

    private val mockQuoteClient = object : QuoteClient(HttpClient(OkHttp)) {
        override suspend fun fetchQuotes(targets: List<Pair<String, String>>): Map<String, Quote> {
            if (targets.any { it.first == "QQQ" }) {
                return mapOf("QQQ" to Quote("QQQ", "Invesco QQQ", BigDecimal("450.00"), BigDecimal("-11.25"), BigDecimal("-2.50")))
            }
            return emptyMap()
        }
    }

    private val mockTianTianClient = object : TianTianFundClient(HttpClient(OkHttp)) {
        override suspend fun fetchEstimation(code: String): TianTianFundRaw? {
            return TianTianFundRaw(
                fundcode = code,
                name = "Nasdaq 100 ETF",
                jzrq = "2026-05-19",
                dwjz = "1.5000",
                gsz = "0", // 0 implies missing real-time estimation
                gszzl = "0",
                gztime = "2026-05-20 15:00"
            )
        }
        override suspend fun fetchNavTrend(code: String): List<NavTrendItem>? = emptyList()
    }

    private val customEngine = CustomValuationEngine(mockRepository, mockQuoteClient, org.mockito.kotlin.mock(), mockTianTianClient)
    private val fundService = FundService(mockTianTianClient, mockRepository, customEngine, ValuationDisplayNormalizer(mockRepository))

    @Test
    fun `TestCase 1 and 2 - QDII missing estimation uses linked etf fallback with negative return`() = runBlocking {
        val result = fundService.getRealtimeEstimation(baseFundCode)

        assertNotNull(result)
        assertEquals(baseFundCode, result.code)
        
        // Expected: QQQ drops 2.50%, 1.5000 * 0.975 = 1.4625
        assertEquals("-2.50", result.estimatedGrowthRate)
        assertEquals("1.4625", result.estimatedNav)
    }

    @Test
    fun `TestCase 3 - Precision test for extreme data`() = runBlocking {
        val quoteClientSmallDrop = object : QuoteClient(HttpClient(OkHttp)) {
            override suspend fun fetchQuotes(targets: List<Pair<String, String>>): Map<String, Quote> {
                return mapOf("QQQ" to Quote("QQQ", "Invesco QQQ", BigDecimal("450.00"), BigDecimal("-0.04"), BigDecimal("-0.01")))
            }
        }
        val customEngineSmallDrop = CustomValuationEngine(mockRepository, quoteClientSmallDrop, org.mockito.kotlin.mock(), mockTianTianClient)
        val service = FundService(mockTianTianClient, mockRepository, customEngineSmallDrop, ValuationDisplayNormalizer(mockRepository))

        val result = service.getRealtimeEstimation(baseFundCode)
        assertEquals("-0.01", result.estimatedGrowthRate)
        assertEquals("1.4999", BigDecimal(result.estimatedNav).setScale(4, RoundingMode.HALF_UP).toPlainString())
    }

    @Test
    fun `TestCase 4 - Safety Net when linked etf fails`() = runBlocking {
        val quoteClientFail = object : QuoteClient(HttpClient(OkHttp)) {
            override suspend fun fetchQuotes(targets: List<Pair<String, String>>): Map<String, Quote> {
                return emptyMap() 
            }
        }
        val customEngineFail = CustomValuationEngine(mockRepository, quoteClientFail, org.mockito.kotlin.mock(), mockTianTianClient)
        val service = FundService(mockTianTianClient, mockRepository, customEngineFail, ValuationDisplayNormalizer(mockRepository))

        val result = service.getRealtimeEstimation(baseFundCode)
        
        assertNotNull(result)
        assertEquals("1.5000", result.estimatedNav)
        assertEquals("0.00", result.estimatedGrowthRate)
    }
}
