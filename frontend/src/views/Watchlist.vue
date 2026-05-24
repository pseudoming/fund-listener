<template>
  <div class="watchlist-page safe-bottom">
    <header class="page-header">
      <div style="display: flex; align-items: baseline; gap: 12px; margin-bottom: 4px;">
        <h1 class="page-header__title" style="margin-bottom: 0;">基金监控</h1>
        <!-- 市场状态指示 -->
        <div class="page-header__status" style="margin-top: 0;">
          <span class="market-dot" :class="marketStatusClass"></span>
          <span class="market-text font-medium">{{ marketStatusText }}</span>
        </div>
      </div>
      <p class="page-header__subtitle">
        实时估值 · 重仓穿透 · 盘中指标
      </p>
    </header>

    <!-- 搜索 -->
    <div class="search-wrap">
      <div style="display: flex; gap: 8px; align-items: center; width: 100%;">
        <FundSearch style="flex: 1" @search="handleSearch" />
        <button v-if="hasSearched" class="btn btn--danger" @click="clearSearch">清空</button>
      </div>
    </div>

    <!-- 基金卡片列表 -->
    <main class="app__content">
      <template v-if="hasSearched">
        <TransitionGroup name="list" tag="div" class="app__cards">
          <template v-for="fund in funds" :key="fund.code">
            <FundCard
              :data="fund.data"
              :loading="fund.loading"
              :error="fund.error"
              :refreshing="fund.refreshing"
              :expanded="expandedCode === fund.code"
              allow-remove
              @remove="removeFromWatchlist(fund.code)"
              @retry="refreshFund(fund.code)"
              @toggle-holdings="toggleHoldings(fund.code)"
              @click="goToDetail(fund.code, fund)"
            />
            <div v-if="expandedCode === fund.code" class="app__expanded-panel">
              <FundTrendChart :code="fund.code" />
              <HoldingsPanel :fund-code="fund.code" />
            </div>
          </template>
        </TransitionGroup>
      </template>

      <!-- 空状态 -->
      <div v-else class="app__empty glass-panel animate-in">
        <div class="app__empty-icon">📈</div>
        <p class="app__empty-text">输入名称/拼音/代码，查看实时估值</p>
        <div class="app__empty-hint">
          <span class="app__hint-tag" @click="quickSearch('110022')">易方达消费行业股票</span>
          <span class="app__hint-tag" @click="quickSearch('161725')">招商中证白酒指数A</span>
          <span class="app__hint-tag" @click="quickSearch('003834')">申万菱信沪深300指数</span>
        </div>
      </div>
    </main>
  </div>
</template>

<script>
import { reactive, ref } from 'vue'

// Global state for Watchlist to preserve across route changes
const globalFunds = reactive([])
const globalHasSearched = ref(false)
const globalExpandedCode = ref(null)
</script>

<script setup>
import { computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import FundSearch from '../components/FundSearch.vue'
import FundCard from '../components/FundCard.vue'
import HoldingsPanel from '../components/HoldingsPanel.vue'
import FundTrendChart from '../components/FundTrendChart.vue'
import { fetchFundRealtime, fetchDashboard } from '../api/fund'
import api from '../api/index'
import { showToast } from 'vant'

const router = useRouter()
const funds = globalFunds
const hasSearched = globalHasSearched
const expandedCode = globalExpandedCode

// ── Market status ──────────────────────────────────────────────────

const marketStatus = reactive({
  isTrading: false,
  isHoliday: false,
  holidayReason: null
})

const marketStatusClass = computed(() => {
  if (marketStatus.isTrading) return 'market-dot--trading'
  return 'market-dot--closed'
})

const marketStatusText = computed(() => {
  if (marketStatus.holidayReason) return marketStatus.holidayReason
  if (marketStatus.isTrading) return '交易中'
  return '已收盘'
})

async function fetchMarketStatus() {
  try {
    const data = await api.get('/market/status')
    marketStatus.isTrading = data.isTrading
    marketStatus.isHoliday = data.isHoliday
    marketStatus.holidayReason = data.holidayReason
  } catch (_) {
    // Ignore — market status is non-critical
  }
}

let marketTimer = null

onMounted(async () => { 
  fetchMarketStatus()
  marketTimer = setInterval(fetchMarketStatus, 60000)
  
  await initWatchlist()
})

onUnmounted(() => {
  if (marketTimer) clearInterval(marketTimer)
  funds.forEach(fund => {
    if (fund.timer) {
      clearInterval(fund.timer)
      fund.timer = null
    }
  })
})

async function initWatchlist() {
  try {
    let data = await api.get('/watchlist')
    if (data.length === 0) {
      // 首次加载且无自选数据时，自动将当前持仓基金添加为初始自选基金
      const dash = await fetchDashboard()
      if (dash && dash.funds && dash.funds.length > 0) {
        for (const f of dash.funds) {
          await api.post(`/watchlist/${f.fundCode}`)
        }
        data = await api.get('/watchlist')
      }
    }

    // 清空并重新构建反应式自选列表，然后开启轮询
    funds.splice(0, funds.length)
    if (data.length > 0) {
      hasSearched.value = true
      for (const item of data) {
        const fund = reactive({
          code: item.code,
          data: item,
          loading: false,
          error: null,
          refreshing: false,
          timer: null
        })
        funds.push(fund)
        startPolling(fund)
      }
    } else {
      hasSearched.value = false
    }
  } catch (e) {
    console.error("Failed to load watchlist", e)
    showToast("加载自选列表失败")
  }
}

async function clearSearch() {
  const codesToDelete = funds.map(f => f.code)
  let failCount = 0
  let lastFailMsg = ''
  
  for (const code of codesToDelete) {
    try {
      await api.delete(`/watchlist/${code}`)
      const idx = funds.findIndex(f => f.code === code)
      if (idx >= 0) {
        if (funds[idx].timer) clearInterval(funds[idx].timer)
        funds.splice(idx, 1)
      }
    } catch (e) {
      failCount++
      lastFailMsg = e.message
    }
  }
  
  if (funds.length === 0) {
    hasSearched.value = false
    expandedCode.value = null
  }
  
  if (failCount > 0) {
    showToast(lastFailMsg || `其中 ${failCount} 只持仓基金无法取消自选`)
  } else {
    showToast('已清空自选列表')
  }
}

/** 取消自选 */
async function removeFromWatchlist(code) {
  try {
    await api.delete(`/watchlist/${code}`)
    
    const idx = funds.findIndex(f => f.code === code)
    if (idx >= 0) {
      if (funds[idx].timer) {
        clearInterval(funds[idx].timer)
      }
      funds.splice(idx, 1)
    }

    if (funds.length === 0) {
      hasSearched.value = false
    }
    showToast('已取消自选')
  } catch (e) {
    showToast(e.message || '取消自选失败')
  }
}

/** 查询并添加基金到自选 */
async function handleSearch(code) {
  hasSearched.value = true

  // 如果已存在，刷新数据
  const existing = funds.find((f) => f.code === code)
  if (existing) {
    await refreshFund(code)
    return
  }

  // 新增卡片到顶部
  const fund = reactive({
    code,
    data: null,
    loading: true,
    error: null,
    refreshing: false,
    timer: null,
  })
  funds.unshift(fund)

  // 写入数据库自选表
  try {
    await api.post(`/watchlist/${code}`)
  } catch (e) {
    console.error(e)
    showToast(e.message || '添加自选失败')
  }

  await loadFundData(fund)
  startPolling(fund)
}

/** 加载/刷新基金数据 */
async function loadFundData(fund) {
  fund.loading = !fund.data
  fund.refreshing = !!fund.data
  fund.error = null

  try {
    fund.data = await fetchFundRealtime(fund.code)
  } catch (e) {
    fund.error = e.message
  } finally {
    fund.loading = false
    fund.refreshing = false
  }
}

/** 刷新指定基金 */
async function refreshFund(code) {
  const fund = funds.find((f) => f.code === code)
  if (fund) {
    await loadFundData(fund)
  }
}

/** 快捷搜索 */
function quickSearch(code) {
  handleSearch(code)
}

/** 展开/收起重仓股 */
function toggleHoldings(code) {
  expandedCode.value = expandedCode.value === code ? null : code
}

/** 进入详情页 */
function goToDetail(code, item = null) {
  router.push({
    path: `/fund/${code}`,
    query: { 
      from: 'watchlist',
      sinceAddedRate: item?.sinceAddedGrowthRate || ''
    }
  })
}

/** 自动刷新 — 30s */
function startPolling(fund) {
  if (fund.timer) clearInterval(fund.timer)
  fund.timer = setInterval(() => loadFundData(fund), 30000)
}
</script>

<style scoped>
.app__content {
  padding: 0 var(--space-lg);
  padding-bottom: var(--space-2xl);
}

.search-wrap {
  padding: var(--space-md) var(--space-lg);
  position: sticky;
  top: 0;
  z-index: 10;
  background: rgba(250, 246, 240, 0.95);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(0, 0, 0, 0.03);
  margin-bottom: var(--space-sm);
}

.dashboard-wrap {
  padding: 0 var(--space-lg);
}

.mt-4 {
  margin-top: var(--space-lg);
}

.app__cards {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}

.app__expanded-panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  margin-top: calc(var(--space-sm) * -1); /* pull closer to the card */
  margin-bottom: var(--space-sm);
  padding: 0 var(--space-xs);
}

/* 空状态 */
.app__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px var(--space-lg);
  margin-top: var(--space-xl);
  text-align: center;
  border-radius: var(--radius-xl);
}

.app__empty-icon {
  font-size: 56px;
  margin-bottom: var(--space-md);
  opacity: 0.9;
}

.app__empty-text {
  font-size: 15px;
  color: var(--color-text-secondary);
  margin-bottom: var(--space-xl);
  font-weight: 500;
}

.app__empty-hint {
  display: flex;
  gap: var(--space-md);
}

.app__hint-tag {
  padding: 8px 16px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: 20px;
  font-size: 14px;
  color: var(--color-accent);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
}

.app__hint-tag:hover {
  background: var(--color-accent-soft);
  border-color: var(--color-accent);
}

.app__hint-tag:active {
  transform: scale(0.95);
}

/* 市场状态 */
.page-header__status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.market-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.market-dot--trading {
  background: var(--color-fall);
  box-shadow: 0 0 8px var(--color-fall);
}
.market-dot--closed {
  background: var(--color-text-muted);
}

.market-text {
  font-size: 12px;
  color: var(--color-text-secondary);
}

/* TransitionGroup 动画 */
.list-enter-active {
  animation: fadeInUpSpring var(--duration-normal) var(--ease-spring) both;
}

.list-leave-active {
  transition: all var(--duration-fast) var(--ease-out);
}

.list-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}

.list-move {
  transition: transform var(--duration-normal) var(--ease-spring);
}
</style>
