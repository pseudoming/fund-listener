package com.fundlistener.client

import com.fundlistener.model.TianTianFundRaw
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * 天天基金数据抓取客户端
 *
 * 接口: http://fundgz.1234567.com.cn/js/{code}.js
 * 响应格式: JSONP — jsonpgz({...});
 * 需要剥离 JSONP 包装后解析 JSON
 */
class TianTianFundClient(private val httpClient: HttpClient) {

    private val logger = LoggerFactory.getLogger(TianTianFundClient::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    companion object {
        private const val BASE_URL = "http://fundgz.1234567.com.cn/js"
        private val JSONP_REGEX = Regex("""^jsonpgz\((.*)\);?\s*$""")
    }

    /**
     * 获取基金实时估值数据
     * @param code 基金代码，如 "110022"
     * @return 解析后的原始数据 DTO，如果接口返回空或解析失败则返回 null
     */
    suspend fun fetchEstimation(code: String): TianTianFundRaw? {
        return try {
            val response: HttpResponse = httpClient.get("$BASE_URL/$code.js") {
                headers {
                    // 模拟浏览器请求，降低被拦截概率
                    append("Referer", "http://fund.eastmoney.com/")
                    append("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                }
            }
            // 天天基金返回畸形 Content-Type: "charset=UTF-8,gbk"
            // Ktor bodyAsText() 会抛 BadContentTypeFormatException，改用 readRawBytes 绕过
            val body = response.readRawBytes().toString(Charsets.UTF_8)
            parseJsonp(body)
        } catch (e: Exception) {
            logger.error("Failed to fetch estimation for fund $code", e)
            null
        }
    }

    /**
     * 剥离 JSONP 包装，解析内层 JSON
     * 输入: jsonpgz({"fundcode":"110022",...});
     * 输出: TianTianFundRaw
     */
    internal fun parseJsonp(raw: String): TianTianFundRaw? {
        val match = JSONP_REGEX.find(raw.trim())
        if (match == null) {
            logger.warn("JSONP pattern not matched, raw response: {}", raw.take(200))
            return null
        }
        val jsonStr = match.groupValues[1]
        return try {
            json.decodeFromString<TianTianFundRaw>(jsonStr)
        } catch (e: Exception) {
            logger.error("Failed to parse fund JSON: {}", jsonStr.take(200), e)
            null
        }
    }
    /**
     * 获取基金前十大重仓股
     * @param code 基金代码
     */
    suspend fun fetchHoldings(code: String): com.fundlistener.model.FundHoldingsResponse? {
        return try {
            val url = "http://fundf10.eastmoney.com/FundArchivesDatas.aspx?type=jjcc&code=$code&topline=10&year=&month=&rt=0.8"
            val response: HttpResponse = httpClient.get(url) {
                headers {
                    append("Referer", "http://fundf10.eastmoney.com/")
                    append("User-Agent", "Mozilla/5.0")
                }
            }
            val body = response.readRawBytes().toString(Charsets.UTF_8)
            
            // Extract the HTML part inside var apidata={ content:"..."
            val startIdx = body.indexOf("content:\"")
            val endIdx = body.lastIndexOf("\",arryear")
            if (startIdx == -1 || endIdx == -1 || endIdx <= startIdx + 9) {
                logger.warn("Failed to extract HTML fragment from response")
                return null
            }
            val htmlFragment = body.substring(startIdx + 9, endIdx)

            val document = org.jsoup.Jsoup.parse(htmlFragment)
            
            // Extract report date
            val reportDateStr = document.select("label.right font.px12").text()
            
            // Extract holdings
            val holdings = document.select("table.w782 tbody tr").mapNotNull { row ->
                val cols = row.select("td")
                if (cols.size < 7) return@mapNotNull null
                
                // Column 2: code inside <a> (like <a ...>600519</a> or APA)
                val stockCode = cols[1].select("a").text().trim()
                // Column 3: name
                val stockName = cols[2].select("a").text().trim()
                // Column 7: ratio (e.g. 9.90%)
                val ratioStr = cols[6].text().replace("%", "").trim()
                
                if (stockCode.isNotEmpty() && stockName.isNotEmpty()) {
                    com.fundlistener.model.FundHolding(stockCode, stockName, ratioStr)
                } else {
                    null
                }
            }
            
            com.fundlistener.model.FundHoldingsResponse(code, reportDateStr, holdings)
        } catch (e: Exception) {
            logger.error("Failed to fetch holdings for fund $code", e)
            null
        }
    }
}
