package com.fundlistener.model

import kotlinx.serialization.Serializable

@Serializable
data class StockExposure(
    val stockCode: String,
    val stockName: String,
    val marketType: String,
    val totalExposure: String,  // 格式化后的金额
    val exposureRatio: String,  // 占总仓位的比例
    val growthRate: String? = null,
    val changeAmount: String? = null
)

@Serializable
data class PenetrationResponse(
    val totalMarketValue: String,
    val topStocks: List<StockExposure>
)
