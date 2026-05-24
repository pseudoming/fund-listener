package com.fundlistener.routes

import com.fundlistener.client.HolidayChecker
import com.fundlistener.client.QuoteCache
import com.fundlistener.client.TradingHoursChecker
import com.fundlistener.model.*
import com.fundlistener.repository.FundRepository
import com.fundlistener.service.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.math.BigDecimal

fun Route.fundRoutes() {
    val fundService by inject<FundService>()
    val quoteCache by inject<QuoteCache>()
    val valuationService by inject<ValuationService>()
    val repository by inject<FundRepository>()
    val dashboardService by inject<DashboardService>()

    route("/api/funds") {
        /**
         * GET /api/funds/search
         * 模糊搜索基金
         */
        get("/search") {
            val key = call.request.queryParameters["key"] ?: ""
            val results = fundService.searchFunds(key)
            call.respond(results)
        }

        /**
         * GET /api/funds/{code}/realtime
         * 获取基金实时估值数据
         */
        get("/{code}/realtime") {
            val code = call.parameters["code"]
                ?: throw IllegalArgumentException("Fund code is required")
            val estimation = fundService.getRealtimeEstimation(code)
            call.respond(estimation)
        }

        /**
         * GET /api/funds/{code}/trend
         * 获取基金历史净值走势
         */
        get("/{code}/trend") {
            val code = call.parameters["code"]
                ?: throw IllegalArgumentException("Fund code is required")
            val trend = fundService.getFundNavTrend(code)
            call.respond(trend)
        }

        /**
         * GET /api/funds/{code}/holdings
         * 获取基金前十大重仓股
         */
        get("/{code}/holdings") {
            val code = call.parameters["code"]
                ?: throw IllegalArgumentException("Fund code is required")
            val holdings = fundService.getFundHoldings(code)
            call.respond(holdings)
        }

        /**
         * GET /api/funds/{code}/valuation-rules
         * 获取基金自定义估值规则列表
         */
        get("/{code}/valuation-rules") {
            val code = call.parameters["code"]
                ?: throw IllegalArgumentException("Fund code is required")
            val rules = repository.getValuationRules(code)
            call.respond(rules)
        }

        /**
         * PUT /api/funds/{code}/valuation-rules
         * 保存/覆盖基金自定义估值规则列表
         */
        put("/{code}/valuation-rules") {
            val code = call.parameters["code"]
                ?: throw IllegalArgumentException("Fund code is required")
            val rules = call.receive<List<FundValuationRule>>()
            val updatedRules = rules.map { rule ->
                rule.copy(
                    fundCode = code,
                    createdAt = if (rule.createdAt <= 0) System.currentTimeMillis() else rule.createdAt
                )
            }
            repository.saveValuationRules(code, updatedRules)
            call.respond(mapOf("success" to true))
        }
    }

    /**
     * GET /api/quotes?codes=600519,000333,00700,NVDA
     * 批量股票行情（带缓存），Phase 4 估值引擎前端接口。
     */
    get("/api/quotes") {
        val codesParam = call.request.queryParameters["codes"]
            ?: throw IllegalArgumentException("Query parameter 'codes' is required (comma-separated)")
        val codes = codesParam.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val quotes = quoteCache.getQuotes(codes)
        call.respond(quotes.map { it.toResponse() })
    }

    /**
     * GET /api/valuation/{code}
     * 穿透估值：获取基金加权估值、PE/PB 百分位、覆盖度。
     */
    get("/api/valuation/{code}") {
        val code = call.parameters["code"]
            ?: throw IllegalArgumentException("Fund code is required")
        val result = valuationService.evaluate(code)
        call.respond(result)
    }

    /**
     * GET /api/valuation/{code}/history
     * 估值历史快照 + 百分位状态，供前端走势图使用。
     */
    get("/api/valuation/{code}/history") {
        val code = call.parameters["code"]
            ?: throw IllegalArgumentException("Fund code is required")

        val snapshots = repository.getSnapshots(code, limit = 365)
            .sortedBy { it.snapshotTime }

        val points = snapshots.map { s ->
            SnapshotPoint(
                time = s.snapshotTime,
                estimatedNav = s.estimatedNav.toPlainString(),
                estimatedGrowthRate = s.estimatedGrowthRate.toPlainString(),
                weightedPe = s.weightedPe?.toPlainString(),
                weightedPb = s.weightedPb?.toPlainString(),
                pePercentile = s.pePercentile?.toPlainString(),
                pbPercentile = s.pbPercentile?.toPlainString(),
                coverageRate = s.coverageRate?.toPlainString()
            )
        }

        // 计算当前百分位状态
        val latestSnapshot = snapshots.lastOrNull()
        val peInfo = buildPercentileInfo("PE", latestSnapshot?.weightedPe, snapshots)
        val pbInfo = buildPercentileInfo("PB", latestSnapshot?.weightedPb, snapshots)

        call.respond(
            ValuationHistoryResponse(
                fundCode = code,
                snapshots = points,
                pePercentile = peInfo,
                pbPercentile = pbInfo
            )
        )
    }

    /**
     * GET /api/market/status
     * 返回市场状态：是否交易中、是否节假日、当前时间。
     */
    get("/api/market/status") {
        val isTrading = TradingHoursChecker.isTradingNow()
        val isHoliday = HolidayChecker.isHoliday()
        val holidayReason = HolidayChecker.holidayReason()
        val beijingTime = TradingHoursChecker.nowBeijing()

        call.respond(
            MarketStatusResponse(
                isTrading = isTrading,
                isHoliday = isHoliday,
                holidayReason = holidayReason,
                currentTime = beijingTime.toString(),
                cacheTtlSeconds = TradingHoursChecker.cacheTtlSeconds(false)
            )
        )
    }

    /**
     * GET /api/dashboard
     * 首页看板：总持仓、今日盈亏、高估基金标红。
     */
    get("/api/dashboard") {
        val dashboard = dashboardService.getDashboard()
        call.respond(dashboard)
    }

    // ═══════════════════════════════════════════
    //  自选 API
    // ═══════════════════════════════════════════

    route("/api/watchlist") {
        /**
         * GET /api/watchlist
         * 获取所有自选基金的最新估值数据
         */
        get {
            val watchListItems = repository.getWatchlistItems()
            val result = watchListItems.map { (code, addedAtMs) ->
                val metadata = try {
                    val cached = repository.getFundMetadata(code)
                    if (cached != null) {
                        cached
                    } else {
                        fundService.getRealtimeEstimation(code)
                        repository.getFundMetadata(code)
                    }
                } catch (_: Exception) { null }
                
                var latestNavRecord = repository.getLatestNav(code)
                var latestSnapshot = repository.getLatestSnapshot(code)
                
                // 如果本地没有流水数据，强制拉取一次
                if (latestNavRecord == null && latestSnapshot == null) {
                    try {
                        fundService.getRealtimeEstimation(code)
                        latestNavRecord = repository.getLatestNav(code)
                        latestSnapshot = repository.getLatestSnapshot(code)
                    } catch (_: Exception) {}
                }
                
                val position = repository.getPosition(code)
                val fallbackName = position?.fundName ?: "未知基金"
                
                val navHistory = repository.getNavHistory(code, limit = 365) // 取足够长的历史去寻找添加当天的净值
                val yesterdayNavStr = if (navHistory.size >= 2) navHistory[1].nav.toPlainString() else null
                
                val estimationTimeStr = latestSnapshot?.snapshotTime?.let {
                    java.time.Instant.ofEpochMilli(it)
                        .atZone(java.time.ZoneId.of("Asia/Shanghai"))
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                } ?: ""
                
                // 计算关注以来涨幅
                var sinceAddedGrowthRate = ""
                val addedDateStr = java.time.Instant.ofEpochMilli(addedAtMs)
                    .atZone(java.time.ZoneId.of("Asia/Shanghai"))
                    .toLocalDate().toString()
                
                val baseNavRecord = navHistory.firstOrNull { it.navDate <= addedDateStr }
                val baseNav = baseNavRecord?.nav ?: navHistory.lastOrNull()?.nav
                val currentNav = latestSnapshot?.estimatedNav?.takeIf { it > BigDecimal.ZERO } ?: latestNavRecord?.nav?.takeIf { it > BigDecimal.ZERO } ?: baseNav
                
                if (baseNav != null && currentNav != null && baseNav > BigDecimal.ZERO) {
                    val diff = currentNav.subtract(baseNav)
                    val rate = diff.divide(baseNav, 4, java.math.RoundingMode.HALF_UP).multiply(BigDecimal("100"))
                    sinceAddedGrowthRate = rate.toPlainString()
                }

                mapOf(
                    "code" to code,
                    "name" to (metadata?.fundName ?: fallbackName),
                    "navDate" to (latestNavRecord?.navDate ?: ""),
                    "nav" to (latestNavRecord?.nav?.toPlainString() ?: ""),
                    "estimatedNav" to (latestSnapshot?.estimatedNav?.toPlainString() ?: ""),
                    "estimatedGrowthRate" to (latestSnapshot?.estimatedGrowthRate?.toPlainString() ?: ""),
                    "sinceAddedGrowthRate" to sinceAddedGrowthRate,
                    "addedDate" to addedDateStr,
                    "estimationTime" to estimationTimeStr,
                    "yesterdayNav" to (yesterdayNavStr ?: ""),
                    "manager" to (metadata?.fundManager ?: "")
                )
            }
            call.respond(result)
        }

        /**
         * POST /api/watchlist/{code}
         * 添加到自选
         */
        post("/{code}") {
            val code = call.parameters["code"] ?: return@post call.respond(io.ktor.http.HttpStatusCode.BadRequest, mapOf("error" to "Missing code"))
            repository.addToWatchlist(code)
            
            try {
                fundService.getRealtimeEstimation(code)
            } catch (_: Exception) {}
            
            call.respond(mapOf("success" to true))
        }

        /**
         * DELETE /api/watchlist/{code}
         * 取消自选。如果持仓中包含此基金，禁止删除！
         */
        delete("/{code}") {
            val code = call.parameters["code"] ?: return@delete call.respond(io.ktor.http.HttpStatusCode.BadRequest, mapOf("error" to "Missing code"))
            
            val position = repository.getPosition(code)
            if (position != null) {
                call.respond(io.ktor.http.HttpStatusCode.BadRequest, mapOf("error" to "该基金仍在持仓中，禁止取消自选。"))
                return@delete
            }
            
            repository.removeFromWatchlist(code)
            call.respond(mapOf("success" to true))
        }
    }

    // ═══════════════════════════════════════════
    //  配置 API
    // ═══════════════════════════════════════════

    /**
     * GET /api/config
     * 获取所有配置项（目前只有 qdii_display_mode）
     */
    get("/api/config") {
        val qdiiMode = repository.getConfig("qdii_display_mode") ?: "alipay"
        call.respond(mapOf("qdii_display_mode" to qdiiMode))
    }

    /**
     * POST /api/config
     * 更新配置项
     * Body: { "qdii_display_mode": "alipay" | "realtime" }
     */
    post("/api/config") {
        val body = call.receive<Map<String, String>>()
        for ((key, value) in body) {
            repository.setConfig(key, value)
        }
        call.respond(mapOf("success" to true))
    }
}


private fun buildPercentileInfo(
    label: String,
    current: BigDecimal?,
    snapshots: List<ValuationSnapshot>
): PercentileInfo? {
    if (snapshots.isEmpty()) return null
    if (current == null) return PercentileInfo("NO_VALUE", null, snapshots.size, PercentileCalculator.MIN_DAYS)

    val result = when (label) {
        "PE" -> PercentileCalculator.calcPEPercentile(current, snapshots)
        "PB" -> PercentileCalculator.calcPBPercentile(current, snapshots)
        else -> null
    } ?: return null

    return when (result) {
        is PercentileCalculator.PercentileResult.Valid ->
            PercentileInfo("VALID", result.percentile.toPlainString(), snapshots.size, PercentileCalculator.MIN_DAYS)
        is PercentileCalculator.PercentileResult.Degraded ->
            PercentileInfo("DEGRADED", null, result.currentDays, result.requiredDays)
        is PercentileCalculator.PercentileResult.NoValue ->
            PercentileInfo("NO_VALUE", null, snapshots.size, PercentileCalculator.MIN_DAYS)
    }
}
