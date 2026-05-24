package com.fundlistener.android.ocr

import android.graphics.BitmapFactory
import com.fundlistener.service.ocr.OcrParser
import com.fundlistener.service.ocr.OcrResult
import com.fundlistener.service.ocr.OcrService
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import org.slf4j.LoggerFactory
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit 离线 OCR 实现 — 替换云 OCR，完全离线运行。
 *
 * 使用 Google ML Kit Chinese Text Recognition，
 * 识别后交给 [OcrParser] 提取结构化字段。
 *
 * 特性：
 *   - 完全离线，无需网络
 *   - 支持中文（`ChineseTextRecognizerOptions`）
 *   - 首次使用会下载模型（约 20MB），由 Google Play Services 管理
 *
 * 替换方式：Koin DI 中一行代码切换
 *   single<OcrService> { MlKitOcrService() }
 */
class MlKitOcrService(private val parser: OcrParser = OcrParser()) : OcrService {

    private val logger = LoggerFactory.getLogger(MlKitOcrService::class.java)

    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    override suspend fun recognize(imageBytes: ByteArray, hint: String?, tradeDate: String?): OcrResult {
        logger.info("MlKitOcrService: processing image ({} bytes)", imageBytes.size)

        return try {
            // Decode bytes to Bitmap
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                ?: return OcrResult(rawText = "", extractedFields = emptyList(), confidence = 0.0)

            val inputImage = InputImage.fromBitmap(bitmap, 0)

            // ML Kit async → coroutine bridge
            val visionText = suspendCancellableCoroutine { cont ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { text ->
                        cont.resume(text)
                    }
                    .addOnFailureListener { e ->
                        cont.resumeWithException(e)
                    }
            }

            bitmap.recycle()

            val allLines = visionText.textBlocks.flatMap { it.lines }
            val rawText = if (allLines.isNotEmpty()) {
                val groupedRows = algoBIouOverlap(allLines, 0.3)
                groupedRows.joinToString("\n") { row ->
                    row.sortedBy { it.boundingBox?.left ?: 0 }.joinToString(" ") { it.text }
                }
            } else {
                visionText.text
            }

            if (rawText.isBlank()) {
                logger.warn("MlKitOcrService: no text recognized")
                return OcrResult(rawText = "", extractedFields = emptyList(), confidence = 0.0)
            }

            val fields = parser.parse(rawText)
            logger.info("MlKitOcrService: extracted {} field(s), text length={}", fields.size, rawText.length)

            OcrResult(
                rawText = rawText,
                extractedFields = fields,
                confidence = estimateConfidence(fields)
            )
        } catch (e: Exception) {
            logger.error("MlKitOcrService: recognition failed", e)
            OcrResult(rawText = "", extractedFields = emptyList(), confidence = 0.0)
        }
    }

    /**
     * Algorithm B: Y-Axis IoU 重叠度物理聚类算法 (Android ML Kit 版)
     * 计算 Y 轴重叠比例，将并排多列（如基金名称、持有份额、持仓成本）聚类归口到同一行文本中。
     */
    private fun algoBIouOverlap(
        lines: List<Text.Line>,
        overlapThreshold: Double
    ): List<List<Text.Line>> {
        if (lines.isEmpty()) return emptyList()

        val sortedLines = lines.sortedBy { line ->
            line.boundingBox?.top ?: 0
        }

        val rows = mutableListOf<MutableList<Text.Line>>()

        for (line in sortedLines) {
            val rect = line.boundingBox
            val yMin = rect?.top ?: 0
            val yMax = rect?.bottom ?: 0
            val h = (yMax - yMin).toDouble()

            var placed = false
            for (row in rows) {
                val rowYMin = row.minOf { r -> r.boundingBox?.top ?: 0 }
                val rowYMax = row.maxOf { r -> r.boundingBox?.bottom ?: 0 }
                val rowH = (rowYMax - rowYMin).toDouble()

                val interMin = maxOf(yMin, rowYMin)
                val interMax = minOf(yMax, rowYMax)
                val interH = maxOf(0, interMax - interMin).toDouble()

                val minH = minOf(h, rowH)
                if (minH > 0.0 && (interH / minH) >= overlapThreshold) {
                    row.add(line)
                    placed = true
                    break
                }
            }

            if (!placed) {
                rows.add(mutableListOf(line))
            }
        }

        return rows
    }

    private fun estimateConfidence(fields: List<com.fundlistener.service.ocr.ExtractedField>): Double {
        if (fields.isEmpty()) return 0.0
        return fields.map { it.confidence }.average()
    }
}
