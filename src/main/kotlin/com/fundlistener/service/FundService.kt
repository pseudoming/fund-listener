package com.fundlistener.service

import com.fundlistener.client.TianTianFundClient
import com.fundlistener.model.FundEstimation
import com.fundlistener.repository.FundRepository

/**
 * 基金服务层
 * 当前仅封装实时估值获取，后续 Phase 会扩展持仓管理、重仓穿透等能力
 */
class FundService(
    private val fundClient: TianTianFundClient,
    private val repository: FundRepository,
    private val customValuationEngine: CustomValuationEngine
) {

    /**
     * 获取基金实时估值
     * 如果存在自定义估值规则，优先使用自定义估值引擎计算；否则回退到天天基金。
     * @throws IllegalArgumentException 基金代码为空或接口返回空
     */
    suspend fun getRealtimeEstimation(code: String): FundEstimation {
        require(code.isNotBlank()) { "Fund code must not be blank" }
        
        val rules = repository.getValuationRules(code)
        if (rules.isNotEmpty()) {
            return customValuationEngine.estimate(code)
                ?: throw IllegalArgumentException("No custom estimation data found for fund $code")
        }

        var estimation: FundEstimation
        var isDummy = false
        val raw = fundClient.fetchEstimation(code)
        if (raw != null) {
            estimation = raw.toEstimation()
        } else {
            isDummy = true
            val latestNav = repository.getLatestNav(code)
            val meta = repository.getFundMetadata(code)
            val defaultNavDate = latestNav?.navDate ?: "2000-01-01"
            val defaultNavValue = latestNav?.nav?.toPlainString() ?: "0.0000"
            
            estimation = com.fundlistener.model.FundEstimation(
                code = code,
                name = meta?.fundName ?: code,
                navDate = defaultNavDate,
                nav = defaultNavValue,
                estimatedNav = defaultNavValue,
                estimatedGrowthRate = "0.00",
                estimationTime = "$defaultNavDate 15:00",
                yesterdayNav = ""
            )
        }

        try {
            val trend = fundClient.fetchNavTrend(code)
            if (!trend.isNullOrEmpty()) {
                val latestTrend = trend.last()
                val trendDateStr = java.time.Instant.ofEpochMilli(latestTrend.x)
                    .atZone(java.time.ZoneId.of("Asia/Shanghai"))
                    .toLocalDate()
                    .toString()
                
                val todayStr = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString()
                
                if (trendDateStr == todayStr || trendDateStr > estimation.navDate || isDummy) {
                    val yesterdayTrend = if (trend.size >= 2) trend[trend.size - 2] else null
                    val yesterdayNavVal = yesterdayTrend?.y?.toString() ?: estimation.nav
                    
                    estimation = estimation.copy(
                        navDate = trendDateStr,
                        nav = latestTrend.y.toString(),
                        estimatedNav = latestTrend.y.toString(),
                        estimatedGrowthRate = latestTrend.equityReturn.toString(),
                        estimationTime = "$trendDateStr 15:00",
                        yesterdayNav = yesterdayNavVal
                    )
                    
                    try {
                        val historyRecords = trend.map { t ->
                            val tDateStr = java.time.Instant.ofEpochMilli(t.x)
                                .atZone(java.time.ZoneId.of("Asia/Shanghai"))
                                .toLocalDate()
                                .toString()
                            com.fundlistener.model.NavHistory(
                                id = 0,
                                fundCode = code,
                                navDate = tDateStr,
                                nav = t.y.toBigDecimal(),
                                accNav = t.y.toBigDecimal(),
                                createdAt = System.currentTimeMillis()
                            )
                        }
                        repository.batchUpsertNavHistory(historyRecords)
                    } catch (e: Exception) {
                        org.slf4j.LoggerFactory.getLogger(FundService::class.java).warn("Failed to insert nav_history for {}", code, e)
                    }
                } else {
                    val yesterdayTrend = if (trend.size >= 2) trend[trend.size - 2] else null
                    val yesterdayNavVal = yesterdayTrend?.y?.toString() ?: estimation.nav
                    estimation = estimation.copy(yesterdayNav = yesterdayNavVal)
                }
            } else {
                estimation = estimation.copy(yesterdayNav = estimation.nav)
            }
        } catch (e: Exception) {
            estimation = estimation.copy(yesterdayNav = estimation.nav)
        }

        return estimation
    }

    /**
     * 获取基金十大重仓股
     */
    suspend fun getFundHoldings(code: String): com.fundlistener.model.FundHoldingsResponse {
        require(code.isNotBlank()) { "Fund code must not be blank" }
        return fundClient.fetchHoldings(code)
            ?: throw IllegalArgumentException("Failed to fetch holdings for fund $code")
    }

    /**
     * 如果本地基金缓存为空，则从远程同步天天基金所有基金的基本信息
     */
    suspend fun syncFundsIfEmpty() {
        val count = repository.getFundCount()
        if (count == 0) {
            val list = fundClient.fetchAllFunds()
            if (list != null && list.isNotEmpty()) {
                repository.batchInsertFunds(list)
            }
        }
    }

    /**
     * 模糊搜索基金
     */
    suspend fun searchFunds(keyword: String): List<com.fundlistener.model.FundSearchResult> {
        if (keyword.isBlank()) return emptyList()
        return repository.searchFunds(keyword)
    }

    /**
     * 获取历史净值走势
     */
    suspend fun getFundNavTrend(code: String): List<com.fundlistener.model.NavTrendItem> {
        require(code.isNotBlank()) { "Fund code must not be blank" }
        return fundClient.fetchNavTrend(code)
            ?: throw IllegalArgumentException("Failed to fetch nav trend for fund $code")
    }
}

