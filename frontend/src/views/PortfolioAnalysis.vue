<template>
  <div class="portfolio-analysis-page safe-bottom animate-in">
    <header class="page-header flex-header">
      <div class="header-title-section">
        <div class="header-title-row">
          <div style="display: flex; align-items: baseline; gap: 8px;">
            <button class="back-btn" @click="$router.back()">
              <svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
                <path d="M15.41 16.59L10.83 12l4.58-4.59L14 6l-6 6 6 6 1.41-1.41z"/>
              </svg>
            </button>
            <h1 class="page-header__title" style="margin-bottom: 0;">持仓分析</h1>
          </div>
        </div>
      </div>
    </header>

    <div class="trend-wrap">
      <TrendChart title="组合历史走势" />
    </div>

    <div class="penetration-wrap">
      <PortfolioPenetration />
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import TrendChart from '../components/TrendChart.vue'
import PortfolioPenetration from '../components/PortfolioPenetration.vue'

const router = useRouter()
</script>

<style scoped>
.portfolio-analysis-page {
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

.back-btn {
  background: none;
  border: none;
  color: var(--color-text);
  cursor: pointer;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.trend-wrap, .penetration-wrap {
  padding: 0 var(--space-lg) var(--space-md);
}
</style>
