package com.fundlistener.service

import com.fundlistener.model.FundHolding
import com.fundlistener.model.FundHoldingsResponse
import com.fundlistener.model.StockQuote
import com.fundlistener.model.StockValuationDetail
import com.fundlistener.model.ValuationResult
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 估值计算引擎 — 纯函数，无 IO。
 *
 * 核心公式：
 *   加权涨跌幅 = Σ(涨跌幅 × 仓位占比) / Σ 仓位占比
 *   加权 PE    = Σ(PE × 仓位占比) / Σ 仓位占比（仅计入有 PE 数据的股票）
 *   加权 PB    = Σ(PB × 仓位占比) / Σ 仓位占比（仅计入有 PB 数据的股票）
 *   覆盖度     = 已覆盖仓位占比 / 总仓位占比 × 100%
 */
object ValuationEngine {

    private val SCALE = 4
    private val RM = RoundingMode.HALF_UP

    /**
     * 根据基金持仓与股票行情计算加权估值。
     *
     * @param holdings 基金前十大重仓股
     * @param quotes 对应的股票实时行情列表
     */
    fun calculate(holdings: FundHoldingsResponse, quotes: List<StockQuote>): ValuationResult {
        val quoteMap = quotes.associateBy { it.code }

        // 解析每只持仓股
        val details = holdings.holdings.map { h -> computeDetail(h, quoteMap) }

        val totalRatio = details.sumOf { BigDecimal(it.ratio) }
        val coveredRatio = details
            .filter { it.hasQuote }
            .sumOf { BigDecimal(it.ratio) }

        val weightedChange = computeWeighted(
            details = details.filter { it.hasQuote && it.changePercent != null },
            ratioExtractor = { BigDecimal(it.ratio) },
            valueExtractor = { BigDecimal(it.changePercent!!) }
        )

        val weightedPE = computeWeighted(
            details = details.filter { it.hasQuote && it.pe != null },
            ratioExtractor = { BigDecimal(it.ratio) },
            valueExtractor = { BigDecimal(it.pe!!) }
        )

        val weightedPB = computeWeighted(
            details = details.filter { it.hasQuote && it.pb != null },
            ratioExtractor = { BigDecimal(it.ratio) },
            valueExtractor = { BigDecimal(it.pb!!) }
        )

        val coveragePercent = if (totalRatio.compareTo(BigDecimal.ZERO) > 0) {
            coveredRatio.divide(totalRatio, SCALE, RM).multiply(BigDecimal(100))
        } else {
            BigDecimal.ZERO
        }

        return ValuationResult(
            fundCode = holdings.fundCode,
            reportDate = holdings.reportDate,
            weightedChangePercent = weightedChange?.setScale(2, RM)?.toPlainString(),
            weightedPE = weightedPE?.setScale(2, RM)?.toPlainString(),
            weightedPB = weightedPB?.setScale(2, RM)?.toPlainString(),
            coveragePercent = coveragePercent.setScale(2, RM).toPlainString(),
            totalRatioCovered = coveredRatio.setScale(2, RM).toPlainString(),
            totalRatio = totalRatio.setScale(2, RM).toPlainString(),
            missingCount = details.count { !it.hasQuote },
            stockDetails = details
        )
    }

    private fun computeDetail(holding: FundHolding, quoteMap: Map<String, StockQuote>): StockValuationDetail {
        val quote = quoteMap[holding.stockCode]
        val hasQuote = quote != null && !quote.isSuspended
        return StockValuationDetail(
            stockCode = holding.stockCode,
            stockName = holding.stockName,
            ratio = holding.ratio,
            changePercent = quote?.changePercent?.toPlainString(),
            pe = quote?.pe?.toPlainString(),
            pb = quote?.pb?.toPlainString(),
            hasQuote = hasQuote
        )
    }

    private fun computeWeighted(
        details: List<StockValuationDetail>,
        ratioExtractor: (StockValuationDetail) -> BigDecimal,
        valueExtractor: (StockValuationDetail) -> BigDecimal
    ): BigDecimal? {
        if (details.isEmpty()) return null
        val totalWeight = details.sumOf { ratioExtractor(it) }
        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) return null
        val weightedSum = details.sumOf {
            ratioExtractor(it).multiply(valueExtractor(it))
        }
        return weightedSum.divide(totalWeight, SCALE, RM)
    }
}
