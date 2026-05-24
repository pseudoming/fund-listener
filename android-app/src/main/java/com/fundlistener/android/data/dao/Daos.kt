package com.fundlistener.android.data.dao

import androidx.room.*
import com.fundlistener.android.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FundPositionDao {
    @Query("SELECT * FROM fund_position ORDER BY updated_at DESC")
    suspend fun getAll(): List<RoomFundPosition>

    @Query("SELECT * FROM fund_position WHERE fund_code = :code")
    suspend fun get(code: String): RoomFundPosition?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(position: RoomFundPosition)

    @Query("DELETE FROM fund_position WHERE fund_code = :code")
    suspend fun delete(code: String)
}

@Dao
interface FundTransactionDao {
    @Insert
    suspend fun insert(tx: RoomFundTransaction)

    @Query("SELECT * FROM fund_transaction WHERE fund_code = :code ORDER BY trade_date DESC, created_at DESC")
    suspend fun getByFund(code: String): List<RoomFundTransaction>

    @Query("SELECT * FROM fund_transaction ORDER BY trade_date DESC, created_at DESC")
    suspend fun getAll(): List<RoomFundTransaction>

    @Query("DELETE FROM fund_transaction WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM fund_transaction")
    suspend fun count(): Int
}

@Dao
interface NavHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: RoomNavHistory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<RoomNavHistory>)

    @Query("SELECT * FROM nav_history WHERE fund_code = :code ORDER BY nav_date DESC LIMIT :limit")
    suspend fun getByFund(code: String, limit: Int = 365): List<RoomNavHistory>

    @Query("SELECT * FROM nav_history WHERE fund_code = :code ORDER BY nav_date DESC LIMIT 1")
    suspend fun getLatest(code: String): RoomNavHistory?
}

@Dao
interface ValuationSnapshotDao {
    @Insert
    suspend fun insert(snapshot: RoomValuationSnapshot)

    @Query("SELECT * FROM valuation_snapshot WHERE fund_code = :code ORDER BY snapshot_time DESC LIMIT :limit")
    suspend fun getByFund(code: String, limit: Int = 100): List<RoomValuationSnapshot>

    @Query("SELECT * FROM valuation_snapshot WHERE fund_code = :code ORDER BY snapshot_time DESC LIMIT 1")
    suspend fun getLatest(code: String): RoomValuationSnapshot?

    @Query("SELECT COUNT(*) FROM valuation_snapshot WHERE fund_code = :code")
    suspend fun count(code: String): Int
}
