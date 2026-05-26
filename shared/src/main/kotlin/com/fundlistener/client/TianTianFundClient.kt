package com.fundlistener.client

import com.fundlistener.model.TianTianFundRaw
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

/**
 * 天天基金数据抓取客户端
 *
 * 接口: http://fundgz.1234567.com.cn/js/{code}.js
 * 响应格式: JSONP — jsonpgz({...});
 * 需要剥离 JSONP 包装后解析 JSON
 */
open class TianTianFundClient(private val httpClient: HttpClient) {

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
    open suspend fun fetchEstimation(code: String): TianTianFundRaw? {
        return try {
            RetryPolicy.STANDARD.execute {
                val response: HttpResponse = httpClient.get("$BASE_URL/$code.js") {
                    headers {
                        append("Referer", "http://fund.eastmoney.com/")
                        append("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    }
                }
                val body = response.readRawBytes().toString(Charsets.UTF_8)
                parseJsonp(body)
            }
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
        val jsonStr = match.groupValues[1].trim()
        if (jsonStr.isEmpty()) {
            logger.warn("Extracted JSON string is empty")
            return null
        }
        return try {
            json.decodeFromString<TianTianFundRaw>(jsonStr)
        } catch (e: Exception) {
            logger.error("Failed to parse fund JSON: {}", jsonStr.take(200), e)
            null
        }
    }

    /**
     * 获取基金历史净值走势
     */
    open suspend fun fetchNavTrend(code: String): List<com.fundlistener.model.NavTrendItem>? {
        return try {
            val body = RetryPolicy.STANDARD.execute {
                val response: HttpResponse = httpClient.get("http://fund.eastmoney.com/pingzhongdata/$code.js") {
                    headers {
                        append("Referer", "http://fund.eastmoney.com/")
                        append("User-Agent", "Mozilla/5.0")
                    }
                }
                response.readRawBytes().toString(Charsets.UTF_8)
            } ?: return null
            
            val searchKey = "var Data_netWorthTrend ="
            val startIdx = body.indexOf(searchKey)
            if (startIdx == -1) return null
            val startJson = startIdx + searchKey.length
            val endIdx = body.indexOf(";", startJson)
            val jsonStr = if (endIdx == -1) body.substring(startJson).trim() else body.substring(startJson, endIdx).trim()
            
            json.decodeFromString<List<com.fundlistener.model.NavTrendItem>>(jsonStr)
        } catch (e: Exception) {
            logger.error("Failed to fetch/parse NavTrend for fund $code", e)
            null
        }
    }

    /**
     * 获取基金经理姓名列表
     */
    suspend fun fetchFundManager(code: String): String? {
        return try {
            val body = RetryPolicy.STANDARD.execute {
                val response: HttpResponse = httpClient.get("http://fund.eastmoney.com/pingzhongdata/$code.js") {
                    headers {
                        append("Referer", "http://fund.eastmoney.com/")
                        append("User-Agent", "Mozilla/5.0")
                    }
                }
                response.readRawBytes().toString(Charsets.UTF_8)
            } ?: return null
            
            val managerSearchKey = "var Data_currentFundManager ="
            val mStartIdx = body.indexOf(managerSearchKey)
            if (mStartIdx == -1) return null
            val mStartJson = mStartIdx + managerSearchKey.length
            val mEndIdx = body.indexOf(";", mStartJson)
            val managerBlock = if (mEndIdx == -1) body.substring(mStartJson).trim() else body.substring(mStartJson, mEndIdx).trim()
            
            val nameRegex = Regex("""\"name\"\:\"([^\"]+)\"""")
            val matches = nameRegex.findAll(managerBlock)
            val names = matches.map { it.groupValues[1] }.toList()
            if (names.isNotEmpty()) names.joinToString(", ") else null
        } catch (e: Exception) {
            logger.error("Failed to fetch managers for fund $code", e)
            null
        }
    }

    /**
     * 获取基金前十大重仓股
     * @param code 基金代码
     */
    suspend fun fetchHoldings(code: String): com.fundlistener.model.FundHoldingsResponse? {
        return try {
            RetryPolicy.STANDARD.execute {
                val response: HttpResponse = httpClient.get("https://fundmobapi.eastmoney.com/FundMNewApi/FundMNInverstPosition") {
                    parameter("FCODE", code)
                    parameter("deviceid", "Wap")
                    parameter("plat", "Wap")
                    parameter("product", "EFund")
                    parameter("version", "2.0.0")
                    headers {
                        append("User-Agent", "Mozilla/5.0")
                    }
                }
                val body = response.readRawBytes().toString(Charsets.UTF_8)
                val parsed = json.decodeFromString<EastMoneyPositionResponse>(body)
                val stocks = parsed.Datas?.fundStocks?.map { 
                    com.fundlistener.model.FundHolding(
                        stockCode = it.GPDM,
                        stockName = it.GPJC,
                        ratio = it.JZBL
                    )
                }?.toMutableList() ?: mutableListOf()
                
                val isEtfLink = parsed.Datas?.ETFCODE != null && parsed.Datas?.ETFSHORTNAME != null
                if (stocks.isEmpty() && isEtfLink) {
                    stocks.add(
                        com.fundlistener.model.FundHolding(
                            stockCode = parsed.Datas!!.ETFCODE!!,
                            stockName = parsed.Datas.ETFSHORTNAME!!,
                            ratio = "95.00" // ETF联接基金通常90%~95%以上投资于目标ETF
                        )
                    )
                }

                if (stocks.isNotEmpty()) {
                    com.fundlistener.model.FundHoldingsResponse(
                        fundCode = code,
                        reportDate = parsed.Expansion ?: "",
                        holdings = stocks,
                        assetType = if (isEtfLink) "ETF_LINK" else "STOCK_FUND",
                        linkedEtfCode = parsed.Datas?.ETFCODE,
                        linkedEtfName = parsed.Datas?.ETFSHORTNAME
                    )
                } else null
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch holdings for fund $code", e)
            null
        }
    }

    /**
     * 获取全量基金基本信息列表用于模糊检索
     */
    suspend fun fetchAllFunds(): List<com.fundlistener.model.FundSearchResult>? {
        return try {
            val response: HttpResponse = httpClient.get("http://fund.eastmoney.com/js/fundcode_search.js") {
                headers {
                    append("User-Agent", "Mozilla/5.0")
                }
            }
            val body = response.readRawBytes().toString(Charsets.UTF_8)
            val startIdx = body.indexOf("[[")
            val endIdx = body.lastIndexOf("]]")
            if (startIdx == -1 || endIdx == -1 || endIdx <= startIdx) {
                logger.warn("fetchAllFunds: Failed to find array brackets in JS output")
                return null
            }
            val jsonStr = body.substring(startIdx, endIdx + 2)
            val rawList = json.decodeFromString<List<List<String>>>(jsonStr)
            rawList.mapNotNull { entry ->
                if (entry.size >= 5) {
                    com.fundlistener.model.FundSearchResult(
                        code = entry[0],
                        pinyinInitials = entry[1],
                        name = entry[2],
                        type = entry[3],
                        pinyinFull = entry[4]
                    )
                } else null
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch all funds from EastMoney JS", e)
            null
        }
    }
    /**
     * 调用天天基金搜索 API 进行模糊匹配
     */
    suspend fun searchLive(keyword: String): List<com.fundlistener.service.ocr.FundCandidate> {
        return try {
            RetryPolicy.STANDARD.execute {
                val response: HttpResponse = httpClient.get("http://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx") {
                    parameter("m", "1")
                    parameter("key", keyword)
                    headers {
                        append("User-Agent", "Mozilla/5.0")
                    }
                }
                val body = response.readRawBytes().toString(Charsets.UTF_8)
                val jsonElement = json.parseToJsonElement(body)
                val datas = jsonElement.jsonObject["Datas"]?.jsonArray
                
                datas?.mapNotNull { item ->
                    val obj = item.jsonObject
                    val code = obj["CODE"]?.jsonPrimitive?.content
                    val name = obj["NAME"]?.jsonPrimitive?.content
                    if (code != null && name != null) {
                        com.fundlistener.service.ocr.FundCandidate(code, name)
                    } else null
                } ?: emptyList()
            } ?: emptyList()
        } catch (e: Exception) {
            logger.error("Failed to live search for fund $keyword", e)
            emptyList()
        }
    }
}


@kotlinx.serialization.Serializable
data class EastMoneyPositionStock(
    val GPDM: String,
    val GPJC: String,
    val JZBL: String
)

@kotlinx.serialization.Serializable
data class EastMoneyPositionDatas(
    val fundStocks: List<EastMoneyPositionStock> = emptyList(),
    val ETFCODE: String? = null,
    val ETFSHORTNAME: String? = null
)

@kotlinx.serialization.Serializable
data class EastMoneyPositionResponse(
    val Datas: EastMoneyPositionDatas? = null,
    val Expansion: String? = null
)
