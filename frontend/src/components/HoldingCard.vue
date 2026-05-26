<template>
  <div class="holding-list-item animate-in" :class="{ 'edit-mode': isEditMode, 'is-selected': isSelected }" @click="$emit('click')">
    <div class="pos-checkbox-container" v-if="isEditMode">
      <div class="pos-checkbox" :class="{ 'is-active': isSelected }"></div>
    </div>
    
    <div class="item-col col-name">
      <div class="fund-name-wrapper">
        <div class="fund-name" :title="data.fundName">{{ data.fundName }}</div>
      </div>
      <div class="fund-tags">
        <span class="tag tag--flat" v-if="data.fundCode">{{ data.fundCode }}</span>
        <span class="status-mini" :class="data.isSettled ? 'status-settled' : 'status-estimating'">
          {{ getStatusTagLabel(data.isSettled, data.navDate) }}
        </span>
        <span class="tag tag--fall ml-1" v-if="data.isOvervalued">高估</span>
      </div>
    </div>

    <div class="item-col col-market">
      <div class="val-market font-mono">{{ formatVal(marketValue) }}</div>
      <div class="val-pnl font-mono mt-1" :class="rateClass(data.latestPnl)">
        {{ formatWithSign(data.latestPnl) }}
      </div>
    </div>

    <div class="item-col col-holding">
      <div class="val-holding-amt font-mono" :class="rateClass(holdingPnl)">
        {{ formatWithSign(holdingPnl) }}
      </div>
      <div class="val-holding-rate font-mono mt-1" :class="rateClass(holdingPnlRate)">
        {{ formatWithSign(holdingPnlRate) }}%
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { getStatusTagLabel } from '../utils/formatters'

const props = defineProps({
  data: {
    type: Object,
    required: true
  },
  isEditMode: Boolean,
  isSelected: Boolean
})

defineEmits(['click'])

const marketValue = computed(() => {
  const shares = parseFloat(props.data.totalShares || '0')
  const nav = parseFloat(props.data.estimatedNav || props.data.yesterdayNav || '0')
  return shares * nav
})

const holdingPnl = computed(() => {
  const cost = parseFloat(props.data.totalCost || '0')
  return marketValue.value - cost
})

const holdingPnlRate = computed(() => {
  const cost = parseFloat(props.data.totalCost || '0')
  if (cost <= 0) return 0
  return (holdingPnl.value / cost) * 100
})

const formatVal = (val) => {
  const n = parseFloat(val)
  if (isNaN(n)) return val
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const formatWithSign = (val) => {
  const n = parseFloat(val || '0')
  const formatted = formatVal(Math.abs(n))
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
</script>

<style scoped>
.holding-list-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background: transparent;
  position: relative;
  transition: background-color var(--duration-fast);
  cursor: pointer;
}
.holding-list-item::after {
  content: '';
  position: absolute;
  left: 16px;
  right: 16px;
  bottom: 0;
  height: 1px;
  background-color: var(--color-border);
}
.holding-list-item:last-child::after {
  display: none;
}
.holding-list-item:hover {
  background: rgba(0,0,0,0.02);
}
.edit-mode {
  padding-left: 50px;
}
.is-selected {
  background: rgba(18, 162, 86, 0.05);
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

.item-col {
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.col-name {
  flex: 2;
  text-align: left;
  padding-right: 8px;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  height: 60px; /* Force exactly 3 lines roughly (2 for name, 1 for tags) */
}
.col-market {
  flex: 1.5;
  text-align: right;
  padding-right: 8px;
}
.col-holding {
  flex: 1.5;
  text-align: right;
}

.fund-name-wrapper {
  flex: 1;
  display: flex;
  align-items: flex-start;
}

.fund-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  line-height: 1.35;
  max-height: 2.7em; /* 2 lines exactly */
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: normal;
  word-break: break-all;
}
.fund-tags {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 2px;
  height: 16px; /* 1 line for tags */
}
.tag {
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 4px;
}
.status-mini {
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 4px;
  font-weight: 600;
}
.status-settled {
  background: rgba(25, 118, 210, 0.1);
  color: #1976d2;
}
.status-estimating {
  background: rgba(255, 152, 0, 0.15);
  color: #ef6c00;
}

.val-market {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
}
.val-pnl {
  font-size: 14px;
  font-weight: 600;
}

.val-holding-amt {
  font-size: 15px;
  font-weight: 600;
}
.val-holding-rate {
  font-size: 14px;
  font-weight: 600;
}
</style>
