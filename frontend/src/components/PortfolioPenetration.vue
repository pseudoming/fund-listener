<template>
  <div class="penetration-card card animate-in">
    <div class="card-header">
      <h3 class="card-title">🔍 底层持仓穿透 (Top 10)</h3>
      <span class="card-subtitle" v-if="!loading && data">穿透总市值: ¥{{ data.totalMarketValue }}</span>
    </div>

    <van-skeleton title :row="4" :loading="loading" class="skeleton-glow">
      <template v-if="!loading">
        <div v-if="!data || data.topStocks.length === 0" class="empty-state">
          暂无重仓股数据，请确保已拉取基金重仓信息
        </div>
        <div v-else>
          <!-- ECharts 饼图容器 -->
          <div ref="chartRef" class="chart-container"></div>

          <!-- 列表数据 -->
          <div class="stock-list">
            <div class="stock-item list-header-row">
              <span class="col-name">股票名称</span>
              <span class="col-ratio text-right">占比(金额)</span>
              <span class="col-change text-right">今日涨跌</span>
            </div>
            <div v-for="(item, index) in data.topStocks" :key="item.stockCode" class="stock-item">
              <span class="col-name font-medium">
                <span class="rank-badge" :class="'rank-' + (index + 1)">{{ index + 1 }}</span>
                <span>
                  {{ item.stockName }}
                  <span class="text-muted" style="font-size: 10px; font-weight: normal; margin-left: 2px;">{{ item.marketType }}</span>
                </span>
              </span>
              <span class="col-ratio text-right font-mono">
                <div>{{ item.exposureRatio }}%</div>
                <div class="text-muted" style="font-size:10px">¥{{ item.totalExposure }}</div>
              </span>
              <span class="col-change text-right font-mono" :class="getColorClass(item.growthRate)">
                <div>{{ formatGrowthRate(item.growthRate) }}</div>
                <div style="font-size:10px">{{ formatChangeAmount(item.changeAmount) }}</div>
              </span>
            </div>
          </div>
        </div>
      </template>
    </van-skeleton>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { fetchPenetration } from '../api/fund'
import { showToast } from 'vant'
import * as echarts from 'echarts'

const loading = ref(true)
const data = ref(null)
const chartRef = ref(null)
let chartInstance = null

const getColorClass = (val) => {
  if (!val) return 'text-muted'
  const num = parseFloat(val)
  if (num > 0) return 'text-danger'
  if (num < 0) return 'text-success'
  return 'text-muted'
}

const formatGrowthRate = (val) => {
  if (!val) return '--'
  const num = parseFloat(val)
  return num > 0 ? `+${val}%` : `${val}%`
}

const formatChangeAmount = (val) => {
  if (!val) return '--'
  const num = parseFloat(val)
  return num > 0 ? `+¥${val}` : `-¥${Math.abs(num)}`
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await fetchPenetration()
    data.value = res
  } catch (err) {
    showToast('穿透分析获取失败')
    console.error(err)
  } finally {
    loading.value = false
  }

  if (data.value && data.value.topStocks && data.value.topStocks.length > 0) {
    await nextTick()
    initChart()
  }
}

const initChart = () => {
  if (!chartRef.value) return
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }

  const chartData = data.value.topStocks.map(item => ({
    name: item.stockName,
    value: parseFloat(item.totalExposure)
  }))

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: ¥{c} ({d}%)',
      backgroundColor: 'rgba(255, 255, 255, 0.9)',
      borderColor: '#e2e8f0',
      textStyle: { color: '#1e293b', fontSize: 12 }
    },
    series: [
      {
        name: '底层持仓',
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: true,
        itemStyle: {
          borderRadius: 4,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: false,
          position: 'center'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 14,
            fontWeight: 'bold'
          }
        },
        labelLine: {
          show: false
        },
        data: chartData
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
.penetration-card {
  margin-bottom: var(--space-lg);
  padding: var(--space-md);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: var(--space-md);
}

.card-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text);
  margin: 0;
}

.card-subtitle {
  font-size: 12px;
  color: var(--color-text-secondary);
  font-weight: 500;
}

.chart-container {
  width: 100%;
  height: 220px;
  margin-bottom: var(--space-md);
}

.empty-state {
  padding: var(--space-xl) 0;
  text-align: center;
  color: var(--color-text-muted);
  font-size: 13px;
}

.stock-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stock-item {
  display: flex;
  align-items: center;
  padding: 8px 4px;
  border-bottom: 1px solid var(--color-border-light);
  font-size: 13px;
}

.stock-item:last-child {
  border-bottom: none;
}

.list-header-row {
  color: var(--color-text-muted);
  font-size: 11px;
  font-weight: 500;
  border-bottom: 1px solid var(--color-border);
  padding-bottom: 6px;
  margin-bottom: 4px;
}

.col-name {
  flex: 3;
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.col-ratio {
  flex: 1.5;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.col-change {
  flex: 1.5;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.text-danger {
  color: var(--color-danger);
}

.text-success {
  color: var(--color-success);
}

.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 700;
  background: var(--color-bg-secondary);
  color: var(--color-text-muted);
}

.rank-1 { background: #fef0f0; color: #f56c6c; }
.rank-2 { background: #fdf6ec; color: #e6a23c; }
.rank-3 { background: #f0f9eb; color: #67c23a; }
</style>
