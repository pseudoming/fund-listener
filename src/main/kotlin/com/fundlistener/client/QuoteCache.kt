package com.fundlistener.client

import com.fundlistener.model.StockQuote
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * 股票行情缓存，包裹 StockQuoteClient 实现带 TTL 的批量缓存。
 *
 * TTL 策略由 [TradingHoursChecker] 驱动：
 *   - 交易时段 30s 刷新
 *   - 非交易时段 1h 刷新
 *   - 停牌股 5min 刷新（避免交易时段反复无效请求）
 *
 * 调用方只需往 QuoteCache 里要数据，不关心是否命中缓存。
 */
class QuoteCache(private val client: StockQuoteClient) {

    private val logger = LoggerFactory.getLogger(QuoteCache::class.java)

    private data class CachedEntry(
        val quote: StockQuote,
        val timestampMs: Long
    )

    private val cache = ConcurrentHashMap<String, CachedEntry>()

    /**
     * 批量获取股票行情（缓存优先）。
     *
     * @param codes 股票代码列表
     * @return 所有成功获取到的行情数据（缓存 + 新抓取）
     */
    suspend fun getQuotes(codes: List<String>): List<StockQuote> {
        if (codes.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val hits = mutableListOf<StockQuote>()
        val misses = mutableListOf<String>()

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

        if (misses.isNotEmpty()) {
            logger.info("QuoteCache: {} hit(s), {} miss(es) — fetching from API", hits.size, misses.size)
            val freshQuotes = client.fetchQuotes(misses)
            val nowMs = System.currentTimeMillis()
            for (quote in freshQuotes) {
                cache[quote.code] = CachedEntry(quote, nowMs)
                if (quote.isSuspended) {
                    logger.info("QuoteCache: stock {} marked SUSPENDED, TTL set to 5min", quote.code)
                }
            }
            // 标记未返回的 code 为停牌/无效，缓存空标记避免短时重复查询
            val returnedCodes = freshQuotes.map { it.code }.toSet()
            for (miss in misses) {
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
        } else {
            logger.debug("QuoteCache: all {} code(s) hit cache", codes.size)
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
