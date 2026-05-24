package com.fundlistener.service.ocr

/**
 * OCR 字段提取规则 — 声明式、可配置，不硬编码在业务逻辑里。
 *
 * 每条规则定义：
 *   - fieldName:  输出的字段名
 *   - patterns:   正则表达式列表，按顺序尝试匹配
 *   - postProcess: 可选的值转换函数名，如 "trim", "stripChinese"
 *
 * 支持从支付宝基金持仓截图提取：
 *   fundName  - 基金名称
 *   fundCode  - 基金代码（6 位数字）
 *   shares    - 持有份额
 *   cost      - 持仓成本
 */
data class ExtractionRule(
    val fieldName: String,
    val patterns: List<String>,
    val postProcess: String? = null
)

/**
 * 支付宝基金持仓截图的默认提取规则。
 *
 * 参考 hzm0321/real-time-fund 的 VLM Prompt 设计，
 * 规则集基于支付宝 App 常见持仓页布局编写：
 *   - "基金名称" 或 "产品名称" 后跟中文名称
 *   - "基金代码" 或 "代码" 后跟 6 位数字
 *   - "持有份额" 后跟数字 + "份"
 *   - "持仓成本" 或持仓金额区域通常有 "¥" + 数字
 */
object AlipayFundRules {

    val DEFAULT = listOf(
        ExtractionRule(
            fieldName = "fundName",
            patterns = listOf(
                """基金名称[：:]\s*([\u4e00-\u9fa5\u3400-\u4dbf（）\(\)A-Z]{2,30})""",
                """产品名称[：:]\s*([\u4e00-\u9fa5\u3400-\u4dbf（）\(\)A-Z]{2,30})""",
                """([\u4e00-\u9fa5]{2,6}(?:型|类|混合|债券|指数|货币|灵活|FOF).{0,10})"""
            ),
            postProcess = "trim"
        ),
        ExtractionRule(
            fieldName = "fundCode",
            patterns = listOf(
                """(?:基金)?代码[：:]\s*(\d{6})""",
                """(\d{6})\s*(?:基金代码|代码)""",
                """\b(\d{6})\b"""
            ),
            postProcess = "trim"
        ),
        ExtractionRule(
            fieldName = "shares",
            patterns = listOf(
                """(?:持有)?份额[：:]\s*([\d,]+\.?\d*)\s*份?""",
                """持有\s*([\d,]+\.?\d*)\s*份""",
                """份额[：:]\s*([\d,]+\.?\d*)"""
            ),
            postProcess = "stripComma"
        ),
        ExtractionRule(
            fieldName = "cost",
            patterns = listOf(
                """(?:持仓)?成本[：:]\s*[¥￥]?\s*([\d,]+\.?\d*)""",
                """持仓金额[：:]\s*[¥￥]?\s*([\d,]+\.?\d*)""",
                """[¥￥]\s*([\d,]+\.?\d{2})""",
                """成本[：:]\s*([\d,]+\.?\d*)"""
            ),
            postProcess = "stripComma"
        )
    )
}
