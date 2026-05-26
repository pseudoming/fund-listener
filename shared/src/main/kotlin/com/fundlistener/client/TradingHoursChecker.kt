package com.fundlistener.client

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A 股交易时段判断工具。
 *
 * 用于驱动行情缓存 TTL 策略：
 *   - 交易时段：TTL 30s（盘中实时刷新）
 *   - 非交易时段：TTL 1h（盘后数据不变，减少无谓请求）
 *
 * A 股交易时间（北京时间 UTC+8）：
 *   上午 9:30 – 11:30
 *   下午 13:00 – 15:00
 *   周一至周五（不含法定节假日 — 节假日判断不做，成本过高）
 */
object TradingHoursChecker {

    private val ZONE_CN = ZoneId.of("Asia/Shanghai")

    /** 上午开盘 */
    private val MORNING_OPEN = LocalTime.of(9, 30)
    private val MORNING_CLOSE = LocalTime.of(11, 30)

    /** 下午开盘 */
    private val AFTERNOON_OPEN = LocalTime.of(13, 0)
    private val AFTERNOON_CLOSE = LocalTime.of(15, 0)

    /**
     * 判断当前是否处于 A 股交易时段（不含节假日判断）。
     */
    fun isTradingNow(): Boolean {
        val now = ZonedDateTime.now(ZONE_CN)
        val day = now.dayOfWeek
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return false

        val time = now.toLocalTime()
        return (time in MORNING_OPEN..MORNING_CLOSE) ||
               (time in AFTERNOON_OPEN..AFTERNOON_CLOSE)
    }

    /**
     * 判断当前是否处于基金当日净值发布时段（交易日 15:00 - 23:00）。
     */
    fun isNavUpdatingTime(): Boolean {
        val now = ZonedDateTime.now(ZONE_CN)
        val day = now.dayOfWeek
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return false

        val time = now.toLocalTime()
        return time.isAfter(LocalTime.of(15, 0)) && time.isBefore(LocalTime.of(23, 0))
    }

    /**
     * 返回当前适用的行情缓存 TTL（秒）。
     *
     * 规则：
     *   - A 股交易时段：30s
     *   - 净值发布时段：300s (5min)
     *   - 非交易时段：3600s (1h)
     *   - 停牌股：TTL 延长到 300s（5min），避免交易时段反复查询
     */
    fun cacheTtlSeconds(isSuspended: Boolean): Long {
        return when {
            isSuspended -> 300L
            isTradingNow() -> 30L
            isNavUpdatingTime() -> 300L
            else -> 3600L
        }
    }

    /**
     * 获取当前北京时间用于日志/调试。
     */
    fun nowBeijing(): ZonedDateTime = ZonedDateTime.now(ZONE_CN)

    /**
     * 获取最近一次 A 股收盘的时间戳 (毫秒)。
     * 用于判定 L2 缓存中的行情数据是否为“绝对新鲜”的收盘价。
     */
    fun getLastMarketCloseTimeMs(): Long {
        var dt = ZonedDateTime.now(ZONE_CN)
        while (true) {
            val day = dt.dayOfWeek
            val isTradingDay = day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY
            if (isTradingDay) {
                val closeTime = dt.with(AFTERNOON_CLOSE)
                if (!dt.isBefore(closeTime)) {
                    return closeTime.toInstant().toEpochMilli()
                }
            }
            dt = dt.minusDays(1).with(LocalTime.MAX)
        }
    }
}
