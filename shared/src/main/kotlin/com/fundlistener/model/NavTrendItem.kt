package com.fundlistener.model

import kotlinx.serialization.Serializable

@Serializable
data class NavTrendItem(
    val x: Long,
    val y: Double,
    val equityReturn: Double,
    val unitMoney: String = ""
)
