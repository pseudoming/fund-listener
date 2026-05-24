package com.fundlistener.service.ocr

import kotlinx.serialization.Serializable

/**
 * OCR 识别原始结果 — 包含原始文本和提取的结构化字段。
 */
@Serializable
data class OcrResult(
    val rawText: String,
    val extractedFields: List<ExtractedField>,
    val confidence: Double = 0.0,
    val parsedFunds: List<ParsedFund> = emptyList()
) {
    /** 按字段名取第一个匹配值 */
    fun firstValue(fieldName: String): String? =
        extractedFields.firstOrNull { it.name == fieldName }?.value
}

@Serializable
data class ExtractedField(
    val name: String,         // 字段名：fundName, fundCode, shares, cost
    val value: String,        // 提取到的值
    val confidence: Double,   // 匹配置信度
    val source: String        // 匹配来源描述，如 "pattern: fundCode"
)

@Serializable
data class ParsedFund(
    val fundCode: String,
    val fundName: String,
    val amount: String,
    val holdingReturn: String,
    val holdingReturnPercent: String = "",
    val cost: String,
    val shares: String,
    val nav: String = "",
    val confidence: Double = 0.9,
    val isDuplicate: Boolean = false,
    val existingShares: String = "0.0",
    val existingCost: String = "0.0",
    val candidates: List<FundCandidate> = emptyList()
)

@Serializable
data class FundCandidate(
    val fundCode: String,
    val fundName: String
)
