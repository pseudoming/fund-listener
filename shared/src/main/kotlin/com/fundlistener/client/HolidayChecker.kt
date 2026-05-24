package com.fundlistener.client

import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.ZoneId

/**
 * 节假日判断 — 检测当天是否为中国 A 股节假日。
 *
 * 基于硬编码的节假日列表（覆盖 2025-2026 主要假期）：
 *   春节、清明节、劳动节、端午节、中秋节、国庆节
 *
 * 机制限制：此列表需每年手动更新。后续可改为：
 *   - 从东方财富交易日历接口动态获取
 *   - 由用户自行维护 holidays.txt
 *
 * 当前实现权衡：漏判（把节假日当交易日）只会导致 API 返回空数据并被标记为停牌，
 *   不会导致数据错误，因此可接受。
 */
object HolidayChecker {

    private val logger = LoggerFactory.getLogger(HolidayChecker::class.java)

    private val ZONE_CN = ZoneId.of("Asia/Shanghai")

    // 2025-2026 A股休市日期（周末以外的法定节假日 + 调休工作日）
    // 来源：上交所交易日历公告
    private val holidays: Set<LocalDate> by lazy {
        setOf(
            // 2026 春节: 2.16 - 2.20
            LocalDate.of(2026, 2, 16), LocalDate.of(2026, 2, 17),
            LocalDate.of(2026, 2, 18), LocalDate.of(2026, 2, 19),
            LocalDate.of(2026, 2, 20),
            // 2026 清明节: 4.5 (周末，不重复)
            // 2026 劳动节: 5.1 - 5.5
            LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 4),
            LocalDate.of(2026, 5, 5),
            // 2026 端午节: 6.19 (周五)
            LocalDate.of(2026, 6, 19),
            // 2026 中秋节: 9.25 (周五)
            LocalDate.of(2026, 9, 25),
            // 2026 国庆节: 10.1 - 10.7
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2),
            LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6),
            LocalDate.of(2026, 10, 7),
            // 2025 剩余节日（备用）
            LocalDate.of(2025, 6, 2),  // 端午节补休？2025.5.31-6.2
            LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 2),
            LocalDate.of(2025, 10, 3), LocalDate.of(2025, 10, 6),
            LocalDate.of(2025, 10, 7),
            LocalDate.of(2025, 12, 31),
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2)
        )
    }

    /**
     * 判断今天是否为 A 股节假日（含周末）。
     */
    fun isHoliday(): Boolean {
        val today = LocalDate.now(ZONE_CN)
        val dayOfWeek = today.dayOfWeek
        // 周末
        if (dayOfWeek == java.time.DayOfWeek.SATURDAY || dayOfWeek == java.time.DayOfWeek.SUNDAY) {
            return true
        }
        // 法定节假日
        return today in holidays
    }

    /**
     * 获取节假日原因描述，供前端展示。
     */
    fun holidayReason(): String? {
        val today = LocalDate.now(ZONE_CN)
        return when {
            today.dayOfWeek == java.time.DayOfWeek.SATURDAY || today.dayOfWeek == java.time.DayOfWeek.SUNDAY -> "周末休市"
            today in holidays -> "法定节假日休市"
            else -> null
        }
    }
}
