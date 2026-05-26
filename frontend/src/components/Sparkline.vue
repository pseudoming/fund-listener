<template>
  <svg
    :viewBox="`0 0 ${width} ${height}`"
    :width="width"
    :height="height"
    class="sparkline"
  >
    <!-- 网格线 -->
    <line
      v-if="midY !== null"
      x1="0" :y1="midY" :x2="width" :y2="midY"
      stroke="rgba(255,255,255,0.05)"
      stroke-dasharray="2,4"
    />
    <!-- 折线 -->
    <polyline
      v-if="pointsStr"
      :points="pointsStr"
      fill="none"
      :stroke="color"
      stroke-width="1.5"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <!-- 填充区 -->
    <polygon
      v-if="areaStr"
      :points="areaStr"
      :fill="color"
      fill-opacity="0.12"
    />
    <!-- 最后一个点的圆 -->
    <circle
      v-if="lastX !== null && lastY !== null"
      :cx="lastX"
      :cy="lastY"
      r="2.5"
      :fill="color"
    />
  </svg>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  points: { type: Array, default: () => [] },   // Array<number|null>
  width: { type: Number, default: 300 },
  height: { type: Number, default: 80 },
  color: { type: String, default: '#6366f1' }
})

const padding = 4

const computedBounds = computed(() => {
  // [FIX #2 背景说明]:
  // 上游 ValuationDashboard 已通过 Forward-fill 保证了输入数组的纯净性，
  // 此处直接使用即可，安全地移除了前人为了防止崩溃加的恶心 `isNaN(p)` 防御性补丁。
  const valid = props.points
  if (valid.length === 0) return null
  let min = Math.min(...valid), max = Math.max(...valid)
  if (min === max) {
    const offset = Math.abs(min) * 0.01 || 0.1
    min -= offset
    max += offset
  }
  // 上下留 10% 边距
  const range = max - min
  return {
    min: min - range * 0.1,
    max: max + range * 0.1,
    range: range * 1.2
  }
})

const midY = computed(() => props.height / 2)

const pointsStr = computed(() => {
  const bounds = computedBounds.value
  if (!bounds || bounds.range === 0) return ''
  const stepX = (props.width - padding * 2) / Math.max(props.points.length - 1, 1)
  return props.points
    .map((p, i) => {
      // 移除 isNaN(p)
      const x = padding + i * stepX
      const y = props.height - padding - (p - bounds.min) / bounds.range * (props.height - padding * 2)
      // 移除 isNaN(x) || isNaN(y) 的补丁
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
})

const areaStr = computed(() => {
  if (!pointsStr.value) return ''
  return `${pointsStr.value} ${props.width - padding},${props.height - padding} ${padding},${props.height - padding}`
})

const lastX = computed(() => {
  const bounds = computedBounds.value
  if (!bounds || props.points.length === 0) return null
  const stepX = (props.width - padding * 2) / Math.max(props.points.length - 1, 1)
  const lastIdx = props.points.length - 1
  return padding + lastIdx * stepX
})

const lastY = computed(() => {
  const bounds = computedBounds.value
  if (!bounds) return null
  const lastVal = props.points[props.points.length - 1]
  if (lastVal == null) return null
  return props.height - padding - (lastVal - bounds.min) / bounds.range * (props.height - padding * 2)
})
</script>

<style scoped>
.sparkline {
  display: block;
  max-width: 100%;
  height: auto;
}
</style>
