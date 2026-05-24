package com.fundlistener.model

import kotlinx.serialization.Serializable

/**
 * 估值历史响应 — 供前端走势图使用。
 */
@Serializable
data class ValuationHistoryResponse(
    val fundCode: String,
    /** 历史快照列表，按时间升序 */
    val snapshots: List<SnapshotPoint>,
    /** PE 百分位状态 */
    val pePercentile: PercentileInfo?,
    /** PB 百分位状态 */
    val pbPercentile: PercentileInfo?
)

@Serializable
data class SnapshotPoint(
    val time: Long,               // snapshot_time 毫秒时间戳
    val estimatedNav: String,
    val estimatedGrowthRate: String,
    val weightedPe: String?,      // null 表示当日无 PE 数据
    val weightedPb: String?,
    val pePercentile: String?,
    val pbPercentile: String?,
    val coverageRate: String?
)

@Serializable
data class PercentileInfo(
    val status: String,           // "VALID" | "DEGRADED" | "NO_VALUE"
    val value: String?,           // 百分位值，如 "72.50"
    val currentDays: Int,         // 已有数据天数
    val requiredDays: Int         // 需要的最低天数
)
