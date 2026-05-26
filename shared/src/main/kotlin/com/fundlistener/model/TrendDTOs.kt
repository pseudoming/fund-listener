package com.fundlistener.model

import kotlinx.serialization.Serializable

@Serializable
data class TrendPoint(
    val date: String,
    val value: String
)

@Serializable
data class TrendAndDrawdownResponse(
    val trend: List<TrendPoint>,
    val maxDrawdown: String,
    val maxDrawdownStartDate: String,
    val maxDrawdownEndDate: String,
    val totalReturn: String
)
