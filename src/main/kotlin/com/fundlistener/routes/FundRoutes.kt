package com.fundlistener.routes

import com.fundlistener.client.QuoteCache
import com.fundlistener.model.*
import com.fundlistener.repository.FundRepository
import com.fundlistener.service.FundService
import com.fundlistener.service.PercentileCalculator
import com.fundlistener.service.ValuationService
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

    route("/api/funds") {
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

    // ═══════════════════════════════════════════
    //  配置 API
    // ═══════════════════════════════════════════

    get("/api/config") {
        val qdiiMode = repository.getConfig("qdii_display_mode") ?: "alipay"
        call.respond(mapOf("qdii_display_mode" to qdiiMode))
    }

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
