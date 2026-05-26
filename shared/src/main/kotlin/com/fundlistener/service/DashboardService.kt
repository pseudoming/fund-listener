package com.fundlistener.service

import com.fundlistener.model.DashboardFund
import com.fundlistener.model.DashboardResponse
import com.fundlistener.repository.FundRepository
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId

/**
 * 首页看板服务 — 聚合总持仓、今日盈亏、高估预警。
 *
 * 支持两种 QDII 展示模式（通过 app_config 中的 qdii_display_mode 控制）：
 *   - "alipay"  (默认): 与支付宝对齐，仅展示已结算的官方净值，日期平移一天
 *   - "realtime": 展示实时盘中估算，数据更超前但未经官方确认
 */
class DashboardService(
    private val fundService: FundService,
    private val repository: FundRepository,
    private val quoteClient: com.fundlistener.client.QuoteClient,
    private val normalizer: ValuationDisplayNormalizer
) {
    private val logger = LoggerFactory.getLogger(DashboardService::class.java)

    companion object {
        /** PE 百分位超过此阈值视为高估 */
        const val OVERVALUED_THRESHOLD = 70
        private val SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai")
    }

    suspend fun getDashboard(): DashboardResponse {
        val positions = repository.getAllPositions()
        if (positions.isEmpty()) {
            return DashboardResponse(
                funds = emptyList(),
                totalCost = "0.00",
                totalMarketValue = "0.00",
                latestPnl = "0.00",
                latestPnlPercent = "0.00",
                overvaluedCount = 0,
                overvaluedThreshold = OVERVALUED_THRESHOLD,
                lastUpdatedTime = java.time.LocalDateTime.now(SHANGHAI_ZONE)
                    .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
            )
        }

        // 读取 QDII 展示模式配置，默认 alipay
        val qdiiMode = try {
            repository.getConfig("qdii_display_mode") ?: "alipay"
        } catch (_: Exception) { "alipay" }

        val today = LocalDate.now(SHANGHAI_ZONE)

        val fundItems = mutableListOf<DashboardFund>()
        var totalCost = BigDecimal.ZERO
        var totalMarketValue = BigDecimal.ZERO
        var totalPnl = BigDecimal.ZERO

        for (pos in positions) {
            val meta = try {
                val cached = repository.getFundMetadata(pos.fundCode)
                if (cached != null) {
                    cached
                } else {
                    fundService.getRealtimeEstimation(pos.fundCode)
                    repository.getFundMetadata(pos.fundCode)
                }
            } catch (_: Exception) { null }

            var latestNavRecord = repository.getLatestNav(pos.fundCode)
            var latestSnapshot = repository.getLatestSnapshot(pos.fundCode)
            
            // 如果本地没有流水数据，则强制拉取一次
            if (latestNavRecord == null && latestSnapshot == null) {
                try {
                    fundService.getRealtimeEstimation(pos.fundCode)
                    latestNavRecord = repository.getLatestNav(pos.fundCode)
                    latestSnapshot = repository.getLatestSnapshot(pos.fundCode)
                } catch (_: Exception) {}
            }

            val navHistory = repository.getNavHistory(pos.fundCode, limit = 5)
            val yesterdayNavStr = if (navHistory.size >= 2) navHistory[1].nav.toPlainString() else null
            
            val estimationTimeStr = latestSnapshot?.snapshotTime?.let {
                java.time.Instant.ofEpochMilli(it)
                    .atZone(SHANGHAI_ZONE)
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            } ?: ""

            val estimation = com.fundlistener.model.FundEstimation(
                code = pos.fundCode,
                name = meta?.fundName ?: pos.fundName,
                navDate = latestNavRecord?.navDate ?: "",
                nav = latestNavRecord?.nav?.toPlainString() ?: "",
                estimatedNav = latestSnapshot?.estimatedNav?.toPlainString() ?: "",
                estimatedGrowthRate = latestSnapshot?.estimatedGrowthRate?.toPlainString() ?: "",
                estimationTime = estimationTimeStr,
                yesterdayNav = yesterdayNavStr ?: ""
            )

            var growthRate = estimation.estimatedGrowthRate.toBigDecimalOrNull()
            val pePercentile = latestSnapshot?.pePercentile
            val pbPercentile = latestSnapshot?.pbPercentile
            val isOvervalued = pePercentile != null && pePercentile >= BigDecimal(OVERVALUED_THRESHOLD)

            val cost = pos.totalCost
            totalCost += cost

            val yesterdayNav = yesterdayNavStr?.toBigDecimalOrNull() ?: pos.avgCostNav
            var currentNav = estimation.estimatedNav.takeIf { it.isNotBlank() }?.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }

            // QDII 盘中实时估值降级逻辑 (Fallback using linked ETF)
            if (currentNav == null && !meta?.linkedEtfCode.isNullOrBlank()) {
                try {
                    val etfMeta = repository.getStockMetadata(meta!!.linkedEtfCode!!)
                    val etfGrowthStr = etfMeta?.growthRate
                    if (!etfGrowthStr.isNullOrBlank()) {
                        val etfGrowth = etfGrowthStr.toBigDecimalOrNull()
                        if (etfGrowth != null) {
                            growthRate = etfGrowth
                            val baseNav = estimation.nav.takeIf { it.isNotBlank() }?.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO } ?: yesterdayNav
                            currentNav = baseNav.multiply(
                                BigDecimal.ONE.add(etfGrowth.divide(BigDecimal(100), 8, RoundingMode.HALF_UP))
                            )
                            logger.info("QDII Fallback calculation for \${pos.fundCode}: Linked ETF \${meta.linkedEtfCode} growthRate=\$etfGrowth%, baseNav=\$baseNav -> currentNav=\$currentNav")
                        }
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to apply QDII fallback for \${pos.fundCode} using ETF \${meta?.linkedEtfCode}", e)
                }
            }

            // 安全兜底
            if (currentNav == null || currentNav <= BigDecimal.ZERO) {
                currentNav = estimation.nav.takeIf { it.isNotBlank() }?.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
                    ?: yesterdayNav
                
                if (currentNav <= BigDecimal.ZERO) {
                    logger.error("Data pipeline broken for \${pos.fundCode}: currentNav is 0. yesterdayNav=\$yesterdayNav, avgCostNav=\${pos.avgCostNav}")
                    throw IllegalStateException("持仓市值为0异常：基金 \${pos.fundCode} 无法获取有效净值数据 (数据链路断点：无有效昨日净值或成本净值)")
                }
            }

            // 使用公共 Normalize 逻辑统一转换显示指标
            val norm = normalizer.normalize(
                estimation = estimation.copy(
                    estimatedNav = currentNav.toPlainString(),
                    yesterdayNav = yesterdayNav.toPlainString(),
                    estimatedGrowthRate = growthRate?.toPlainString() ?: estimation.estimatedGrowthRate
                ),
                meta = meta,
                qdiiMode = qdiiMode
            )

            val displayNav = norm.displayNav.toBigDecimal()
            val displayYesterdayNav = norm.displayYesterdayNav.toBigDecimal()
            val displayGrowthRate = norm.displayGrowthRate.toBigDecimal()
            
            // 收益计算仍与持仓份额相关
            val displayPnl = if (norm.isSettled) {
                displayNav.subtract(displayYesterdayNav).multiply(pos.totalShares)
            } else {
                displayYesterdayNav.multiply(pos.totalShares)
                    .multiply(displayGrowthRate)
                    .divide(BigDecimal(100), 8, RoundingMode.HALF_UP)
            }


            val marketVal = displayNav.multiply(pos.totalShares)
            totalMarketValue += marketVal
            totalPnl += displayPnl

            fundItems.add(
                DashboardFund(
                    fundCode = pos.fundCode,
                    fundName = estimation.name,
                    totalShares = pos.totalShares.toPlainString(),
                    totalCost = cost.toPlainString(),
                    avgCostNav = pos.avgCostNav.toPlainString(),
                    estimatedNav = norm.displayNav,
                    estimatedGrowthRate = norm.displayGrowthRate,
                    pePercentile = pePercentile?.toPlainString(),
                    pbPercentile = pbPercentile?.toPlainString(),
                    isOvervalued = isOvervalued,
                    latestPnl = displayPnl.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    yesterdayNav = norm.displayYesterdayNav,
                    isSettled = norm.isSettled,
                    navDate = norm.displayNavDate
                )
            )
        }

        val totalYesterdayValue = totalMarketValue - totalPnl
        val pnlPercent = if (totalYesterdayValue.compareTo(BigDecimal.ZERO) > 0) {
            totalPnl.divide(totalYesterdayValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100))
        } else BigDecimal.ZERO

        val overvaluedCount = fundItems.count { it.isOvervalued }

        // Fetch market indices
        val indexTargets = listOf(
            "sh000001" to "INDEX",
            "sz399001" to "INDEX",
            ".IXIC" to "INDEX" // us.IXIC
        )
        val indexQuotes = try {
            quoteClient.fetchQuotes(indexTargets)
        } catch (e: Exception) {
            emptyMap()
        }

        val marketIndices = mutableListOf<com.fundlistener.model.MarketIndexQuote>()
        
        val shQuote = indexQuotes["sh000001"] ?: indexQuotes["000001"]
        if (shQuote != null) {
            val isRise = shQuote.changePercent > BigDecimal.ZERO
            val isFall = shQuote.changePercent < BigDecimal.ZERO
            val sign = if (isRise) "+" else ""
            marketIndices.add(com.fundlistener.model.MarketIndexQuote(
                name = "上证",
                changePercent = "$sign${shQuote.changePercent.toPlainString()}%",
                isRise = isRise,
                isFall = isFall
            ))
        }

        val szQuote = indexQuotes["sz399001"] ?: indexQuotes["399001"]
        if (szQuote != null) {
            val isRise = szQuote.changePercent > BigDecimal.ZERO
            val isFall = szQuote.changePercent < BigDecimal.ZERO
            val sign = if (isRise) "+" else ""
            marketIndices.add(com.fundlistener.model.MarketIndexQuote(
                name = "深证",
                changePercent = "$sign${szQuote.changePercent.toPlainString()}%",
                isRise = isRise,
                isFall = isFall
            ))
        }

        val ndxQuote = indexQuotes["us.ixic"] ?: indexQuotes[".ixic"] ?: indexQuotes[".IXIC"] ?: indexQuotes["us.IXIC"]
        if (ndxQuote != null) {
            val isRise = ndxQuote.changePercent > BigDecimal.ZERO
            val isFall = ndxQuote.changePercent < BigDecimal.ZERO
            val sign = if (isRise) "+" else ""
            marketIndices.add(com.fundlistener.model.MarketIndexQuote(
                name = "纳指",
                changePercent = "$sign${ndxQuote.changePercent.toPlainString()}%",
                isRise = isRise,
                isFall = isFall
            ))
        }

        return DashboardResponse(
            funds = fundItems,
            totalCost = totalCost.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            totalMarketValue = totalMarketValue.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            latestPnl = totalPnl.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            latestPnlPercent = pnlPercent.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            overvaluedCount = overvaluedCount,
            overvaluedThreshold = OVERVALUED_THRESHOLD,
            lastUpdatedTime = java.time.LocalDateTime.now(SHANGHAI_ZONE)
                .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")),
            marketIndices = marketIndices
        )
    }

    /**
     * 计算整个个人账户的底层股票穿透持仓金额与占比。
     */
    suspend fun getPortfolioPenetration(): com.fundlistener.model.PenetrationResponse {
        val dashboard = getDashboard()
        val totalMarketValue = dashboard.totalMarketValue.toBigDecimalOrNull() ?: BigDecimal.ZERO

        if (totalMarketValue <= BigDecimal.ZERO) {
            return com.fundlistener.model.PenetrationResponse("0.00", emptyList())
        }

        // 股票代码 -> 暴露金额
        val exposureMap = mutableMapOf<String, BigDecimal>()
        val codeToName = mutableMapOf<String, String>()
        val codeToMarket = mutableMapOf<String, String>()
        val codeToGrowth = mutableMapOf<String, String?>()

        for (fund in dashboard.funds) {
            val fundMarketValue = fund.totalShares.toBigDecimalOrNull()?.multiply(
                fund.estimatedNav?.toBigDecimalOrNull() ?: BigDecimal.ZERO
            ) ?: BigDecimal.ZERO

            if (fundMarketValue <= BigDecimal.ZERO) continue

            val holdings = repository.getFundHoldingMappings(fund.fundCode)
            for (holding in holdings) {
                val weight = holding.weightPercent.toBigDecimalOrNull()?.divide(BigDecimal(100), 8, RoundingMode.HALF_UP)
                    ?: BigDecimal.ZERO
                val exposure = fundMarketValue.multiply(weight)
                
                exposureMap[holding.stockCode] = exposureMap.getOrDefault(holding.stockCode, BigDecimal.ZERO).add(exposure)
                
                // 尝试获取股票元数据
                val stockMeta = repository.getStockMetadata(holding.stockCode)
                if (stockMeta != null && stockMeta.marketType != "UNKNOWN") {
                    codeToName[holding.stockCode] = stockMeta.stockName
                    codeToMarket[holding.stockCode] = try {
                        com.fundlistener.model.MarketType.valueOf(stockMeta.marketType).toString()
                    } catch (e: Exception) {
                        stockMeta.marketType
                    }
                    codeToGrowth[holding.stockCode] = stockMeta.growthRate
                } else {
                    codeToName[holding.stockCode] = stockMeta?.stockName ?: holding.stockCode
                    codeToMarket[holding.stockCode] = try {
                        com.fundlistener.model.MarketType.classify(holding.stockCode).toString()
                    } catch (e: Exception) {
                        "UNKNOWN"
                    }
                    codeToGrowth[holding.stockCode] = stockMeta?.growthRate
                }
            }
        }

        // 排序取前 10
        val topStocks = exposureMap.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { (stockCode, exposure) ->
                val ratio = exposure.divide(totalMarketValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100))
                val growthRateStr = codeToGrowth[stockCode]
                val changeAmount = growthRateStr?.toBigDecimalOrNull()?.let { rate ->
                    exposure.multiply(rate).divide(BigDecimal(100), 2, RoundingMode.HALF_UP).toPlainString()
                }

                com.fundlistener.model.StockExposure(
                    stockCode = stockCode,
                    stockName = codeToName[stockCode] ?: stockCode,
                    marketType = codeToMarket[stockCode] ?: "UNKNOWN",
                    totalExposure = exposure.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    exposureRatio = ratio.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    growthRate = growthRateStr,
                    changeAmount = changeAmount
                )
            }

        return com.fundlistener.model.PenetrationResponse(
            totalMarketValue = totalMarketValue.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            topStocks = topStocks
        )
    }

    /**
     * 计算整个个人账户持仓组合的历史收益走势与最大回撤
     * 近似计算方式：用当前持仓份额 × 历史每一天的净值，得到每一天的历史总市值
     */
    suspend fun getPortfolioTrend(): com.fundlistener.model.TrendAndDrawdownResponse {
        val positions = repository.getAllPositions()
        if (positions.isEmpty()) {
            return com.fundlistener.model.TrendAndDrawdownResponse(emptyList(), "0.00", "", "", "0.00")
        }

        // date -> total value
        val dailyMarketValue = sortedMapOf<String, BigDecimal>()
        val dateCounts = mutableMapOf<String, Int>()
        var totalCurrentCost = BigDecimal.ZERO

        for (pos in positions) {
            totalCurrentCost = totalCurrentCost.add(pos.totalCost)
            val history = repository.getNavHistory(pos.fundCode, limit = 360)
            for (nav in history) {
                val value = nav.nav.multiply(pos.totalShares)
                dailyMarketValue[nav.navDate] = dailyMarketValue.getOrDefault(nav.navDate, BigDecimal.ZERO).add(value)
                dateCounts[nav.navDate] = dateCounts.getOrDefault(nav.navDate, 0) + 1
            }
        }

        // 仅保留所有基金都有数据的日期，防止因某基金数据缺失导致总市值大幅回落产生的“虚假收益”
        val validDates = dateCounts.filter { it.value == positions.size }.keys
        dailyMarketValue.keys.retainAll(validDates)

        if (dailyMarketValue.isEmpty()) {
            return com.fundlistener.model.TrendAndDrawdownResponse(emptyList(), "0.00", "", "", "0.00")
        }

        var peak = BigDecimal.ZERO
        var maxDrawdown = BigDecimal.ZERO
        var peakDate = ""
        var mdStartDate = ""
        var mdEndDate = ""

        val trendPoints = mutableListOf<com.fundlistener.model.TrendPoint>()

        for ((dateStr, value) in dailyMarketValue) {
            if (peakDate.isEmpty()) {
                peak = value
                peakDate = dateStr
                mdStartDate = dateStr
                mdEndDate = dateStr
            }

            if (value > peak) {
                peak = value
                peakDate = dateStr
            } else {
                if (peak > BigDecimal.ZERO) {
                    val drawdown = (peak.subtract(value)).divide(peak, 8, RoundingMode.HALF_UP)
                    if (drawdown > maxDrawdown) {
                        maxDrawdown = drawdown
                        mdStartDate = peakDate
                        mdEndDate = dateStr
                    }
                }
            }

            trendPoints.add(com.fundlistener.model.TrendPoint(dateStr, value.setScale(2, RoundingMode.HALF_UP).toPlainString()))
        }

        val firstVal = dailyMarketValue.values.first()
        val lastVal = dailyMarketValue.values.last()
        val totalReturn = if (firstVal > BigDecimal.ZERO) {
            (lastVal.subtract(firstVal)).divide(firstVal, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100))
        } else {
            BigDecimal.ZERO
        }

        return com.fundlistener.model.TrendAndDrawdownResponse(
            trend = trendPoints,
            maxDrawdown = maxDrawdown.multiply(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP).toPlainString(),
            maxDrawdownStartDate = mdStartDate,
            maxDrawdownEndDate = mdEndDate,
            totalReturn = totalReturn.setScale(2, RoundingMode.HALF_UP).toPlainString()
        )
    }
}
