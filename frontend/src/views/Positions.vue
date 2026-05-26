<template>
  <div class="positions-page safe-bottom animate-in">
    <!-- Header -->
    <header class="page-header flex-header">
      <div class="header-title-section">
        <div class="header-title-row">
          <div style="display: flex; align-items: baseline; gap: 8px;">
            <h1 class="page-header__title" style="margin-bottom: 0;">我的持仓</h1>
            <span v-if="dashboardData?.lastUpdatedTime" class="text-muted font-mono" style="font-size: 11px; font-weight: 500;">
              ↻ 最后更新: {{ dashboardData.lastUpdatedTime }}
            </span>
          </div>
          <!-- 顶部操作栏 -->
          <div class="header-actions">
            <button class="btn btn--secondary btn-import-mini" @click="toggleUploadZone">
              📸 {{ showUploadZone ? '收起导入' : '导入截图' }}
            </button>
            <button
              class="btn-settings-gear"
              @click="$router.push('/settings')"
              title="系统设置"
            >
              <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
                <path d="M19.43 12.98c.04-.32.07-.64.07-.98s-.03-.66-.07-.98l2.11-1.65c.19-.15.24-.42.12-.64l-2-3.46c-.12-.22-.39-.3-.61-.22l-2.49 1c-.52-.4-1.08-.73-1.69-.98l-.38-2.65C14.46 2.18 14.25 2 14 2h-4c-.25 0-.46.18-.49.42l-.38 2.65c-.61.25-1.17.59-1.69.98l-2.49-1c-.23-.09-.49 0-.61.22l-2 3.46c-.13.22-.07.49.12.64l2.11 1.65c-.04.32-.07.65-.07.98s.03.66.07.98l-2.11 1.65c-.19.15-.24.42-.12.64l2 3.46c.12.22.39.3.61.22l2.49-1c.52.4 1.08.73 1.69.98l.38 2.65c.03.24.24.42.49.42h4c.25 0 .46-.18.49-.42l.38-2.65c.61-.25 1.17-.59 1.69-.98l2.49 1c.23.09.49 0 .61-.22l2-3.46c.12-.22.07-.49-.12-.64l-2.11-1.65zM12 15.5c-1.93 0-3.5-1.57-3.5-3.5s1.57-3.5 3.5-3.5 3.5 1.57 3.5 3.5-1.57 3.5-3.5 3.5z"/>
              </svg>
            </button>
          </div>
        </div>
        <p class="page-header__subtitle">管理基金持仓，实时估值监控与重仓穿透</p>
      </div>
    </header>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh" success-text="刷新成功" class="pull-refresh-container">

    <!-- 搜索 -->
    <div class="search-wrap">
      <FundSearch @search="onSearchFund" placeholder="搜索名称/代码以添加持仓或自选" />
    </div>

    <!-- OCR 导入 (折叠面板) -->
    <Transition name="collapse">
      <div class="ocr-wrap" v-show="showUploadZone">
        <OcrUpload @imported="onOcrImported" />
      </div>
    </Transition>

    <!-- 持仓汇总看板 -->
    <div class="dashboard-wrap" v-if="!loading && positions.length > 0 && dashboardData">
      <div class="dashboard-card card">
        <div class="dashboard__summary">
          <div class="dashboard__metric">
            <span class="dashboard__metric-label">总持仓</span>
            <span class="dashboard__metric-value font-mono">¥{{ formatVal(dashboardData.totalMarketValue) }}</span>
          </div>
          <div class="dashboard__metric">
            <span class="dashboard__metric-label">{{ dashboardLabels.pnlLabel }}</span>
            <span class="dashboard__metric-value font-mono" :class="pnlColor">
              {{ pnlPrefix }}¥{{ formatValAbs(dashboardData.latestPnl) }}
            </span>
          </div>
          <div class="dashboard__metric">
            <span class="dashboard__metric-label">{{ dashboardLabels.percentLabel }}</span>
            <span class="dashboard__metric-value font-mono" :class="pnlColor">
              {{ pnlPrefix }}{{ dashboardData.latestPnlPercent }}%
            </span>
          </div>
        </div>
        
        <div class="market-ticker" v-if="dashboardData.marketIndices && dashboardData.marketIndices.length > 0">
          <span class="ticker-item font-mono" 
            v-for="(idx, i) in dashboardData.marketIndices" 
            :key="i"
            :class="{'text-rise': idx.isRise, 'text-fall': idx.isFall}">
            {{ idx.name }} {{ idx.changePercent }} {{ idx.isRise ? '↑' : (idx.isFall ? '↓' : '-') }}
          </span>
        </div>
      </div>
    </div>



    <!-- 持仓列表区 -->
    <main class="app__content">
      <!-- 加载中 -->
      <van-skeleton title :row="5" :loading="loading" class="skeleton-glow">
        <template v-if="!loading">
          <!-- 无数据 -->
          <div v-if="positions.length === 0" class="app__empty glass-panel animate-in">
            <div class="app__empty-icon">💼</div>
            <p class="app__empty-text">暂无持仓，上传截图或搜索添加吧</p>
          </div>

          <!-- 列表包裹区 -->
          <div v-else class="position-wrap">
            <!-- 列表表头及排序 (位于卡片外部, 开启悬浮) -->
            <div class="sticky-headers">
              <div class="list-header-bar">
                <span class="list-title">全部持仓 ({{ positions.length }}只)</span>
                <div class="list-actions-right" style="display: flex; gap: 12px; align-items: center;">
                  <div class="list-sort-controls">
                    <SortDropdown v-model="sortBy" :options="sortOptions" />
                    <button class="sort-order-btn-double" @click="toggleSortOrder" :title="sortOrder === 'asc' ? '当前：升序' : '当前：降序'">
                      <span class="arrow-up" :class="{ 'arrow-active': sortOrder === 'asc' }">▲</span>
                      <span class="arrow-down" :class="{ 'arrow-active': sortOrder === 'desc' }">▼</span>
                    </button>
                  </div>
                  <div class="analysis-btn" @click="$router.push('/portfolio-analysis')">
                    <span>📊 持仓分析</span>
                  </div>
                </div>
              </div>
              
              <div class="list-header-row text-xs text-muted font-medium mb-1 px-4 flex">
                <div class="col-name" style="flex: 2; text-align: left;">名称</div>
                <div class="col-market" style="flex: 1.5; text-align: right;">金额/最新收益</div>
                <div class="col-holding" style="flex: 1.5; text-align: right;">持有收益/率</div>
              </div>
            </div>

            <!-- 纯白卡片容器，只包裹持仓项 -->
            <div class="position-list">
              <HoldingCard
                v-for="pos in sortedPositions" 
                :key="pos.fundCode"
                :data="pos"
                :is-edit-mode="isEditMode"
                :is-selected="selectedCodes.includes(pos.fundCode)"
                @click="handleCardClick(pos)"
              />
            </div>

            <!-- 底部批量管理区 -->
            <div class="list-footer-batch-container">
              <!-- 未开启管理模式时，显示管理入口按钮 -->
              <div v-if="!isEditMode" class="batch-manage-trigger-row animate-in">
                <button class="btn btn--outline btn-batch-manage" @click="toggleEditMode">
                  ⚙️ 批量管理持仓
                </button>
              </div>

              <!-- 开启管理模式时，显示展开的管理面板 -->
              <div v-else class="batch-manage-panel card glass-panel animate-in">
                <div class="panel-header-row">
                  <div class="panel-select-all" @click="toggleSelectAll">
                    <div class="pos-checkbox" :class="{ 'is-active': isAllSelected }"></div>
                    <span class="panel-select-label font-medium">全选 ({{ selectedCodes.length }} / {{ positions.length }})</span>
                  </div>
                  <button class="btn btn--secondary btn-mini" @click="toggleEditMode">取消</button>
                </div>
                <div class="panel-action-row">
                  <button 
                    class="btn btn--danger btn-batch-delete-inline" 
                    :disabled="selectedCodes.length === 0"
                    @click="confirmBatchDelete"
                  >
                    删除已选 ({{ selectedCodes.length }}只)
                  </button>
                </div>
              </div>
            </div>
          </div>
        </template>
      </van-skeleton>
    </main>
    </van-pull-refresh>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { fetchDashboard } from '../api/fund'
import { showToast, showConfirmDialog } from 'vant'
import { deletePosition } from '../api/position'
import FundSearch from '../components/FundSearch.vue'
import HoldingCard from '../components/HoldingCard.vue'
import OcrUpload from '../components/OcrUpload.vue'
import SortDropdown from '../components/SortDropdown.vue'
import { getDashboardLabels } from '../utils/formatters.js'

const router = useRouter()
const dashboardData = ref(null)
const positions = ref([])
const loading = ref(true)
const refreshing = ref(false)
const showUploadZone = ref(false)

const isEditMode = ref(false)
const selectedCodes = ref([])

// 排序状态
const sortBy = ref('marketValue')
const sortOrder = ref('desc') // 'asc', 'desc'

const toggleSortOrder = () => {
  sortOrder.value = sortOrder.value === 'asc' ? 'desc' : 'asc'
}

const sortOptions = [
  { value: 'marketValue', label: '金额' },
  { value: 'latestPnl', label: '昨日收益' },
  { value: 'estimatedGrowthRate', label: '最新涨跌' },
  { value: 'totalPnlRate', label: '持有收益率' },
  { value: 'growthRate', label: '估值涨幅' }
]

const sortedPositions = computed(() => {
  return [...positions.value].sort((a, b) => {
    let valA = 0
    let valB = 0
    if (sortBy.value === 'marketValue') {
      const sharesA = parseFloat(a.totalShares || '0')
      const navA = parseFloat(a.estimatedNav || '0')
      valA = sharesA * navA

      const sharesB = parseFloat(b.totalShares || '0')
      const navB = parseFloat(b.estimatedNav || '0')
      valB = sharesB * navB
    } else if (sortBy.value === 'growthRate') {
      valA = parseFloat(a.estimatedGrowthRate || '0')
      valB = parseFloat(b.estimatedGrowthRate || '0')
    } else if (sortBy.value === 'totalPnlAmount') {
      const costA = parseFloat(a.totalCost || '0')
      const sharesA = parseFloat(a.totalShares || '0')
      const navA = parseFloat(a.estimatedNav || '0')
      valA = (sharesA * navA) - costA

      const costB = parseFloat(b.totalCost || '0')
      const sharesB = parseFloat(b.totalShares || '0')
      const navB = parseFloat(b.estimatedNav || '0')
      valB = (sharesB * navB) - costB
    } else if (sortBy.value === 'totalPnlRate') {
      const costA = parseFloat(a.totalCost || '0')
      const sharesA = parseFloat(a.totalShares || '0')
      const navA = parseFloat(a.estimatedNav || '0')
      valA = costA > 0 ? ((sharesA * navA) - costA) / costA : 0.0

      const costB = parseFloat(b.totalCost || '0')
      const sharesB = parseFloat(b.totalShares || '0')
      const navB = parseFloat(b.estimatedNav || '0')
      valB = costB > 0 ? ((sharesB * navB) - costB) / costB : 0.0
    } else if (sortBy.value === 'latestPnl') {
      valA = parseFloat(a.latestPnl || '0')
      valB = parseFloat(b.latestPnl || '0')
    } else if (sortBy.value === 'estimatedGrowthRate') {
      valA = parseFloat(a.estimatedGrowthRate || '0')
      valB = parseFloat(b.estimatedGrowthRate || '0')
    }

    if (sortOrder.value === 'asc') {
      return valA - valB
    } else {
      return valB - valA
    }
  })
})

const isAllSelected = computed(() => {
  return positions.value.length > 0 && selectedCodes.value.length === positions.value.length
})

const toggleEditMode = () => {
  isEditMode.value = !isEditMode.value
  if (!isEditMode.value) {
    selectedCodes.value = []
  }
}

const handleCardClick = (pos) => {
  if (isEditMode.value) {
    const idx = selectedCodes.value.indexOf(pos.fundCode)
    if (idx >= 0) {
      selectedCodes.value.splice(idx, 1)
    } else {
      selectedCodes.value.push(pos.fundCode)
    }
  } else {
    goToDetail(pos)
  }
}

const toggleSelectAll = () => {
  if (isAllSelected.value) {
    selectedCodes.value = []
  } else {
    selectedCodes.value = positions.value.map(p => p.fundCode)
  }
}

const confirmBatchDelete = () => {
  if (selectedCodes.value.length === 0) return
  
  showConfirmDialog({
    title: '批量删除持仓',
    message: `确定要删除这 ${selectedCodes.value.length} 个基金持仓监控吗？此操作将彻底清除持仓及关联交易历史。`,
  }).then(async () => {
    try {
      loading.value = true
      await Promise.all(selectedCodes.value.map(code => deletePosition(code)))
      showToast('删除成功')
      selectedCodes.value = []
      isEditMode.value = false
      await loadData()
    } catch (e) {
      showToast('批量删除失败，请重试')
      console.error(e)
    } finally {
      loading.value = false
    }
  }).catch(() => {})
}

const loadData = async () => {
  if (!refreshing.value) loading.value = true
  try {
    const res = await fetchDashboard()
    if (res && res.funds) {
      dashboardData.value = res
      positions.value = res.funds

      // 自动同步持仓基金到自选列表（存放在 localStorage 中，但删除持仓不影响自选）
      try {
        let watchlistCodes = []
        const saved = localStorage.getItem('watchlist_funds')
        if (saved) watchlistCodes = JSON.parse(saved)
        
        let changed = false
        res.funds.forEach(f => {
          if (!watchlistCodes.includes(f.fundCode)) {
            watchlistCodes.push(f.fundCode)
            changed = true
          }
        })
        
        if (changed) {
          localStorage.setItem('watchlist_funds', JSON.stringify(watchlistCodes))
        }
      } catch (e) {
        console.error("Failed to sync positions to watchlist", e)
      }
    } else {
      positions.value = []
    }
    
    // 如果没有任何持仓，默认展开导入区域
    if (positions.value.length === 0) {
      showUploadZone.value = true
    }
  } catch (err) {
    showToast('获取持仓数据失败')
    console.error(err)
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

const onRefresh = () => {
  refreshing.value = true
  loadData()
}

onMounted(() => {
  loadData()
})

const toggleUploadZone = () => {
  showUploadZone.value = !showUploadZone.value
}

const onSearchFund = (code) => {
  router.push({
    path: `/fund/${code}`,
    query: { from: 'positions' }
  })
}

const onOcrImported = () => {
  loadData()
  showToast('导入持仓成功')
}

const goToDetail = (pos) => {
  router.push({
    path: `/fund/${pos.fundCode}`,
    query: { name: pos.fundName, from: 'positions' }
  })
}

// ── 格式化工具方法 ──────────────────────────────────────────────────

const formatVal = (val) => {
  const n = parseFloat(val)
  if (isNaN(n)) return val
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const formatValAbs = (val) => {
  const n = Math.abs(parseFloat(val))
  if (isNaN(n)) return val
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 动态大盘标签
const dashboardLabels = computed(() => {
  if (!dashboardData.value || !dashboardData.value.funds) {
    return { pnlLabel: '今日盈亏', percentLabel: '估值收益率' }
  }
  return getDashboardLabels(dashboardData.value.funds)
})

// 盈亏颜色和符号
const pnlColor = computed(() => {
  const pnl = parseFloat(dashboardData.value?.latestPnl || '0')
  return pnl > 0 ? 'text-rise' : pnl < 0 ? 'text-fall' : ''
})

const pnlPrefix = computed(() => {
  const pnl = parseFloat(dashboardData.value?.latestPnl || '0')
  return pnl >= 0 ? '+' : ''
})



const copyText = async (text) => {
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
</script>

<style scoped>
.positions-page {
  padding-bottom: var(--space-2xl);
}

.flex-header {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  align-items: flex-start;
  padding: var(--space-lg) var(--space-lg) var(--space-sm);
}

.header-title-section {
  width: 100%;
}

.header-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.header-actions {
  display: flex;
  align-items: center;
}

.btn-import-mini {
  border-radius: 20px;
  padding: 6px 14px;
  height: auto;
  font-size: 12px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px solid var(--color-border);
  box-shadow: 0 2px 4px rgba(0,0,0,0.02);
  transition: all var(--duration-fast);
  background: var(--color-bg-card-alt);
}

.btn-import-mini:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 6px rgba(0,0,0,0.04);
}


.ocr-wrap {
  padding: 0 var(--space-lg) var(--space-md);
  overflow: hidden;
}

/* 汇总面板样式 */
.dashboard-wrap {
  padding: 0 var(--space-lg) var(--space-md);
}

.dashboard-card {
  padding: 0;
  overflow: hidden;
}

.penetration-wrap {
  padding: 0 var(--space-lg) var(--space-md);
}

.trend-wrap {
  padding: 0 var(--space-lg) var(--space-md);
}

.dashboard__summary {
  display: flex;
  padding: var(--space-xl);
  gap: var(--space-md);
  background: linear-gradient(135deg, rgba(255,255,255,0.7), rgba(255,255,255,0.4));
  border-radius: var(--radius-lg);
}

.dashboard__metric {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.dashboard__metric-label {
  font-size: 11px;
  color: var(--color-text-secondary);
  font-weight: 500;
}

.dashboard__metric-value {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.market-ticker {
  display: flex;
  justify-content: space-between;
  padding: var(--space-md) var(--space-xl);
  border-top: 1px dashed var(--color-border);
  background: var(--color-bg-card-alt);
}
.ticker-item {
  font-size: 13px;
  font-weight: 600;
}
.analysis-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--color-accent);
  background: var(--color-accent-soft);
  padding: 4px 10px;
  border-radius: 16px;
  cursor: pointer;
  font-weight: 600;
  transition: opacity var(--duration-fast);
}
.analysis-btn:active {
  opacity: 0.7;
}

/* 列表与布局 */

.app__content.has-batch-bar {
  padding-bottom: 80px;
}

.position-list {
  display: flex;
  flex-direction: column;
  background: var(--color-bg-card);
  border-radius: var(--radius-md);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
  gap: 0;
}

/* 底部批量管理区 */
.list-footer-batch-container {
  margin-top: var(--space-md);
  margin-bottom: var(--space-lg);
  padding: 0 var(--space-xs);
}

.batch-manage-trigger-row {
  display: flex;
  justify-content: center;
  align-items: center;
}

.btn-batch-manage {
  border-radius: 20px;
  padding: 8px 24px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
  border-color: var(--color-border);
  background: var(--color-bg-card-alt);
  transition: all var(--duration-fast);
}

.btn-batch-manage:hover {
  border-color: var(--color-accent);
  background: var(--color-accent-soft);
  color: var(--color-accent);
}

.btn-batch-manage:active {
  transform: scale(0.95);
}

.list-header-row {
  display: flex;
  align-items: center;
  padding: 8px 16px;
  font-size: 12px;
  color: var(--color-text-secondary);
  border-bottom: 1px solid var(--color-border);
}

.batch-manage-panel {
  padding: var(--space-md);
  border-radius: var(--radius-lg);
  border: 1px dashed var(--color-border);
  background: var(--color-bg-card-alt);
}

.panel-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-md);
}

.panel-select-all {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  cursor: pointer;
  user-select: none;
}

.pos-checkbox {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid #b0b5bd;
  transition: all var(--duration-fast);
  position: relative;
}
.pos-checkbox.is-active {
  background: var(--color-accent);
  border-color: var(--color-accent);
}
.pos-checkbox.is-active::after {
  content: '';
  position: absolute;
  top: 4px;
  left: 7px;
  width: 4px;
  height: 8px;
  border-right: 2px solid white;
  border-bottom: 2px solid white;
  transform: rotate(45deg);
}

.panel-select-label {
  font-size: 13px;
  color: var(--color-text);
}

.btn-mini {
  padding: 4px 12px;
  font-size: 12px;
  border-radius: 12px;
  height: 26px;
}

.panel-action-row {
  display: flex;
  justify-content: center;
}

.btn-batch-delete-inline {
  width: 100%;
  border-radius: var(--radius-md);
  padding: 10px 0;
  font-size: 13px;
  font-weight: 700;
}

/* 动效 */
.collapse-enter-active,
.collapse-leave-active {
  transition: max-height var(--duration-normal) ease-in-out, opacity var(--duration-normal) ease-in-out;
  max-height: 350px;
}

.collapse-enter-from,
.collapse-leave-to {
  max-height: 0;
  opacity: 0;
}

/* 空状态 */

/* 悬浮表头 */
.sticky-headers {
  position: sticky;
  top: 0;
  z-index: 100;
  background: var(--color-bg, #f7f8fa);
  padding-top: var(--space-md);
  margin-top: calc(-1 * var(--space-md));
  padding-bottom: 4px;
}

/* 突破 van-pull-refresh 的 overflow:hidden 限制，否则 position: sticky 会失效 */
:deep(.van-pull-refresh) {
  overflow: visible !important;
}
:deep(.van-pull-refresh__track) {
  overflow: visible !important;
}

/* 列表头部条 */
.list-header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-md);
  padding: 0 var(--space-xs);
}

.list-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text-secondary);
  letter-spacing: -0.01em;
}

.list-sort-controls {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}


/* 升降序切换双箭头按钮 */
.sort-order-btn-double {
  background: var(--color-bg-card-alt);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 2px 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  line-height: 1;
  cursor: pointer;
  transition: all var(--duration-fast);
  gap: 1px;
  height: 28px;
}

.sort-order-btn-double:hover {
  border-color: var(--color-accent);
  background: var(--color-accent-soft);
}

.arrow-up, .arrow-down {
  font-size: 7px;
  color: #c0c0c0; /* 偏灰色 */
  transition: color var(--duration-fast);
  line-height: 1;
}

.arrow-up.arrow-active, .arrow-down.arrow-active {
  color: var(--color-accent); /* 亮色 */
  font-weight: bold;
}

/* Dropdown Animation */
.fade-slide-enter-active, .fade-slide-leave-active {
  transition: transform var(--duration-fast) var(--ease-spring), opacity var(--duration-fast);
}
.fade-slide-enter-from, .fade-slide-leave-to {
  transform: scale(0.95) translateY(-4px);
  opacity: 0;
}
.fade-slide-enter-to, .fade-slide-leave-from {
  transform: scale(1) translateY(0);
  opacity: 1;
}

.update-time-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 11px;
  color: var(--color-text-muted);
  padding: 8px 0 4px 0;
  margin-bottom: var(--space-sm);
}

.update-icon {
  font-size: 12px;
  opacity: 0.8;
}

/* ── Settings gear button ── */
.btn-settings-gear {
  margin-left: 8px;
  background: none;
  border: none;
  font-size: 18px;
  color: var(--color-text-muted);
  cursor: pointer;
  padding: 4px;
  line-height: 1;
  transition: color 0.2s, transform 0.3s;
  display: flex;
  align-items: center;
}

.btn-settings-gear:hover {
  color: var(--color-accent);
  transform: rotate(45deg);
}
</style>
