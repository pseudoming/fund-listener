package com.fundlistener.model

import kotlinx.serialization.Serializable

@Serializable
data class DashboardResponse(
    val funds: List<DashboardFund>,
    val totalCost: String,
    val totalMarketValue: String,
    val latestPnl: String,          // 最新盈亏金额
    val latestPnlPercent: String,   // 最新盈亏百分比
    val overvaluedCount: Int,       // PE 百分位 > 阈值 的基金数
    val overvaluedThreshold: Int,   // 高估阈值
    val lastUpdatedTime: String,    // 界面数据最后更新时间
    val marketIndices: List<MarketIndexQuote> = emptyList() // 大盘指数行情
)

@Serializable
data class MarketIndexQuote(
    val name: String,
    val changePercent: String,
    val isRise: Boolean,
    val isFall: Boolean
)

@Serializable
data class DashboardFund(
    val fundCode: String,
    val fundName: String,
    val totalShares: String,
    val totalCost: String,
    val avgCostNav: String,
    val estimatedNav: String?,
    val estimatedGrowthRate: String?,
    val pePercentile: String?,
    val pbPercentile: String?,
    val isOvervalued: Boolean,
    val latestPnl: String = "",
    val yesterdayNav: String = "",
    val isSettled: Boolean = false,
    val navDate: String = ""
)


@Serializable
data class MarketStatusResponse(
    val isTrading: Boolean,
    val isHoliday: Boolean,
    val holidayReason: String?,
    val currentTime: String,
    val cacheTtlSeconds: Long
)
