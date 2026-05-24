package com.fundlistener.model

import kotlinx.serialization.Serializable

@Serializable
data class FundHolding(
    val stockCode: String,
    val stockName: String,
    val ratio: String // 百分比字符串，或者小数形式 (为了统一，用 "9.90" 表示 9.90%)
)

@Serializable
data class FundHoldingsResponse(
    val fundCode: String,
    val reportDate: String, // 财报日期，如 2026-03-31
    val holdings: List<FundHolding>
)
