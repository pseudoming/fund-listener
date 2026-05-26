/**
 * 计算两个日期字符串之间的天数差 (Today - navDate)
 */
function getDiffDays(dateStr) {
  if (!dateStr) return 0
  try {
    const navDate = new Date(dateStr)
    const today = new Date()
    // 抹平具体时间差异，仅比较日期
    const navTime = new Date(navDate.getFullYear(), navDate.getMonth(), navDate.getDate()).getTime()
    const todayTime = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime()
    return Math.floor((todayTime - navTime) / (1000 * 3600 * 24))
  } catch (e) {
    return 0
  }
}

/**
 * 获取单只基金的收益状态机标签
 * @param {boolean} isSettled - 是否已出官方结算净值
 * @param {string} navDate - 净值所属日期 (YYYY-MM-DD)
 * @returns {string} - 标签文本
 */
export function getPnlLabel(isSettled, navDate) {
  const diff = getDiffDays(navDate)
  
  if (!isSettled) {
    if (diff === 0) return '今日估算'
    if (diff === 1) return '昨日估算'
    const shortDate = navDate.substring(5) // MM-DD
    return `估算(${shortDate})`
  } else {
    if (diff === 0) return '今日收益'
    if (diff === 1) return '昨日收益'
    const shortDate = navDate.substring(5)
    return `收益(${shortDate})`
  }
}

/**
 * 获取用于迷你标签的短状态文案，带有时间轴上下文
 * @param {boolean} isSettled - 是否已出官方结算净值
 * @param {string} navDate - 净值所属日期 (YYYY-MM-DD)
 * @returns {string} - 短标签文本
 */
export function getStatusTagLabel(isSettled, navDate) {
  const diff = getDiffDays(navDate)
  
  if (!isSettled) {
    if (diff === 0) return '今日估算'
    if (diff === 1) return '昨日估算'
    const shortDate = navDate.substring(5) // MM-DD
    return `估算 ${shortDate}`
  } else {
    if (diff === 0) return '今日已结'
    if (diff === 1) return '昨日已结'
    const shortDate = navDate.substring(5)
    return `已结 ${shortDate}`
  }
}

/**
 * 获取大盘总览的状态机标签（冒泡逻辑）
 * @param {Array} funds - 持仓基金列表
 * @returns {Object} - { pnlLabel: string, percentLabel: string }
 */
export function getDashboardLabels(funds) {
  if (!funds || funds.length === 0) {
    return { pnlLabel: '今日盈亏', percentLabel: '估值收益率' }
  }

  // 1. 判断大盘整体状态：是否所有持仓均已结算
  const isAllSettled = funds.every(f => f.isSettled)

  if (!isAllSettled) {
    // 只要有任何一只是估算中，大盘就是估算模式
    return { pnlLabel: '今日估算盈亏', percentLabel: '估算收益率' }
  }

  // 2. 如果全员结算，取最晚的一个结算日期作为大盘基准日期
  const maxDateStr = funds.map(f => f.navDate).reduce((a, b) => {
    if (!a) return b
    if (!b) return a
    return a > b ? a : b
  }, '')

  const diff = getDiffDays(maxDateStr)

  if (diff === 0) {
    return { pnlLabel: '今日盈亏', percentLabel: '今日收益率' }
  } else if (diff === 1) {
    return { pnlLabel: '昨日盈亏', percentLabel: '昨日收益率' }
  } else {
    const shortDate = maxDateStr ? maxDateStr.substring(5) : ''
    return { 
      pnlLabel: `最新盈亏(${shortDate})`, 
      percentLabel: '最新收益率' 
    }
  }
}
