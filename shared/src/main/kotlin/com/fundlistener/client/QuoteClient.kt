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
            val changePercent = fields[31].toBigDecimalOrNull() ?: BigDecimal.ZERO

            val quote = Quote(
                code = code,
                name = name,
                price = price,
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
        val trimmed = targetCode.trim().lowercase()
        if (trimmed.startsWith("sh") || trimmed.startsWith("sz")) {
            return trimmed
        }
        return if (componentType.equals("INDEX", ignoreCase = true)) {
            when {
                trimmed.startsWith("399") -> "sz$trimmed"
                trimmed.startsWith("000") || trimmed.startsWith("00") -> "sh$trimmed"
                else -> "sh$trimmed"
            }
        } else {
            when {
                trimmed.startsWith("6") -> "sh$trimmed"
                trimmed.startsWith("0") || trimmed.startsWith("3") -> "sz$trimmed"
                else -> "sh$trimmed"
            }
        }
    }
}
