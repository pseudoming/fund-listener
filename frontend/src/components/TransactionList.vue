<template>
  <div class="tx-section card">
    <div class="tx-title font-semibold">交易明细</div>
    <van-empty v-if="transactions.length === 0" description="暂无交易记录" />
    <div class="tx-list" v-else>
      <div class="tx-item animate-in" v-for="tx in transactions" :key="tx.id">
        <div class="tx-left">
          <div class="tx-type" :class="tx.type">{{ tx.type === 'BUY' ? '买入' : '卖出' }}</div>
          <div class="tx-date">{{ tx.tradeDate }}</div>
        </div>
        <div class="tx-right">
          <div class="tx-shares text-num font-medium">{{ tx.type === 'BUY' ? '+' : '-' }}{{ formatNum(tx.shares) }} 份</div>
          <div class="tx-nav text-num text-secondary">净值: {{ tx.nav }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  transactions: { type: Array, default: () => [] }
})

const formatNum = (val) => {
  const n = parseFloat(val) || 0
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
</script>
<style scoped>
.tx-section {
  margin-top: var(--space-lg);
}

.tx-title {
  font-size: 16px;
  font-weight: 500;
  color: var(--color-text);
  margin-bottom: 12px;
}

.tx-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tx-item {
  background: var(--color-bg-input);
  border-radius: var(--radius-sm);
  padding: 12px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tx-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.tx-type {
  font-size: 14px;
  font-weight: 500;
}
.tx-type.BUY {
  color: var(--color-rise);
}
.tx-type.SELL {
  color: var(--color-fall);
}

.tx-date {
  font-size: 12px;
  color: var(--color-text-muted);
}

.tx-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.tx-shares {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text);
}

.tx-nav {
  font-size: 12px;
  color: var(--color-text-muted);
}
</style>
