package com.fundlistener.client

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import org.slf4j.LoggerFactory
import java.math.BigDecimal

open class QuoteClient(private val httpClient: HttpClient) {
    private val logger = LoggerFactory.getLogger(QuoteClient::class.java)

    companion object {
        private const val BASE_URL = "http://qt.gtimg.cn/q="
    }

    data class Quote(
        val code: String,
        val name: String,
        val price: BigDecimal,
        val changeAmount: BigDecimal,
        val changePercent: BigDecimal
    )

    /**
     * 获取股票和指数的实时行情
     * @param targets 包含 (代码, 类型) 的列表，类型为 "STOCK" 或 "INDEX"
     */
    open suspend fun fetchQuotes(targets: List<Pair<String, String>>): Map<String, Quote> {
        if (targets.isEmpty()) return emptyMap()

        val formattedCodes = targets.map { (code, type) ->
            formatTencentCode(code, type)
        }

        val url = "$BASE_URL${formattedCodes.joinToString(",")}"

        return try {
            val response: HttpResponse = httpClient.get(url)
            val bytes = response.readRawBytes()
            val raw = String(bytes, charset("GBK"))
            parseQuotes(raw)
        } catch (e: Exception) {
            logger.error("Failed to fetch real-time quotes from Tencent: $url", e)
            emptyMap()
        }
    }

    internal fun parseQuotes(raw: String): Map<String, Quote> {
        val result = mutableMapOf<String, Quote>()
        val lines = raw.split("\n", ";").map { it.trim() }.filter { it.isNotEmpty() }
        for (line in lines) {
            if (!line.contains("=\"")) continue
            val parts = line.split("=\"")
            if (parts.size < 2) continue

            val keyPart = parts[0].trim() // v_sz000858
            val rawCode = keyPart.removePrefix("v_") // sz000858

            val dataPart = parts[1].removeSuffix("\"").trim()
            val fields = dataPart.split("~")
            if (fields.size < 32) {
                continue
            }

            val name = fields[1]
            val code = fields[2]
            val price = fields[3].toBigDecimalOrNull() ?: BigDecimal.ZERO
            val changeAmount = if (fields.size > 31) fields[31].toBigDecimalOrNull() ?: BigDecimal.ZERO else BigDecimal.ZERO
            val changePercent = if (fields.size > 32) fields[32].toBigDecimalOrNull() ?: BigDecimal.ZERO else BigDecimal.ZERO

            val quote = Quote(
                code = code,
                name = name,
                price = price,
                changeAmount = changeAmount,
                changePercent = changePercent
            )
            result[code] = quote
            result[rawCode] = quote
            result[rawCode.lowercase()] = quote
            result[rawCode.uppercase()] = quote
        }
        return result
    }

    fun formatTencentCode(targetCode: String, componentType: String): String {
        val trimmed = targetCode.trim()
        val lower = trimmed.lowercase()
        if (lower.startsWith("sh") || lower.startsWith("sz") || lower.startsWith("us") || lower.startsWith("hk")) {
            return trimmed
        }
        val market = try {
            com.fundlistener.model.MarketType.classify(trimmed)
        } catch (e: Exception) {
            com.fundlistener.model.MarketType.A_SHARE
        }
        return when (market) {
            com.fundlistener.model.MarketType.US_STOCK -> "us${trimmed.uppercase()}"
            com.fundlistener.model.MarketType.HK_STOCK -> "hk$trimmed"
            com.fundlistener.model.MarketType.TW_STOCK -> "tw$trimmed"
            com.fundlistener.model.MarketType.A_SHARE -> {
                when {
                    trimmed.startsWith("6") || trimmed.startsWith("5") -> "sh$trimmed"
                    trimmed.startsWith("0") || trimmed.startsWith("3") || trimmed.startsWith("1") -> "sz$trimmed"
                    else -> "sh$trimmed"
                }
            }
        }
    }
}
