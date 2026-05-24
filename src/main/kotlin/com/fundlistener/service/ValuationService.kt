package com.fundlistener.service

import com.fundlistener.client.QuoteCache
import com.fundlistener.model.ValuationResult
import com.fundlistener.model.ValuationSnapshot
import com.fundlistener.repository.FundRepository
import org.slf4j.LoggerFactory
import java.math.BigDecimal

/**
 * 估值服务 — 编排持仓抓取 + 行情获取 + 加权估值 + 百分位 + 快照写入 + 事件分发。
 */
class ValuationService(
    private val fundService: FundService,
    private val quoteCache: QuoteCache,
    private val repository: FundRepository,
    private val eventBus: ValuationEventBus
) {
    private val logger = LoggerFactory.getLogger(ValuationService::class.java)

    /**
     * 对指定基金执行完整的穿透估值计算：
     *   1. 获取基金前十大重仓股
     *   2. 批量获取股票行情（走缓存）
     *   3. ValuationEngine 计算加权估值
     *   4. 获取基金实时估值数据
     *   5. 计算历史 PE/PB 百分位（含降级逻辑）
     *   6. 估值快照写入 valuation_snapshot
     *   7. 通过 ValuationEventBus 分发事件
     */
    suspend fun evaluate(code: String): ValuationResult {
        // 1. 获取持仓
        val holdings = fundService.getFundHoldings(code)
        logger.info("Valuation: fund {} holdings fetched, reportDate={}, stocks={}",
            code, holdings.reportDate, holdings.holdings.size)

        // 2. 批量获取行情
        val stockCodes = holdings.holdings.map { it.stockCode }
        val quotes = quoteCache.getQuotes(stockCodes)
        logger.info("Valuation: got {} quote(s) for {} stocks", quotes.size, stockCodes.size)

        // 3. 计算加权估值
        val result = ValuationEngine.calculate(holdings, quotes)
        logResult(result)

        // 4. 获取基金实时估值
        val estimation = try {
            fundService.getRealtimeEstimation(code)
        } catch (e: Exception) {
            logger.warn("Valuation: failed to get realtime estimation for {}: {}", code, e.message)
            null
        }

        // 5. 计算历史百分位
        val history = repository.getSnapshots(code, limit = 365)
        val pePercentileResult = if (result.weightedPE != null) {
            PercentileCalculator.calcPEPercentile(BigDecimal(result.weightedPE), history)
        } else null

        val pbPercentileResult = if (result.weightedPB != null) {
            PercentileCalculator.calcPBPercentile(BigDecimal(result.weightedPB), history)
        } else null

        // 6. 写入快照
        val now = System.currentTimeMillis()
        val snapshot = ValuationSnapshot(
            fundCode = code,
            snapshotTime = now,
            estimatedNav = estimation?.nav?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
            estimatedGrowthRate = estimation?.estimatedGrowthRate?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
            weightedPe = result.weightedPE?.toBigDecimalOrNull(),
            weightedPb = result.weightedPB?.toBigDecimalOrNull(),
            pePercentile = (pePercentileResult as? PercentileCalculator.PercentileResult.Valid)?.percentile,
            pbPercentile = (pbPercentileResult as? PercentileCalculator.PercentileResult.Valid)?.percentile,
            coverageRate = result.coveragePercent.toBigDecimalOrNull(),
            createdAt = now
        )
        repository.insertSnapshot(snapshot)
        logger.info("Valuation: snapshot written for {} (historical days: {})",
            code, history.size)

        // 7. 事件分发
        eventBus.onSnapshotWritten(snapshot)

        // 8. 百分位日志
        logPercentile("PE", pePercentileResult, history.size)
        logPercentile("PB", pbPercentileResult, history.size)

        return result
    }

    private fun logResult(r: ValuationResult) {
        val missingInfo = if (r.missingCount > 0) " (${r.missingCount} stock(s) missing)" else ""
        logger.info(
            "Valuation result for {} | report={} | weightedChange={} | weightedPE={} | weightedPB={} | coverage={}%{}",
            r.fundCode,
            r.reportDate,
            r.weightedChangePercent ?: "N/A",
            r.weightedPE ?: "N/A",
            r.weightedPB ?: "N/A",
            r.coveragePercent,
            missingInfo
        )
        for (d in r.stockDetails) {
            val status = when {
                !d.hasQuote -> "⛔ MISSING"
                d.changePercent == null -> "⚠  NO_DATA"
                else -> "✓"
            }
            val change = d.changePercent?.let { "${it}%" } ?: "-"
            val pe = d.pe ?: "-"
            val pb = d.pb ?: "-"
            logger.info(
                "  {} {} | {} | ratio={}% | change={} | PE={} | PB={}",
                status, d.stockCode, d.stockName, d.ratio, change, pe, pb
            )
        }
    }

    private fun logPercentile(label: String, result: PercentileCalculator.PercentileResult?, historyDays: Int) {
        when (result) {
            is PercentileCalculator.PercentileResult.Valid ->
                logger.info("  {} 百分位: {}% (历史 {} 天)", label, result.percentile.toPlainString(), historyDays)
            is PercentileCalculator.PercentileResult.Degraded ->
                logger.info("  {} 百分位: 数据积累中 (已有 {} 天，需要 {} 天)", label, result.currentDays, result.requiredDays)
            is PercentileCalculator.PercentileResult.NoValue ->
                logger.info("  {} 百分位: 无当前值，跳过", label)
            null -> {}
        }
    }
}
