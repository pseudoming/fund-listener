package com.fundlistener.service.ocr

import org.slf4j.LoggerFactory

/**
 * Mock OCR 实现 — 开发期使用，无需云 API Key。
 *
 * 接受一个可选 hint 参数（基金代码），返回模拟的支付宝持仓截图解析结果。
 * 当 hint 有效时构造对应基金的模拟数据；无 hint 时返回通用模板。
 *
 * Mock 数据格式模拟支付宝持仓页常见布局：
 *   基金名称  易方达消费行业股票
 *   基金代码  110022
 *   持有份额  1,234.56 份
 *   持仓成本  ¥5,000.00
 */
class MockOcrService(private val parser: OcrParser = OcrParser()) : OcrService {

    private val logger = LoggerFactory.getLogger(MockOcrService::class.java)

    private val mockFunds = mapOf(
        "110022" to Triple("易方达消费行业股票", "1,234.56", "5,000.00"),
        "161725" to Triple("招商中证白酒指数(LOF)A", "888.88", "3,200.00"),
        "003834" to Triple("华夏能源革新股票A", "1,500.00", "6,800.00"),
        "000041" to Triple("华夏全球精选股票(QDII)", "456.78", "2,100.00")
    )

    override suspend fun recognize(imageBytes: ByteArray, hint: String?): OcrResult {
        logger.info("MockOcrService: processing image ({} bytes), hint={}", imageBytes.size, hint)

        val rawText = if (hint != null && hint in mockFunds) {
            val (name, shares, cost) = mockFunds[hint]!!
            """
                基金名称: $name
                基金代码: $hint
                持有份额: $shares 份
                持仓成本: ¥$cost
                最新净值: 2.9930
                持有收益: +350.00
            """.trimIndent()
        } else {
            """
                基金名称: 未知基金
                基金代码: 000000
                持有份额: 0.00 份
                持仓成本: ¥0.00
            """.trimIndent()
        }

        val fields = parser.parse(rawText)
        return OcrResult(rawText = rawText, extractedFields = fields, confidence = if (hint in mockFunds) 0.95 else 0.1)
    }
}
