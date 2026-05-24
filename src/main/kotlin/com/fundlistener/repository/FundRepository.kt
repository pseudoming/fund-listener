package com.fundlistener.repository

import com.fundlistener.model.*

/**
 * 基金数据存储层接口
 *
 * JVM 开发期: SqliteFundRepository (JDBC + xerial sqlite-jdbc)
 * Android 生产期: RoomFundRepository (Room DAO 封装)
 *
 * 上层代码（Service / Route）仅依赖此接口，不感知底层实现。
 */
interface FundRepository {

    // ═══════════════════════════════════════════
    //  持仓 (fund_position)
    // ═══════════════════════════════════════════

    /** 获取所有持仓，按 updated_at DESC 排序 */
    suspend fun getAllPositions(): List<FundPosition>

    /** 按基金代码查询持仓，不存在返回 null */
    suspend fun getPosition(fundCode: String): FundPosition?

    /** 插入或更新持仓（以 fund_code 为唯一键） */
    suspend fun upsertPosition(position: FundPosition)

    /** 删除持仓（同时不删除交易记录，保留历史） */
    suspend fun deletePosition(fundCode: String)

    // ═══════════════════════════════════════════
    //  交易记录 (fund_transaction)
    // ═══════════════════════════════════════════

    /** 插入一条交易记录 */
    suspend fun insertTransaction(transaction: FundTransaction)

    /** 查询某只基金的全部交易记录，按 trade_date DESC, created_at DESC 排序 */
    suspend fun getTransactionsByFund(fundCode: String): List<FundTransaction>

    /** 查询所有交易记录 */
    suspend fun getAllTransactions(): List<FundTransaction>

    /** 删除一条交易记录 */
    suspend fun deleteTransaction(id: String)

    // ═══════════════════════════════════════════
    //  净值历史 (nav_history)
    // ═══════════════════════════════════════════

    /** 插入或更新一条净值记录（以 fund_code + nav_date 为唯一键） */
    suspend fun upsertNavHistory(record: NavHistory)

    /** 批量插入/更新净值记录 */
    suspend fun batchUpsertNavHistory(records: List<NavHistory>)

    /** 查询净值历史，按 nav_date DESC 排序，默认最近 365 条 */
    suspend fun getNavHistory(fundCode: String, limit: Int = 365): List<NavHistory>

    /** 获取最新一条净值记录 */
    suspend fun getLatestNav(fundCode: String): NavHistory?

    // ═══════════════════════════════════════════
    //  估值快照 (valuation_snapshot)
    // ═══════════════════════════════════════════

    /** 插入一条估值快照 */
    suspend fun insertSnapshot(snapshot: ValuationSnapshot)

    /** 查询估值快照，按 snapshot_time DESC 排序 */
    suspend fun getSnapshots(fundCode: String, limit: Int = 100): List<ValuationSnapshot>

    /** 获取最新一条估值快照 */
    suspend fun getLatestSnapshot(fundCode: String): ValuationSnapshot?

    /** 获取快照总数（用于判断数据积累是否足够计算百分位） */
    suspend fun getSnapshotCount(fundCode: String): Int

    // ═══════════════════════════════════════════
    //  自定义估值规则 (fund_valuation_rule)
    // ═══════════════════════════════════════════

    /** 获取某只基金的所有估值规则 */
    suspend fun getValuationRules(fundCode: String): List<FundValuationRule>

    /** 插入一条估值规则 */
    suspend fun insertValuationRule(rule: FundValuationRule)

    /** 删除某只基金的所有估值规则 */
    suspend fun deleteValuationRules(fundCode: String)

    /** 删除一条特定的估值规则 */
    suspend fun deleteValuationRule(id: Long)

    /** 覆盖保存某只基金的所有估值规则 */
    suspend fun saveValuationRules(fundCode: String, rules: List<FundValuationRule>)

    // ═══════════════════════════════════════════
    //  应用配置 (app_config)
    // ═══════════════════════════════════════════

    /** 获取配置项，不存在则返回 null */
    suspend fun getConfig(key: String): String?

    /** 设置配置项 */
    suspend fun setConfig(key: String, value: String)
}
