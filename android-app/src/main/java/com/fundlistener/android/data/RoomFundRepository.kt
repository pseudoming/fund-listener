package com.fundlistener.android.data

import com.fundlistener.android.data.entity.*
import com.fundlistener.model.*
import com.fundlistener.repository.FundRepository
import java.math.BigDecimal

/**
 * Room 实现的 FundRepository — Android 生产期数据层。
 *
 * 与 JVM 期 [SqliteFundRepository] 实现相同接口，
 * 上层 Service / Route 代码零修改。
 */
class RoomFundRepository(private val db: FundDatabase) : FundRepository {

    private val posDao = db.fundPositionDao()
    private val txDao = db.fundTransactionDao()
    private val navDao = db.navHistoryDao()
    private val snapDao = db.valuationSnapshotDao()

    // ── Position ─────────────────────────────────────────────────

    override suspend fun getAllPositions(): List<FundPosition> =
        posDao.getAll().map { it.toModel() }

    override suspend fun getPosition(fundCode: String): FundPosition? =
        posDao.get(fundCode)?.toModel()

    override suspend fun upsertPosition(position: FundPosition) =
        posDao.upsert(position.toRoom())

    override suspend fun deletePosition(fundCode: String) =
        posDao.delete(fundCode)

    // ── Transaction ──────────────────────────────────────────────

    override suspend fun insertTransaction(transaction: FundTransaction) =
        txDao.insert(transaction.toRoom())

    override suspend fun getTransactionsByFund(fundCode: String): List<FundTransaction> =
        txDao.getByFund(fundCode).map { it.toModel() }

    override suspend fun getAllTransactions(): List<FundTransaction> =
        txDao.getAll().map { it.toModel() }

    override suspend fun deleteTransaction(id: String) =
        txDao.delete(id)

    // ── Nav History ──────────────────────────────────────────────

    override suspend fun upsertNavHistory(record: NavHistory) =
        navDao.upsert(record.toRoom())

    override suspend fun batchUpsertNavHistory(records: List<NavHistory>) =
        navDao.upsertAll(records.map { it.toRoom() })

    override suspend fun getNavHistory(fundCode: String, limit: Int): List<NavHistory> =
        navDao.getByFund(fundCode, limit).map { it.toModel() }

    override suspend fun getLatestNav(fundCode: String): NavHistory? =
        navDao.getLatest(fundCode)?.toModel()

    // ── Valuation Snapshot ───────────────────────────────────────

    override suspend fun insertSnapshot(snapshot: ValuationSnapshot) =
        snapDao.insert(snapshot.toRoom())

    override suspend fun getSnapshots(fundCode: String, limit: Int): List<ValuationSnapshot> =
        snapDao.getByFund(fundCode, limit).map { it.toModel() }

    override suspend fun getLatestSnapshot(fundCode: String): ValuationSnapshot? =
        snapDao.getLatest(fundCode)?.toModel()

    override suspend fun getSnapshotCount(fundCode: String): Int =
        snapDao.count(fundCode)

    // ── Mapping extensions ───────────────────────────────────────

    private fun RoomFundPosition.toModel() = FundPosition(
        fundCode = fundCode, fundName = fundName,
        totalShares = BigDecimal(totalShares), totalCost = BigDecimal(totalCost),
        createdAt = createdAt, updatedAt = updatedAt
    )

    private fun FundPosition.toRoom() = RoomFundPosition(
        fundCode = fundCode, fundName = fundName,
        totalShares = totalShares.toPlainString(), totalCost = totalCost.toPlainString(),
        createdAt = createdAt, updatedAt = updatedAt
    )

    private fun RoomFundTransaction.toModel() = FundTransaction(
        id = id, fundCode = fundCode, type = TransactionType.valueOf(type),
        shares = BigDecimal(shares), nav = BigDecimal(nav),
        amount = BigDecimal(amount), fee = BigDecimal(fee),
        tradeDate = tradeDate, note = note, createdAt = createdAt
    )

    private fun FundTransaction.toRoom() = RoomFundTransaction(
        id = id, fundCode = fundCode, type = type.name,
        shares = shares.toPlainString(), nav = nav.toPlainString(),
        amount = amount.toPlainString(), fee = fee.toPlainString(),
        tradeDate = tradeDate, note = note, createdAt = createdAt
    )

    private fun RoomNavHistory.toModel() = NavHistory(
        id = id, fundCode = fundCode, navDate = navDate,
        nav = BigDecimal(nav), accNav = accNav?.toBigDecimalOrNull(),
        createdAt = createdAt
    )

    private fun NavHistory.toRoom() = RoomNavHistory(
        id = id, fundCode = fundCode, navDate = navDate,
        nav = nav.toPlainString(), accNav = accNav?.toPlainString(),
        createdAt = createdAt
    )

    private fun RoomValuationSnapshot.toModel() = ValuationSnapshot(
        id = id, fundCode = fundCode, snapshotTime = snapshotTime,
        estimatedNav = BigDecimal(estimatedNav), estimatedGrowthRate = BigDecimal(estimatedGrowthRate),
        weightedPe = weightedPe?.toBigDecimalOrNull(), weightedPb = weightedPb?.toBigDecimalOrNull(),
        pePercentile = pePercentile?.toBigDecimalOrNull(), pbPercentile = pbPercentile?.toBigDecimalOrNull(),
        coverageRate = coverageRate?.toBigDecimalOrNull(), createdAt = createdAt
    )

    private fun ValuationSnapshot.toRoom() = RoomValuationSnapshot(
        id = id, fundCode = fundCode, snapshotTime = snapshotTime,
        estimatedNav = estimatedNav.toPlainString(), estimatedGrowthRate = estimatedGrowthRate.toPlainString(),
        weightedPe = weightedPe?.toPlainString(), weightedPb = weightedPb?.toPlainString(),
        pePercentile = pePercentile?.toPlainString(), pbPercentile = pbPercentile?.toPlainString(),
        coverageRate = coverageRate?.toPlainString(), createdAt = createdAt
    )
}
