package com.fundlistener.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * 股票实时行情，用于 Phase 4 加权估值计算。
 *
 * 所有金额/价格字段使用 BigDecimal，null 表示数据不可用（停牌/非交易时段）。
 */
data class StockQuote(
    val code: String,
    val name: String,
    val market: MarketType,
    val latestPrice: BigDecimal?,
    val changePercent: BigDecimal?,
    val changeAmount: BigDecimal?,
    val pe: BigDecimal?,
    val pb: BigDecimal?,
    val high: BigDecimal?,
    val low: BigDecimal?,
    val open: BigDecimal?,
    val prevClose: BigDecimal?,
    val isSuspended: Boolean = false
)

/**
 * API 响应用的序列化 DTO，字段使用 String 保持与 FundEstimation 风格一致。
 */
@Serializable
data class StockQuoteResponse(
    val code: String,
    val name: String,
    val market: String,
    val latestPrice: String?,
    val changePercent: String?,
    val changeAmount: String?,
    val pe: String?,
    val pb: String?,
    val high: String?,
    val low: String?,
    val open: String?,
    val prevClose: String?,
    val isSuspended: Boolean
)

fun StockQuote.toResponse(): StockQuoteResponse = StockQuoteResponse(
    code = code,
    name = name,
    market = market.name,
    latestPrice = latestPrice?.toPlainString(),
    changePercent = changePercent?.toPlainString(),
    changeAmount = changeAmount?.toPlainString(),
    pe = pe?.toPlainString(),
    pb = pb?.toPlainString(),
    high = high?.toPlainString(),
    low = low?.toPlainString(),
    open = open?.toPlainString(),
    prevClose = prevClose?.toPlainString(),
    isSuspended = isSuspended
)

/**
 * 东方财富 push2 API 批量行情响应（内部解析用，非 API DTO）
 */
@Serializable
data class EastMoneyQuoteResponse(
    val rc: Int? = null,
    val data: EastMoneyQuoteData? = null
)

@Serializable
data class EastMoneyQuoteData(
    val diff: List<EastMoneyQuoteItem>? = null
)

@Serializable
data class EastMoneyQuoteItem(
    val f2: String? = null,
    val f3: String? = null,
    val f4: String? = null,
    val f9: String? = null,
    val f12: String? = null,
    val f14: String? = null,
    val f15: String? = null,
    val f16: String? = null,
    val f17: String? = null,
    val f18: String? = null,
    val f23: String? = null
) {
    fun toStockQuote(market: MarketType): StockQuote = StockQuote(
        code = f12 ?: "",
        name = f14 ?: "",
        market = market,
        latestPrice = f2?.toBigDecimalOrNull(),
        changePercent = f3?.toBigDecimalOrNull(),
        changeAmount = f4?.toBigDecimalOrNull(),
        pe = f9?.toBigDecimalOrNull(),
        pb = f23?.toBigDecimalOrNull(),
        high = f15?.toBigDecimalOrNull(),
        low = f16?.toBigDecimalOrNull(),
        open = f17?.toBigDecimalOrNull(),
        prevClose = f18?.toBigDecimalOrNull(),
        isSuspended = f2 == "-" || f2 == null
    )
}
