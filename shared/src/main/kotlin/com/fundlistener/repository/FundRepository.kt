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
    //  全量基金缓存 (all_funds)
    // ═══════════════════════════════════════════

    /** 模糊搜索基金 */
    suspend fun searchFunds(keyword: String, limit: Int = 20): List<FundSearchResult>

    /** 批量插入基金信息 */
    suspend fun batchInsertFunds(funds: List<FundSearchResult>)

    /** 获取缓存的基金总数 */
    suspend fun getFundCount(): Int

    // ═══════════════════════════════════════════
    //  应用配置 (app_config)
    // ═══════════════════════════════════════════

    /** 获取配置项，不存在则返回 null */
    suspend fun getConfig(key: String): String?

    /** 设置配置项 */
    suspend fun setConfig(key: String, value: String)

    // ═══════════════════════════════════════════
    //  自选基金 (watchlist)
    // ═══════════════════════════════════════════

    /** 获取所有自选基金的代码 */
    suspend fun getAllWatchlist(): List<String>

    /** 获取所有自选基金的代码及添加时间 */
    suspend fun getWatchlistItems(): List<Pair<String, Long>>

    /** 添加基金到自选 */
    suspend fun addToWatchlist(fundCode: String)

    /** 从自选删除基金 */
    suspend fun removeFromWatchlist(fundCode: String)

    /** 判断是否在自选中 */
    suspend fun isInWatchlist(fundCode: String): Boolean

    // ═══════════════════════════════════════════
    //  基金完整元信息与指标缓存 (fund_metadata)
    // ═══════════════════════════════════════════

    /** 获取基金元数据缓存 */
    suspend fun getFundMetadata(fundCode: String): FundMetadata?

    /** 写入/更新基金元数据缓存 */
    suspend fun upsertFundMetadata(metadata: FundMetadata)

    /** 获取所有缓存的基金元数据 */
    suspend fun getAllFundMetadata(): List<FundMetadata>

    // ═══════════════════════════════════════════
    //  股票/重仓相关 (stock_metadata, fund_holding_mapping, stock_price_history)
    // ═══════════════════════════════════════════

    /** 插入或更新股票基本信息 */
    suspend fun upsertStockMetadata(metadata: StockMetadata)

    /** 获取单个股票的基本信息 */
    suspend fun getStockMetadata(stockCode: String): StockMetadata?

    /** 获取多只股票的基本信息 */
    suspend fun getStocksMetadata(stockCodes: List<String>): List<StockMetadata>

    /** 批量插入或更新基金重仓映射 */
    suspend fun batchUpsertFundHoldingMappings(mappings: List<FundHoldingMapping>)

    /** 删除某只基金的所有重仓映射（通常用于重新全量写入） */
    suspend fun deleteFundHoldingMappings(fundCode: String)

    /** 获取某只基金的重仓映射列表 */
    suspend fun getFundHoldingMappings(fundCode: String): List<FundHoldingMapping>

    /** 插入或更新股票每日历史价格 */
    suspend fun upsertStockPriceHistory(history: StockPriceHistory)

    /** 获取股票最新价格（历史表中） */
    suspend fun getLatestStockPrice(stockCode: String): StockPriceHistory?
}


