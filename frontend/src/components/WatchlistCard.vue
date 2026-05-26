<template>
  <div class="watchlist-card card animate-in" :class="{ 'edit-mode': isEditMode, 'is-selected': isSelected }" @click="$emit('click')">
    <div class="pos-checkbox-container" v-if="isEditMode">
      <div class="pos-checkbox" :class="{ 'is-active': isSelected }"></div>
    </div>
    
    <div class="card-header">
      <div class="card-title">
        {{ data.name }} 
        <span class="tag tag--flat" v-if="data.code">{{ data.code }}</span>
      </div>
      <span class="status-icon" :class="rateClass(data.estimatedGrowthRate)">
        {{ arrow(data.estimatedGrowthRate) }}
      </span>
    </div>

    <div class="card-body">
      <div class="val-row">
        <div class="rate font-mono" :class="rateClass(data.estimatedGrowthRate)">
          {{ formatWithSign(data.estimatedGrowthRate) }}%
        </div>
        <div class="nav font-mono" :class="rateClass(data.estimatedGrowthRate)">
          {{ data.estimatedNav || data.nav || '--' }}
        </div>
      </div>
      
      <div class="holding-info text-muted font-mono" v-if="data.totalShares && parseFloat(data.totalShares) > 0">
        持有 {{ formatNum(data.totalShares) }} · {{ getPnlLabel(data.isSettled, data.navDate) }} 
        <span :class="rateClass(data.latestPnl)">
          {{ formatWithSign(data.latestPnl) }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { getPnlLabel } from '../utils/formatters.js'

const props = defineProps({
  data: {
    type: Object,
    required: true
  },
  isEditMode: Boolean,
  isSelected: Boolean
})

defineEmits(['click'])

const formatNum = (val) => {
  const n = parseFloat(val)
  if (isNaN(n)) return val
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const formatWithSign = (val) => {
  const n = parseFloat(val || '0')
  const formatted = formatNum(Math.abs(n))
  if (n > 0) return `+${formatted}`
  if (n < 0) return `-${formatted}`
  return '0.00'
}

const rateClass = (val) => {
  const n = parseFloat(val || '0')
  if (n > 0) return 'text-rise'
  if (n < 0) return 'text-fall'
  return ''
}

const arrow = (val) => {
  const n = parseFloat(val || '0')
  if (n > 0) return '↑'
  if (n < 0) return '↓'
  return '-'
}
</script>

<style scoped>
.watchlist-card {
  display: flex;
  flex-direction: column;
  padding: var(--space-lg);
  gap: var(--space-sm);
  background: var(--color-bg-card);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-card);
  position: relative;
  overflow: hidden;
  transition: transform var(--duration-fast) var(--ease-spring), border-color var(--duration-fast);
  cursor: pointer;
}
.edit-mode {
  padding-left: 50px;
}
.is-selected {
  border-color: var(--color-accent);
}
.pos-checkbox-container {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  background: rgba(0,0,0,0.01);
}
.pos-checkbox {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid var(--color-border);
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

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-title {
  font-size: 16px;
  font-weight: 700;
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 8px;
  line-height: 1.4;
}
.tag {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 12px;
}
.status-icon {
  font-size: 18px;
  font-weight: 800;
}
.val-row {
  display: flex;
  align-items: baseline;
  gap: var(--space-md);
  margin-top: var(--space-xs);
}
.rate {
  font-size: 32px;
  font-weight: 800;
}
.nav {
  font-size: 18px;
  font-weight: 600;
}
.holding-info {
  font-size: 12px;
  margin-top: var(--space-sm);
}
</style>
