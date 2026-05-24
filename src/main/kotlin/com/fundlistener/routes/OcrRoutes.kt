package com.fundlistener.routes

import com.fundlistener.service.ocr.OcrService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.ocrRoutes() {
    val ocrService by inject<OcrService>()

    route("/api/ocr") {
        /**
         * POST /api/ocr/parse
         *
         * 上传一张支付宝持仓截图，返回提取的结构化持仓信息。
         *
         * Content-Type: image/jpeg, image/png, 或 application/octet-stream
         * Body: 图片二进制数据
         *
         * Query params:
         *   - hint: 可选，基金代码提示
         *
         * 响应：OcrResult JSON
         */
        post("/parse") {
            val hint = call.request.queryParameters["hint"]

            val imageBytes = try {
                call.receive<ByteArray>()
            } catch (_: Exception) {
                null
            }

            if (imageBytes == null || imageBytes.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No image data provided"))
                return@post
            }

            val result = ocrService.recognize(imageBytes, hint)
            call.respond(result)
        }

        /**
         * GET /api/ocr/rules
         *
         * 返回当前配置的 OCR 提取规则，供前端调试/展示。
         */
        get("/rules") {
            call.respond(
                mapOf(
                    "description" to "支付宝基金持仓截图提取规则（AlipayFundRules.DEFAULT）",
                    "fields" to listOf(
                        mapOf("field" to "fundName", "description" to "基金名称"),
                        mapOf("field" to "fundCode", "description" to "基金代码（6位数字）"),
                        mapOf("field" to "shares", "description" to "持有份额"),
                        mapOf("field" to "cost", "description" to "持仓成本")
                    )
                )
            )
        }
    }
}
