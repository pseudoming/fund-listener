package com.fundlistener.service.ocr

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OcrMathEstimationTest {

    // 模拟真实的后台逆向恢复逻辑
    private fun estimateHoldingReturn(amount: Double, parsedPct: Double?, parsedHoldingReturn: Double): Double {
        var holdingReturn = parsedHoldingReturn
        if (parsedPct != null && holdingReturn == 0.0 && parsedPct != 0.0) {
            holdingReturn = amount - amount / (1.0 + parsedPct / 100.0)
        }
        return holdingReturn
    }

    @Test
    fun testPositiveReturnEstimation() {
        // 支付宝截图实例: 易方达恒生红利低波ETF联接A
        // 最新市值 (amount): 14787.44
        // 真实持有收益 (holdingReturn): 3056.96
        // 收益率 (parsedPct): +26.06%
        val amount = 14787.44
        val parsedPct = 26.06
        val parsedHoldingReturn = 0.0 // 模拟OCR没有识别出该值，或者识别为0

        val estimatedReturn = estimateHoldingReturn(amount, parsedPct, parsedHoldingReturn)
        val expectedReturn = 3056.96

        // 验证误差范围在 0.01 元以内
        val diff = abs(estimatedReturn - expectedReturn)
        println("Positive estimation diff: $diff (Estimated: $estimatedReturn, Expected: $expectedReturn)")
        assertTrue(diff < 0.01, "Positive return math estimation error is too large")
    }

    @Test
    fun testNegativeReturnEstimation() {
        // 模拟亏损截图实例
        // 最新市值 (amount): 900.00
        // 收益率 (parsedPct): -10.00%
        // 原持有成本应该是: 1000.00 元
        // 真实持有收益 (holdingReturn) 应该是: -100.00 元
        val amount = 900.00
        val parsedPct = -10.00
        val parsedHoldingReturn = 0.0

        val estimatedReturn = estimateHoldingReturn(amount, parsedPct, parsedHoldingReturn)
        val expectedReturn = -100.00

        val diff = abs(estimatedReturn - expectedReturn)
        println("Negative estimation diff: $diff (Estimated: $estimatedReturn, Expected: $expectedReturn)")
        assertTrue(diff < 0.01, "Negative return math estimation error is too large")
    }

    @Test
    fun testZeroPercentEstimation() {
        // 收益率为 0.00%
        val amount = 1000.00
        val parsedPct = 0.00
        val parsedHoldingReturn = 0.0

        val estimatedReturn = estimateHoldingReturn(amount, parsedPct, parsedHoldingReturn)
        assertEquals(0.0, estimatedReturn, "Zero percent should return 0.0")
    }

    @Test
    fun testNormalFlowNoEstimation() {
        // 如果 OCR 已经成功识别到了收益额，不应当被覆盖
        val amount = 14787.44
        val parsedPct = 26.06
        val parsedHoldingReturn = 3056.96

        val estimatedReturn = estimateHoldingReturn(amount, parsedPct, parsedHoldingReturn)
        assertEquals(3056.96, estimatedReturn, "Should not overwrite if holdingReturn was already parsed")
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = IntArray(s2.length + 1) { it }
        for (i in 1..s1.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..s2.length) {
                val temp = dp[j]
                if (s1[i - 1] == s2[j - 1]) {
                    dp[j] = prev
                } else {
                    dp[j] = minOf(dp[j - 1], dp[j], prev) + 1
                }
                prev = temp
            }
        }
        return dp[s2.length]
    }

    @Test
    fun testLevenshteinDistance() {
        // 验证编辑距离算法的正确性
        assertEquals(0, levenshteinDistance("博时标普500ETF联接(QDII)A", "博时标普500ETF联接(QDII)A"))
        
        // 模拟识别出的“博时标普500ETF联接美元汇(QDII)A”和“博时标普500ETF联接(QDII)A”的编辑距离
        val dist1 = levenshteinDistance("博时标普500ETF联接(QDII)A", "博时标普500ETF联接(QDII)A")
        val dist2 = levenshteinDistance("博时标普500ETF联接(QDII)A", "博时标普500ETF联接美元汇(QDII)A")
        
        assertTrue(dist1 < dist2, "Exact match distance should be smaller than partial match with extra words")
        println("Distance to exact match: $dist1, Distance to partial match: $dist2")
    }

    @Test
    fun testFundMatchingResolution() {
        val ocrName = "博时标普500ETF联接(QDII)A"
        val candidates = listOf(
            "博时标普500ETF联接美元汇(QDII)A" to "013425",
            "博时标普500ETF联接A" to "050025"
        )
        
        // 使用更新后的匹配逻辑：先剔除括号及其内容再计算编辑距离
        val classLetter = "A"
        val normalizedOcr = ocrName.replace("（", "(").replace("）", ")").replace(" ", "")
        val cleanedOcr = normalizedOcr.replace(Regex("\\([^)]*\\)"), "") // 去掉括号内容
        
        val sorted = candidates.map { (name, code) ->
            val resNormalized = name.replace("（", "(").replace("）", ")").replace(" ", "")
            val cleanedCandidate = resNormalized.replace(Regex("\\([^)]*\\)"), "")
            
            val distance = levenshteinDistance(cleanedOcr, cleanedCandidate)
            Triple(code, name, distance)
        }.sortedBy { it.third }
        
        println("Sorted candidates for $ocrName:")
        sorted.forEach { println("Code: ${it.first}, Name: ${it.second}, Distance: ${it.third}") }
        
        assertEquals("050025", sorted.first().first, "Should match 050025 as the best candidate")
    }

    @Test
    fun testSharesAndCostCalculation() {
        // 模拟识别出的市值 amount = 10000.00 元
        // 模拟识别出的收益 holdingReturn = 1000.00 元
        // 模拟数据库查询出的基金最新净值 latestNav = 2.0000 元
        val amount = 10000.00
        val holdingReturn = 1000.00
        val latestNav = 2.0000

        // 核心计算逻辑：
        // 1. 份额 = 市值 / 净值
        val calculatedShares = if (latestNav > 0.0) amount / latestNav else 0.0
        // 2. 成本 = 市值 - 累计收益金额
        val costVal = amount - holdingReturn

        // 断言验证
        assertEquals(5000.0000, calculatedShares, 0.0001, "Shares calculation should be 5000.0")
        assertEquals(9000.00, costVal, 0.01, "Cost calculation should be 9000.0")
    }

    @Test
    fun testTieredNavLookup() {
        // Test resolution logic with fallbacks:
        // Mock values:
        val mockHistoryNav = 2.5000
        val mockSnapshotNav = 2.5500
        val fallbackNav = 1.0

        // Case 1: history is present -> use history
        var resolvedNav = mockHistoryNav
        assertEquals(2.5000, resolvedNav, 0.0001)

        // Case 2: history is null, snapshot is present -> use snapshot
        val historyNavNullable: Double? = null
        resolvedNav = historyNavNullable ?: mockSnapshotNav
        assertEquals(2.5500, resolvedNav, 0.0001)

        // Case 3: both are null -> use fallback 1.0
        val snapshotNavNullable: Double? = null
        resolvedNav = historyNavNullable ?: snapshotNavNullable ?: fallbackNav
        assertEquals(1.0, resolvedNav, 0.0001)
    }
}
