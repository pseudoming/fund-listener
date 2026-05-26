import api from './index'

/**
 * 获取基金实时估值数据
 * @param {string} code 基金代码，如 '110022'
 * @returns {Promise<{
 *   code: string,
 *   name: string,
 *   navDate: string,
 *   nav: string,
 *   estimatedNav: string,
 *   estimatedGrowthRate: string,
 *   estimationTime: string
 * }>}
 */
export function fetchFundRealtime(code) {
  return api.get(`/funds/${code}/realtime`)
}

/**
 * 获取基金前十大重仓股
 * @param {string} code 基金代码
 */
export function fetchFundHoldings(code) {
  return api.get(`/funds/${code}/holdings`)
}

/**
 * 获取首页看板数据
 * @returns {Promise<DashboardResponse>}
 */
export function fetchDashboard() {
  return api.get('/dashboard')
}

/**
 * 获取基金估值规则
 * @param {string} code 基金代码
 */
export function getValuationRules(code) {
  return api.get(`/funds/${code}/valuation-rules`)
}

/**
 * 更新基金估值规则
 * @param {string} code 基金代码
 * @param {Array} rules 规则列表
 */
export function updateValuationRules(code, rules) {
  return api.put(`/funds/${code}/valuation-rules`, { rules })
}

/**
 * 获取基金历史走势
 * @param {string} code 基金代码
 */
export function fetchFundTrend(code) {
  return api.get(`/funds/${code}/trend`)
}

/**
 * 获取账户底层股票穿透分析
 */
export function fetchPenetration() {
  return api.get('/dashboard/penetration')
}

/**
 * 获取整个个人账户的历史收益走势与最大回撤
 */
export function fetchDashboardTrend() {
  return api.get('/dashboard/trend')
}
