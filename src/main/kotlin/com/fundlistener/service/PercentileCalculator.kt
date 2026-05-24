package com.fundlistener.service

import com.fundlistener.model.ValuationSnapshot
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 历史百分位计算器 — 纯函数，无 IO。
 *
 * 百分位定义：
 *   PE 百分位 = 历史 PE 低于当前 PE 的天数比例 × 100%
 *   例如 P90 = 当前 PE 高于历史 90% 的天数，表示估值偏高
 *
 * 降级策略：
 *   历史快照不足 [MIN_DAYS] 条时返回 PercentileResult.Degraded，
 *   前端应展示"数据积累中，需 xx 个交易日"提示。
 */
object PercentileCalculator {

    /** 最低历史数据天数阈值 */
    const val MIN_DAYS = 20

    private val SCALE = 2
    private val RM = RoundingMode.HALF_UP

    /**
     * 百分位计算结果。
     */
    sealed class PercentileResult {
        /** 数据充足 — 返回百分位值（0-100） */
        data class Valid(val percentile: BigDecimal) : PercentileResult()

        /** 数据不足 — 返回已有天数，前端应降级展示 */
        data class Degraded(val currentDays: Int, val requiredDays: Int = MIN_DAYS) : PercentileResult()

        /** 当前无 PE/PB 数据可计算 */
        data object NoValue : PercentileResult()
    }

    /**
     * 根据历史快照列表，计算当前 PE 的百分位。
     *
     * @param currentPE 当前加权 PE，null 表示无 PE 数据
     * @param history 历史快照列表（包含有效 PE 的快照），按时间 DESC 排列
     * @return 百分位计算结果
     */
    fun calcPEPercentile(currentPE: BigDecimal?, history: List<ValuationSnapshot>): PercentileResult {
        if (currentPE == null) return PercentileResult.NoValue
        return calcPercentile(currentPE, history.mapNotNull { it.weightedPe })
    }

    /**
     * 根据历史快照列表，计算当前 PB 的百分位。
     */
    fun calcPBPercentile(currentPB: BigDecimal?, history: List<ValuationSnapshot>): PercentileResult {
        if (currentPB == null) return PercentileResult.NoValue
        return calcPercentile(currentPB, history.mapNotNull { it.weightedPb })
    }

    /**
     * 通用百分位计算：
     *   percentile = count(historical < current) / total × 100
     */
    private fun calcPercentile(current: BigDecimal, history: List<BigDecimal>): PercentileResult {
        if (history.size < MIN_DAYS) {
            return PercentileResult.Degraded(history.size)
        }

        val lowerCount = history.count { it < current }
        val percentile = BigDecimal(lowerCount)
            .divide(BigDecimal(history.size), SCALE + 2, RM)
            .multiply(BigDecimal(100))
            .setScale(SCALE, RM)

        return PercentileResult.Valid(percentile)
    }
}
