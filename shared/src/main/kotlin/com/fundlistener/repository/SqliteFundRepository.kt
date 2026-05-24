package com.fundlistener.repository

import com.fundlistener.db.DatabaseFactory
import com.fundlistener.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.sql.ResultSet

/**
 * FundRepository 的 SQLite/JDBC 实现（JVM 开发期）
 *
 * Android 期由 RoomFundRepository 替代，上层通过 FundRepository 接口调用，无感切换。
 *
 * 注意：
 * - 所有 BigDecimal 字段以 toPlainString() 存入 TEXT 列
 * - 所有 suspend 函数在 Dispatchers.IO 上执行，避免阻塞 Ktor 事件循环
 * - SQLite 单连接模型，JDBC 操作本身是同步的，通过 withContext(IO) 桥接
 */
class SqliteFundRepository(private val db: DatabaseFactory) : FundRepository {

    private val logger = LoggerFactory.getLogger(SqliteFundRepository::class.java)
    private val conn get() = db.getConnection()

    // ═══════════════════════════════════════════
    //  持仓
    // ═══════════════════════════════════════════

    override suspend fun getAllPositions(): List<FundPosition> = dbQuery {
        val sql = "SELECT * FROM fund_position ORDER BY updated_at DESC"
        conn.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                buildList { while (rs.next()) add(rs.toFundPosition()) }
            }
        }
    }

    override suspend fun getPosition(fundCode: String): FundPosition? = dbQuery {
        val sql = "SELECT * FROM fund_position WHERE fund_code = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, fundCode)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.toFundPosition() else null }
        }
    }

    override suspend fun upsertPosition(position: FundPosition) = dbExecute {
        val sql = """
            INSERT INTO fund_position (fund_code, fund_name, total_shares, total_cost, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(fund_code) DO UPDATE SET
                fund_name = excluded.fund_name,
                total_shares = excluded.total_shares,
                total_cost = excluded.total_cost,
                updated_at = excluded.updated_at
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, position.fundCode)
            stmt.setString(2, position.fundName)
            stmt.setString(3, position.totalShares.toPlainString())
            stmt.setString(4, position.totalCost.toPlainString())
            stmt.setLong(5, position.createdAt)
            stmt.setLong(6, position.updatedAt)
            stmt.executeUpdate()
        }
    }

    override suspend fun deletePosition(fundCode: String) = dbExecute {
        conn.prepareStatement("DELETE FROM fund_position WHERE fund_code = ?").use { stmt ->
            stmt.setString(1, fundCode)
            stmt.executeUpdate()
        }
    }

    // ═══════════════════════════════════════════
    //  交易记录
    // ═══════════════════════════════════════════

    override suspend fun insertTransaction(transaction: FundTransaction) = dbExecute {
        val sql = """
            INSERT INTO fund_transaction (id, fund_code, type, shares, nav, amount, fee, trade_date, note, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, transaction.id)
            stmt.setString(2, transaction.fundCode)
            stmt.setString(3, transaction.type.name)
            stmt.setString(4, transaction.shares.toPlainString())
            stmt.setString(5, transaction.nav.toPlainString())
            stmt.setString(6, transaction.amount.toPlainString())
            stmt.setString(7, transaction.fee.toPlainString())
            stmt.setString(8, transaction.tradeDate)
            stmt.setString(9, transaction.note)
            stmt.setLong(10, transaction.createdAt)
            stmt.executeUpdate()
        }
    }

    override suspend fun getTransactionsByFund(fundCode: String): List<FundTransaction> = dbQuery {
        val sql = "SELECT * FROM fund_transaction WHERE fund_code = ? ORDER BY trade_date DESC, created_at DESC"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, fundCode)
            stmt.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.toFundTransaction()) }
            }
        }
    }

    override suspend fun getAllTransactions(): List<FundTransaction> = dbQuery {
        val sql = "SELECT * FROM fund_transaction ORDER BY trade_date DESC, created_at DESC"
        conn.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                buildList { while (rs.next()) add(rs.toFundTransaction()) }
            }
        }
    }

    override suspend fun deleteTransaction(id: String) = dbExecute {
        conn.prepareStatement("DELETE FROM fund_transaction WHERE id = ?").use { stmt ->
            stmt.setString(1, id)
            stmt.executeUpdate()
        }
    }

    // ═══════════════════════════════════════════
    //  净值历史
    // ═══════════════════════════════════════════

    override suspend fun upsertNavHistory(record: NavHistory) = dbExecute {
        val sql = """
            INSERT INTO fund_nav_history (fund_code, nav_date, nav, acc_nav, created_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(fund_code, nav_date) DO UPDATE SET
                nav = excluded.nav,
                acc_nav = excluded.acc_nav
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, record.fundCode)
            stmt.setString(2, record.navDate)
            stmt.setString(3, record.nav.toPlainString())
            stmt.setString(4, record.accNav?.toPlainString())
            stmt.setLong(5, record.createdAt)
            stmt.executeUpdate()
        }
    }

    override suspend fun batchUpsertNavHistory(records: List<NavHistory>) = dbExecute {
        if (records.isEmpty()) return@dbExecute
        val sql = """
            INSERT INTO fund_nav_history (fund_code, nav_date, nav, acc_nav, created_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(fund_code, nav_date) DO UPDATE SET
                nav = excluded.nav,
                acc_nav = excluded.acc_nav
        """.trimIndent()
        conn.autoCommit = false
        try {
            conn.prepareStatement(sql).use { stmt ->
                for (record in records) {
                    stmt.setString(1, record.fundCode)
                    stmt.setString(2, record.navDate)
                    stmt.setString(3, record.nav.toPlainString())
                    stmt.setString(4, record.accNav?.toPlainString())
                    stmt.setLong(5, record.createdAt)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = true
        }
    }

    override suspend fun getNavHistory(fundCode: String, limit: Int): List<NavHistory> = dbQuery {
        val sql = "SELECT * FROM fund_nav_history WHERE fund_code = ? ORDER BY nav_date DESC LIMIT ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, fundCode)
            stmt.setInt(2, limit)
            stmt.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.toNavHistory()) }
            }
        }
    }

    override suspend fun getLatestNav(fundCode: String): NavHistory? = dbQuery {
        val sql = "SELECT * FROM fund_nav_history WHERE fund_code = ? ORDER BY nav_date DESC LIMIT 1"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, fundCode)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.toNavHistory() else null }
        }
    }

    // ═══════════════════════════════════════════
    //  估值快照
    // ═══════════════════════════════════════════

    override suspend fun insertSnapshot(snapshot: ValuationSnapshot) = dbExecute {
        val sql = """
            INSERT INTO fund_valuation_snapshot
                (fund_code, snapshot_time, estimated_nav, estimated_growth_rate,
                 weighted_pe, weighted_pb, pe_percentile, pb_percentile, coverage_rate, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, snapshot.fundCode)
            stmt.setLong(2, snapshot.snapshotTime)
            stmt.setString(3, snapshot.estimatedNav.toPlainString())
            stmt.setString(4, snapshot.estimatedGrowthRate.toPlainString())
            stmt.setString(5, snapshot.weightedPe?.toPlainString())
            stmt.setString(6, snapshot.weightedPb?.toPlainString())
            stmt.setString(7, snapshot.pePercentile?.toPlainString())
            stmt.setString(8, snapshot.pbPercentile?.toPlainString())
            stmt.setString(9, snapshot.coverageRate?.toPlainString())
            stmt.setLong(10, snapshot.createdAt)
            stmt.executeUpdate()
        }
    }

    override suspend fun getSnapshots(fundCode: String, limit: Int): List<ValuationSnapshot> = dbQuery {
        val sql = "SELECT * FROM fund_valuation_snapshot WHERE fund_code = ? ORDER BY snapshot_time DESC LIMIT ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, fundCode)
            stmt.setInt(2, limit)
            stmt.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.toValuationSnapshot()) }
            }
        }
    }

    override suspend fun getLatestSnapshot(fundCode: String): ValuationSnapshot? = dbQuery {
        val sql = "SELECT * FROM fund_valuation_snapshot WHERE fund_code = ? ORDER BY snapshot_time DESC LIMIT 1"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, fundCode)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.toValuationSnapshot() else null }
        }
    }

    override suspend fun getSnapshotCount(fundCode: String): Int = dbQuery {
        val sql = "SELECT COUNT(*) FROM fund_valuation_snapshot WHERE fund_code = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, fundCode)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    // ═══════════════════════════════════════════
    //  内部工具
    // ═══════════════════════════════════════════

    /** 在 IO 线程执行查询 */
    private suspend fun <T> dbQuery(block: () -> T): T =
        withContext(Dispatchers.IO) { block() }

    /** 在 IO 线程执行写入（无返回值） */
    private suspend fun dbExecute(block: () -> Unit) =
        withContext(Dispatchers.IO) { block() }

    // --- ResultSet → Entity 映射 ---

    private fun ResultSet.toFundPosition() = FundPosition(
        fundCode = getString("fund_code"),
        fundName = getString("fund_name"),
        totalShares = BigDecimal(getString("total_shares")),
        totalCost = BigDecimal(getString("total_cost")),
        createdAt = getLong("created_at"),
        updatedAt = getLong("updated_at")
    )

    private fun ResultSet.toFundTransaction() = FundTransaction(
        id = getString("id"),
        fundCode = getString("fund_code"),
        type = TransactionType.valueOf(getString("type")),
        shares = BigDecimal(getString("shares")),
        nav = BigDecimal(getString("nav")),
        amount = BigDecimal(getString("amount")),
        fee = BigDecimal(getString("fee")),
        tradeDate = getString("trade_date"),
        note = getString("note") ?: "",
        createdAt = getLong("created_at")
    )

    private fun ResultSet.toNavHistory() = NavHistory(
        id = getLong("id"),
        fundCode = getString("fund_code"),
        navDate = getString("nav_date"),
        nav = BigDecimal(getString("nav")),
        accNav = getString("acc_nav")?.let { BigDecimal(it) },
        createdAt = getLong("created_at")
    )

    private fun ResultSet.toValuationSnapshot() = ValuationSnapshot(
        id = getLong("id"),
        fundCode = getString("fund_code"),
        snapshotTime = getLong("snapshot_time"),
        estimatedNav = BigDecimal(getString("estimated_nav")),
        estimatedGrowthRate = BigDecimal(getString("estimated_growth_rate")),
        weightedPe = getString("weighted_pe")?.let { BigDecimal(it) },
        weightedPb = getString("weighted_pb")?.let { BigDecimal(it) },
        pePercentile = getString("pe_percentile")?.let { BigDecimal(it) },
        pbPercentile = getString("pb_percentile")?.let { BigDecimal(it) },
        coverageRate = getString("coverage_rate")?.let { BigDecimal(it) },
        createdAt = getLong("created_at")
    )

    override suspend fun getValuationRules(fundCode: String): List<FundValuationRule> = dbQuery {
        val sql = "SELECT * FROM fund_valuation_rule WHERE fund_code = ? ORDER BY id ASC"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, fundCode)
            stmt.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.toFundValuationRule()) }
            }
        }
    }

    override suspend fun insertValuationRule(rule: FundValuationRule) = dbExecute {
        val sql = """
            INSERT INTO fund_valuation_rule (fund_code, component_type, target_code, weight_percent, created_at)
            VALUES (?, ?, ?, ?, ?)
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, rule.fundCode)
            stmt.setString(2, rule.componentType)
            stmt.setString(3, rule.targetCode)
            stmt.setDouble(4, rule.weightPercent)
            stmt.setLong(5, rule.createdAt)
            stmt.executeUpdate()
        }
    }

    override suspend fun deleteValuationRules(fundCode: String) = dbExecute {
        val sql = "DELETE FROM fund_valuation_rule WHERE fund_code = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, fundCode)
            stmt.executeUpdate()
        }
    }

    override suspend fun deleteValuationRule(id: Long) = dbExecute {
        val sql = "DELETE FROM fund_valuation_rule WHERE id = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, id)
            stmt.executeUpdate()
        }
    }

    override suspend fun saveValuationRules(fundCode: String, rules: List<FundValuationRule>) = dbExecute {
        val autoCommit = conn.autoCommit
        try {
            conn.autoCommit = false
            
            // 1. Delete old rules
            conn.prepareStatement("DELETE FROM fund_valuation_rule WHERE fund_code = ?").use { stmt ->
                stmt.setString(1, fundCode)
                stmt.executeUpdate()
            }
            
            // 2. Insert new rules
            val sql = """
                INSERT INTO fund_valuation_rule (fund_code, component_type, target_code, weight_percent, created_at)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                for (rule in rules) {
                    stmt.setString(1, fundCode)
                    stmt.setString(2, rule.componentType)
                    stmt.setString(3, rule.targetCode)
                    stmt.setDouble(4, rule.weightPercent)
                    stmt.setLong(5, rule.createdAt)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
            
            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = autoCommit
        }
    }

    private fun ResultSet.toFundValuationRule() = FundValuationRule(
        id = getLong("id"),
        fundCode = getString("fund_code"),
        componentType = getString("component_type"),
        targetCode = getString("target_code"),
        weightPercent = getDouble("weight_percent"),
        createdAt = getLong("created_at")
    )

    private fun ResultSet.toFundSearchResult() = FundSearchResult(
        code = getString("code"),
        pinyinInitials = getString("pinyin_initials"),
        name = getString("name"),
        type = getString("type"),
        pinyinFull = getString("pinyin_full")
    )

    override suspend fun searchFunds(keyword: String, limit: Int): List<FundSearchResult> = dbQuery {
        val query = "%$keyword%"
        val sql = "SELECT * FROM all_funds WHERE code LIKE ? OR name LIKE ? OR pinyin_initials LIKE ? OR pinyin_full LIKE ? LIMIT ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, query)
            stmt.setString(2, query)
            stmt.setString(3, query)
            stmt.setString(4, query)
            stmt.setInt(5, limit)
            stmt.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.toFundSearchResult()) }
            }
        }
    }

    override suspend fun batchInsertFunds(funds: List<FundSearchResult>) = dbExecute {
        if (funds.isEmpty()) return@dbExecute
        val sql = """
            INSERT OR REPLACE INTO all_funds (code, pinyin_initials, name, type, pinyin_full)
            VALUES (?, ?, ?, ?, ?)
        """.trimIndent()
        val autoCommit = conn.autoCommit
        try {
            conn.autoCommit = false
            conn.prepareStatement(sql).use { stmt ->
                for (fund in funds) {
                    stmt.setString(1, fund.code)
                    stmt.setString(2, fund.pinyinInitials)
                    stmt.setString(3, fund.name)
                    stmt.setString(4, fund.type)
                    stmt.setString(5, fund.pinyinFull)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = autoCommit
        }
    }

    override suspend fun getFundCount(): Int = dbQuery {
        val sql = "SELECT COUNT(*) FROM all_funds"
        conn.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    // ═══════════════════════════════════════════
    //  应用配置 (app_config)
    // ═══════════════════════════════════════════

    override suspend fun getConfig(key: String): String? = dbQuery {
        val sql = "SELECT value FROM app_config WHERE key = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, key)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getString("value") else null }
        }
    }

    override suspend fun setConfig(key: String, value: String) = dbExecute {
        val sql = """
            INSERT INTO app_config (key, value) VALUES (?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, key)
            stmt.setString(2, value)
            stmt.executeUpdate()
        }
    }

    // ═══════════════════════════════════════════
    //  自选基金 (watchlist)
    // ═══════════════════════════════════════════

    override suspend fun getAllWatchlist(): List<String> = dbQuery {
        val sql = "SELECT fund_code FROM watchlist ORDER BY created_at DESC"
        conn.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                buildList { while (rs.next()) add(rs.getString("fund_code")) }
            }
        }
    }

    override suspend fun getWatchlistItems(): List<Pair<String, Long>> = dbQuery {
        val sql = "SELECT fund_code, created_at FROM watchlist ORDER BY created_at DESC"
        conn.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                buildList { while (rs.next()) add(rs.getString("fund_code") to rs.getLong("created_at")) }
            }
        }
    }

    override suspend fun addToWatchlist(fundCode: String) = dbExecute {
        val sql = "INSERT OR IGNORE INTO watchlist (fund_code, created_at) VALUES (?, ?)"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, fundCode)
            stmt.setLong(2, System.currentTimeMillis())
            stmt.executeUpdate()
        }
    }

    override suspend fun removeFromWatchlist(fundCode: String) = dbExecute {
        val sql = "DELETE FROM watchlist WHERE fund_code = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, fundCode)
            stmt.executeUpdate()
        }
    }

    override suspend fun isInWatchlist(fundCode: String): Boolean = dbQuery {
        val sql = "SELECT COUNT(*) FROM watchlist WHERE fund_code = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, fundCode)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) > 0 else false }
        }
    }

    // ═══════════════════════════════════════════
    //  基金完整元信息与指标缓存 (fund_metadata)
    // ═══════════════════════════════════════════

    private fun ResultSet.toFundMetadata() = FundMetadata(
        fundCode = getString("fund_code"),
        fundName = getString("fund_name"),
        fundType = getString("fund_type"),
        fundManager = getString("fund_manager"),
        topHoldings = getString("top_holdings"),
        assetType = getString("asset_type"),
        linkedEtfCode = getString("linked_etf_code"),
        linkedEtfName = getString("linked_etf_name"),
        lastUpdated = getLong("last_updated")
    )

    override suspend fun getFundMetadata(fundCode: String): FundMetadata? = dbQuery {
        val sql = "SELECT * FROM fund_metadata WHERE fund_code = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, fundCode)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.toFundMetadata() else null }
        }
    }

    override suspend fun upsertFundMetadata(metadata: FundMetadata) = dbExecute {
        val sql = """
            INSERT INTO fund_metadata (
                fund_code, fund_name, fund_type, fund_manager, top_holdings, 
                asset_type, linked_etf_code, linked_etf_name, last_updated
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(fund_code) DO UPDATE SET
                fund_name = excluded.fund_name,
                fund_type = excluded.fund_type,
                fund_manager = excluded.fund_manager,
                top_holdings = excluded.top_holdings,
                asset_type = excluded.asset_type,
                linked_etf_code = excluded.linked_etf_code,
                linked_etf_name = excluded.linked_etf_name,
                last_updated = excluded.last_updated
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, metadata.fundCode)
            stmt.setString(2, metadata.fundName)
            stmt.setString(3, metadata.fundType)
            stmt.setString(4, metadata.fundManager)
            stmt.setString(5, metadata.topHoldings)
            stmt.setString(6, metadata.assetType)
            stmt.setString(7, metadata.linkedEtfCode)
            stmt.setString(8, metadata.linkedEtfName)
            stmt.setLong(9, metadata.lastUpdated)
            stmt.executeUpdate()
        }
    }

    override suspend fun getAllFundMetadata(): List<FundMetadata> = dbQuery {
        val sql = "SELECT * FROM fund_metadata"
        conn.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                buildList { while (rs.next()) add(rs.toFundMetadata()) }
            }
        }
    }

    // ═══════════════════════════════════════════
    //  股票/重仓相关 (stock_metadata, fund_holding_mapping, stock_price_history)
    // ═══════════════════════════════════════════

    private fun ResultSet.toStockMetadata() = StockMetadata(
        stockCode = getString("stock_code"),
        stockName = getString("stock_name"),
        marketType = getString("market_type"),
        currentPrice = getString("current_price"),
        growthRate = getString("growth_rate"),
        updatedAt = getLong("updated_at")
    )

    override suspend fun upsertStockMetadata(metadata: StockMetadata) = dbExecute {
        val sql = """
            INSERT INTO stock_metadata (stock_code, stock_name, market_type, current_price, growth_rate, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(stock_code) DO UPDATE SET
                stock_name = excluded.stock_name,
                market_type = excluded.market_type,
                current_price = excluded.current_price,
                growth_rate = excluded.growth_rate,
                updated_at = excluded.updated_at
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, metadata.stockCode)
            stmt.setString(2, metadata.stockName)
            stmt.setString(3, metadata.marketType)
            stmt.setString(4, metadata.currentPrice)
            stmt.setString(5, metadata.growthRate)
            stmt.setLong(6, metadata.updatedAt)
            stmt.executeUpdate()
        }
    }

    override suspend fun getStockMetadata(stockCode: String): StockMetadata? = dbQuery {
        val sql = "SELECT * FROM stock_metadata WHERE stock_code = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, stockCode)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.toStockMetadata() else null }
        }
    }

    override suspend fun getStocksMetadata(stockCodes: List<String>): List<StockMetadata> = dbQuery {
        if (stockCodes.isEmpty()) return@dbQuery emptyList()
        val placeholders = stockCodes.joinToString(",") { "?" }
        val sql = "SELECT * FROM stock_metadata WHERE stock_code IN ($placeholders)"
        conn.prepareStatement(sql).use { stmt ->
            stockCodes.forEachIndexed { index, code -> stmt.setString(index + 1, code) }
            stmt.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.toStockMetadata()) }
            }
        }
    }

    private fun ResultSet.toFundHoldingMapping() = FundHoldingMapping(
        id = getLong("id"),
        fundCode = getString("fund_code"),
        stockCode = getString("stock_code"),
        weightPercent = getString("weight_percent"),
        reportDate = getString("report_date"),
        createdAt = getLong("created_at")
    )

    override suspend fun batchUpsertFundHoldingMappings(mappings: List<FundHoldingMapping>) = dbExecute {
        if (mappings.isEmpty()) return@dbExecute
        val sql = """
            INSERT INTO fund_holding_mapping (fund_code, stock_code, weight_percent, report_date, created_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(fund_code, stock_code) DO UPDATE SET
                weight_percent = excluded.weight_percent,
                report_date = excluded.report_date
        """.trimIndent()
        conn.autoCommit = false
        try {
            conn.prepareStatement(sql).use { stmt ->
                for (m in mappings) {
                    stmt.setString(1, m.fundCode)
                    stmt.setString(2, m.stockCode)
                    stmt.setString(3, m.weightPercent)
                    stmt.setString(4, m.reportDate)
                    stmt.setLong(5, m.createdAt)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = true
        }
    }

    override suspend fun deleteFundHoldingMappings(fundCode: String) = dbExecute {
        val sql = "DELETE FROM fund_holding_mapping WHERE fund_code = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, fundCode)
            stmt.executeUpdate()
        }
    }

    override suspend fun getFundHoldingMappings(fundCode: String): List<FundHoldingMapping> = dbQuery {
        val sql = "SELECT * FROM fund_holding_mapping WHERE fund_code = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, fundCode)
            stmt.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.toFundHoldingMapping()) }
            }
        }
    }

    private fun ResultSet.toStockPriceHistory() = StockPriceHistory(
        id = getLong("id"),
        stockCode = getString("stock_code"),
        tradeDate = getString("trade_date"),
        closePrice = getString("close_price"),
        createdAt = getLong("created_at")
    )

    override suspend fun upsertStockPriceHistory(history: StockPriceHistory) = dbExecute {
        val sql = """
            INSERT INTO stock_price_history (stock_code, trade_date, close_price, created_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(stock_code, trade_date) DO UPDATE SET
                close_price = excluded.close_price
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, history.stockCode)
            stmt.setString(2, history.tradeDate)
            stmt.setString(3, history.closePrice)
            stmt.setLong(4, history.createdAt)
            stmt.executeUpdate()
        }
    }

    override suspend fun getLatestStockPrice(stockCode: String): StockPriceHistory? = dbQuery {
        val sql = "SELECT * FROM stock_price_history WHERE stock_code = ? ORDER BY trade_date DESC LIMIT 1"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, stockCode)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.toStockPriceHistory() else null }
        }
    }
}


