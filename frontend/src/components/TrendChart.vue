<template>
  <div class="trend-card card animate-in">
    <div class="card-header">
      <h3 class="card-title">📈 {{ title }}</h3>
      <div class="trend-ranges">
        <span 
          v-for="r in ranges" 
          :key="r.val" 
          class="range-btn" 
          :class="{ active: currentRange === r.val }"
          @click="setRange(r.val)"
        >
          {{ r.label }}
        </span>
      </div>
    </div>

    <van-skeleton title :row="4" :loading="loading" class="skeleton-glow">
      <template v-if="!loading">
        <div v-if="!data || data.trend.length === 0" class="empty-state">
          暂无历史走势数据
        </div>
        <div v-else>
          <!-- 核心指标摘要 -->
          <div class="trend-summary">
            <div class="metric">
              <span class="metric-label">期间收益</span>
              <span class="metric-value font-mono" :class="pnlColor">
                {{ parseFloat(displayTotalReturn) >= 0 ? '+' : '' }}{{ displayTotalReturn }}%
              </span>
            </div>
            <div class="metric">
              <span class="metric-label">最大回撤</span>
              <span class="metric-value text-fall font-mono">
                -{{ displayMaxDrawdown }}%
              </span>
            </div>
          </div>
          <div class="drawdown-period" v-if="parseFloat(displayMaxDrawdown) > 0">
            回撤区间: {{ displayMaxDrawdownStartDate }} 至 {{ displayMaxDrawdownEndDate }}
          </div>

          <!-- ECharts 折线图容器 -->
          <div ref="chartRef" class="chart-container"></div>
        </div>
      </template>
    </van-skeleton>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick, watch, computed } from 'vue'
import { fetchFundTrend, fetchDashboardTrend } from '../api/fund'
import { showToast } from 'vant'
import * as echarts from 'echarts'

const props = defineProps({
  code: {
    type: String,
    default: null // 如果传了 code 就是单只基金，否则是整个组合
  },
  title: {
    type: String,
    default: '历史走势与回撤'
  }
})

const loading = ref(true)
const data = ref(null)
const chartRef = ref(null)
let chartInstance = null
const currentRange = ref(365) // default 1 year
const defaultRanges = [
  { label: '1月', val: 30 },
  { label: '3月', val: 90 },
  { label: '半年', val: 180 },
  { label: '1年', val: 365 },
  { label: '3年', val: 1095 },
  { label: '全部', val: 0 },
]

const ranges = computed(() => {
  if (!props.code) {
    return defaultRanges.filter(r => r.label !== '3年')
  }
  return defaultRanges
})

const setRange = (days) => {
  currentRange.value = days
  if (data.value && data.value.trend && data.value.trend.length > 0) {
    initChart()
  }
}

const filteredTrend = computed(() => {
  if (!data.value || !data.value.trend) return []
  let filtered = data.value.trend
  if (currentRange.value > 0) {
    const cutoffDate = new Date(Date.now() - currentRange.value * 24 * 60 * 60 * 1000)
    filtered = data.value.trend.filter(d => new Date(d.date) >= cutoffDate)
  }
  if (!filtered.length && data.value.trend.length) {
    filtered = data.value.trend.slice(-30) // fallback to last 30 days
  }
  return filtered
})

const displayTotalReturn = computed(() => {
  const trend = filteredTrend.value
  if (!trend.length) return '0.00'
  const firstVal = parseFloat(trend[0].value)
  const lastVal = parseFloat(trend[trend.length - 1].value)
  if (firstVal <= 0) return '0.00'
  return (((lastVal - firstVal) / firstVal) * 100).toFixed(2)
})

const displayMaxDrawdownData = computed(() => {
  const trend = filteredTrend.value
  if (!trend.length) return { maxDrawdown: '0.00', startDate: '', endDate: '' }
  
  let maxDrawdown = 0
  let peak = parseFloat(trend[0].value)
  let peakDate = trend[0].date
  
  let currentMaxDrawdownStartDate = peakDate
  let currentMaxDrawdownEndDate = peakDate
  
  for (let i = 1; i < trend.length; i++) {
    const currentVal = parseFloat(trend[i].value)
    if (currentVal > peak) {
      peak = currentVal
      peakDate = trend[i].date
    } else {
      const drawdown = (peak - currentVal) / peak * 100
      if (drawdown > maxDrawdown) {
        maxDrawdown = drawdown
        currentMaxDrawdownStartDate = peakDate
        currentMaxDrawdownEndDate = trend[i].date
      }
    }
  }
  
  return {
    maxDrawdown: maxDrawdown.toFixed(2),
    startDate: currentMaxDrawdownStartDate,
    endDate: currentMaxDrawdownEndDate
  }
})

const displayMaxDrawdown = computed(() => displayMaxDrawdownData.value.maxDrawdown)
const displayMaxDrawdownStartDate = computed(() => displayMaxDrawdownData.value.startDate)
const displayMaxDrawdownEndDate = computed(() => displayMaxDrawdownData.value.endDate)

const loadData = async () => {
  loading.value = true
  try {
    let res = null
    if (props.code) {
      res = await fetchFundTrend(props.code)
    } else {
      res = await fetchDashboardTrend()
    }
    data.value = res
  } catch (err) {
    showToast('走势获取失败')
    console.error(err)
  } finally {
    loading.value = false
  }

  if (data.value && data.value.trend && data.value.trend.length > 0) {
    await nextTick()
    initChart()
  }
}

const initChart = () => {
  if (!chartRef.value) return
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }

  const filtered = filteredTrend.value
  const dates = filtered.map(item => item.date)
  const values = filtered.map(item => parseFloat(item.value))

  const option = {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.9)',
      borderColor: '#e2e8f0',
      textStyle: { color: '#1e293b', fontSize: 12 },
      formatter: function (params) {
        return params[0].name + '<br/>' + params[0].marker + ' 净值/市值: ' + params[0].value
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '5%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates,
      axisLabel: {
        color: '#64748b',
        fontSize: 10,
        formatter: function (value) {
          const parts = value.split('-')
          if (parts.length === 3) {
            const [y, m, d] = parts
            if (currentRange.value === 30 || currentRange.value === 90) return `${m}-${d}`
            return `${y}-${m}`
          }
          return value
        }
      },
      axisLine: { lineStyle: { color: '#e2e8f0' } }
    },
    yAxis: {
      type: 'value',
      scale: true,
      axisLabel: { color: '#64748b', fontSize: 10 },
      splitLine: { lineStyle: { color: '#f1f5f9', type: 'dashed' } }
    },
    series: [
      {
        name: '走势',
        type: 'line',
        smooth: true,
        symbol: 'none',
        sampling: 'lttb',
        itemStyle: { color: '#3b82f6' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(59,130,246,0.3)' },
            { offset: 1, color: 'rgba(59,130,246,0.05)' }
          ])
        },
        data: values,
        markArea: {
          silent: true,
          itemStyle: {
            color: 'rgba(239, 68, 68, 0.1)' // 浅红色背景标示回撤
          },
          data: [
            [
              { xAxis: displayMaxDrawdownStartDate.value },
              { xAxis: displayMaxDrawdownEndDate.value }
            ]
          ]
        }
      }
    ]
  }

  chartInstance.setOption(option)
}

const handleResize = () => {
  if (chartInstance) {
    chartInstance.resize()
  }
}

const pnlColor = computed(() => {
  if (!data.value) return ''
  const v = parseFloat(displayTotalReturn.value)
  if (v > 0) return 'text-rise'
  if (v < 0) return 'text-fall'
  return ''
})

onMounted(() => {
  loadData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})
</script>

<style scoped>
.trend-card {
  margin-bottom: var(--space-lg);
  padding: var(--space-md);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-md);
}

.card-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text);
  margin: 0;
}

.trend-ranges {
  display: flex;
  background: rgba(0, 0, 0, 0.03);
  border-radius: 12px;
  padding: 2px;
}

.range-btn {
  font-size: 11px;
  color: var(--color-text-muted);
  padding: 4px 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
}

.range-btn.active {
  background: white;
  color: var(--color-text);
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(0,0,0,0.05);
}

.trend-summary {
  display: flex;
  gap: var(--space-lg);
  margin-bottom: 8px;
  padding: 12px;
  background: var(--color-bg-secondary);
  border-radius: var(--radius-md);
}

.metric {
  display: flex;
  flex-direction: column;
}

.metric-label {
  font-size: 11px;
  color: var(--color-text-secondary);
  margin-bottom: 4px;
}

.metric-value {
  font-size: 16px;
  font-weight: 700;
}

.drawdown-period {
  font-size: 11px;
  color: var(--color-text-muted);
  margin-bottom: var(--space-md);
  padding-left: 4px;
}

.chart-container {
  width: 100%;
  height: 240px;
}

.empty-state {
  padding: var(--space-xl) 0;
  text-align: center;
  color: var(--color-text-muted);
  font-size: 13px;
}
</style>
