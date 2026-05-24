<template>
  <div class="dashboard card">
    <!-- 加载中 -->
    <div v-if="loading" class="dashboard__loading">
      <div class="skeleton" style="width: 60%; height: 16px; margin: 0 auto;"></div>
      <div class="skeleton" style="width: 100%; height: 80px; margin-top: 12px;"></div>
      <div class="skeleton" style="width: 80%; height: 14px; margin: 12px auto 0;"></div>
    </div>

    <!-- 错误 -->
    <div v-else-if="error" class="dashboard__error" @click="load">
      <span>⚠️ {{ error }}</span>
      <span class="dashboard__retry">点击重试</span>
    </div>

    <!-- 数据 -->
    <template v-else-if="data">
      <!-- 标题行 -->
      <div class="dashboard__header">
        <span class="dashboard__title">估值仪表盘</span>
        <span class="dashboard__badge" v-if="data.reportDate">
          报告期 {{ data.reportDate }}
        </span>
      </div>

      <!-- 百分位仪表 -->
      <div class="dashboard__gauges">
        <!-- PE 百分位 -->
        <div class="gauge">
          <div class="gauge__label">
            <span>PE 百分位</span>
            <span class="gauge__value font-mono" :class="percentileColor(pePercentileValue)">
              {{ pePercentileText }}
            </span>
          </div>
          <PercentileBar
            :value="pePercentileValue"
            :status="historyData?.pePercentile?.status"
          />
          <div class="gauge__current font-mono">
            当前 PE {{ data.weightedPE || '--' }}
          </div>
        </div>

        <!-- PB 百分位 -->
        <div class="gauge">
          <div class="gauge__label">
            <span>PB 百分位</span>
            <span class="gauge__value font-mono" :class="percentileColor(pbPercentileValue)">
              {{ pbPercentileText }}
            </span>
          </div>
          <PercentileBar
            :value="pbPercentileValue"
            :status="historyData?.pbPercentile?.status"
          />
          <div class="gauge__current font-mono">
            当前 PB {{ data.weightedPB || '--' }}
          </div>
        </div>
      </div>

      <!-- 降级提示 -->
      <div
        v-if="degradationMessage"
        class="dashboard__degraded"
      >
        {{ degradationMessage }}
      </div>

      <!-- 历史走势迷你图 -->
      <div class="dashboard__chart" v-if="chartData.length > 1">
        <div class="dashboard__chart-header">
          <span class="dashboard__chart-label">PE/PB 走势</span>
          <span class="dashboard__chart-tabs">
            <button
              :class="{ active: chartMode === 'PE' }"
              @click="chartMode = 'PE'"
            >PE</button>
            <button
              :class="{ active: chartMode === 'PB' }"
              @click="chartMode = 'PB'"
            >PB</button>
          </span>
        </div>
        <Sparkline
          :points="chartPoints"
          :width="300"
          :height="80"
          :color="chartColor"
        />
      </div>

      <!-- 覆盖度 -->
      <div class="dashboard__coverage">
        <span class="text-muted">估值覆盖度</span>
        <span class="font-mono font-semibold">{{ data.coveragePercent }}%</span>
        <div class="dashboard__coverage-bar">
          <div
            class="dashboard__coverage-fill"
            :style="{ width: data.coveragePercent + '%' }"
          ></div>
        </div>
        <span class="text-muted" style="font-size: 11px;">
          {{ data.totalRatioCovered }} / {{ data.totalRatio }}% 仓位
        </span>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { fetchValuation, fetchValuationHistory } from '../api/valuation'
import PercentileBar from './PercentileBar.vue'
import Sparkline from './Sparkline.vue'

const props = defineProps({
  fundCode: { type: String, required: true }
})

const loading = ref(false)
const error = ref(null)
const data = ref(null)
const historyData = ref(null)
const chartMode = ref('PE')

const pePercentileValue = computed(() => {
  const info = historyData.value?.pePercentile
  if (info?.status === 'VALID') return parseFloat(info.value)
  return null
})

const pbPercentileValue = computed(() => {
  const info = historyData.value?.pbPercentile
  if (info?.status === 'VALID') return parseFloat(info.value)
  return null
})

const pePercentileText = computed(() => {
  const info = historyData.value?.pePercentile
  if (!info) return '--'
  if (info.status === 'VALID') return info.value + '%'
  if (info.status === 'DEGRADED') return '积累中'
  return '无数据'
})

const pbPercentileText = computed(() => {
  const info = historyData.value?.pbPercentile
  if (!info) return '--'
  if (info.status === 'VALID') return info.value + '%'
  if (info.status === 'DEGRADED') return '积累中'
  return '无数据'
})

const degradationMessage = computed(() => {
  const pe = historyData.value?.pePercentile
  const pb = historyData.value?.pbPercentile
  const msgs = []
  if (pe?.status === 'DEGRADED') {
    msgs.push(`PE 数据积累中，已有 ${pe.currentDays} 天，需 ${pe.requiredDays} 天`)
  }
  if (pb?.status === 'DEGRADED') {
    msgs.push(`PB 数据积累中，已有 ${pb.currentDays} 天，需 ${pb.requiredDays} 天`)
  }
  return msgs.join('；')
})

const chartData = computed(() => {
  if (!historyData.value?.snapshots) return []
  return historyData.value.snapshots
})

const chartPoints = computed(() => {
  const key = chartMode.value === 'PE' ? 'weightedPe' : 'weightedPb'
  return chartData.value
    .map(s => s[key] ? parseFloat(s[key]) : null)
})

const chartColor = computed(() => '#6366f1')

function percentileColor(val) {
  if (val == null) return ''
  if (val >= 70) return 'text-rise'
  if (val >= 30) return ''
  return 'text-fall'
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const [valRes, histRes] = await Promise.all([
      fetchValuation(props.fundCode),
      fetchValuationHistory(props.fundCode)
    ])
    data.value = valRes
    historyData.value = histRes
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (props.fundCode) load()
})

watch(() => props.fundCode, () => {
  if (props.fundCode) load()
})
</script>

<style scoped>
.dashboard {
  margin-top: var(--space-md);
  padding: 0;
  overflow: hidden;
}

.dashboard__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-md) var(--space-lg);
  border-bottom: 1px solid var(--color-border);
}

.dashboard__title {
  font-size: 13px;
  font-weight: 600;
}

.dashboard__badge {
  font-size: 11px;
  color: var(--color-accent);
  background: var(--color-accent-soft);
  padding: 2px 8px;
  border-radius: 20px;
  font-weight: 500;
}

/* 仪表区 */
.dashboard__gauges {
  padding: var(--space-lg);
  display: flex;
  flex-direction: column;
  gap: var(--space-xl);
}

.gauge__label {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-bottom: 6px;
}

.gauge__value {
  font-size: 18px;
  font-weight: 700;
}

.gauge__current {
  font-size: 11px;
  color: var(--color-text-muted);
  text-align: right;
  margin-top: 4px;
}

/* 降级提示 */
.dashboard__degraded {
  margin: 0 var(--space-lg) var(--space-md);
  padding: var(--space-sm) var(--space-md);
  background: rgba(251, 191, 36, 0.1);
  border: 1px solid rgba(251, 191, 36, 0.25);
  border-radius: var(--radius-sm);
  font-size: 11px;
  color: #fbbf24;
  text-align: center;
}

/* 走势图 */
.dashboard__chart {
  padding: 0 var(--space-lg) var(--space-md);
}

.dashboard__chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-sm);
}

.dashboard__chart-label {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.dashboard__chart-tabs {
  display: flex;
  gap: 2px;
  background: var(--color-bg-card-alt);
  border-radius: 6px;
  padding: 2px;
}

.dashboard__chart-tabs button {
  padding: 2px 10px;
  border: none;
  border-radius: 4px;
  font-size: 11px;
  font-family: var(--font-mono);
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  transition: all var(--duration-fast);
}

.dashboard__chart-tabs button.active {
  background: var(--color-accent);
  color: white;
}

/* 覆盖度 */
.dashboard__coverage {
  padding: var(--space-md) var(--space-lg);
  border-top: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.dashboard__coverage-bar {
  width: 100%;
  height: 4px;
  background: var(--color-bg-card-alt);
  border-radius: 2px;
  overflow: hidden;
  margin: 2px 0;
}

.dashboard__coverage-fill {
  height: 100%;
  background: var(--color-accent);
  border-radius: 2px;
  transition: width var(--duration-normal) var(--ease-out);
}

/* 加载/错误 */
.dashboard__loading {
  padding: var(--space-xl);
}

.dashboard__error {
  padding: var(--space-xl);
  text-align: center;
  font-size: 12px;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.dashboard__retry {
  display: block;
  font-size: 11px;
  color: var(--color-accent);
  margin-top: 4px;
}
</style>
