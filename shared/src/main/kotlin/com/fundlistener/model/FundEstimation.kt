package com.fundlistener.model

import kotlinx.serialization.Serializable

/**
 * 天天基金实时估值数据（来自 fundgz.1234567.com.cn）
 *
 * 原始 JSONP 字段映射:
 *   fundcode → code
 *   name     → name
 *   jzrq     → navDate      (净值日期)
 *   dwjz     → nav          (单位净值)
 *   gsz      → estimatedNav (估算净值)
 *   gszzl    → estimatedGrowthRate (估算涨跌幅 %)
 *   gztime   → estimationTime (估算时间)
 */
@Serializable
data class FundEstimation(
    val code: String,
    val name: String,
    val navDate: String,
    val nav: String,
    val estimatedNav: String,
    val estimatedGrowthRate: String,
    val estimationTime: String,
    val yesterdayNav: String = ""
)

/**
 * 天天基金 JSONP 内层 JSON 的反序列化 DTO
 */
@Serializable
data class TianTianFundRaw(
    val fundcode: String,
    val name: String,
    val jzrq: String,
    val dwjz: String,
    val gsz: String,
    val gszzl: String,
    val gztime: String
) {
    fun toEstimation() = FundEstimation(
        code = fundcode,
        name = name,
        navDate = jzrq,
        nav = dwjz,
        estimatedNav = gsz,
        estimatedGrowthRate = gszzl,
        estimationTime = gztime
    )
}
