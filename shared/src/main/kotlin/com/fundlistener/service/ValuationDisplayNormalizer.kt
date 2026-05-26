package com.fundlistener.service

import com.fundlistener.model.FundEstimation
import com.fundlistener.model.FundMetadata
import com.fundlistener.repository.FundRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class ValuationDisplayNormalizer(private val repository: FundRepository) {

    /**
     * 标准化基金的展示数据（处理 QDII 时差与支付宝模式，以及通用的 isSettled 判定）
     */
    suspend fun normalize(
        estimation: FundEstimation,
        meta: FundMetadata?,
        qdiiMode: String? = null
    ): NormalizedDisplayResult {
        val actualQdiiMode = qdiiMode ?: (repository.getConfig("qdii_display_mode") ?: "alipay")
        
        // ─── 判定是否已有官方净值（原始逻辑） ───
        val isOfficialUpdated = (estimation.navDate == estimation.estimationTime.split(" ").getOrNull(0) ||
             (estimation.nav == estimation.estimatedNav && estimation.yesterdayNav.isNotEmpty() && estimation.yesterdayNav != estimation.nav))

        // ─── QDII 时差检测 ───
        val officialNavDate = try { LocalDate.parse(estimation.navDate) } catch (_: Exception) { null }
        val estimationDate = try {
            LocalDate.parse(estimation.estimationTime.split(" ")[0])
        } catch (_: Exception) { null }
        val fundType = meta?.fundType ?: estimation.type ?: ""
        val fundName = meta?.fundName ?: estimation.name
        val isQdiiFund = fundType.contains("QDII", ignoreCase = true) || 
                         fundType.contains("海外", ignoreCase = true) || 
                         fundName.contains("QDII", ignoreCase = true)
        val isQdiiDelayed = isQdiiFund && officialNavDate != null && estimationDate != null && officialNavDate < estimationDate

        val displaySettled: Boolean
        val displayNavDate: String
        val displayNav: BigDecimal
        val displayGrowthRate: BigDecimal
        val displayYesterdayNav: BigDecimal

        // 解析原始值
        val currentNav = estimation.estimatedNav.takeIf { it.isNotBlank() }?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val yesterdayNav = estimation.yesterdayNav.takeIf { it.isNotBlank() }?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val officialNav = estimation.nav.takeIf { it.isNotBlank() }?.toBigDecimalOrNull() ?: currentNav
        val originalGrowthRate = estimation.estimatedGrowthRate.takeIf { it.isNotBlank() }?.toBigDecimalOrNull() ?: BigDecimal.ZERO

        if (isQdiiDelayed && actualQdiiMode == "alipay") {
            // ═══ 支付宝对齐模式 ═══
            // 从 fund_nav_history 查找 navDate 的前一天净值来计算真实的涨跌幅
            val prevNav = try {
                val navHistory = repository.getNavHistory(estimation.code, limit = 5)
                val navDateStr = officialNavDate.toString()
                val idx = navHistory.indexOfFirst { it.navDate == navDateStr }
                if (idx >= 0 && idx + 1 < navHistory.size) {
                    navHistory[idx + 1].nav
                } else {
                    yesterdayNav
                }
            } catch (_: Exception) { yesterdayNav }

            displayNav = officialNav
            displayYesterdayNav = prevNav
            
            // 计算平移后的涨跌幅
            displayGrowthRate = if (prevNav > BigDecimal.ZERO) {
                officialNav.subtract(prevNav)
                    .divide(prevNav, 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal(100))
            } else {
                BigDecimal.ZERO
            }
            
            displaySettled = true
            displayNavDate = officialNavDate!!.plusDays(1).toString()
        } else if (isOfficialUpdated) {
            // ═══ 普通 A 股基金，已结算 ═══
            displayNav = currentNav
            displayYesterdayNav = yesterdayNav
            displayGrowthRate = originalGrowthRate // 若已出净值，天天基金的 estimatedGrowthRate 通常就是实际涨跌幅
            displaySettled = true
            displayNavDate = estimation.navDate
        } else {
            // ═══ 估算模式（A 股盘中 / QDII realtime 模式） ═══
            displayNav = currentNav
            displayYesterdayNav = yesterdayNav
            displayGrowthRate = originalGrowthRate
            displaySettled = false
            displayNavDate = estimation.estimationTime.split(" ").getOrNull(0) ?: estimation.navDate
        }

        return NormalizedDisplayResult(
            isSettled = displaySettled,
            displayNavDate = displayNavDate,
            displayNav = displayNav.setScale(4, RoundingMode.HALF_UP).toPlainString(),
            displayGrowthRate = displayGrowthRate.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            displayYesterdayNav = displayYesterdayNav.setScale(4, RoundingMode.HALF_UP).toPlainString()
        )
    }
}

data class NormalizedDisplayResult(
    val isSettled: Boolean,
    val displayNavDate: String,
    val displayNav: String,
    val displayGrowthRate: String,
    val displayYesterdayNav: String
)
