package com.fundlistener.client

import com.fundlistener.model.EastMoneyQuoteResponse
import com.fundlistener.model.MarketType
import com.fundlistener.model.StockQuote
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * 股票实时行情客户端，封装东方财富 push2 批量接口。
 *
 * 按市场类型分发到同一 API，通过 secid 前缀区分市场:
 *   A 股沪市: 0.600519
 *   A 股深市: 1.000333
 *   港股:      116.00700
 *   美股:      105.NVDA
 *
 * push2 API 支持单次请求混合多市场 secids（逗号分隔）。
 *
 * 注意：美股 secid 统一使用 105 前缀，该前缀覆盖 NASDAQ。
 *       部分 NYSE 股票可能需要 106 前缀，如果查不到可先尝试 105、
 *       再尝试 106 做二次查询，见 [fetchQuotesWithFallback]。
 */
class StockQuoteClient(private val httpClient: HttpClient) {

    private val logger = LoggerFactory.getLogger(StockQuoteClient::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    companion object {
        private const val PUSH2_URL = "http://push2.eastmoney.com/api/qt/ulist.np/get"
        // push2 API 字段映射:
        // f2=最新价 f3=涨跌幅 f4=涨跌额 f9=PE(TTM) f12=代码 f14=名称
        // f15=最高 f16=最低 f17=今开 f18=昨收 f23=PB
        private const val QUOTE_FIELDS = "f2,f3,f4,f9,f12,f14,f15,f16,f17,f18,f23"
    }

    /**
     * 批量获取股票实时行情。
     *
     * @param codes 股票代码列表，混合市场（如 ["600519", "00700", "NVDA"]）
     * @return 成功获取到的行情列表，查不到的股票会被静默忽略
     */
    suspend fun fetchQuotes(codes: List<String>): List<StockQuote> {
        if (codes.isEmpty()) return emptyList()

        val codeToMarket = mutableMapOf<String, MarketType>()
        val secIds = codes.mapNotNull { code ->
            val trimmed = code.trim()
            try {
                val market = MarketType.classify(trimmed)
                codeToMarket[trimmed] = market
                MarketType.toSecId(trimmed)
            } catch (e: IllegalArgumentException) {
                logger.warn("Skipping unknown stock code: $code")
                null
            }
        }

        if (secIds.isEmpty()) return emptyList()

        return fetchQuotesBySecIds(secIds, codeToMarket)
    }

    private suspend fun fetchQuotesBySecIds(secIds: List<String>, codeToMarket: Map<String, MarketType>): List<StockQuote> {
        if (secIds.isEmpty()) return emptyList()

        return try {
            val body = RetryPolicy.STANDARD.execute {
                val response: HttpResponse = httpClient.get(PUSH2_URL) {
                    url {
                        parameters.append("fltt", "2")
                        parameters.append("fields", QUOTE_FIELDS)
                        parameters.append("secids", secIds.joinToString(","))
                    }
                    headers {
                        append("Referer", "http://quote.eastmoney.com/")
                        append("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    }
                }
                response.readRawBytes().toString(Charsets.UTF_8)
            } ?: return emptyList()
            parseQuotes(body, codeToMarket)
        } catch (e: Exception) {
            logger.error("Failed to fetch stock quotes for ${secIds.size} secids", e)
            emptyList()
        }
    }

    /**
     * 带美股交易所回退的批量行情查询。
     *
     * 先用 105(NASDAQ) 前缀查询所有美股，未命中的再按 106(NYSE) 二次查询。
     * 用于解决部分 NYSE 股票（如 BRK.B）在 105 前缀下无数据的问题。
     * 目前先留 TODO，S09 根据实际测试决定是否需要启用此方法。
     */
    suspend fun fetchQuotesWithFallback(codes: List<String>): List<StockQuote> {
        val initialResults = fetchQuotes(codes)
        val initialCodes = initialResults.map { it.code }.toSet()

        val missingUsCodes = codes.map { it.trim() }
            .filter { it !in initialCodes }
            .filter { 
                try {
                    MarketType.classify(it) == MarketType.US_STOCK
                } catch (e: Exception) {
                    false
                }
            }

        if (missingUsCodes.isEmpty()) {
            return initialResults
        }

        val codeToMarket = missingUsCodes.associateWith { MarketType.US_STOCK }
        val fallbackSecIds = missingUsCodes.map { "106.$it" }
        
        val fallbackResults = fetchQuotesBySecIds(fallbackSecIds, codeToMarket)
        
        return initialResults + fallbackResults
    }

    internal fun parseQuotes(
        raw: String,
        codeToMarket: Map<String, MarketType>
    ): List<StockQuote> {
        return try {
            val response = json.decodeFromString<EastMoneyQuoteResponse>(raw)
            val items = response.data?.diff ?: return emptyList()
            items.mapNotNull { item ->
                val code = item.f12 ?: return@mapNotNull null
                val market = codeToMarket[code] ?: return@mapNotNull null
                item.toStockQuote(market)
            }
        } catch (e: Exception) {
            logger.error("Failed to parse stock quote response", e)
            emptyList()
        }
    }
}
