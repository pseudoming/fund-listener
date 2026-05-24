import api from './index'

/**
 * 上传支付宝持仓截图，获取 OCR 解析结果。
 * @param {File|Blob} imageFile 图片文件
 * @param {string} hint 可选基金代码提示
 * @returns {Promise<OcrResult>}
 */
export function parseOcrImage(imageFile, tradeDate, hint) {
  const params = {}
  if (hint) params.hint = hint
  if (tradeDate) params.tradeDate = tradeDate
  return api.post('/ocr/parse', imageFile, {
    headers: { 'Content-Type': imageFile.type || 'image/png' },
    params
  })
}
