package com.fundlistener.model

import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.serialization.Serializable

/**
 * 持仓汇总
 * 对应 fund_position 表，每个 fund_code 最多一行
 */
data class FundPosition(
    val fundCode: String,
    val fundName: String,
    val totalShares: BigDecimal,
    val totalCost: BigDecimal,
    val createdAt: Long,
    val updatedAt: Long
) {
    /** 平均成本净值 = totalCost / totalShares，份额为 0 时返回 ZERO */
    val avgCostNav: BigDecimal
        get() = if (totalShares.compareTo(BigDecimal.ZERO) > 0)
            totalCost.divide(totalShares, 4, RoundingMode.HALF_UP)
        else BigDecimal.ZERO
}

/**
 * 交易类型
 */
enum class TransactionType {
    BUY, SELL
}

/**
 * 交易记录
 * 对应 fund_transaction 表
 */
data class FundTransaction(
    val id: String,
    val fundCode: String,
    val type: TransactionType,
    val shares: BigDecimal,
    val nav: BigDecimal,
    val amount: BigDecimal,
    val fee: BigDecimal = BigDecimal.ZERO,
    val tradeDate: String,
    val note: String = "",
    val createdAt: Long
)

/**
 * 净值历史
 * 对应 fund_nav_history 表
 */
data class NavHistory(
    val id: Long = 0,
    val fundCode: String,
    val navDate: String,
    val nav: BigDecimal,
    val accNav: BigDecimal? = null,
    val createdAt: Long
)

/**
 * 估值快照
 * 对应 fund_valuation_snapshot 表
 * Phase 4 的 PE/PB 相关字段预留为 nullable
 */
data class ValuationSnapshot(
    val id: Long = 0,
    val fundCode: String,
    val snapshotTime: Long,
    val estimatedNav: BigDecimal,
    val estimatedGrowthRate: BigDecimal,
    val weightedPe: BigDecimal? = null,
    val weightedPb: BigDecimal? = null,
    val pePercentile: BigDecimal? = null,
    val pbPercentile: BigDecimal? = null,
    val coverageRate: BigDecimal? = null,
    val createdAt: Long
)

/**
 * 自定义估值规则
 * 对应 fund_valuation_rule 表
 */
@Serializable
data class FundValuationRule(
    val id: Long = 0,
    val fundCode: String,
    val componentType: String,
    val targetCode: String,
    val weightPercent: Double,
    val createdAt: Long
)

/**
 * 全量基金模糊搜索返回结果
 */
@Serializable
data class FundSearchResult(
    val code: String,
    val pinyinInitials: String,
    val name: String,
    val type: String,
    val pinyinFull: String
)

/**
 * 基金完整元信息与指标缓存实体
 */
@Serializable
data class FundMetadata(
    val fundCode: String,
    val fundName: String,
    val fundType: String? = null,
    val fundManager: String? = null,
    val topHoldings: String? = null,
    val assetType: String? = null,
    val linkedEtfCode: String? = null,
    val linkedEtfName: String? = null,
    val lastUpdated: Long
)

/**
 * 个股/ETF 基础元数据
 */
@Serializable
data class StockMetadata(
    val stockCode: String,
    val stockName: String,
    val marketType: String,
    val currentPrice: String? = null,
    val growthRate: String? = null,
    val updatedAt: Long
)

/**
 * 基金重仓映射关系
 */
@Serializable
data class FundHoldingMapping(
    val id: Long = 0,
    val fundCode: String,
    val stockCode: String,
    val weightPercent: String,
    val reportDate: String,
    val createdAt: Long
)

/**
 * 个股历史价格
 */
@Serializable
data class StockPriceHistory(
    val id: Long = 0,
    val stockCode: String,
    val tradeDate: String,
    val closePrice: String,
    val createdAt: Long
)


