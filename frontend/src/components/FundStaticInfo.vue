<template>
  <div class="overview-card card">
    <div class="metrics" v-if="!isWatchlist">
      <div class="metric">
        <div class="label">持有份额</div>
        <div class="val text-num">{{ formatNum(position?.totalShares || '0') }}</div>
      </div>
      <div class="metric">
        <div class="label">持仓成本</div>
        <div class="val text-num">¥{{ formatNum(position?.totalCost || '0') }}</div>
      </div>
      <div class="metric">
        <div class="label">当前 / 成本净值</div>
        <div class="val text-num">
          {{ currentNav || '--' }} / {{ position?.avgCostNav || '0.0000' }}
        </div>
      </div>
    </div>
    
    <div class="metrics" v-else>
      <div class="metric">
        <div class="label">基金类型</div>
        <div class="val">{{ fundType || '--' }}</div>
      </div>
      <div class="metric" v-if="linkedEtfCode">
        <div class="label">场内挂钩标的</div>
        <div class="val">{{ linkedEtfName || '--' }}</div>
      </div>
      <div class="metric" v-else>
        <div class="label">基金经理</div>
        <div class="val">{{ fundManager || '--' }}</div>
      </div>
      <div class="metric">
        <div class="label">起购金额</div>
        <div class="val text-num">10.00元</div>
      </div>
    </div>
    
    <div class="action-buttons">
      <button class="btn btn--primary" style="flex: 1;" @click="$emit('buy')">买入</button>
      <button class="btn btn--secondary" style="flex: 1;" :disabled="!hasPosition" @click="$emit('sell')">卖出</button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  mode: { type: String, default: 'position' },
  hasPosition: { type: Boolean, default: false },
  position: { type: Object, default: null },
  currentNav: { type: String, default: '' },
  fundType: { type: String, default: '' },
  fundManager: { type: String, default: '' },
  linkedEtfCode: { type: String, default: '' },
  linkedEtfName: { type: String, default: '' }
})

const isWatchlist = computed(() => props.mode === 'watchlist')

const formatNum = (val) => {
  const n = parseFloat(val) || 0
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
</script>
<style scoped>
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
</style>
