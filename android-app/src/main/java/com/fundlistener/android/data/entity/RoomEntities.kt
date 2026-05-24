package com.fundlistener.android.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity — fund_position 表。
 * 与 shared 模块的 FundPosition 对应，BigDecimal 字段序列化为 String 存储。
 */
@Entity(tableName = "fund_position")
data class RoomFundPosition(
    @PrimaryKey
    @ColumnInfo(name = "fund_code")
    val fundCode: String,

    @ColumnInfo(name = "fund_name")
    val fundName: String,

    @ColumnInfo(name = "total_shares")
    val totalShares: String,       // BigDecimal → String

    @ColumnInfo(name = "total_cost")
    val totalCost: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(tableName = "fund_transaction")
data class RoomFundTransaction(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "fund_code")
    val fundCode: String,

    val type: String,              // "BUY" or "SELL"

    val shares: String,

    val nav: String,

    val amount: String,

    @ColumnInfo(name = "fee", defaultValue = "0")
    val fee: String,

    @ColumnInfo(name = "trade_date")
    val tradeDate: String,

    @ColumnInfo(name = "note", defaultValue = "")
    val note: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(tableName = "nav_history")
data class RoomNavHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "fund_code")
    val fundCode: String,

    @ColumnInfo(name = "nav_date")
    val navDate: String,

    val nav: String,

    @ColumnInfo(name = "acc_nav")
    val accNav: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(tableName = "valuation_snapshot")
data class RoomValuationSnapshot(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "fund_code")
    val fundCode: String,

    @ColumnInfo(name = "snapshot_time")
    val snapshotTime: Long,

    @ColumnInfo(name = "estimated_nav")
    val estimatedNav: String,

    @ColumnInfo(name = "estimated_growth_rate")
    val estimatedGrowthRate: String,

    @ColumnInfo(name = "weighted_pe")
    val weightedPe: String? = null,

    @ColumnInfo(name = "weighted_pb")
    val weightedPb: String? = null,

    @ColumnInfo(name = "pe_percentile")
    val pePercentile: String? = null,

    @ColumnInfo(name = "pb_percentile")
    val pbPercentile: String? = null,

    @ColumnInfo(name = "coverage_rate")
    val coverageRate: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
