<template>
  <div class="fund-card-wrapper" style="display: contents;">

  
  <div v-if="loading && !data" class="card animate-in" :class="mode === 'detail' ? 'alipay-style-card' : 'fund-card'">
    <div class="fund-card__skeleton">
      <div class="skeleton" style="width: 60%; height: 18px;"></div>
      <div class="skeleton" style="width: 35%; height: 14px; margin-top: 8px;"></div>
      <div class="skeleton" style="width: 100%; height: 48px; margin-top: 16px;"></div>
      <div class="skeleton" style="width: 100%; height: 32px; margin-top: 12px;"></div>
    </div>
  </div>

  
  <div v-else-if="displayError" class="card animate-in fund-card--error" @click="$emit('retry')">
    <div class="fund-card__error-icon">⚠️</div>
    <div class="fund-card__error-text">{{ displayError }}</div>
    <div class="fund-card__error-hint">点击重试</div>
  </div>

  
  <template v-else-if="data || mode === 'detail'">
    
    <div v-if="mode === 'detail'" class="alipay-style-card card" v-show="hasPosition || isWatchlist">
      
      <template v-if="isWatchlist">
        <div class="alipay-card-label">最新净值 / {{ computedIsSettled ? '已结算' : '估值中' }} ({{ estimationTime || navDate }})</div>
        <div class="alipay-card-value font-mono">{{ currentNav || '--' }}</div>
        <div class="alipay-card-grid">
          <div class="alipay-grid-item">
            <div class="label">{{ computedIsSettled ? '实际涨跌幅' : '估算涨跌幅' }}</div>
            <div class="val font-mono" :class="rateClass(estimatedGrowthRate)">
              {{ formatWithSign(estimatedGrowthRate) }}%
            </div>
          </div>
          <div class="alipay-grid-item">
            <div class="label">关注以来涨幅</div>
            <div class="val font-mono" :class="rateClass(sinceAddedGrowthRate)">
              {{ formatWithSign(sinceAddedGrowthRate) }}%
            </div>
          </div>
        </div>
      </template>
      
      <!-- 持仓详情 -->
      <template v-else>
        <div class="alipay-card-label">持有金额(元)</div>
        <div class="alipay-card-value font-mono">{{ formatNum(currentMarketValue) }}</div>
        <div class="alipay-card-grid">
          <div class="alipay-grid-item">
            <div class="label">最新收益(元)</div>
            <div class="val font-mono" :class="rateClass(todayPnl)">
              {{ formatWithSign(todayPnl) }}
            </div>
          </div>
          <div class="alipay-grid-item">
            <div class="label">持有收益(元)</div>
            <div class="val font-mono" :class="rateClass(holdingPnl)">
              {{ formatWithSign(holdingPnl) }}
            </div>
          </div>
          <div class="alipay-grid-item">
            <div class="label">持有收益率</div>
            <div class="val font-mono" :class="rateClass(holdingPnl)">
              {{ holdingPnlRate }}
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- ==================== 持仓模式 (Position Style) ==================== -->
    <div v-else-if="mode === 'position'" class="card position-card animate-in" 
      :class="{ 'edit-mode': isEditMode, 'is-selected': isSelected }"
      @click="$emit('click')"
    >
      <!-- 复选框容器 -->
      <div class="pos-checkbox-container">
        <div class="pos-checkbox" :class="{ 'is-active': isSelected }"></div>
      </div>

      <span class="status-tag" :class="data.isSettled ? 'status-settled' : 'status-estimating'">
        {{ data.isSettled ? '已结算' : '估值中' }}
      </span>
      <div class="position-card__content">
        <div class="pos-header">
          <div class="pos-name-section">
            <span class="pos-name">{{ data.fundName || data.name }}</span>
            <div class="pos-code-wrap" @click.stop="copyText(data.fundCode || data.code)" title="点击复制基金代码">
              <span class="pos-code font-mono">{{ data.fundCode || data.code }}</span>
              <span class="copy-btn-mini">📋</span>
            </div>
          </div>
          <span class="pos-badge font-mono" :class="rateClass(data.estimatedGrowthRate)">
            {{ formatGrowthRate(data.estimatedGrowthRate) }}
          </span>
        </div>
        
        <div class="pos-grid">
          <div class="pos-col">
            <div class="label">持有市值</div>
            <div class="val val--primary font-mono">¥{{ formatMarketValue(data) }}</div>
          </div>
          <div class="pos-col">
            <div class="label">持有收益</div>
            <div class="val font-mono" :class="rateClass(getAccumulatedPnl(data))">
              {{ formatAccumulatedPnl(data) }}
            </div>
          </div>
          <div class="pos-col">
            <div class="label">{{ getPnlLabel(data) }}</div>
            <div class="val font-mono" :class="rateClass(data.todayPnl)">
              {{ formatTodayPnl(data) }}
            </div>
          </div>
        </div>

        <div class="pos-grid pos-grid--secondary">
          <div class="pos-col">
            <div class="label">持有份额</div>
            <div class="val font-mono">{{ formatShares(data.totalShares) }}</div>
          </div>
          <div class="pos-col">
            <div class="label">持仓成本</div>
            <div class="val font-mono">¥{{ formatCost(data.totalCost) }}</div>
          </div>
          <div class="pos-col">
            <div class="label">{{ data.isSettled ? '成本 / 最新净值' : '成本 / 估算净值' }}</div>
            <div class="val font-mono">{{ data.avgCostNav }} / {{ data.estimatedNav || '--' }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- ==================== 自选模式 (Watchlist Style) ==================== -->
    <div v-else-if="mode === 'watchlist'" class="fund-card card animate-in" :class="cardClass" @click="$emit('click')">
      <!-- 取消自选按钮 -->
      <button v-if="allowRemove" class="fund-card__remove-btn" @click.stop="$emit('remove')" title="取消自选">✕</button>
      
      <div class="fund-card__header" :class="{ 'has-remove-btn': allowRemove }">
        <div class="fund-card__info">
          <h2 class="fund-card__name">{{ data.name || data.fundName }}</h2>
          <div class="fund-card__code-wrap" @click.stop="copyText(data.code || data.fundCode)" title="点击复制基金代码">
            <span class="fund-card__code font-mono text-muted">{{ data.code || data.fundCode }}</span>
            <span class="copy-btn-mini">📋</span>
          </div>
        </div>
        <span class="tag" :class="tagClass">{{ trendIcon }} {{ directionLabel }}</span>
      </div>

      <div class="fund-card__body">
        <div class="fund-card__rate-section">
          <span class="fund-card__rate text-num" :class="rateClass(data.estimatedGrowthRate)">
            {{ formatGrowthRate(data.estimatedGrowthRate) }}
          </span>
          <span class="fund-card__rate-label text-muted">{{ computedIsSettled ? '实际涨跌幅' : '估算涨跌幅' }}</span>
        </div>

        <div class="fund-card__nav-row">
          <div class="fund-card__nav-item">
            <span class="fund-card__nav-value text-num" :class="rateClass(data.estimatedGrowthRate)">
              {{ data.estimatedNav }}
            </span>
            <span class="fund-card__nav-label text-muted">{{ computedIsSettled ? '最新净值' : '估算净值' }}</span>
          </div>
          <div class="fund-card__nav-divider"></div>
          <div class="fund-card__nav-item">
            <span class="fund-card__nav-value text-num">{{ data.nav }}</span>
            <span class="fund-card__nav-label text-muted">上期净值</span>
          </div>
          
          <template v-if="data.sinceAddedGrowthRate">
            <div class="fund-card__nav-divider"></div>
            <div class="fund-card__nav-item">
              <span class="fund-card__nav-value text-num" :class="rateClass(data.sinceAddedGrowthRate)">
                {{ formatGrowthRate(data.sinceAddedGrowthRate) }}
              </span>
              <span class="fund-card__nav-label text-muted" style="white-space: nowrap">关注以来({{ data.addedDate ? data.addedDate.substring(5) : '' }})</span>
            </div>
          </template>
        </div>
      </div>

      <div class="fund-card__footer">
        <span class="fund-card__time text-muted">{{ computedIsSettled ? '结算时间' : '估算时间' }} {{ data.estimationTime }}</span>
        <span v-if="refreshing" class="fund-card__refreshing">
          <span class="fund-card__dot"></span>刷新中
        </span>
      </div>

      <div class="fund-card__expand" @click.stop="$emit('toggle-holdings')">
        <span class="fund-card__expand-label">走势与重仓</span>
        <span class="fund-card__expand-chevron" :class="{ 'is-expanded': expanded }">▾</span>
      </div>
    </div>
  </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { showToast } from 'vant'

const props = defineProps({
  // General
  mode: { type: String, default: 'watchlist' }, // 'watchlist', 'position', 'detail'
  data: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  error: { type: String, default: null },
  
  // Watchlist specific
  refreshing: { type: Boolean, default: false },
  expanded: { type: Boolean, default: false },
  allowRemove: { type: Boolean, default: false },
  
  // Position specific
  isSelected: { type: Boolean, default: false },
  isEditMode: { type: Boolean, default: false },

  // Detail mode specific
  isWatchlist: { type: Boolean, default: false },
  hasPosition: { type: Boolean, default: false },
  currentMarketValue: { type: String, default: '0.00' },
  todayPnl: { type: String, default: '0.00' },
  holdingPnl: { type: String, default: '0.00' },
  holdingPnlRate: { type: String, default: '0.00%' },
  currentNav: { type: String, default: '' },
  estimatedGrowthRate: { type: String, default: '0.00' },
  sinceAddedGrowthRate: { type: String, default: '' },
  estimationTime: { type: String, default: '' },
  navDate: { type: String, default: '' }
})

const emit = defineEmits(['retry', 'toggle-holdings', 'click', 'remove'])

const computedIsSettled = computed(() => {
  if (props.data && typeof props.data.isSettled === 'boolean') {
    return props.data.isSettled
  }
  const nDate = props.navDate || props.data?.navDate
  const eTime = props.estimationTime || props.data?.estimationTime
  if (nDate && eTime) {
    return nDate === eTime.substring(0, 10)
  }
  return false
})

const displayError = computed(() => {
  if (props.error) return props.error
  if (props.mode === 'position' || (props.mode === 'detail' && props.hasPosition)) {
    if (props.mode === 'detail') {
      const val = parseFloat(props.currentMarketValue) || 0
      if (val === 0) return '市值为0拦截: 底层份额网关数据未同步或净值熔断(为0)'
    } else {
      const pos = props.data || {}
      const shares = parseFloat(pos.totalShares) || 0
      const nav = parseFloat(pos.estimatedNav || pos.nav) || 0
      if (shares * nav === 0) {
        const reason = shares === 0 ? '底层份额网关数据未同步(为0)' : 'API盘中估值/基础净值熔断(为0)'
        console.error(`[链路断点拦截] ${pos.fundCode || pos.code || '未知代码'} 持仓为0, 溯源 -> ${reason}`)
        return `市值为0拦截: ${reason}`
      }
    }
  }
  return null
})

// Common Utils
function copyText(text) {
  if (!text) return
  navigator.clipboard.writeText(text).then(() => {
    showToast(`基金代码 ${text} 已复制`)
  }).catch(() => {
    const input = document.createElement('input')
    input.setAttribute('value', text)
    document.body.appendChild(input)
    input.select()
    document.execCommand('copy')
    document.body.removeChild(input)
    showToast(`基金代码 ${text} 已复制`)
  })
}

const rateClass = (val) => {
  const n = parseFloat(val) || 0
  return n > 0 ? 'text-rise' : (n < 0 ? 'text-fall' : '')
}

const formatWithSign = (val) => {
  const n = parseFloat(val) || 0
  const prefix = n > 0 ? '+' : ''
  return `${prefix}${n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

const formatNum = (val) => {
  const n = parseFloat(val) || 0
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const formatGrowthRate = (val) => {
  if (!val) return '--'
  const n = parseFloat(val)
  if (isNaN(n)) return val
  const prefix = n >= 0 ? '+' : ''
  return `${prefix}${n.toFixed(2)}%`
}

// Watchlist specific
const isRise = computed(() => parseFloat(props.data?.estimatedGrowthRate || '0') > 0)
const isFall = computed(() => parseFloat(props.data?.estimatedGrowthRate || '0') < 0)

const trendIcon = computed(() => isRise.value ? '↑' : (isFall.value ? '↓' : '−'))
const directionLabel = computed(() => isRise.value ? '上涨' : (isFall.value ? '下跌' : '持平'))
const tagClass = computed(() => isRise.value ? 'tag--rise' : (isFall.value ? 'tag--fall' : 'tag--flat'))
const cardClass = computed(() => isRise.value ? 'card-glow-rise' : (isFall.value ? 'card-glow-fall' : ''))

// Position specific
const formatMarketValue = (pos) => {
  if (!pos.estimatedNav) return '--'
  const val = parseFloat(pos.totalShares) * parseFloat(pos.estimatedNav)
  return val.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const getAccumulatedPnl = (pos) => {
  if (!pos.estimatedNav) return 0
  const cost = parseFloat(pos.totalCost)
  const val = parseFloat(pos.totalShares) * parseFloat(pos.estimatedNav)
  return val - cost
}

const formatAccumulatedPnl = (pos) => {
  const pnl = getAccumulatedPnl(pos)
  if (pnl === 0) return '--'
  const cost = parseFloat(pos.totalCost)
  const pct = cost > 0 ? (pnl / cost) * 100 : 0
  const prefix = pnl >= 0 ? '+' : ''
  return `${prefix}¥${pnl.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} (${prefix}${pct.toFixed(2)}%)`
}

const formatTodayPnl = (pos) => {
  if (!pos.todayPnl) return '--'
  const pnl = parseFloat(pos.todayPnl)
  if (isNaN(pnl)) return '--'
  const prefix = pnl >= 0 ? '+' : ''
  return `${prefix}¥${pnl.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

const getPnlLabel = (pos) => {
  if (!pos.isSettled) return '今日估算'
  if (!pos.navDate) return '今日结算'
  const today = new Date()
  const navDate = new Date(pos.navDate)
  const todayMs = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime()
  const navMs = new Date(navDate.getFullYear(), navDate.getMonth(), navDate.getDate()).getTime()
  const diffDays = Math.round((todayMs - navMs) / (1000 * 60 * 60 * 24))
  if (diffDays === 0) return '今日结算'
  if (diffDays === 1) return '昨日结算'
  if (diffDays === 2) return '前天结算'
  const m = (navDate.getMonth() + 1).toString().padStart(2, '0')
  const d = navDate.getDate().toString().padStart(2, '0')
  return `${m}-${d} 结算`
}

const formatShares = (val) => {
  const n = parseFloat(val)
  if (isNaN(n)) return val
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const formatCost = (val) => {
  const n = parseFloat(val)
  if (isNaN(n)) return val
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
</script>

<style scoped>
/* ========== COMMON & WATCHLIST STYLES ========== */
.fund-card { position: relative; overflow: hidden; display: flex; flex-direction: column; }
.fund-card__header { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-sm); transition: padding-right var(--duration-fast); }
.fund-card__header.has-remove-btn { padding-right: var(--space-lg); }
.fund-card__info { flex: 1; min-width: 0; }
.fund-card__remove-btn { position: absolute; top: 8px; right: 8px; background: rgba(0, 0, 0, 0.04); border: none; width: 20px; height: 20px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 10px; color: var(--color-text-secondary); cursor: pointer; z-index: 10; transition: all var(--duration-fast); padding: 0; }
.fund-card__remove-btn:hover { background: rgba(225, 29, 72, 0.08); color: var(--color-rise); transform: scale(1.1); }
.fund-card__name { font-size: 17px; font-weight: 700; line-height: 1.3; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; letter-spacing: -0.01em; }
.fund-card__code-wrap, .pos-code-wrap { display: inline-flex; align-items: center; gap: 4px; margin-top: 4px; cursor: pointer; background: var(--color-bg-card-alt); padding: 2px 6px; border-radius: 6px; transition: background var(--duration-fast); }
.fund-card__code-wrap:active, .pos-code-wrap:active { background: var(--color-accent-soft); }
.fund-card__code, .pos-code { font-size: 13px; font-family: var(--font-mono); display: inline-block; line-height: 1; }
.copy-btn-mini { font-size: 11px; opacity: 0.6; transition: opacity var(--duration-fast); line-height: 1; }
.fund-card__code-wrap:hover .copy-btn-mini, .pos-code-wrap:hover .copy-btn-mini { opacity: 1; }
.fund-card__body { margin-top: var(--space-lg); }
.fund-card__rate-section { display: flex; align-items: baseline; gap: var(--space-sm); }
.fund-card__rate { font-size: 40px; font-weight: 700; letter-spacing: -0.03em; line-height: 1; }
.fund-card__rate-label { font-size: 12px; flex-shrink: 0; }
.fund-card__nav-row { display: flex; align-items: center; margin-top: var(--space-lg); background: var(--color-bg-card-alt); border-radius: var(--radius-md); padding: var(--space-md) var(--space-lg); border: 1px solid var(--color-border); }
.fund-card__nav-item { flex: 1; display: flex; flex-direction: column; gap: 4px; }
.fund-card__nav-value { font-size: 19px; font-weight: 600; line-height: 1.2; }
.fund-card__nav-label { font-size: 11px; }
.fund-card__nav-divider { width: 1px; height: 28px; background: var(--color-border); margin: 0 var(--space-lg); flex-shrink: 0; }
.fund-card__footer { display: flex; align-items: center; justify-content: space-between; margin-top: var(--space-md); padding-top: var(--space-md); border-top: 1px dashed var(--color-border); }
.fund-card__time { font-size: 11px; }
.fund-card__refreshing { display: flex; align-items: center; gap: 6px; font-size: 11px; color: var(--color-accent); }
@keyframes blink { 0%, 100% { opacity: 1; transform: scale(1); } 50% { opacity: 0.3; transform: scale(0.8); } }
.fund-card__dot { width: 6px; height: 6px; border-radius: 50%; background: var(--color-accent); animation: blink 1.5s ease-in-out infinite; }
.fund-card__skeleton { display: flex; flex-direction: column; }
.fund-card--error { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 160px; cursor: pointer; border-color: rgba(225, 29, 72, 0.3); background: rgba(225, 29, 72, 0.05); }
.fund-card__error-icon { font-size: 32px; margin-bottom: var(--space-sm); }
.fund-card__error-text { font-size: 13px; color: var(--color-text-secondary); text-align: center; }
.fund-card__error-hint { font-size: 12px; color: var(--color-accent); margin-top: var(--space-xs); }
.fund-card__expand { display: flex; align-items: center; justify-content: center; gap: 4px; padding: 12px var(--space-lg); margin: var(--space-md) calc(var(--space-lg) * -1) calc(var(--space-lg) * -1); background: var(--color-bg-card-alt); border-top: 1px solid var(--color-border); cursor: pointer; user-select: none; transition: all var(--duration-fast) var(--ease-out); }
.fund-card__expand:active { background: var(--color-accent-soft); }
.fund-card__expand-label { font-size: 12px; font-weight: 500; color: var(--color-accent); }
.fund-card__expand-chevron { font-size: 12px; color: var(--color-accent); transition: transform var(--duration-fast); line-height: 1; }
.fund-card__expand-chevron.is-expanded { transform: rotate(180deg); }

/* ========== ALIPAY DETAIL STYLES ========== */
.alipay-style-card { background: linear-gradient(135deg, var(--color-bg-card) 0%, var(--color-bg-card-alt) 100%); border: 1px solid var(--color-border-card); padding: var(--space-xl) var(--space-lg); display: flex; flex-direction: column; align-items: center; text-align: center; position: relative; overflow: hidden; }
.alipay-card-label { font-size: 13px; color: var(--color-text-muted); margin-bottom: 4px; }
.alipay-card-value { font-size: 32px; font-weight: 800; color: var(--color-text); margin-bottom: 24px; letter-spacing: -0.03em; }
.alipay-card-grid { display: grid; grid-template-columns: repeat(3, 1fr); width: 100%; border-top: 1px solid var(--color-border-subtle); padding-top: 16px; }
.alipay-card-grid:has(> :nth-child(2):last-child) { grid-template-columns: repeat(2, 1fr); }
.alipay-grid-item { display: flex; flex-direction: column; gap: 4px; }
.alipay-grid-item:not(:last-child) { border-right: 1px solid var(--color-border-subtle); }
.alipay-grid-item .label { font-size: 11px; color: var(--color-text-muted); }
.alipay-grid-item .val { font-size: 15px; font-weight: 600; }

/* ========== POSITION STYLES ========== */
.position-card { padding: 0; cursor: pointer; position: relative; overflow: hidden; transition: transform var(--duration-normal), box-shadow var(--duration-normal), border-color var(--duration-normal); display: flex; align-items: stretch; }
.position-card:hover { transform: translateY(-2px); box-shadow: var(--shadow-lg); }
.position-card.is-selected { border-color: var(--color-accent) !important; box-sizing: border-box; background: rgba(139, 90, 43, 0.02); }
.position-card__content { flex: 1; padding: 16px 20px; min-width: 0; }
.pos-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; border-bottom: 1px solid var(--color-border); padding-bottom: 12px; }
.pos-name-section { display: flex; flex-direction: row; align-items: baseline; flex-wrap: wrap; gap: 8px; min-width: 0; flex: 1; }
.pos-name { font-size: 16px; font-weight: 700; color: var(--color-text); letter-spacing: -0.01em; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.status-tag { position: absolute; top: 0; right: 0; font-size: 10px; padding: 4px 8px; border-bottom-left-radius: 8px; font-weight: 600; z-index: 2; }
.status-estimating { background: rgba(255, 152, 0, 0.15); color: #ed6c02; }
.status-settled { background: rgba(76, 175, 80, 0.15); color: #2e7d32; }
.pos-badge { font-size: 18px; font-weight: 700; letter-spacing: -0.02em; }
.pos-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 12px; }
.pos-grid--secondary { margin-bottom: 0; padding-top: 12px; border-top: 1px dashed var(--color-border); }
.pos-col { display: flex; flex-direction: column; gap: 4px; }
.pos-col .label { font-size: 11px; color: var(--color-text-secondary); font-weight: 500; }
.pos-col .val { font-size: 14px; font-weight: 600; color: var(--color-text); }
.pos-col .val--primary { font-size: 15px; font-weight: 700; }

.pos-checkbox-container { display: none; width: 44px; align-items: center; justify-content: center; background: rgba(0,0,0,0.01); border-right: 1px solid var(--color-border); transition: width var(--duration-normal); }
.position-card.edit-mode .pos-checkbox-container { display: flex; }
.pos-checkbox { width: 20px; height: 20px; border-radius: 50%; border: 2px solid #b0b5bd; transition: all var(--duration-fast); position: relative; }
.pos-checkbox.is-active { background: var(--color-accent); border-color: var(--color-accent); }
.pos-checkbox.is-active::after { content: ''; position: absolute; top: 4px; left: 7px; width: 4px; height: 8px; border-right: 2px solid white; border-bottom: 2px solid white; transform: rotate(45deg); }
</style>
