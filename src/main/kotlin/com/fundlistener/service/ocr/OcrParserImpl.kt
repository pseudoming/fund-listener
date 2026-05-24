package com.fundlistener.service.ocr

import org.slf4j.LoggerFactory

/**
 * OCR 文本解析器 — 根据 [ExtractionRule] 配置从原始文本提取结构化字段。
 *
 * 核心设计：字段提取规则通过 [rules] 参数注入，不硬编码在解析逻辑中。
 * 更换截图来源（如更换到天天基金而非支付宝）只需替换规则集。
 */
class OcrParser(private val rules: List<ExtractionRule> = AlipayFundRules.DEFAULT) {

    private val logger = LoggerFactory.getLogger(OcrParser::class.java)

    /**
     * 从原始 OCR 文本中根据规则提取字段。
     */
    fun parse(rawText: String): List<ExtractedField> {
        val results = mutableListOf<ExtractedField>()

        for (rule in rules) {
            val extracted = tryPatterns(rule, rawText)
            if (extracted != null) {
                logger.info("OcrParser: extracted {} = '{}' via {}", rule.fieldName, extracted, rule.patterns.first().take(60))
                results.add(
                    ExtractedField(
                        name = rule.fieldName,
                        value = postProcess(extracted, rule.postProcess),
                        confidence = 0.9,
                        source = "pattern: ${rule.fieldName}"
                    )
                )
            } else {
                logger.warn("OcrParser: no match for field {}", rule.fieldName)
            }
        }

        return results
    }

    private fun tryPatterns(rule: ExtractionRule, text: String): String? {
        for (pattern in rule.patterns) {
            try {
                val regex = Regex(pattern)
                val match = regex.find(text)
                if (match != null && match.groupValues.size > 1) {
                    val value = match.groupValues[1].trim()
                    if (value.isNotEmpty()) return value
                }
            } catch (e: Exception) {
                logger.warn("OcrParser: invalid regex pattern '{}'", pattern.take(40), e)
            }
        }
        return null
    }

    private fun postProcess(value: String, process: String?): String {
        return when (process) {
            "trim" -> value.trim()
            "stripComma" -> value.replace(",", "").trim()
            "stripChinese" -> value.replace(Regex("[\u4e00-\u9fa5]"), "").trim()
            else -> value
        }
    }
}
