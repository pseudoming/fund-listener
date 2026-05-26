package com.fundlistener.client

import com.fundlistener.model.StockQuote
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

import com.fundlistener.repository.FundRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 股票行情缓存，实现 L1 (内存) + L2 (SQLite) 多级缓存策略。
 *
 * TTL 策略由 [TradingHoursChecker] 驱动：
 *   - 交易时段 30s 刷新
 *   - 非交易时段：利用 L2 SQLite 提供持久化的盘后收盘价，杜绝重启风暴
 *   - 停牌股 5min 刷新（避免交易时段反复无效请求）
 *
 * 调用方只需往 QuoteCache 里要数据，不关心是否命中缓存。
 */
class QuoteCache(
    private val client: StockQuoteClient,
    private val tencentClient: QuoteClient,
    private val repository: FundRepository
) {

    private val logger = LoggerFactory.getLogger(QuoteCache::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private data class CachedEntry(
        val quote: StockQuote,
        val timestampMs: Long
    )

    private val cache = ConcurrentHashMap<String, CachedEntry>()

    /**
     * 批量获取股票行情（L1 -> L2 -> API）。
     */
    suspend fun getQuotes(codes: List<String>): List<StockQuote> {
        if (codes.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val hits = mutableListOf<StockQuote>()
        val misses = mutableListOf<String>()

        // 1. L1 Memory Cache Check
        for (code in codes) {
            val entry = cache[code]
            if (entry != null) {
                val ttl = TradingHoursChecker.cacheTtlSeconds(entry.quote.isSuspended) * 1000
                if (now - entry.timestampMs < ttl) {
                    hits.add(entry.quote)
                    continue
                }
            }
            misses.add(code)
        }

        if (misses.isEmpty()) {
            logger.debug("QuoteCache: all {} code(s) hit L1 cache", codes.size)
            return hits
        }

        // 2. L2 SQLite Cache Check for Misses
        val l2Misses = mutableListOf<String>()
        val l2Hits = mutableListOf<StockQuote>()
        
        try {
            val dbMetas = repository.getStocksMetadata(misses)
            val dbMetaMap = dbMetas.associateBy { it.stockCode }
            
            val lastCloseTime = TradingHoursChecker.getLastMarketCloseTimeMs()
            val isTradingNow = TradingHoursChecker.isTradingNow()
            
            for (code in misses) {
                val meta = dbMetaMap[code]
                if (meta != null && meta.currentPrice != null) {
                    // Strictly Fresh Check
                    // 只有在非交易时间，且 L2 数据在最近一次收盘之后更新过，才是绝对稳定的收盘价
                    val isFreshClosePrice = !isTradingNow && meta.updatedAt >= lastCloseTime
                    
                    if (isFreshClosePrice) {
                        val market = try { com.fundlistener.model.MarketType.classify(code) } catch (_: Exception) { com.fundlistener.model.MarketType.A_SHARE }
                        val quote = StockQuote(
                            code = code,
                            name = meta.stockName,
                            market = market,
                            latestPrice = meta.currentPrice?.toBigDecimalOrNull(),
                            changePercent = meta.growthRate?.toBigDecimalOrNull(),
                            changeAmount = null,
                            pe = null,
                            pb = null,
                            high = null,
                            low = null,
                            open = null,
                            prevClose = null,
                            isSuspended = false
                        )
                        l2Hits.add(quote)
                        // Promote to L1
                        cache[code] = CachedEntry(quote, now)
                        logger.debug("QuoteCache: promoted {} from L2 to L1 (Strictly Fresh)", code)
                        continue
                    }
                }
                l2Misses.add(code)
            }
        } catch (e: Exception) {
            logger.warn("QuoteCache: failed to check L2 cache", e)
            l2Misses.clear()
            l2Misses.addAll(misses)
        }
        
        hits.addAll(l2Hits)

        // 3. Network Fetch for L2 Misses
        if (l2Misses.isNotEmpty()) {
            logger.info("QuoteCache: L1/L2 miss for {} stocks, fetching from API", l2Misses.size)
            var freshQuotes = client.fetchQuotes(l2Misses)
            
            // Fallback to Tencent API if EastMoney fails completely
            if (freshQuotes.isEmpty() && l2Misses.isNotEmpty()) {
                logger.warn("QuoteCache: EastMoney returned 0 quotes, falling back to Tencent API")
                val tencentTargets = l2Misses.map { it to "STOCK" }
                val tencentMap = tencentClient.fetchQuotes(tencentTargets)
                freshQuotes = l2Misses.mapNotNull { code ->
                    val formatted = tencentClient.formatTencentCode(code, "STOCK")
                    val tq = tencentMap[formatted] ?: tencentMap[formatted.lowercase()] ?: tencentMap[code] ?: tencentMap[code.lowercase()] ?: return@mapNotNull null
                    val market = try { com.fundlistener.model.MarketType.classify(code) } catch (_: Exception) { com.fundlistener.model.MarketType.A_SHARE }
                    StockQuote(
                        code = code,
                        name = tq.name,
                        market = market,
                        latestPrice = tq.price,
                        changePercent = tq.changePercent,
                        changeAmount = tq.changeAmount,
                        pe = null,
                        pb = null,
                        high = null,
                        low = null,
                        open = null,
                        prevClose = null,
                        isSuspended = false
                    )
                }
            }
            
            val nowMs = System.currentTimeMillis()
            
            val validQuotesToPersist = mutableListOf<StockQuote>()
            
            for (quote in freshQuotes) {
                cache[quote.code] = CachedEntry(quote, nowMs)
                if (quote.isSuspended) {
                    logger.info("QuoteCache: stock {} marked SUSPENDED, TTL set to 5min", quote.code)
                } else if (quote.latestPrice != null) {
                    validQuotesToPersist.add(quote)
                }
            }
            
            // 标记未返回的 code 为停牌/无效，避免短时重复查询
            val returnedCodes = freshQuotes.map { it.code }.toSet()
            for (miss in l2Misses) {
                if (miss !in returnedCodes) {
                    val market = try {
                        com.fundlistener.model.MarketType.classify(miss)
                    } catch (_: Exception) {
                        com.fundlistener.model.MarketType.A_SHARE
                    }
                    val suspendedEntry = CachedEntry(
                        quote = StockQuote(
                            code = miss,
                            name = "",
                            market = market,
                            latestPrice = null,
                            changePercent = null,
                            changeAmount = null,
                            pe = null,
                            pb = null,
                            high = null,
                            low = null,
                            open = null,
                            prevClose = null,
                            isSuspended = true
                        ),
                        timestampMs = nowMs
                    )
                    cache[miss] = suspendedEntry
                    logger.info("QuoteCache: stock {} not returned by API, caching as suspended", miss)
                }
            }
            hits.addAll(freshQuotes)
            
            // 4. Async Persist to L2
            if (validQuotesToPersist.isNotEmpty()) {
                scope.launch {
                    try {
                        val persistTime = System.currentTimeMillis()
                        for (q in validQuotesToPersist) {
                            val metadata = com.fundlistener.model.StockMetadata(
                                stockCode = q.code,
                                stockName = q.name,
                                marketType = q.market.name,
                                currentPrice = q.latestPrice?.toPlainString(),
                                growthRate = q.changePercent?.toPlainString(),
                                updatedAt = persistTime
                            )
                            repository.upsertStockMetadata(metadata)
                        }
                        logger.debug("QuoteCache: async persisted {} quotes to L2", validQuotesToPersist.size)
                    } catch (e: Exception) {
                        logger.warn("QuoteCache: async persist to L2 failed", e)
                    }
                }
            }
        }

        return hits
    }

    /**
     * 清除指定股票的缓存。
     */
    fun invalidate(code: String) {
        cache.remove(code)
        logger.debug("QuoteCache: invalidated {}", code)
    }

    /**
     * 清空全部缓存。
     */
    fun invalidateAll() {
        val count = cache.size
        cache.clear()
        logger.info("QuoteCache: cleared {} entries", count)
    }

    /**
     * 当前缓存条目数，供调试。
     */
    fun size(): Int = cache.size
}
