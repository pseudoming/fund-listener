<template>
  <div class="layout-container">
    <!-- 主体内容区 -->
    <div class="main-content">
      <div class="content-wrapper">
        <router-view />
      </div>
    </div>

    <!-- 自定义底部导航栏 -->
    <nav class="custom-tabbar">
      <button
        class="tabbar-item"
        :class="{ 'active': active === '/' }"
        @click="navigate('/')"
      >
        <span class="tabbar-icon">💼</span>
        <span class="tabbar-text">持仓</span>
      </button>
      <button
        class="tabbar-item"
        :class="{ 'active': active === '/watchlist' }"
        @click="navigate('/watchlist')"
      >
        <span class="tabbar-icon">👁️</span>
        <span class="tabbar-text">自选</span>
      </button>
    </nav>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const active = ref('/')

// 同步激活的标签
watch(
  () => route.path,
  (path) => {
    if (path === '/watchlist') {
      active.value = '/watchlist'
    } else if (path === '/') {
      active.value = '/'
    }
  },
  { immediate: true }
)

function navigate(path) {
  active.value = path
  router.push(path)
}
</script>

<style scoped>
.layout-container {
  min-height: 100vh;
  width: 100%;
  background: transparent;
  display: block;
}

.main-content {
  width: 100%;
  padding-bottom: 80px; /* 留出底部导航栏空间 */
}

.content-wrapper {
  padding: 0;
}

/* 自定义底部导航栏 - 固定在居中容器内 */
.custom-tabbar {
  position: fixed;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 100%;
  max-width: 600px;
  height: 64px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-top: 1px solid var(--color-border);
  display: flex;
  justify-content: space-around;
  align-items: center;
  z-index: 1000;
  padding-bottom: env(safe-area-inset-bottom);
}

.tabbar-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  background: transparent;
  border: none;
  color: var(--color-text-secondary);
  cursor: pointer;
  padding: 4px 12px;
  transition: all var(--duration-fast) var(--ease-out);
  flex: 1;
}

.tabbar-icon {
  font-size: 20px;
}

.tabbar-text {
  font-size: 11px;
  font-weight: 600;
}

.tabbar-item.active {
  color: var(--color-accent);
}
</style>
