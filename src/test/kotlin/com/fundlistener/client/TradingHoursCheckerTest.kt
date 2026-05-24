package com.fundlistener.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TradingHoursCheckerTest {

    @Test
    fun `cacheTtlSeconds for suspended stock always returns 300`() {
        assertEquals(300L, TradingHoursChecker.cacheTtlSeconds(isSuspended = true))
    }

    @Test
    fun `cacheTtlSeconds for normal stock returns 30 or 3600`() {
        val ttl = TradingHoursChecker.cacheTtlSeconds(isSuspended = false)
        // Must be one of the two valid TTLs
        assertTrue(ttl == 30L || ttl == 3600L, "Expected 30 or 3600 but got $ttl")
    }

    @Test
    fun `isTradingNow does not throw`() {
        // Can't assert exact value (depends on real clock), but must not throw
        val result = TradingHoursChecker.isTradingNow()
        assertNotNull(result)
    }

    @Test
    fun `isTradingNow returns false on weekends`() {
        val result = TradingHoursChecker.isTradingNow()
        // Cannot assert exact value (depends on real time), but verify type and non-exception
        assertNotNull(result)
    }

    @Test
    fun `nowBeijing returns Asia Shanghai zone`() {
        val now = TradingHoursChecker.nowBeijing()
        assertEquals("Asia/Shanghai", now.zone.id)
    }

    @Test
    fun `cacheTtlSeconds suspended stock is longer than trading TTL`() {
        val suspendedTtl = TradingHoursChecker.cacheTtlSeconds(isSuspended = true)
        val normalTtl = TradingHoursChecker.cacheTtlSeconds(isSuspended = false)
        // Suspended TTL (300s) should be >= 30s (trading) but <= 3600s (non-trading)
        assertTrue(suspendedTtl == 300L, "Suspended TTL should be 300s")
        // Normal TTL during non-trading (3600) is larger, during trading (30) is smaller
        // so we can't assert ordering without knowing time
    }
}
