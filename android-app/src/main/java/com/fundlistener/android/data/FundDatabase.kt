package com.fundlistener.android.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fundlistener.android.data.converter.BigDecimalConverters
import com.fundlistener.android.data.dao.*
import com.fundlistener.android.data.entity.*

/**
 * Room Database — Android 生产期数据层。
 *
 * Schema 版本与 JVM SQLite 完全兼容：
 *   表名 / 列名 / 类型与 schema.sql 保持一致。
 *
 * 升级策略：
 *   - Version 1: 初始 schema
 *   - 后续加字段使用 destructive migration 或手动 Migration
 */
@Database(
    entities = [
        RoomFundPosition::class,
        RoomFundTransaction::class,
        RoomNavHistory::class,
        RoomValuationSnapshot::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(BigDecimalConverters::class)
abstract class FundDatabase : RoomDatabase() {

    abstract fun fundPositionDao(): FundPositionDao
    abstract fun fundTransactionDao(): FundTransactionDao
    abstract fun navHistoryDao(): NavHistoryDao
    abstract fun valuationSnapshotDao(): ValuationSnapshotDao
}
