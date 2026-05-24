package com.fundlistener.model

import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * 单只股票在估值计算中的明细。
 */
@Serializable
data class StockValuationDetail(
    val stockCode: String,
    val stockName: String,
    val ratio: String,           // 仓位占比，如 "9.90"
    val changePercent: String?,  // 涨跌幅，null 表示无行情
    val pe: String?,             // PE(TTM)，null 表示无数据
    val pb: String?,             // PB，null 表示无数据
    val hasQuote: Boolean        // 是否成功获取行情
)

/**
 * 基金加权估值计算结果。
 *
 * 所有比例 / 百分位字段为字符串，与 FundEstimation 风格一致。
 */
@Serializable
data class ValuationResult(
    val fundCode: String,
    val reportDate: String,              // 季报日期，如 "2026-03-31"
    val weightedChangePercent: String?,  // 加权涨跌幅 %，null = 无任何有效行情
    val weightedPE: String?,             // 加权 PE(TTM)，null = 无 PE 数据
    val weightedPB: String?,             // 加权 PB，null = 无 PB 数据
    val coveragePercent: String,         // 估值覆盖度 %，如 "87.50"
    val totalRatioCovered: String,       // 已覆盖仓位占比
    val totalRatio: String,              // 全部仓位占比（通常接近 100）
    val missingCount: Int,               // 无行情数据的股票数
    val stockDetails: List<StockValuationDetail>
)
