<template>
  <div class="page-container detail-page-container animate-in">
    <!-- Header -->
    <header class="page-header detail-header">
      <div class="header-left">
        <button class="btn btn--secondary back-btn-mini" @click="onClickLeft">
          <span>←</span> 返回
        </button>
      </div>
      <button class="btn btn--danger delete-btn-mini" @click="onDeletePosition" v-if="hasPosition">删除</button>
    </header>

    <div class="detail-title-section px-lg" v-if="!loading">
      <h1 class="detail-title">{{ fundName || position?.fundName || '基金详情' }}</h1>
      <div class="detail-code-wrap" @click="copyText(code)" title="点击复制基金代码">
        <span class="detail-code font-mono">{{ code }}</span>
        <span class="copy-btn-mini">📋</span>
      </div>
    </div>

    <van-skeleton title :row="5" :loading="loading" class="mt-4">
      <template v-if="!loading">
        <div class="detail-content">
          <!-- Alipay-style Core Metrics Card -->
          <FundCard
            mode="detail"
            :is-watchlist="currentMode === 'watchlist'"
            :has-position="hasPosition"
            :current-market-value="currentMarketValue"
            :today-pnl="todayPnl"
            :holding-pnl="holdingPnl"
            :holding-pnl-rate="holdingPnlRate"
            :current-nav="realtimeData?.estimatedNav || realtimeData?.nav"
            :estimated-growth-rate="realtimeData?.estimatedGrowthRate"
            :since-added-growth-rate="sinceAddedGrowthRate"
            :estimation-time="realtimeData?.estimationTime"
            :nav-date="realtimeData?.navDate"
          />

          <!-- 静态信息卡片 (份额、成本/经理、起购额)及交易操作 -->
          <FundStaticInfo
            :mode="currentMode"
            :has-position="hasPosition"
            :position="position"
            :current-nav="realtimeData?.estimatedNav || realtimeData?.nav"
            :fund-type="realtimeData?.type"
            :fund-manager="realtimeData?.manager"
            :linked-etf-code="linkedEtfCode"
            :linked-etf-name="linkedEtfName"
            @buy="openTrade('BUY')"
            @sell="openTrade('SELL')"
          />

          <!-- 历史走势 -->
          <div class="trend-section">
            <FundTrendChart :code="code" />
          </div>

          <!-- 估值仪表盘 -->
          <div class="dashboard-section">
            <ValuationDashboard :fund-code="code" />
          </div>
          
          <div class="settings-btn-wrap">
            <button class="btn btn--secondary" style="width: 100%;" @click="showValuationEditor = true">
              自定义估值法设置
            </button>
          </div>

          <!-- 基金重仓 -->
          <div class="holdings-section card" v-if="topHoldings && topHoldings.length > 0">
            <div class="tx-title font-semibold" style="margin-bottom: var(--space-md);">前十大重仓股票</div>
            <div class="holdings-list">
              <div class="holding-item" v-for="h in topHoldings" :key="h.stockCode">
                <div class="h-left">
                  <div class="h-name">{{ h.stockName }}</div>
                  <div class="h-code text-num text-muted">{{ h.stockCode }}</div>
                </div>
                <div class="h-right">
                  <div class="h-ratio font-mono">{{ h.ratio }}%</div>
                  <div class="h-bar-bg">
                    <div class="h-bar-fill" :style="{ width: Math.min(parseFloat(h.ratio)*2, 100) + '%' }"></div>
                  </div>
                </div>
              </div>
            </div>
            <div class="h-footer text-muted" style="margin-top: 8px; font-size: 11px; text-align: right;">
              更新日期: {{ holdingsReportDate }}
            </div>
          </div>

          <!-- 交易明细 -->
          <TransactionList v-if="currentMode === 'position'" :transactions="transactions" />
        </div>
      </template>
    </van-skeleton>

    <!-- 交易弹窗 -->
    <van-action-sheet v-model:show="showTradeSheet" teleport="body" :title="tradeType === 'BUY' ? '买入基金' : '卖出基金'">
      <div class="trade-form">
        <van-form @submit="onTradeSubmit">
          <!-- 交易日期优先，因为净值依赖它 -->
          <van-field
            v-model="tradeForm.tradeDate"
            name="tradeDate"
            label="交易日期"
            type="date"
            placeholder="选择交易日期"
            :rules="[{ required: true, message: '请选择交易日期' }]"
          />
          <!-- 交易金额 -->
          <van-field
            v-model="tradeForm.amount"
            name="amount"
            label="交易金额"
            placeholder="请输入交易金额(元)"
            type="number"
            :rules="[{ required: true, message: '请填写交易金额' }]"
          />
          <!-- 自动计算显示的净值和份额 (只读) -->
          <van-field
            v-model="tradeForm.nav"
            name="nav"
            label="成交净值"
            placeholder="自动获取净值"
            readonly
            class="readonly-field"
            :rules="[{ required: true, message: '未能获取到该日期的净值，请重新选择日期' }]"
          />
          <van-field
            v-model="tradeForm.shares"
            name="shares"
            label="确认份额"
            placeholder="根据金额和净值自动计算"
            readonly
            class="readonly-field"
            :rules="[{ required: true, message: '确认份额未计算出' }]"
          />
          <!-- 手续费选填 -->
          <van-field
            v-model="tradeForm.fee"
            name="fee"
            label="手续费"
            placeholder="请输入手续费(选填)"
            type="number"
          />
          
          <div style="margin: 16px;">
            <button type="submit" class="btn btn--primary" style="width: 100%;" :disabled="submitting">
              {{ submitting ? '提交中...' : '提交交易' }}
            </button>
          </div>
        </van-form>
      </div>
    </van-action-sheet>
    <!-- 估值设置弹窗 -->
    <van-popup v-model:show="showValuationEditor" teleport="body" position="bottom" style="background: transparent; padding: 16px 16px 32px 16px;">
      <ValuationEditor :fund-code="code" @saved="onValuationSaved" @close="showValuationEditor = false" />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchPositionDetail, buyPosition, sellPosition, deletePosition } from '../api/position'
import { fetchFundRealtime, fetchFundHoldings, fetchFundTrend } from '../api/fund'
import { showToast, showConfirmDialog } from 'vant'
import ValuationDashboard from '../components/ValuationDashboard.vue'
import ValuationEditor from '../components/ValuationEditor.vue'
import FundTrendChart from '../components/FundTrendChart.vue'
import FundCard from '../components/FundCard.vue'
import FundStaticInfo from '../components/FundStaticInfo.vue'
import TransactionList from '../components/TransactionList.vue'

const route = useRoute()
const router = useRouter()
const code = route.params.code

const loading = ref(true)
const position = ref(null)
const transactions = ref([])
const fundName = ref(route.query.name || '')
const topHoldings = ref([])
const holdingsReportDate = ref('')
const sinceAddedGrowthRate = ref(route.query.sinceAddedRate || '')
const currentMode = ref(route.query.from === 'watchlist' ? 'watchlist' : 'position')

const assetType = ref('')
const linkedEtfCode = ref('')
const linkedEtfName = ref('')

const hasPosition = computed(() => position.value && parseFloat(position.value.totalShares) > 0)
const pageTitle = computed(() => {
  if (fundName.value) return `${fundName.value} (${code})`
  if (position.value?.fundName) return `${position.value.fundName} (${code})`
  return `持仓明细 (${code})`
})

const realtimeData = ref(null)
const trendData = ref([])

const currentMarketValue = computed(() => {
  if (!position.value) return '0.00'
  const shares = parseFloat(position.value.totalShares) || 0
  const nav = parseFloat(realtimeData.value?.estimatedNav || realtimeData.value?.nav || position.value?.avgCostNav) || 0
  if (shares <= 0 || nav <= 0) return '0.00'
  return (shares * nav).toFixed(2)
})

const todayPnl = computed(() => {
  if (!position.value || !realtimeData.value) return '0.00'
  const shares = parseFloat(position.value.totalShares) || 0
  const yesterdayNav = parseFloat(realtimeData.value.yesterdayNav || realtimeData.value.nav) || 0
  const currentNav = parseFloat(realtimeData.value.estimatedNav || realtimeData.value.nav) || 0
  
  if (shares <= 0 || currentNav <= 0 || yesterdayNav <= 0) return '0.00'

  const isOfficialUpdated = (realtimeData.value.navDate === realtimeData.value.estimationTime.split(' ')[0]) || 
                            (realtimeData.value.nav === realtimeData.value.estimatedNav && 
                             realtimeData.value.yesterdayNav && 
                             realtimeData.value.yesterdayNav !== realtimeData.value.nav)

  if (isOfficialUpdated) {
    return ((currentNav - yesterdayNav) * shares).toFixed(2)
  } else {
    const rate = parseFloat(realtimeData.value.estimatedGrowthRate) || 0
    return ((shares * yesterdayNav) * (rate / 100)).toFixed(2)
  }
})

const holdingPnl = computed(() => {
  if (!position.value) return '0.00'
  const cost = parseFloat(position.value.totalCost) || 0
  const marketVal = parseFloat(currentMarketValue.value) || 0
  return (marketVal - cost).toFixed(2)
})

const holdingPnlRate = computed(() => {
  if (!position.value) return '0.00%'
  const cost = parseFloat(position.value.totalCost) || 0
  const pnl = parseFloat(holdingPnl.value) || 0
  if (cost <= 0) return '0.00%'
  const rate = (pnl / cost) * 100
  const prefix = rate >= 0 ? '+' : ''
  return `${prefix}${rate.toFixed(2)}%`
})

const todayPnlClass = computed(() => {
  const p = parseFloat(todayPnl.value) || 0
  return p > 0 ? 'text-rise' : (p < 0 ? 'text-fall' : '')
})

const holdingPnlClass = computed(() => {
  const p = parseFloat(holdingPnl.value) || 0
  return p > 0 ? 'text-rise' : (p < 0 ? 'text-fall' : '')
})

const formatWithSign = (val) => {
  const n = parseFloat(val) || 0
  const prefix = n >= 0 ? '+' : ''
  return `${prefix}${n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

const loadData = async () => {
  try {
    loading.value = true
    // 同时尝试获取实时信息（拿名字）和持仓信息
    const [realtimeRes, posRes] = await Promise.allSettled([
      fetchFundRealtime(code),
      fetchPositionDetail(code)
    ])
    
    if (realtimeRes.status === 'fulfilled' && realtimeRes.value) {
      realtimeData.value = realtimeRes.value
      // 仅在当前没有名称时，才使用实时数据中的名称兜底
      if (!fundName.value && realtimeData.value?.name) {
        fundName.value = realtimeData.value.name
      }
    }
    
    // Fetch Top Holdings asynchronously
    fetchFundHoldings(code).then(res => {
      if (res) {
        if (res.holdings) {
          topHoldings.value = res.holdings
          holdingsReportDate.value = res.reportDate || ''
        }
        assetType.value = res.assetType || ''
        linkedEtfCode.value = res.linkedEtfCode || ''
        linkedEtfName.value = res.linkedEtfName || ''
      }
    }).catch(e => console.warn('Failed to fetch holdings', e))

    // Fetch Trend Data asynchronously
    fetchFundTrend(code).then(res => {
      if (res && res.length) {
        trendData.value = res
      }
    }).catch(e => console.warn('Failed to fetch trend', e))

    if (posRes.status === 'fulfilled' && posRes.value) {
      position.value = posRes.value.position
      transactions.value = posRes.value.transactions || []
      // 如果持仓数据里存了名称（例如OCR导入的长名称），则优先使用以对齐列表页
      if (position.value?.fundName) {
        fundName.value = position.value.fundName
      }
    } else {
      // 404 or other errors mean no position yet.
      position.value = null
      transactions.value = []
    }
  } catch (err) {
    console.error(err)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
})

const onClickLeft = () => {
  const backState = window.history.state?.back
  if (backState) {
    router.back()
  } else {
    const from = route.query.from || ''
    if (from.includes('watchlist')) {
      router.replace('/watchlist')
    } else {
      router.replace('/')
    }
  }
}

const copyText = (text) => {
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

const onDeletePosition = () => {
  showConfirmDialog({
    title: '删除持仓',
    message: '确定要删除该持仓监控卡片吗？该基金的交易历史也将被永久删除。',
  }).then(async () => {
    try {
      await deletePosition(code)
      showToast('删除成功')
      router.back()
    } catch (err) {
      showToast('删除失败')
    }
  }).catch(() => {})
}

// 交易相关
const showTradeSheet = ref(false)
const tradeType = ref('BUY') // BUY or SELL
const submitting = ref(false)

const getTodayString = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const tradeForm = ref({
  amount: '',
  shares: '',
  nav: '',
  fee: '0.00',
  tradeDate: getTodayString()
})

watch(() => tradeForm.value.tradeDate, (newDate) => {
  if (!newDate) return
  const match = trendData.value.find(d => {
    const dObj = new Date(d.x)
    return `${dObj.getFullYear()}-${String(dObj.getMonth() + 1).padStart(2, '0')}-${String(dObj.getDate()).padStart(2, '0')}` === newDate
  })
  if (match && match.y) {
    tradeForm.value.nav = match.y.toFixed(4)
  } else if (newDate === getTodayString()) {
    tradeForm.value.nav = realtimeData.value?.estimatedNav || realtimeData.value?.nav || ''
  }
})

watch([() => tradeForm.value.amount, () => tradeForm.value.nav], ([newAmount, newNav]) => {
  if (newAmount && newNav) {
    const amt = parseFloat(newAmount)
    const nv = parseFloat(newNav)
    if (!isNaN(amt) && !isNaN(nv) && nv > 0) {
      tradeForm.value.shares = (amt / nv).toFixed(4)
    } else {
      tradeForm.value.shares = ''
    }
  } else {
    tradeForm.value.shares = ''
  }
})

const openTrade = (type) => {
  tradeType.value = type
  tradeForm.value = {
    amount: '',
    shares: '',
    nav: realtimeData.value?.estimatedNav || realtimeData.value?.nav || '',
    fee: '0.00',
    tradeDate: getTodayString()
  }
  showTradeSheet.value = true
}

const onTradeSubmit = async () => {
  try {
    submitting.value = true
    const payload = {
      ...tradeForm.value,
      fundName: fundName.value || position.value?.fundName
    }
    
    if (tradeType.value === 'BUY') {
      await buyPosition(code, payload)
      showToast('买入记录添加成功')
    } else {
      await sellPosition(code, payload)
      showToast('卖出记录添加成功')
    }
    
    showTradeSheet.value = false
    await loadData() // 刷新数据
  } catch (err) {
    showToast(err.response?.data?.error || '操作失败')
  } finally {
    submitting.value = false
  }
}

const formatNum = (numStr) => {
  const num = parseFloat(numStr)
  if (isNaN(num)) return numStr
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 4 })
}

const showValuationEditor = ref(false)
const onValuationSaved = () => {
  showValuationEditor.value = false
  // Could trigger a reload of ValuationDashboard if there was a ref to it, 
  // for now reload full data or just let the user see it updated next time.
  loadData()
}
</script>

<style scoped>
.page-container {
  padding-bottom: 20px;
}

.overview-card {
  margin-bottom: var(--space-lg);
}

.metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-md);
  margin-bottom: 16px;
}

.metric {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.metric .label {
  font-size: 12px;
  color: var(--color-text-muted);
}

.metric .val {
  font-size: 16px;
  font-weight: 500;
  color: var(--color-text);
}

.action-buttons {
  display: flex;
  gap: 12px;
}

.tx-section {
  margin-top: var(--space-lg);
}

.tx-title {
  font-size: 16px;
  font-weight: 500;
  color: var(--color-text);
  margin-bottom: 12px;
}

.tx-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tx-item {
  background: var(--color-bg-input);
  border-radius: var(--radius-sm);
  padding: 12px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tx-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.tx-type {
  font-size: 14px;
  font-weight: 500;
}
.tx-type.BUY {
  color: var(--color-rise);
}
.tx-type.SELL {
  color: var(--color-fall);
}

.tx-date {
  font-size: 12px;
  color: var(--color-text-muted);
}

.tx-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.tx-shares {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text);
}

.tx-nav {
  font-size: 12px;
  color: var(--color-text-muted);
}

.trade-form {
  padding: 20px 16px;
}
.trade-form :deep(.van-cell) {
  background: var(--color-bg-input);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  margin-bottom: 12px;
  padding: 12px 16px;
}
.trade-form :deep(.van-cell::after) {
  display: none;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-xl) var(--space-lg) var(--space-md);
  background: var(--color-bg);
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.back-btn-mini, .delete-btn-mini {
  border-radius: 20px;
  padding: 6px 14px;
  height: auto;
  font-size: 13px;
  font-weight: 600;
}

.detail-title-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  margin-bottom: var(--space-md);
  margin-top: var(--space-sm);
}

.detail-title {
  font-size: 24px;
  font-weight: 800;
  text-align: center;
  color: var(--color-text);
  line-height: 1.2;
  margin: 0;
  letter-spacing: -0.02em;
}

.detail-code-wrap {
  display: inline-flex;
  align-items: center;
  align-self: flex-start;
  gap: 6px;
  cursor: pointer;
  background: var(--color-bg-card-alt);
  padding: 4px 10px;
  border-radius: 8px;
  transition: background var(--duration-fast);
}

.detail-code-wrap:active {
  background: var(--color-accent-soft);
}

.detail-code {
  font-size: 14px;
  color: var(--color-text-muted);
  font-weight: 600;
  line-height: 1;
}

.copy-btn-mini {
  font-size: 12px;
  opacity: 0.6;
  transition: opacity var(--duration-fast);
  line-height: 1;
}

.detail-code-wrap:hover .copy-btn-mini {
  opacity: 1;
}

.detail-content {
  padding: 0 var(--space-lg) var(--space-2xl);
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}

.settings-btn-wrap {
  padding: 0;
}

.alipay-style-card {
  background: linear-gradient(135deg, var(--color-bg-card) 0%, var(--color-bg-card-alt) 100%);
  border: 1px solid var(--color-border-card);
  padding: var(--space-xl) var(--space-lg);
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  position: relative;
  overflow: hidden;
}

.alipay-card-label {
  font-size: 13px;
  color: var(--color-text-muted);
  margin-bottom: 4px;
}

.alipay-card-value {
  font-size: 32px;
  font-weight: 800;
  color: var(--color-text);
  margin-bottom: 24px;
  letter-spacing: -0.03em;
}

.alipay-card-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  width: 100%;
  border-top: 1px solid var(--color-border-subtle);
  padding-top: 16px;
}

.alipay-grid-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.alipay-grid-item:not(:last-child) {
  border-right: 1px solid var(--color-border-subtle);
}

.alipay-grid-item .label {
  font-size: 11px;
  color: var(--color-text-muted);
}

.alipay-grid-item .val {
  font-size: 15px;
  font-weight: 600;
}

/* ── Holdings Section ── */
.holdings-section {
  padding: var(--space-lg);
  margin-bottom: var(--space-xl);
}
.holding-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-sm) 0;
  border-bottom: 1px solid var(--color-border);
}
.holding-item:last-child {
  border-bottom: none;
}
.h-left {
  flex: 1;
}
.h-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text);
  margin-bottom: 2px;
}
.h-code {
  font-size: 11px;
}
.h-right {
  width: 80px;
  text-align: right;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}
.h-ratio {
  font-size: 13px;
  color: var(--color-text);
}
.h-bar-bg {
  width: 100%;
  height: 4px;
  background: var(--color-border);
  border-radius: 2px;
  overflow: hidden;
}
.h-bar-fill {
  height: 100%;
  background: var(--color-accent);
  border-radius: 2px;
}
</style>
.readonly-field :deep(.van-field__control) { color: var(--color-text-muted); background-color: var(--color-bg-card-alt); cursor: not-allowed; }
