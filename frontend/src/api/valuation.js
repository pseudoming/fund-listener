import api from './index'

/**
 * 获取基金穿透估值（加权涨跌幅 + PE/PB + 覆盖度）
 * @param {string} code 基金代码
 */
export function fetchValuation(code) {
  return api.get(`/valuation/${code}`)
}

/**
 * 获取估值历史快照 + 百分位状态
 * @param {string} code 基金代码
 */
export function fetchValuationHistory(code) {
  return api.get(`/valuation/${code}/history`)
}
