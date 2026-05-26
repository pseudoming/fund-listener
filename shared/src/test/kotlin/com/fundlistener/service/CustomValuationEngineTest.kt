package com.fundlistener.service

import com.fundlistener.client.QuoteClient
import com.fundlistener.client.TianTianFundClient
import com.fundlistener.model.*
import com.fundlistener.repository.FundRepository
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CustomValuationEngineTest {

    private val mockRepository = object : FundRepository {
        override suspend fun getAllPositions(): List<FundPosition> = emptyList()
        override suspend fun getPosition(fundCode: String): FundPosition? {
            return FundPosition(fundCode, "Test Custom Fund", BigDecimal.ZERO, BigDecimal.ZERO, 0, 0)
        }
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
            return NavHistory(1, fundCode, "2026-05-19", BigDecimal("1.0000"), null, 0)
        }

        override suspend fun insertSnapshot(snapshot: ValuationSnapshot) {}
        override suspend fun getSnapshots(fundCode: String, limit: Int): List<ValuationSnapshot> = emptyList()
        override suspend fun getLatestSnapshot(fundCode: String): ValuationSnapshot? = null
        override suspend fun getSnapshotCount(fundCode: String): Int = 0

        override suspend fun getValuationRules(fundCode: String): List<FundValuationRule> {
            return listOf(
                FundValuationRule(1, fundCode, "STOCK", "000858", 40.0, 0),
                FundValuationRule(2, fundCode, "INDEX", "000300", 60.0, 0)
            )
        }
        override suspend fun insertValuationRule(rule: FundValuationRule) {}
        override suspend fun deleteValuationRules(fundCode: String) {}
        override suspend fun deleteValuationRule(id: Long) {}
        override suspend fun saveValuationRules(fundCode: String, rules: List<FundValuationRule>) {}

        override suspend fun searchFunds(keyword: String, limit: Int): List<FundSearchResult> = emptyList()
        override suspend fun batchInsertFunds(funds: List<FundSearchResult>) {}
        override suspend fun getFundCount(): Int = 0

        override suspend fun getConfig(key: String): String? = null
        override suspend fun setConfig(key: String, value: String) {}
        override suspend fun getAllWatchlist(): List<String> = emptyList()
        override suspend fun getWatchlistItems(): List<Pair<String, Long>> = emptyList()
        override suspend fun addToWatchlist(fundCode: String) {}
        override suspend fun removeFromWatchlist(fundCode: String) {}
        override suspend fun isInWatchlist(fundCode: String): Boolean = false
        override suspend fun getFundMetadata(fundCode: String): FundMetadata? = null
        override suspend fun upsertFundMetadata(metadata: FundMetadata) {}
        override suspend fun getAllFundMetadata(): List<FundMetadata> = emptyList()
        override suspend fun upsertStockMetadata(metadata: StockMetadata) {}
        override suspend fun getStockMetadata(stockCode: String): StockMetadata? = null
        override suspend fun getStocksMetadata(stockCodes: List<String>): List<StockMetadata> = emptyList()
        override suspend fun batchUpsertFundHoldingMappings(mappings: List<FundHoldingMapping>) {}
        override suspend fun deleteFundHoldingMappings(fundCode: String) {}
        override suspend fun getFundHoldingMappings(fundCode: String): List<FundHoldingMapping> = emptyList()
        override suspend fun upsertStockPriceHistory(history: StockPriceHistory) {}
        override suspend fun getLatestStockPrice(stockCode: String): StockPriceHistory? = null
    }

    private val mockQuoteClient = object : QuoteClient(HttpClient(OkHttp)) {
        override suspend fun fetchQuotes(targets: List<Pair<String, String>>): Map<String, Quote> {
            return mapOf(
                "000858" to Quote("000858", "五粮液", BigDecimal("180.00"), BigDecimal("4.50"), BigDecimal("2.50")), // +2.50%
                "000300" to Quote("000300", "沪深300", BigDecimal("3950.00"), BigDecimal("-39.50"), BigDecimal("-1.00")) // -1.00%
            )
        }
    }

    private val mockTianTianClient = TianTianFundClient(HttpClient(OkHttp))

    private val engine = CustomValuationEngine(mockRepository, mockQuoteClient, org.mockito.kotlin.mock(), mockTianTianClient)

    @Test
    fun `estimate should compute weighted valuation correctly`() = kotlinx.coroutines.runBlocking {
        val result = engine.estimate("110022")

        assertNotNull(result)
        assertEquals("110022", result.code)
        assertEquals("Test Custom Fund", result.name)
        assertEquals("2026-05-19", result.navDate)
        assertEquals("1.0000", result.nav)

        // Weight: 40% * 2.50% + 60% * (-1.00%) = 1.00% - 0.60% = 0.40%
        assertEquals("0.40", result.estimatedGrowthRate)

        // NAV: 1.0000 * (1 + 0.40 / 100) = 1.0040
        assertEquals("1.0040", result.estimatedNav)
    }
}
