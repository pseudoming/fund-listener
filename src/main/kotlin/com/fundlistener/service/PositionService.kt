package com.fundlistener.service

import com.fundlistener.model.*
import com.fundlistener.repository.FundRepository
import java.math.BigDecimal
import java.util.UUID

class PositionService(private val repository: FundRepository) {

    suspend fun getAllPositions(): List<PositionResponse> {
        return repository.getAllPositions().map { it.toResponse() }
    }

    suspend fun getPositionDetail(fundCode: String): PositionDetailResponse? {
        val position = repository.getPosition(fundCode) ?: return null
        val transactions = repository.getTransactionsByFund(fundCode)
        return PositionDetailResponse(
            position = position.toResponse(),
            transactions = transactions.map { it.toResponse() }
        )
    }

    suspend fun buy(fundCode: String, req: TradeRequest): PositionResponse {
        val now = System.currentTimeMillis()
        
        // 1. 记录交易
        val tx = FundTransaction(
            id = UUID.randomUUID().toString(),
            fundCode = fundCode,
            type = TransactionType.BUY,
            shares = BigDecimal(req.shares),
            nav = BigDecimal(req.nav),
            amount = BigDecimal(req.amount),
            fee = BigDecimal(req.fee),
            tradeDate = req.tradeDate,
            note = req.note,
            createdAt = now
        )
        repository.insertTransaction(tx)

        // 2. 更新持仓
        val existing = repository.getPosition(fundCode)
        val position = if (existing != null) {
            existing.copy(
                totalShares = existing.totalShares.add(tx.shares),
                totalCost = existing.totalCost.add(tx.amount),
                updatedAt = now
            )
        } else {
            requireNotNull(req.fundName) { "fundName is required for the first buy" }
            FundPosition(
                fundCode = fundCode,
                fundName = req.fundName,
                totalShares = tx.shares,
                totalCost = tx.amount,
                createdAt = now,
                updatedAt = now
            )
        }
        repository.upsertPosition(position)
        return position.toResponse()
    }

    suspend fun sell(fundCode: String, req: TradeRequest): PositionResponse {
        val now = System.currentTimeMillis()
        val existing = repository.getPosition(fundCode)
            ?: throw IllegalArgumentException("Position not found for fund $fundCode")

        val sellShares = BigDecimal(req.shares)
        if (existing.totalShares < sellShares) {
            throw IllegalArgumentException("Not enough shares to sell. Has ${existing.totalShares}, wants to sell $sellShares")
        }

        // 1. 记录交易
        val tx = FundTransaction(
            id = UUID.randomUUID().toString(),
            fundCode = fundCode,
            type = TransactionType.SELL,
            shares = sellShares,
            nav = BigDecimal(req.nav),
            amount = BigDecimal(req.amount),
            fee = BigDecimal(req.fee),
            tradeDate = req.tradeDate,
            note = req.note,
            createdAt = now
        )
        repository.insertTransaction(tx)

        // 2. 更新持仓
        // 卖出时成本按比例扣减：卖出成本 = 卖出份额 * 平均成本净值
        // 这样可以确保卖出后剩余份额的 avgCostNav 不变
        val costReduction = sellShares.multiply(existing.avgCostNav)
        val newTotalShares = existing.totalShares.subtract(sellShares)
        val newTotalCost = if (newTotalShares.compareTo(BigDecimal.ZERO) <= 0) {
            BigDecimal.ZERO // 全部卖出后成本归零
        } else {
            existing.totalCost.subtract(costReduction)
        }

        val position = existing.copy(
            totalShares = newTotalShares,
            totalCost = newTotalCost,
            updatedAt = now
        )
        repository.upsertPosition(position)
        return position.toResponse()
    }

    suspend fun deletePosition(fundCode: String) {
        repository.deletePosition(fundCode)
        // 需求规定：删除持仓时不删除交易记录，保留历史
    }

    private fun FundPosition.toResponse() = PositionResponse(
        fundCode = fundCode,
        fundName = fundName,
        totalShares = totalShares.toPlainString(),
        totalCost = totalCost.toPlainString(),
        avgCostNav = avgCostNav.toPlainString(),
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun FundTransaction.toResponse() = TransactionResponse(
        id = id,
        fundCode = fundCode,
        type = type.name,
        shares = shares.toPlainString(),
        nav = nav.toPlainString(),
        amount = amount.toPlainString(),
        fee = fee.toPlainString(),
        tradeDate = tradeDate,
        note = note,
        createdAt = createdAt
    )
}
