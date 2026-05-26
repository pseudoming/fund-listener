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
        try {
            repository.addToWatchlist(fundCode)
        } catch (_: Exception) {}
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

        // 2. 更新或删除持仓
        // 卖出时成本按比例扣减：卖出成本 = 卖出份额 * 平均成本净值
        // 这样可以确保卖出后剩余份额的 avgCostNav 不变
        val costReduction = sellShares.multiply(existing.avgCostNav)
        val newTotalShares = existing.totalShares.subtract(sellShares)
        
        if (newTotalShares.compareTo(BigDecimal.ZERO) <= 0) {
            // [FIX #3 背景说明]:
            // 之前全额卖出后，代码仅仅将 totalShares 置为 0 并保留记录，
            // 导致前端 Dashboard 拉取到 0 份额持仓后，会触发 "市值为0拦截" 的暴力错误边界。
            // 这里修改为：当份额 <= 0 时，判定为真实清仓，直接物理删除该持仓。
            // （交易流水已经通过 insertTransaction 保存，不会丢失历史对账记录）
            repository.deletePosition(fundCode)
            
            val position = existing.copy(
                totalShares = BigDecimal.ZERO,
                totalCost = BigDecimal.ZERO,
                updatedAt = now
            )
            return position.toResponse()
        } else {
            val newTotalCost = existing.totalCost.subtract(costReduction)
            val position = existing.copy(
                totalShares = newTotalShares,
                totalCost = newTotalCost,
                updatedAt = now
            )
            repository.upsertPosition(position)
            return position.toResponse()
        }
    }

    suspend fun deletePosition(fundCode: String) {
        // 清空该基金的所有交易记录以彻底移除持仓关联的历史
        val existingTxs = repository.getTransactionsByFund(fundCode)
        for (tx in existingTxs) {
            repository.deleteTransaction(tx.id)
        }
        repository.deletePosition(fundCode)
    }

    suspend fun reset(fundCode: String, req: TradeRequest): PositionResponse {
        val now = System.currentTimeMillis()
        
        // 1. 清空该基金的所有历史交易记录以避免干扰（覆盖模式）
        val existingTxs = repository.getTransactionsByFund(fundCode)
        for (tx in existingTxs) {
            repository.deleteTransaction(tx.id)
        }
        
        // 2. 清空并重建持仓
        repository.deletePosition(fundCode)
        
        val position = FundPosition(
            fundCode = fundCode,
            fundName = req.fundName ?: "未知基金",
            totalShares = BigDecimal(req.shares),
            totalCost = BigDecimal(req.amount),
            createdAt = now,
            updatedAt = now
        )
        repository.upsertPosition(position)
        try {
            repository.addToWatchlist(fundCode)
        } catch (_: Exception) {}
        
        // 3. 记录一笔初始导入交易
        val tx = FundTransaction(
            id = UUID.randomUUID().toString(),
            fundCode = fundCode,
            type = TransactionType.BUY,
            shares = BigDecimal(req.shares),
            nav = BigDecimal(req.nav),
            amount = BigDecimal(req.amount),
            fee = BigDecimal(req.fee),
            tradeDate = req.tradeDate,
            note = "OCR 覆盖导入初始化",
            createdAt = now
        )
        repository.insertTransaction(tx)
        
        return position.toResponse()
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
