<template>
  <div class="trend-chart-wrapper card animate-in">
    <div class="trend-chart-header">
      <h3 class="trend-chart-title">历史走势</h3>
      
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
    
    <div class="trend-chart-container" ref="chartRef"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, markRaw } from 'vue'
import * as echarts from 'echarts'
import { fetchFundTrend } from '../api/fund'
import { showToast } from 'vant'

const props = defineProps({
  code: { type: String, required: true }
})

const chartRef = ref(null)
const chartInstance = ref(null)
const rawData = ref([])
const currentRange = ref(365) // default 1 year (365 days)

const ranges = [
  { label: '1月', val: 30 },
  { label: '3月', val: 90 },
  { label: '半年', val: 180 },
  { label: '1年', val: 365 },
  { label: '3年', val: 1095 },
  { label: '全部', val: 0 },
]

const loadData = async () => {
  try {
    const data = await fetchFundTrend(props.code)
    if (data && data.length) {
      rawData.value = data
      updateChart()
    }
  } catch (err) {
    showToast('加载历史走势失败')
  }
}

const setRange = (days) => {
  currentRange.value = days
  updateChart()
}

const updateChart = () => {
  if (!chartInstance.value) return
  if (!rawData.value.length) return

  let filtered = rawData.value
  if (currentRange.value > 0) {
    const cutoff = Date.now() - currentRange.value * 24 * 60 * 60 * 1000
    filtered = rawData.value.filter(d => d.x >= cutoff)
  }
  
  if (!filtered.length) filtered = rawData.value.slice(-30) // fallback

  // Fill in missing non-trading days to keep physical distance proportional to time
  const filledData = []
  if (filtered.length > 0) {
    let lastItem = filtered[0]
    let currentTimestamp = new Date(lastItem.x).setHours(0, 0, 0, 0)
    const maxTimestamp = new Date(filtered[filtered.length - 1].x).setHours(0, 0, 0, 0)
    let i = 0

    while (currentTimestamp <= maxTimestamp) {
      // Advance 'i' if the next data point is on or before currentTimestamp
      while (i < filtered.length - 1 && new Date(filtered[i + 1].x).setHours(0, 0, 0, 0) <= currentTimestamp) {
        i++
      }
      
      const realItem = filtered[i]
      const isRealDay = new Date(realItem.x).setHours(0, 0, 0, 0) === currentTimestamp
      
      filledData.push({
        x: currentTimestamp,
        y: realItem.y,
        equityReturn: isRealDay ? realItem.equityReturn : 0 // 0% return on holidays
      })
      
      currentTimestamp += 24 * 60 * 60 * 1000
    }
  }

  const dates = filledData.map(d => {
    const date = new Date(d.x)
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
  })
  
  const navs = filledData.map(d => d.y)
  // Determine if it's rising or falling based on first and last point
  const isRise = navs[navs.length - 1] >= navs[0]
  const lineColor = isRise ? '#ef4444' : '#10b981' // cozy red or green
  const areaColorTop = isRise ? 'rgba(239, 68, 68, 0.2)' : 'rgba(16, 185, 129, 0.2)'
  const areaColorBottom = isRise ? 'rgba(239, 68, 68, 0)' : 'rgba(16, 185, 129, 0)'

  const option = {
    grid: {
      left: 10,
      right: 20,
      top: 20,
      bottom: 10,
      containLabel: true
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross', label: { show: false } },
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: 'var(--color-border)',
      textStyle: { color: 'var(--color-text)', fontSize: 12 },
      formatter: (params) => {
        const p = params[0]
        const dataIndex = p.dataIndex
        const item = filledData[dataIndex]
        const rate = item.equityReturn > 0 ? `+${item.equityReturn}%` : `${item.equityReturn}%`
        const color = item.equityReturn > 0 ? 'var(--color-up)' : (item.equityReturn < 0 ? 'var(--color-down)' : 'inherit')
        
        return `<div style="font-weight:600;margin-bottom:4px">${p.axisValue}</div>
                <div>单位净值：<span style="font-weight:600">${item.y.toFixed(4)}</span></div>
                <div>日涨跌幅：<span style="color:${color};font-weight:600">${rate}</span></div>`
      }
    },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: 'var(--color-text-muted)',
        fontSize: 10,
        formatter: (value) => {
          const [y, m, d] = value.split('-');
          if (currentRange.value === 30 || currentRange.value === 90) return `${m}-${d}`;
          return `${y}-${m}`;
        }
      }
    },
    yAxis: {
      type: 'value',
      scale: true,
      splitLine: {
        lineStyle: { color: 'rgba(0,0,0,0.03)' }
      },
      axisLabel: {
        color: 'var(--color-text-muted)',
        fontSize: 10,
        formatter: (val) => val.toFixed(2)
      }
    },
    series: [
      {
        name: '单位净值',
        type: 'line',
        data: navs,
        smooth: 0.25,
        symbol: 'none',
        lineStyle: {
          color: lineColor,
          width: 2
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: areaColorTop },
            { offset: 1, color: areaColorBottom }
          ])
        }
      }
    ]
  }

  chartInstance.value.setOption(option)
}

const resizeHandler = () => {
  if (chartInstance.value) chartInstance.value.resize()
}

onMounted(() => {
  chartInstance.value = markRaw(echarts.init(chartRef.value))
  window.addEventListener('resize', resizeHandler)
  if (props.code) {
    loadData()
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', resizeHandler)
  if (chartInstance.value) {
    chartInstance.value.dispose()
    chartInstance.value = null
  }
})

watch(() => props.code, () => {
  if (props.code) loadData()
})
</script>

<style scoped>
.trend-chart-wrapper {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.trend-chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.trend-chart-title {
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

.trend-chart-container {
  width: 100%;
  height: 200px;
}
</style>
