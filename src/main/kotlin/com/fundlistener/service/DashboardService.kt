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
    private val repository: FundRepository
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
                todayPnl = "0.00",
                todayPnlPercent = "0.00",
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
            // 获取实时估值
            val estimation = try {
                fundService.getRealtimeEstimation(pos.fundCode)
            } catch (_: Exception) { null }

            // 获取最新快照中的百分位
            val latestSnapshot = try {
                repository.getLatestSnapshot(pos.fundCode)
            } catch (_: Exception) { null }

            val growthRate = estimation?.estimatedGrowthRate?.toBigDecimalOrNull()
            val pePercentile = latestSnapshot?.pePercentile
            val pbPercentile = latestSnapshot?.pbPercentile
            val isOvervalued = pePercentile != null && pePercentile >= BigDecimal(OVERVALUED_THRESHOLD)

            val cost = pos.totalCost
            totalCost += cost

            val yesterdayNavStr = estimation?.yesterdayNav
            val yesterdayNav = yesterdayNavStr?.toBigDecimalOrNull() ?: pos.avgCostNav
            val currentNav = estimation?.estimatedNav?.toBigDecimalOrNull()
                ?: estimation?.nav?.toBigDecimalOrNull()
                ?: pos.avgCostNav

            // ─── 判定是否已有官方净值（原始逻辑） ───
            val isOfficialUpdated = estimation != null &&
                (estimation.navDate == estimation.estimationTime.split(" ")[0] ||
                 (estimation.nav == estimation.estimatedNav && estimation.yesterdayNav.isNotEmpty() && estimation.yesterdayNav != estimation.nav))

            // ─── QDII 时差检测 ───
            // 当官方净值日期 (navDate) 落后于估算时间日期时，说明该基金存在跨境结算延迟
            val officialNavDate = try { LocalDate.parse(estimation?.navDate ?: "") } catch (_: Exception) { null }
            val estimationDate = try {
                LocalDate.parse(estimation?.estimationTime?.split(" ")?.get(0) ?: "")
            } catch (_: Exception) { null }
            val isQdiiDelayed = officialNavDate != null && estimationDate != null && officialNavDate < estimationDate

            // ─── 根据模式决定展示逻辑 ───
            val displaySettled: Boolean
            val displayPnl: BigDecimal
            val displayNav: BigDecimal
            val displayNavDate: String

            if (isQdiiDelayed && qdiiMode == "alipay") {
                // ═══ 支付宝对齐模式 ═══
                // 展示 navDate 那天的官方结算净值，日期平移一天
                val officialNav = estimation?.nav?.toBigDecimalOrNull() ?: currentNav
                displayNav = officialNav

                // 从 nav_history 查找 navDate 的前一天净值来计算 PNL
                val prevNav = try {
                    val navHistory = repository.getNavHistory(pos.fundCode, limit = 5)
                    // navHistory 按 nav_date DESC 排序，找到 navDate 对应的记录的下一条
                    val navDateStr = officialNavDate.toString()
                    val idx = navHistory.indexOfFirst { it.navDate == navDateStr }
                    if (idx >= 0 && idx + 1 < navHistory.size) {
                        navHistory[idx + 1].nav
                    } else {
                        // 没找到历史记录，回退到 yesterdayNav
                        yesterdayNav
                    }
                } catch (_: Exception) { yesterdayNav }

                displayPnl = officialNav.subtract(prevNav).multiply(pos.totalShares)
                displaySettled = true
                // 日期平移：官方净值日期 +1 天，模拟支付宝的"到账日"
                displayNavDate = officialNavDate.plusDays(1).toString()
            } else if (isOfficialUpdated) {
                // ═══ 普通 A 股基金，已结算 ═══
                displayNav = currentNav
                displayPnl = currentNav.subtract(yesterdayNav).multiply(pos.totalShares)
                displaySettled = true
                displayNavDate = estimation?.navDate ?: ""
            } else {
                // ═══ 估算模式（A 股盘中 / QDII realtime 模式） ═══
                displayNav = currentNav
                val rate = growthRate ?: BigDecimal.ZERO
                displayPnl = yesterdayNav.multiply(pos.totalShares)
                    .multiply(rate)
                    .divide(BigDecimal(100), 8, RoundingMode.HALF_UP)
                displaySettled = false
                displayNavDate = estimation?.navDate ?: ""
            }

            val marketVal = displayNav.multiply(pos.totalShares)
            totalMarketValue += marketVal
            totalPnl += displayPnl

            fundItems.add(
                DashboardFund(
                    fundCode = pos.fundCode,
                    fundName = pos.fundName,
                    totalShares = pos.totalShares.toPlainString(),
                    totalCost = cost.toPlainString(),
                    avgCostNav = pos.avgCostNav.toPlainString(),
                    estimatedNav = displayNav.setScale(4, RoundingMode.HALF_UP).toPlainString(),
                    estimatedGrowthRate = estimation?.estimatedGrowthRate,
                    pePercentile = pePercentile?.toPlainString(),
                    pbPercentile = pbPercentile?.toPlainString(),
                    isOvervalued = isOvervalued,
                    todayPnl = displayPnl.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    yesterdayNav = yesterdayNav.setScale(4, RoundingMode.HALF_UP).toPlainString(),
                    isSettled = displaySettled,
                    navDate = displayNavDate
                )
            )
        }

        val totalYesterdayValue = totalMarketValue - totalPnl
        val pnlPercent = if (totalYesterdayValue.compareTo(BigDecimal.ZERO) > 0) {
            totalPnl.divide(totalYesterdayValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100))
        } else BigDecimal.ZERO

        val overvaluedCount = fundItems.count { it.isOvervalued }

        return DashboardResponse(
            funds = fundItems,
            totalCost = totalCost.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            totalMarketValue = totalMarketValue.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            todayPnl = totalPnl.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            todayPnlPercent = pnlPercent.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            overvaluedCount = overvaluedCount,
            overvaluedThreshold = OVERVALUED_THRESHOLD,
            lastUpdatedTime = java.time.LocalDateTime.now(SHANGHAI_ZONE)
                .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
        )
    }
}
