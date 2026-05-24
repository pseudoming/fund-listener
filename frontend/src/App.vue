<template>
  <router-view v-slot="{ Component }">
    <keep-alive include="Layout">
      <component :is="Component" />
    </keep-alive>
  </router-view>
</template>

<script setup>
</script>

<style>
/* 覆盖 Vant Tabbar 的颜色适配暗黑模式 */
:root {
  --van-tabbar-background: var(--color-bg);
  --van-tabbar-item-active-color: var(--color-accent);
  --van-tabbar-item-text-color: var(--color-text-muted);
}
.van-tabbar {
  border-top: 1px solid var(--color-border);
}
</style>
