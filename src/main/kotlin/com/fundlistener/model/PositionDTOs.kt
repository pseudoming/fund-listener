package com.fundlistener.model

import kotlinx.serialization.Serializable

@Serializable
data class TradeRequest(
    val fundName: String? = null, // 买入时如果是新基金则需要
    val amount: String,           // 交易金额 (元)
    val shares: String,           // 交易份额
    val nav: String,              // 成交净值
    val fee: String = "0",        // 手续费 (元)
    val tradeDate: String,        // 交易日期 yyyy-MM-dd
    val note: String = ""         // 备注
)

@Serializable
data class PositionResponse(
    val fundCode: String,
    val fundName: String,
    val totalShares: String,
    val totalCost: String,
    val avgCostNav: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class TransactionResponse(
    val id: String,
    val fundCode: String,
    val type: String,
    val shares: String,
    val nav: String,
    val amount: String,
    val fee: String,
    val tradeDate: String,
    val note: String,
    val createdAt: Long
)

@Serializable
data class PositionDetailResponse(
    val position: PositionResponse,
    val transactions: List<TransactionResponse>
)
