package com.fundlistener.service

import com.fundlistener.model.ValuationSnapshot
import org.slf4j.LoggerFactory

/**
 * 估值事件总线接口 — Phase 4.5 预埋，为 Phase 8 盘中预警推送预留扩展点。
 *
 * 当前实现: [LogOnlyValuationEventBus] — 仅日志输出。
 *
 * 后续扩展方式:
 *   1. 实现此接口，注入 Android Notification 或 WebSocket 推送逻辑
 *   2. Koin DI 中替换实现类（single<ValuationEventBus> { MyNotificationBus() }）
 *   3. [ValuationService] 调用方代码零修改
 *
 * 典型扩展场景:
 *   - PE 百分位 > 80% → 发 "高估预警" 通知
 *   - 加权涨跌幅 > 3% → 发 "盘中异动" 通知
 *   - 估值覆盖度 < 50% → 发 "数据不完整" 提醒
 */
interface ValuationEventBus {

    /**
     * 一条估值快照已写入 DB 后触发。
     *
     * @param snapshot 刚写入的估值快照（含 PE/PB 百分位）
     */
    fun onSnapshotWritten(snapshot: ValuationSnapshot)
}

/**
 * ValuationEventBus 的默认实现 — 仅输出结构化日志。
 *
 * 后续 Phase 8 替换为 [NotificationValuationEventBus] 等实现即可，
 * [ValuationService] 无需任何修改。
 */
class LogOnlyValuationEventBus : ValuationEventBus {

    private val logger = LoggerFactory.getLogger(LogOnlyValuationEventBus::class.java)

    override fun onSnapshotWritten(snapshot: ValuationSnapshot) {
        logger.info(
            "EVENT | fund={} | nav={} | growthRate={}% | " +
            "PE={} (P{}%) | PB={} (P{}%) | coverage={}%",
            snapshot.fundCode,
            snapshot.estimatedNav.toPlainString(),
            snapshot.estimatedGrowthRate.toPlainString(),
            snapshot.weightedPe?.toPlainString() ?: "-",
            snapshot.pePercentile?.toPlainString() ?: "-",
            snapshot.weightedPb?.toPlainString() ?: "-",
            snapshot.pbPercentile?.toPlainString() ?: "-",
            snapshot.coverageRate?.toPlainString() ?: "-"
        )
    }
}
