package com.fundlistener.service.ocr

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * 云端 OCR 服务实现 — 调用通用 REST OCR API 识别图片文字。
 *
 * 通过构造函数注入 API 配置，支持任意兼容 REST 接口的 OCR 服务商：
 *   - 百度 OCR：https://aip.baidubce.com/rest/2.0/ocr/v1/accurate_basic
 *   - 腾讯 OCR：https://ocr.tencentcloudapi.com/
 *   - 阿里 OCR：https://ocr-api.cn-hangzhou.aliyuncs.com/
 *
 * 默认使用通用文字识别（general_text）模式，返回纯文本后交给 [OcrParser] 提取字段。
 *
 * 替换方式（Koin DI）:
 *   // 开发期
 *   single<OcrService> { MockOcrService() }
 *   // 生产期（云 OCR）
 *   single<OcrService> { CloudOcrService(get(), CloudOcrConfig(...)) }
 *   // Android 生产期（ML Kit）
 *   single<OcrService> { MlKitOcrService(context) }
 */
class CloudOcrService(
    private val httpClient: HttpClient,
    private val config: CloudOcrConfig,
    private val parser: OcrParser = OcrParser()
) : OcrService {

    private val logger = LoggerFactory.getLogger(CloudOcrService::class.java)

    override suspend fun recognize(imageBytes: ByteArray, hint: String?): OcrResult {
        logger.info("CloudOcrService: sending image ({} bytes) to {}", imageBytes.size, config.endpoint)

        return try {
            val response: HttpResponse = httpClient.post(config.endpoint) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
                    if (config.apiSecret.isNotBlank()) {
                        append("X-Secret-Key", config.apiSecret)
                    }
                }
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("image", imageBytes, Headers.build {
                                append(HttpHeaders.ContentType, "image/png")
                                append(HttpHeaders.ContentDisposition, "filename=\"screenshot.png\"")
                            })
                            append("language_type", "CHN_ENG")
                            config.extraParams.forEach { (k, v) -> append(k, v) }
                        }
                    )
                )
            }

            val body = response.readRawBytes().toString(Charsets.UTF_8)
            val rawText = extractTextFromResponse(body)
            val fields = parser.parse(rawText)

            OcrResult(rawText = rawText, extractedFields = fields, confidence = estimateConfidence(fields))
        } catch (e: Exception) {
            logger.error("CloudOcrService: API call failed", e)
            OcrResult(rawText = "", extractedFields = emptyList(), confidence = 0.0)
        }
    }

    /**
     * 从云 OCR API 的 JSON 响应中提取纯文本。
     *
     * 默认逻辑适配百度 OCR 响应格式：
     *   { "words_result": [{"words": "xxx"}, ...], "words_result_num": N }
     *
     * 如需适配其他服务商，重写此方法或通过 [config.responseTextField] 指定 JSON 路径。
     */
    private fun extractTextFromResponse(jsonBody: String): String {
        return try {
            val json = Json.parseToJsonElement(jsonBody).jsonObject
            // 尝试多个常见的响应字段
            val words = json["words_result"]?.jsonArray
                ?.mapNotNull { it.jsonObject["words"]?.jsonPrimitive?.content }
                ?.joinToString("\n")
            if (!words.isNullOrBlank()) return words

            json["text"]?.jsonPrimitive?.content
                ?: json["data"]?.jsonObject?.get("text")?.jsonPrimitive?.content
                ?: jsonBody.take(500)
        } catch (_: Exception) {
            jsonBody.take(500)
        }
    }

    private fun estimateConfidence(fields: List<ExtractedField>): Double {
        if (fields.isEmpty()) return 0.0
        return fields.map { it.confidence }.average()
    }
}

/**
 * 云 OCR 服务配置 — 所有参数外部注入，不硬编码。
 */
data class CloudOcrConfig(
    /** OCR API 端点 URL */
    val endpoint: String,
    /** API Key / Access Token */
    val apiKey: String = "",
    /** API Secret（部分服务商需要） */
    val apiSecret: String = "",
    /** 额外请求参数（如百度 OCR 的 access_token） */
    val extraParams: Map<String, String> = emptyMap()
)
