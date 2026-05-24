<template>
  <div class="page-container settings-page animate-in">
    <!-- Header -->
    <header class="page-header settings-header">
      <div class="header-left">
        <button class="btn btn--secondary back-btn-mini" @click="goBack">
          <span>←</span> 返回
        </button>
      </div>
      <h2 class="header-title">设置</h2>
      <div class="header-right"></div>
    </header>

    <div class="settings-content">
      <!-- QDII 显示模式 -->
      <div class="settings-section">
        <div class="settings-section-title">QDII 基金显示模式</div>
        <div class="settings-section-desc">
          QDII 基金因投资海外市场，存在 T+2 的净值结算延迟。您可以选择数据的展示方式。
        </div>

        <div class="settings-options">
          <label
            class="settings-option-card"
            :class="{ active: qdiiMode === 'alipay' }"
            @click="selectMode('alipay')"
          >
            <div class="option-radio">
              <div class="radio-outer">
                <div class="radio-inner" v-if="qdiiMode === 'alipay'"></div>
              </div>
            </div>
            <div class="option-body">
              <div class="option-title">
                与支付宝对齐
                <span class="option-badge">推荐</span>
              </div>
              <div class="option-desc">
                使用官方已确认的结算数据，日期标签与支付宝保持一致。数据更权威可靠，适合大多数用户。
              </div>
            </div>
          </label>

          <label
            class="settings-option-card"
            :class="{ active: qdiiMode === 'realtime' }"
            @click="selectMode('realtime')"
          >
            <div class="option-radio">
              <div class="radio-outer">
                <div class="radio-inner" v-if="qdiiMode === 'realtime'"></div>
              </div>
            </div>
            <div class="option-body">
              <div class="option-title">实时盘中前瞻</div>
              <div class="option-desc">
                使用盘中估算数据，数据更新更早（可超前支付宝一天），但未经官方最终确认，存在微小误差。
              </div>
            </div>
          </label>
        </div>
      </div>

      <!-- 未来可扩展的更多设置组 -->
      <div class="settings-section">
        <div class="settings-section-title">关于</div>
        <div class="settings-about-row">
          <span class="about-label">版本</span>
          <span class="about-value font-mono">1.0.0</span>
        </div>
        <div class="settings-about-row">
          <span class="about-label">数据来源</span>
          <span class="about-value">天天基金（免费公开 API）</span>
        </div>
      </div>
    </div>

    <!-- 保存状态提示 -->
    <transition name="fade">
      <div class="save-toast" v-if="showSaved">
        <span>✓</span> 设置已保存
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const qdiiMode = ref('alipay')
const showSaved = ref(false)
const loading = ref(true)

const API_BASE = import.meta.env.VITE_API_BASE || ''

onMounted(async () => {
  try {
    const res = await fetch(`${API_BASE}/api/config`)
    if (res.ok) {
      const data = await res.json()
      qdiiMode.value = data.qdii_display_mode || 'alipay'
    }
  } catch (e) {
    console.warn('Failed to load config:', e)
  } finally {
    loading.value = false
  }
})

async function selectMode(mode) {
  if (qdiiMode.value === mode) return
  qdiiMode.value = mode

  try {
    await fetch(`${API_BASE}/api/config`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ qdii_display_mode: mode })
    })
    showSaved.value = true
    setTimeout(() => { showSaved.value = false }, 1500)
  } catch (e) {
    console.error('Failed to save config:', e)
  }
}

function goBack() {
  if (window.history.state && window.history.state.back) {
    router.back()
  } else {
    router.push('/')
  }
}
</script>

<style scoped>
.settings-page {
  min-height: 100vh;
  background: var(--color-bg);
}

.settings-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-md) var(--space-lg);
  background: var(--color-bg-card);
  border-bottom: 1px solid var(--color-border);
  position: sticky;
  top: 0;
  z-index: 10;
}

.header-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0;
}

.header-right {
  width: 60px; /* 占位，让标题居中 */
}

.settings-content {
  padding: var(--space-lg);
}

/* ── Section ── */
.settings-section {
  margin-bottom: var(--space-2xl);
}

.settings-section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: var(--space-xs);
}

.settings-section-desc {
  font-size: 12px;
  color: var(--color-text-muted);
  line-height: 1.5;
  margin-bottom: var(--space-md);
}

/* ── Option Cards ── */
.settings-options {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.settings-option-card {
  display: flex;
  align-items: flex-start;
  gap: var(--space-md);
  padding: var(--space-lg);
  background: var(--color-bg-card);
  border: 2px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.25s ease;
  box-shadow: var(--shadow-soft);
}

.settings-option-card:hover {
  border-color: var(--color-accent);
  transform: translateY(-1px);
  box-shadow: var(--shadow-card);
}

.settings-option-card.active {
  border-color: var(--color-accent);
  background: var(--color-accent-soft);
}

/* ── Radio ── */
.option-radio {
  flex-shrink: 0;
  padding-top: 2px;
}

.radio-outer {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid var(--color-text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: border-color 0.2s;
}

.settings-option-card.active .radio-outer {
  border-color: var(--color-accent);
}

.radio-inner {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--color-accent);
  animation: radioPopIn 0.2s ease;
}

@keyframes radioPopIn {
  0% { transform: scale(0); }
  100% { transform: scale(1); }
}

/* ── Option Body ── */
.option-body {
  flex: 1;
}

.option-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: var(--space-xs);
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.option-badge {
  font-size: 10px;
  font-weight: 600;
  color: white;
  background: var(--color-accent);
  padding: 1px 6px;
  border-radius: 4px;
  letter-spacing: 0.5px;
}

.option-desc {
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.6;
}

/* ── About Section ── */
.settings-about-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-md) 0;
  border-bottom: 1px solid var(--color-border);
}

.settings-about-row:last-child {
  border-bottom: none;
}

.about-label {
  font-size: 14px;
  color: var(--color-text);
}

.about-value {
  font-size: 13px;
  color: var(--color-text-muted);
}

/* ── Save Toast ── */
.save-toast {
  position: fixed;
  bottom: 80px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(60, 47, 47, 0.88);
  color: white;
  padding: 10px 24px;
  border-radius: 30px;
  font-size: 14px;
  font-weight: 500;
  z-index: 999;
  display: flex;
  align-items: center;
  gap: 6px;
  backdrop-filter: blur(8px);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.18);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(10px);
}
</style>
