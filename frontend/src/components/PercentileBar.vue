<template>
  <div class="percentile-bar">
    <!-- 色阶背景条 -->
    <div class="bar" :class="barClass">
      <div class="bar__fill" :style="{ width: fillWidth }"></div>
      <div
        v-if="indicatorPos !== null"
        class="bar__indicator"
        :style="{ left: indicatorPos }"
      ></div>
    </div>
    <!-- 标签 -->
    <div class="bar__labels">
      <span class="text-fall">低估</span>
      <span>合理</span>
      <span class="text-rise">高估</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** 百分位值 0-100，null 表示无数据 */
  value: { type: Number, default: null },
  /** 状态: VALID | DEGRADED | NO_VALUE */
  status: { type: String, default: null }
})

const fillWidth = computed(() => {
  if (props.value == null) return '0%'
  return Math.min(Math.max(props.value, 0), 100) + '%'
})

const indicatorPos = computed(() => {
  if (props.value == null) return null
  return Math.min(Math.max(props.value, 2), 98) + '%'
})

const barClass = computed(() => {
  if (props.status === 'DEGRADED') return 'bar--degraded'
  if (props.status === 'NO_VALUE') return 'bar--empty'
  return ''
})
</script>

<style scoped>
.percentile-bar {
  user-select: none;
}

.bar {
  position: relative;
  height: 10px;
  border-radius: 5px;
  overflow: hidden;
  background: linear-gradient(to right,
    var(--color-fall) 0%,
    var(--color-fall) 30%,
    #fbbf24 30%,
    #fbbf24 70%,
    var(--color-rise) 70%,
    var(--color-rise) 100%
  );
}

.bar--degraded {
  opacity: 0.4;
}

.bar--empty {
  background: var(--color-bg-card-alt);
}

.bar__fill {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: rgba(255, 255, 255, 0.5);
  border-radius: 5px;
  transition: width var(--duration-normal) var(--ease-out);
}

.bar__indicator {
  position: absolute;
  top: -2px;
  width: 4px;
  height: 14px;
  background: white;
  border-radius: 2px;
  box-shadow: 0 0 4px rgba(0,0,0,0.4);
  transform: translateX(-50%);
  transition: left var(--duration-normal) var(--ease-out);
}

.bar__labels {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  color: var(--color-text-muted);
  margin-top: 3px;
}
</style>
