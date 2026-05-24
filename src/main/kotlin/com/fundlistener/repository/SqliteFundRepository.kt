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
            INSERT INTO nav_history (fund_code, nav_date, nav, acc_nav, created_at)
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
            INSERT INTO nav_history (fund_code, nav_date, nav, acc_nav, created_at)
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
        val sql = "SELECT * FROM nav_history WHERE fund_code = ? ORDER BY nav_date DESC LIMIT ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, fundCode)
            stmt.setInt(2, limit)
            stmt.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.toNavHistory()) }
            }
        }
    }

    override suspend fun getLatestNav(fundCode: String): NavHistory? = dbQuery {
        val sql = "SELECT * FROM nav_history WHERE fund_code = ? ORDER BY nav_date DESC LIMIT 1"
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
            INSERT INTO valuation_snapshot
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
        val sql = "SELECT * FROM valuation_snapshot WHERE fund_code = ? ORDER BY snapshot_time DESC LIMIT ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, fundCode)
            stmt.setInt(2, limit)
            stmt.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.toValuationSnapshot()) }
            }
        }
    }

    override suspend fun getLatestSnapshot(fundCode: String): ValuationSnapshot? = dbQuery {
        val sql = "SELECT * FROM valuation_snapshot WHERE fund_code = ? ORDER BY snapshot_time DESC LIMIT 1"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, fundCode)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.toValuationSnapshot() else null }
        }
    }

    override suspend fun getSnapshotCount(fundCode: String): Int = dbQuery {
        val sql = "SELECT COUNT(*) FROM valuation_snapshot WHERE fund_code = ?"
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
}
