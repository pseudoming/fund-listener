<template>
  <!-- 骨架屏加载中 -->
  <div v-if="loading" class="holdings-panel card">
    <div class="holdings-panel__header">
      <span class="holdings-panel__title">重仓股</span>
      <div class="skeleton" style="width: 80px; height: 18px; border-radius: 4px;"></div>
    </div>
    <div class="holdings-panel__list">
      <div v-for="i in 5" :key="i" class="holdings-panel__row-skeleton">
        <div class="skeleton" style="width: 28px; height: 14px;"></div>
        <div class="skeleton" style="width: 60px; height: 14px;"></div>
        <div class="skeleton" style="width: 100%; height: 6px;"></div>
      </div>
    </div>
  </div>

  <!-- 错误 -->
  <div v-else-if="error" class="holdings-panel card holdings-panel--error" @click="load">
    <div class="holdings-panel__error-icon">⚠️</div>
    <div class="holdings-panel__error-text">{{ error }}</div>
    <div class="holdings-panel__error-hint">点击重试</div>
  </div>

  <!-- 空数据 -->
  <div v-else-if="!data || data.holdings.length === 0" class="holdings-panel card holdings-panel--empty">
    <div class="holdings-panel__empty-text">暂无重仓股数据</div>
  </div>

  <!-- 正常数据 -->
  <div v-else class="holdings-panel card animate-in">
    <!-- 标题行 + 报告日期 -->
    <div class="holdings-panel__header">
      <span class="holdings-panel__title">前十大重仓股</span>
      <span class="holdings-panel__date-badge">{{ reportDateLabel }}</span>
    </div>

    <!-- 重仓股列表 -->
    <div class="holdings-panel__list">
      <div
        v-for="(item, idx) in data.holdings"
        :key="item.stockCode"
        class="holdings-item"
      >
        <!-- 序号 -->
        <span class="holdings-item__index">{{ idx + 1 }}</span>

        <!-- 代码 + 名称 -->
        <div class="holdings-item__info" @click="copyText(item.stockCode)" title="点击复制股票代码">
          <div class="holdings-item__name-row">
            <span class="holdings-item__name">{{ item.stockName }}</span>
            <span v-if="getMarketTag(item.stockCode)" class="market-badge" :class="getMarketTag(item.stockCode).class">
              {{ getMarketTag(item.stockCode).text }}
            </span>
          </div>
          <span class="holdings-item__subcode font-mono">{{ item.stockCode }}</span>
        </div>

        <!-- 占比 + 进度条 -->
        <div class="holdings-item__ratio">
          <span class="holdings-item__ratio-text font-mono">{{ item.ratio }}%</span>
          <div class="holdings-item__bar-track">
            <div
              class="holdings-item__bar-fill"
              :style="{ width: Math.min(parseFloat(item.ratio), 100) + '%' }"
            ></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 覆盖度提示 -->
    <div class="holdings-panel__footer text-muted">
      合计覆盖 {{ totalRatio }}%
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { fetchFundHoldings } from '../api/fund'
import { showToast } from 'vant'

const props = defineProps({
  fundCode: { type: String, required: true },
  preloadedData: { type: Object, default: null }
})

const loading = ref(false)
const error = ref(null)
const data = ref(null)

const reportDateLabel = computed(() => {
  if (!data.value?.reportDate) return ''
  return `报告期 ${data.value.reportDate}`
})

const totalRatio = computed(() => {
  if (!data.value?.holdings) return 0
  return data.value.holdings
    .reduce((sum, h) => sum + parseFloat(h.ratio || 0), 0)
    .toFixed(2)
})

async function load() {
  if (props.preloadedData) {
    data.value = props.preloadedData
    return
  }
  loading.value = true
  error.value = null
  try {
    data.value = await fetchFundHoldings(props.fundCode)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function getMarketTag(code) {
  if (!code) return null
  const cleanCode = code.toUpperCase().trim()
  
  if (cleanCode.endsWith('.US') || /^[A-Z]+$/.test(cleanCode)) {
    return { text: '美股', class: 'tag-us' }
  }
  if (cleanCode.endsWith('.HK') || /^\d{5}$/.test(cleanCode)) {
    return { text: '港股', class: 'tag-hk' }
  }
  if (/^\d{6}$/.test(cleanCode) || cleanCode.endsWith('.SH') || cleanCode.endsWith('.SZ')) {
    if (cleanCode.startsWith('688')) {
      return { text: '科创板', class: 'tag-star' }
    }
    if (cleanCode.startsWith('300') || cleanCode.startsWith('301')) {
      return { text: '创业板', class: 'tag-gem' }
    }
    return { text: 'A股', class: 'tag-a' }
  }
  return null
}

function copyText(text) {
  if (!text) return
  const cleanText = text.replace(/\.(SH|SZ|HK|US)$/i, '')
  navigator.clipboard.writeText(cleanText).then(() => {
    showToast(`代码 ${cleanText} 已复制`)
  }).catch(() => {
    const input = document.createElement('input')
    input.setAttribute('value', cleanText)
    document.body.appendChild(input)
    input.select()
    document.execCommand('copy')
    document.body.removeChild(input)
    showToast(`代码 ${cleanText} 已复制`)
  })
}

onMounted(() => {
  if (props.fundCode) load()
})

watch(() => props.fundCode, () => {
  if (props.fundCode) load()
})

watch(() => props.preloadedData, (newVal) => {
  if (newVal) {
    data.value = newVal
  } else if (props.fundCode) {
    load()
  }
}, { deep: true })
</script>

<style scoped>
.holdings-panel {
  margin-top: var(--space-md);
  padding: 0;
  overflow: hidden;
}

/* 标题行 */
.holdings-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-md) var(--space-lg);
  border-bottom: 1px solid var(--color-border);
}

.holdings-panel__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
  letter-spacing: 0.02em;
}

.holdings-panel__date-badge {
  font-size: 11px;
  color: var(--color-accent);
  background: var(--color-accent-soft);
  padding: 2px 8px;
  border-radius: 20px;
  font-weight: 500;
  white-space: nowrap;
}

/* 列表 */
.holdings-panel__list {
  padding: var(--space-sm) var(--space-lg);
}

.holdings-item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-sm) 0;
}

.holdings-item + .holdings-item {
  border-top: 1px solid rgba(255, 255, 255, 0.03);
}

/* 序号 */
.holdings-item__index {
  width: 18px;
  font-size: 11px;
  color: var(--color-text-muted);
  font-weight: 600;
  flex-shrink: 0;
  text-align: center;
}

/* 代码 + 名称 */
.holdings-item__info {
  flex: 0 0 160px;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  cursor: pointer;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
  transition: opacity var(--duration-fast);
}

.holdings-item__info:active {
  opacity: 0.6;
}

.holdings-item__name-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.holdings-item__name {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.3;
}

.holdings-item__subcode {
  font-size: 11px;
  color: var(--color-text-muted);
  line-height: 1.2;
}

/* 市场标签 */
.market-badge {
  font-size: 9px;
  font-weight: 700;
  padding: 1px 4px;
  border-radius: 3px;
  white-space: nowrap;
  line-height: 1;
}

.tag-a {
  background: rgba(225, 29, 72, 0.08);
  color: var(--color-rise);
}

.tag-hk {
  background: rgba(13, 148, 136, 0.08);
  color: #0d9488;
}

.tag-us {
  background: rgba(59, 130, 246, 0.08);
  color: #3b82f6;
}

.tag-star {
  background: rgba(217, 119, 6, 0.08);
  color: var(--color-accent);
}

.tag-gem {
  background: rgba(147, 51, 234, 0.08);
  color: #9333ea;
}

/* 占比 + 进度条 */
.holdings-item__ratio {
  flex: 1;
  min-width: 0;
}

.holdings-item__ratio-text {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-secondary);
  display: block;
  text-align: right;
  margin-bottom: 3px;
}

.holdings-item__bar-track {
  width: 100%;
  height: 4px;
  background: var(--color-bg-card-alt);
  border-radius: 2px;
  overflow: hidden;
}

.holdings-item__bar-fill {
  height: 100%;
  background: var(--color-accent);
  border-radius: 2px;
  transition: width var(--duration-normal) var(--ease-out);
  min-width: 2px;
}

/* 底部覆盖度 */
.holdings-panel__footer {
  padding: var(--space-sm) var(--space-lg) var(--space-md);
  font-size: 11px;
  text-align: right;
  border-top: 1px solid var(--color-border);
}

/* 骨架屏 */
.holdings-panel__row-skeleton {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 10px 0;
}

/* 错误 */
.holdings-panel--error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  cursor: pointer;
  padding: var(--space-xl);
}

.holdings-panel__error-icon {
  font-size: 24px;
  margin-bottom: var(--space-sm);
}

.holdings-panel__error-text {
  font-size: 12px;
  color: var(--color-text-secondary);
  text-align: center;
}

.holdings-panel__error-hint {
  font-size: 11px;
  color: var(--color-accent);
  margin-top: var(--space-xs);
}

/* 空状态 */
.holdings-panel--empty {
  min-height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-xl);
}

.holdings-panel__empty-text {
  font-size: 12px;
  color: var(--color-text-muted);
}
</style>
