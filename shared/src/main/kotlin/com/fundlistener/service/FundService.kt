package com.fundlistener.service

import com.fundlistener.client.TianTianFundClient
import com.fundlistener.model.FundEstimation
import com.fundlistener.repository.FundRepository
import kotlinx.coroutines.launch

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
        val estimation = if (rules.isNotEmpty()) {
            customValuationEngine.estimate(code)
                ?: throw IllegalArgumentException("No custom estimation data found for fund $code")
        } else {
            val raw = fundClient.fetchEstimation(code)
            
            var est: FundEstimation? = null
            
            val isMissingEstimation = raw == null || raw.gsz.isNullOrBlank() || raw.gsz == "0" || raw.gsz == "0.00"
            if (isMissingEstimation) {
                val meta = repository.getFundMetadata(code)
                if (meta != null && !meta.linkedEtfCode.isNullOrBlank()) {
                    val fallbackRules = listOf(
                        com.fundlistener.model.FundValuationRule(
                            id = 0,
                            fundCode = code,
                            componentType = meta.assetType ?: "INDEX",
                            targetCode = meta.linkedEtfCode,
                            weightPercent = 100.0,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    try {
                        est = customValuationEngine.estimate(code, fallbackRules)
                    } catch (e: Exception) {
                        org.slf4j.LoggerFactory.getLogger(FundService::class.java).warn("Fallback estimation failed for {}", code, e)
                    }
                }
            }

            if (est == null) {
                if (raw != null) {
                    est = raw.toEstimation()
                } else {
                    val latestNav = repository.getLatestNav(code)
                    val meta = repository.getFundMetadata(code)
                    val defaultNavDate = latestNav?.navDate ?: java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).minusDays(1).toString()
                    val defaultNavValue = latestNav?.nav?.toPlainString() ?: "0.0000"
                    
                    est = com.fundlistener.model.FundEstimation(
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
            }

            // Note: If est comes from fallback, it already has correct estimatedNav and estimatedGrowthRate
            // We should only override with trend data if it came from raw and trend is more recent.
            val isFromFallback = est !== null && raw != null && est.estimatedNav != raw.gsz && raw.gsz == "0"
            
            val isDummy = raw == null
            if (!isFromFallback) {
                try {
                    val trend = fundClient.fetchNavTrend(code)
                    if (!trend.isNullOrEmpty()) {
                        val latestTrend = trend.last()
                        val trendDateStr = java.time.Instant.ofEpochMilli(latestTrend.x)
                            .atZone(java.time.ZoneId.of("Asia/Shanghai"))
                            .toLocalDate()
                            .toString()
                        
                        val todayStr = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString()
                        
                        if (trendDateStr == todayStr || trendDateStr > est!!.navDate || isDummy) {
                            val yesterdayTrend = if (trend.size >= 2) trend[trend.size - 2] else null
                            val yesterdayNavVal = yesterdayTrend?.y?.toString() ?: est!!.nav
                            
                            est = est!!.copy(
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
                            org.slf4j.LoggerFactory.getLogger(FundService::class.java).warn("Failed to insert fund_nav_history for {}", code, e)
                        }
                    } else {
                        val yesterdayTrend = if (trend.size >= 2) trend[trend.size - 2] else null
                        val yesterdayNavVal = yesterdayTrend?.y?.toString() ?: est!!.nav
                        est = est!!.copy(yesterdayNav = yesterdayNavVal)
                    }
                } else {
                    est = est!!.copy(yesterdayNav = est!!.nav)
                }
            } catch (e: Exception) {
                est = est!!.copy(yesterdayNav = est!!.nav)
            }
        }
        est!!
    }

        var finalEstimation = estimation
        if (finalEstimation.name == code) {
            try {
                val candidates = fundClient.searchLive(code)
                val match = candidates.find { it.fundCode == code }
                if (match != null) {
                    finalEstimation = finalEstimation.copy(name = match.fundName)
                }
            } catch (e: Exception) {
                org.slf4j.LoggerFactory.getLogger(FundService::class.java).warn("Failed to resolve real name for {}", code, e)
            }
        }

        try {
            upsertMetadataCache(code, finalEstimation)
        } catch (e: Exception) {
            org.slf4j.LoggerFactory.getLogger(FundService::class.java).warn("Failed to update fund_metadata cache for {}", code, e)
        }

        return finalEstimation
    }

    private suspend fun upsertMetadataCache(code: String, estimation: FundEstimation) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val existing = repository.getFundMetadata(code)
                
                val manager = if (existing?.fundManager.isNullOrBlank()) {
                    try {
                        fundClient.fetchFundManager(code)
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    existing?.fundManager
                }

                val fundType = existing?.fundType ?: repository.searchFunds(code, 1).firstOrNull()?.type ?: "混合型"

                var topHoldingsStr = existing?.topHoldings
                if (topHoldingsStr.isNullOrBlank()) {
                    try {
                        val holdings = fundClient.fetchHoldings(code)
                        if (holdings != null) {
                            topHoldingsStr = kotlinx.serialization.json.Json.encodeToString(
                                com.fundlistener.model.FundHoldingsResponse.serializer(), holdings
                            )
                            
                            // 保存重仓股映射关系
                            val mappings = holdings.holdings.map {
                                com.fundlistener.model.FundHoldingMapping(
                                    fundCode = code,
                                    stockCode = it.stockCode,
                                    weightPercent = it.ratio,
                                    reportDate = holdings.reportDate,
                                    createdAt = System.currentTimeMillis()
                                )
                            }
                            repository.batchUpsertFundHoldingMappings(mappings)
                            
                            // 保存基础股票元数据结构预留
                            val stockMetas = holdings.holdings.map {
                                com.fundlistener.model.StockMetadata(
                                    stockCode = it.stockCode,
                                    stockName = it.stockName,
                                    marketType = "UNKNOWN", // 需要通过行情接口补充
                                    currentPrice = null,
                                    growthRate = null,
                                    updatedAt = System.currentTimeMillis()
                                )
                            }
                            for (meta in stockMetas) {
                                try {
                                    repository.upsertStockMetadata(meta)
                                } catch (e: Exception) {}
                            }
                        }
                    } catch (e: Exception) {
                        org.slf4j.LoggerFactory.getLogger(FundService::class.java).warn("Failed to fetch/save holdings for {}", code, e)
                    }
                }

                val metadata = com.fundlistener.model.FundMetadata(
                    fundCode = code,
                    fundName = estimation.name,
                    fundType = fundType,
                    fundManager = manager,
                    topHoldings = topHoldingsStr,
                    lastUpdated = System.currentTimeMillis()
                )
                repository.upsertFundMetadata(metadata)
            } catch (e: Exception) {
                org.slf4j.LoggerFactory.getLogger(FundService::class.java).warn("Failed to async update fund_metadata cache for {}", code, e)
            }
        }
    }

    suspend fun syncMetadata(code: String): FundEstimation {
        return getRealtimeEstimation(code)
    }


    suspend fun getFundHoldings(code: String): com.fundlistener.model.FundHoldingsResponse {
        require(code.isNotBlank()) { "Fund code must not be blank" }
        val metadata = repository.getFundMetadata(code)
        if (metadata != null && !metadata.topHoldings.isNullOrBlank()) {
            try {
                return kotlinx.serialization.json.Json.decodeFromString<com.fundlistener.model.FundHoldingsResponse>(metadata.topHoldings)
            } catch (e: Exception) {
                // ignore and fetch
            }
        }

        val holdings = fundClient.fetchHoldings(code)
            ?: com.fundlistener.model.FundHoldingsResponse(code, "", emptyList())
            
        if (metadata != null) {
            try {
                val updated = metadata.copy(
                    topHoldings = kotlinx.serialization.json.Json.encodeToString(
                        com.fundlistener.model.FundHoldingsResponse.serializer(), holdings
                    ),
                    assetType = holdings.assetType ?: metadata.assetType,
                    linkedEtfCode = holdings.linkedEtfCode ?: metadata.linkedEtfCode,
                    linkedEtfName = holdings.linkedEtfName ?: metadata.linkedEtfName
                )
                repository.upsertFundMetadata(updated)
            } catch (e: Exception) {}
        }
        return holdings
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

