package com.fundlistener.service.ocr

/**
 * OCR 服务接口 — 从图片中识别文字。
 *
 * JVM 开发期: [MockOcrService] 或 [CloudOcrService]
 * Android 生产期: ML Kit 实现（Phase 6 完成，替换 Koin 注入即可）
 */
interface OcrService {

    /**
     * 识别图片中的文字内容。
     *
     * @param imageBytes 图片二进制数据（JPEG/PNG）
     * @param hint 可选提示词（如基金代码），辅助提高识别精度
     * @return OCR 识别结果
     */
    suspend fun recognize(imageBytes: ByteArray, hint: String? = null): OcrResult
}
