package com.fundlistener.model

/**
 * 股票市场类型，用于行情接口分发。
 *
 * 分类规则：
 *   - 含字母 → US_STOCK  (NVDA, AAPL, GOOGL 等)
 *   - 纯数字 5 位 → HK_STOCK  (00700, 01398 等)
 *   - 纯数字 6 位 → A_SHARE   (600519, 000333 等)
 *
 * 东方财富 push2 行情 API secid 前缀:
 *   0   = 上海 A 股
 *   1   = 深圳 A 股
 *   116 = 港股
 *   105 = 美股
 */
enum class MarketType(private val label: String) {
    A_SHARE("A股"),
    HK_STOCK("港股"),
    US_STOCK("美股"),
    TW_STOCK("台股");

    companion object {
        /**
         * 根据股票代码判断所属市场。
         *
         * @throws IllegalArgumentException 当代码格式无法识别时
         */
        fun classify(code: String): MarketType {
            val trimmed = code.trim()
            require(trimmed.isNotEmpty()) { "Stock code must not be empty" }

            return when {
                // 含字母 → 美股
                trimmed.any { it.isLetter() } -> US_STOCK
                // 纯数字 4 位 → 台股
                trimmed.length == 4 && trimmed.all { it.isDigit() } -> TW_STOCK
                // 纯数字 5 位 → 港股
                trimmed.length == 5 && trimmed.all { it.isDigit() } -> HK_STOCK
                // 纯数字 6 位 → A 股
                trimmed.length == 6 && trimmed.all { it.isDigit() } -> A_SHARE
                else -> throw IllegalArgumentException("Cannot classify stock code: $code")
            }
        }

        /**
         * 构建东方财富 push2 API 的 secid。
         *
         * A 股进一步区分沪市/深市:
         *   - 6xxxxx / 688xxx → 沪市 (0)
         *   - 0xxxxx / 2xxxxx / 3xxxxx → 深市 (1)
         */
        fun toSecId(code: String): String {
            val type = classify(code)
            return when (type) {
                A_SHARE -> {
                    val prefix = when (code.first()) {
                        '6' -> "0"  // 沪市
                        else -> "1"  // 深市 (0/2/3 开头)
                    }
                    "$prefix.$code"
                }
                HK_STOCK -> "116.$code"
                US_STOCK -> "105.$code"
                TW_STOCK -> "UNKNOWN.$code" // 暂不支持台股实时行情获取
            }
        }
    }

    override fun toString(): String = label
}
