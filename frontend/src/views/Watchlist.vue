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
            <WatchlistCard
              :data="fund.data"
              :is-edit-mode="isEditMode"
              :is-selected="selectedCodes.includes(fund.code)"
              @click="handleCardClick(fund)"
            />
            <div v-if="expandedCode === fund.code" class="app__expanded-panel">
              <TrendChart :code="fund.code" />
              <HoldingsPanel :fund-code="fund.code" />
            </div>
          </template>
        </TransitionGroup>

        <!-- 底部批量管理区 -->
        <div class="list-footer-batch-container">
          <div v-if="!isEditMode" class="batch-manage-trigger-row animate-in">
            <button class="btn btn--outline btn-batch-manage" @click="toggleEditMode">
              ⚙️ 批量管理自选
            </button>
          </div>
          <div v-else class="batch-manage-panel card glass-panel animate-in">
            <div class="panel-header-row">
              <div class="panel-select-all" @click="toggleSelectAll">
                <div class="pos-checkbox" :class="{ 'is-active': isAllSelected }"></div>
                <span class="panel-select-label font-medium">全选 ({{ selectedCodes.length }} / {{ funds.length }})</span>
              </div>
              <button class="btn btn--secondary btn-mini" @click="toggleEditMode">取消</button>
            </div>
            <div class="panel-action-row">
              <button 
                class="btn btn--danger btn-batch-delete-inline" 
                :disabled="selectedCodes.length === 0"
                @click="confirmBatchDelete"
              >
                取消自选 ({{ selectedCodes.length }}只)
              </button>
            </div>
          </div>
        </div>
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
import WatchlistCard from '../components/WatchlistCard.vue'
import HoldingsPanel from '../components/HoldingsPanel.vue'
import TrendChart from '../components/TrendChart.vue'
import { fetchFundRealtime, fetchDashboard } from '../api/fund'
import api from '../api/index'
import { showToast, showConfirmDialog } from 'vant'

const router = useRouter()
const funds = globalFunds
const hasSearched = globalHasSearched
const expandedCode = globalExpandedCode

const isEditMode = ref(false)
const selectedCodes = ref([])

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

const toggleEditMode = () => {
  isEditMode.value = !isEditMode.value
  if (!isEditMode.value) {
    selectedCodes.value = []
  }
}

const toggleSelectAll = () => {
  if (isAllSelected.value) {
    selectedCodes.value = []
  } else {
    selectedCodes.value = funds.map(f => f.code)
  }
}

const isAllSelected = computed(() => {
  return funds.length > 0 && selectedCodes.value.length === funds.length
})

const handleCardClick = (fund) => {
  if (isEditMode.value) {
    const idx = selectedCodes.value.indexOf(fund.code)
    if (idx >= 0) {
      selectedCodes.value.splice(idx, 1)
    } else {
      selectedCodes.value.push(fund.code)
    }
  } else {
    goToDetail(fund.code, fund.data)
  }
}

const confirmBatchDelete = () => {
  if (selectedCodes.value.length === 0) return
  
  showConfirmDialog({
    title: '批量取消自选',
    message: `确定要取消自选这 ${selectedCodes.value.length} 个基金吗？`,
  }).then(async () => {
    let failCount = 0
    let lastFailMsg = ''
    for (const code of selectedCodes.value) {
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
    
    isEditMode.value = false
    selectedCodes.value = []

    if (failCount > 0) {
      showToast(lastFailMsg || `其中 ${failCount} 只基金无法取消自选(可能是因为有持仓)`)
    } else {
      showToast('批量取消自选成功')
    }
  }).catch(() => {})
}

/** 自动刷新 — 30s */
function startPolling(fund) {
  if (fund.timer) clearInterval(fund.timer)
  fund.timer = setInterval(() => loadFundData(fund), 30000)
}
</script>

<style scoped>

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

/* 底部批量管理区 */
.list-footer-batch-container {
  margin-top: var(--space-md);
  margin-bottom: var(--space-lg);
  padding: 0 var(--space-xs);
}
.batch-manage-trigger-row { display: flex; justify-content: center; align-items: center; }
.btn-batch-manage { border-radius: 20px; padding: 8px 24px; font-size: 13px; font-weight: 600; color: var(--color-text-secondary); border-color: var(--color-border); background: var(--color-bg-card-alt); transition: all var(--duration-fast); }
.btn-batch-manage:hover { border-color: var(--color-accent); background: var(--color-accent-soft); color: var(--color-accent); }
.batch-manage-panel { padding: var(--space-md); border-radius: var(--radius-lg); border: 1px dashed var(--color-border); background: var(--color-bg-card-alt); }
.panel-header-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--space-md); }
.panel-select-all { display: flex; align-items: center; gap: var(--space-sm); cursor: pointer; user-select: none; }
.pos-checkbox { width: 20px; height: 20px; border-radius: 50%; border: 2px solid #b0b5bd; transition: all var(--duration-fast); position: relative; }
.pos-checkbox.is-active { background: var(--color-accent); border-color: var(--color-accent); }
.pos-checkbox.is-active::after { content: ''; position: absolute; top: 4px; left: 7px; width: 4px; height: 8px; border-right: 2px solid white; border-bottom: 2px solid white; transform: rotate(45deg); }
.panel-select-label { font-size: 13px; color: var(--color-text); }
.btn-mini { padding: 4px 12px; font-size: 12px; border-radius: 12px; height: 26px; }
.panel-action-row { display: flex; justify-content: center; }
.btn-batch-delete-inline { width: 100%; border-radius: var(--radius-md); padding: 10px 0; font-size: 13px; font-weight: 700; }
</style>
