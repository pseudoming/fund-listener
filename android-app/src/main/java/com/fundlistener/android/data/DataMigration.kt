package com.fundlistener.android.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.fundlistener.android.data.entity.*
import org.slf4j.LoggerFactory

/**
 * JVM SQLite → Room 数据迁移。
 *
 * 在应用首次启动时执行：
 *   1. 检查旧 JVM 期的 fund-listener.db 是否存在
 *   2. 若存在，逐表读取数据，通过 Room DAO 写入 Room DB
 *   3. 迁移完成后重命名旧文件为 fund-listener.db.migrated
 *
 * 表结构完全兼容（schema.sql 定义的字段与 Room Entity 一致），
 * 不需要 DDL 转换，仅做数据搬运。
 */
class DataMigration(private val context: Context, private val roomDb: FundDatabase) {

    private val logger = LoggerFactory.getLogger(DataMigration::class.java)

    companion object {
        private const val OLD_DB_NAME = "fund-listener.db"
        private const val MIGRATED_SUFFIX = ".migrated"
    }

    /**
     * @return true 表示执行了迁移，false 表示无旧数据或已迁移
     */
    fun migrateIfNeeded(): Boolean {
        val oldDbFile = context.getDatabasePath(OLD_DB_NAME)
        val migratedFile = context.getDatabasePath("$OLD_DB_NAME$MIGRATED_SUFFIX")

        if (migratedFile.exists()) {
            logger.info("DataMigration: already migrated, skipping")
            return false
        }

        if (!oldDbFile.exists()) {
            logger.info("DataMigration: no old DB found, skipping")
            return false
        }

        logger.info("DataMigration: found old DB at {}, starting migration", oldDbFile.absolutePath)

        return try {
            val oldDb = SQLiteDatabase.openDatabase(
                oldDbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            )

            // 在 Room 事务中执行所有迁移写入
            roomDb.runInTransaction {
                migratePositions(oldDb)
                migrateTransactions(oldDb)
                migrateNavHistory(oldDb)
                migrateSnapshots(oldDb)
            }

            oldDb.close()

            // 标记为已迁移
            oldDbFile.renameTo(migratedFile)
            logger.info("DataMigration: completed successfully, old DB renamed to {}", migratedFile.name)
            true
        } catch (e: Exception) {
            logger.error("DataMigration failed", e)
            false
        }
    }

    private fun migratePositions(oldDb: SQLiteDatabase) {
        var count = 0
        oldDb.rawQuery("SELECT * FROM fund_position", null).use { cursor ->
            while (cursor.moveToNext()) {
                val entity = RoomFundPosition(
                    fundCode = cursor.getString("fund_code"),
                    fundName = cursor.getString("fund_name"),
                    totalShares = cursor.getString("total_shares"),
                    totalCost = cursor.getString("total_cost"),
                    createdAt = cursor.getLong("created_at"),
                    updatedAt = cursor.getLong("updated_at")
                )
                roomDb.fundPositionDao().upsert(entity)
                count++
            }
        }
        logger.info("DataMigration: migrated {} fund_position rows", count)
    }

    private fun migrateTransactions(oldDb: SQLiteDatabase) {
        var count = 0
        oldDb.rawQuery("SELECT * FROM fund_transaction", null).use { cursor ->
            while (cursor.moveToNext()) {
                val entity = RoomFundTransaction(
                    id = cursor.getString("id"),
                    fundCode = cursor.getString("fund_code"),
                    type = cursor.getString("type"),
                    shares = cursor.getString("shares"),
                    nav = cursor.getString("nav"),
                    amount = cursor.getString("amount"),
                    fee = cursor.getString("fee"),
                    tradeDate = cursor.getString("trade_date"),
                    note = cursor.getString("note"),
                    createdAt = cursor.getLong("created_at")
                )
                roomDb.fundTransactionDao().insert(entity)
                count++
            }
        }
        logger.info("DataMigration: migrated {} fund_transaction rows", count)
    }

    private fun migrateNavHistory(oldDb: SQLiteDatabase) {
        var count = 0
        oldDb.rawQuery("SELECT * FROM nav_history", null).use { cursor ->
            while (cursor.moveToNext()) {
                val entity = RoomNavHistory(
                    id = cursor.getLong("id"),
                    fundCode = cursor.getString("fund_code"),
                    navDate = cursor.getString("nav_date"),
                    nav = cursor.getString("nav"),
                    accNav = cursor.getString("acc_nav"),
                    createdAt = cursor.getLong("created_at")
                )
                roomDb.navHistoryDao().upsert(entity)
                count++
            }
        }
        logger.info("DataMigration: migrated {} nav_history rows", count)
    }

    private fun migrateSnapshots(oldDb: SQLiteDatabase) {
        var count = 0
        oldDb.rawQuery("SELECT * FROM valuation_snapshot", null).use { cursor ->
            while (cursor.moveToNext()) {
                val entity = RoomValuationSnapshot(
                    id = cursor.getLong("id"),
                    fundCode = cursor.getString("fund_code"),
                    snapshotTime = cursor.getLong("snapshot_time"),
                    estimatedNav = cursor.getString("estimated_nav"),
                    estimatedGrowthRate = cursor.getString("estimated_growth_rate"),
                    weightedPe = cursor.getString("weighted_pe"),
                    weightedPb = cursor.getString("weighted_pb"),
                    pePercentile = cursor.getString("pe_percentile"),
                    pbPercentile = cursor.getString("pb_percentile"),
                    coverageRate = cursor.getString("coverage_rate"),
                    createdAt = cursor.getLong("created_at")
                )
                roomDb.valuationSnapshotDao().insert(entity)
                count++
            }
        }
        logger.info("DataMigration: migrated {} valuation_snapshot rows", count)
    }

    // ── Column name helper ───────────────────────────────────────

    private fun android.database.Cursor.getString(columnName: String): String =
        getString(getColumnIndexOrThrow(columnName))

    private fun android.database.Cursor.getLong(columnName: String): Long =
        getLong(getColumnIndexOrThrow(columnName))
}
