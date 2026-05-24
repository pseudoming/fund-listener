package com.fundlistener.service

import com.fundlistener.client.QuoteClient
import com.fundlistener.client.TianTianFundClient
import com.fundlistener.model.FundEstimation
import com.fundlistener.model.FundValuationRule
import com.fundlistener.repository.FundRepository
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class CustomValuationEngine(
    private val repository: FundRepository,
    private val quoteClient: QuoteClient,
    private val tianTianFundClient: TianTianFundClient
) {
    private val logger = LoggerFactory.getLogger(CustomValuationEngine::class.java)

    /**
     * 根据自定义估值规则估算基金实时净值和涨跌幅
     */
    suspend fun estimate(fundCode: String, providedRules: List<FundValuationRule>? = null): FundEstimation? {
        val rules = providedRules ?: repository.getValuationRules(fundCode)
        if (rules.isEmpty()) return null

        val position = repository.getPosition(fundCode)
        val fundName = position?.fundName ?: "自定义基金 $fundCode"

        // 1. 获取最新收盘净值
        val latestNavRecord = repository.getLatestNav(fundCode)
        var lastNav = latestNavRecord?.nav
        var lastNavDate = latestNavRecord?.navDate

        // 2. 如果本地数据库无历史净值，回退到天天基金获取最新昨收价
        if (lastNav == null || lastNavDate == null) {
            val tianTianRaw = tianTianFundClient.fetchEstimation(fundCode)
            if (tianTianRaw != null) {
                lastNav = tianTianRaw.dwjz.toBigDecimalOrNull()
                lastNavDate = tianTianRaw.jzrq
            }
        }

        if (lastNav == null || lastNavDate == null) {
            lastNav = BigDecimal.ONE
            lastNavDate = "N/A"
        }

        // 3. 批量查询实时行情
        val targets = rules.map { it.targetCode to it.componentType }
        val quotes = quoteClient.fetchQuotes(targets)

        // 4. 加权计算涨跌幅
        var totalGrowth = BigDecimal.ZERO
        for (rule in rules) {
            val quote = quotes[rule.targetCode]
                ?: quotes[rule.targetCode.lowercase()]
                ?: quotes[rule.targetCode.uppercase()]
            if (quote != null) {
                val weight = BigDecimal.valueOf(rule.weightPercent).divide(BigDecimal.valueOf(100.0))
                totalGrowth = totalGrowth.add(quote.changePercent.multiply(weight))
            } else {
                logger.warn("Quote not found for custom valuation engine target: ${rule.targetCode}")
            }
        }

        // 5. 计算估算净值: estimatedNav = lastNav * (1 + totalGrowth / 100)
        val growthMultiplier = BigDecimal.ONE.add(
            totalGrowth.divide(BigDecimal.valueOf(100.0), 8, RoundingMode.HALF_UP)
        )
        val estimatedNav = lastNav.multiply(growthMultiplier)

        val nowTimeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        return FundEstimation(
            code = fundCode,
            name = fundName,
            navDate = lastNavDate,
            nav = lastNav.toPlainString(),
            estimatedNav = estimatedNav.setScale(4, RoundingMode.HALF_UP).toPlainString(),
            estimatedGrowthRate = totalGrowth.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            estimationTime = nowTimeStr,
            yesterdayNav = lastNav.toPlainString()
        )
    }
}
