import { ref, onMounted, onUnmounted } from 'vue'
import { fetchFundRealtime } from '../api/fund'

/**
 * 基金实时估值数据组合式函数
 * @param {string} code 基金代码
 * @param {Object} options
 * @param {number} [options.interval=30000] 自动刷新间隔 (ms)，0 = 不自动刷新
 * @param {boolean} [options.immediate=true] 是否立即加载
 */
export function useFundRealtime(code, options = {}) {
  const { interval = 30000, immediate = true } = options

  const data = ref(null)
  const loading = ref(false)
  const error = ref(null)
  const lastUpdated = ref(null)

  let timer = null

  async function refresh() {
    loading.value = true
    error.value = null
    try {
      data.value = await fetchFundRealtime(code)
      lastUpdated.value = new Date()
    } catch (e) {
      error.value = e.message
    } finally {
      loading.value = false
    }
  }

  function startPolling() {
    stopPolling()
    if (interval > 0) {
      timer = setInterval(refresh, interval)
    }
  }

  function stopPolling() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  onMounted(() => {
    if (immediate) {
      refresh()
    }
    startPolling()
  })

  onUnmounted(() => {
    stopPolling()
  })

  return {
    data,
    loading,
    error,
    lastUpdated,
    refresh,
  }
}
