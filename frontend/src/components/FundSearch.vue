<template>
  <div class="fund-search-container">
    <div class="fund-search">
      <div class="fund-search__input-wrap">
        <span class="fund-search__icon">🔍</span>
        <input
          ref="inputRef"
          v-model="inputCode"
          class="fund-search__input"
          type="text"
          placeholder="输入名称/拼音/代码，如 招商"
          @keyup.enter="handleSearch"
          @focus="showSuggestions = true"
          @blur="onBlur"
        />
        <transition name="fade">
          <button
            v-if="inputCode"
            class="fund-search__clear"
            @click="clearInput"
            aria-label="清除"
          >✕</button>
        </transition>
      </div>
      <button
        class="btn btn--primary"
        :disabled="!inputCode.trim()"
        @click="handleSearch"
      >
        查询
      </button>
    </div>

    <!-- 搜索建议下拉列表 -->
    <transition name="fade">
      <ul v-if="showSuggestions && suggestions.length > 0" class="suggestions-list glass-panel">
        <li
          v-for="item in suggestions"
          :key="item.code"
          class="suggestion-item"
          @mousedown="selectSuggestion(item)"
        >
          <span class="suggestion-item__name">{{ item.name }}</span>
          <span class="suggestion-item__code font-mono text-muted">{{ item.code }}</span>
          <span class="suggestion-item__type text-muted">{{ item.type }}</span>
        </li>
      </ul>
    </transition>
  </div>
</template>

<script setup>
import { ref, watch, onUnmounted } from 'vue'
import api from '../api/index'

const emit = defineEmits(['search'])

const inputRef = ref(null)
const inputCode = ref('')
const suggestions = ref([])
const showSuggestions = ref(false)
let debounceTimer = null

watch(inputCode, (newVal) => {
  if (debounceTimer) clearTimeout(debounceTimer)
  const val = newVal.trim()
  if (!val) {
    suggestions.value = []
    return
  }
  debounceTimer = setTimeout(async () => {
    try {
      const res = await api.get(`/funds/search?key=${encodeURIComponent(val)}`)
      suggestions.value = res || []
    } catch (err) {
      console.error('Failed to fetch search suggestions', err)
    }
  }, 200)
})

onUnmounted(() => {
  if (debounceTimer) clearTimeout(debounceTimer)
})

function handleSearch() {
  const code = inputCode.value.trim()
  if (!code) return
  if (/^\d{6}$/.test(code)) {
    emit('search', code)
    showSuggestions.value = false
  } else {
    if (suggestions.value.length > 0) {
      selectSuggestion(suggestions.value[0])
    }
  }
}

function selectSuggestion(item) {
  inputCode.value = item.code
  emit('search', item.code)
  showSuggestions.value = false
  suggestions.value = []
}

function clearInput() {
  inputCode.value = ''
  suggestions.value = []
  inputRef.value?.focus()
}

function onBlur() {
  setTimeout(() => {
    showSuggestions.value = false
  }, 200)
}
</script>

<style scoped>
.fund-search-container {
  position: relative;
  width: 100%;
}

.fund-search {
  display: flex;
  gap: var(--space-sm);
  padding: 0;
}

.fund-search__input-wrap {
  flex: 1;
  position: relative;
  display: flex;
  align-items: center;
}

.fund-search__icon {
  position: absolute;
  left: 12px;
  font-size: 14px;
  pointer-events: none;
  opacity: 0.5;
}

.fund-search__input {
  width: 100%;
  height: 44px;
  padding: 0 36px 0 36px;
  background: var(--color-bg-input);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text);
  font-size: 15px;
  outline: none;
  transition: border-color var(--duration-fast) var(--ease-out);
}

.fund-search__input::placeholder {
  color: var(--color-text-muted);
  font-size: 13px;
}

.fund-search__input:focus {
  border-color: var(--color-accent);
}

.fund-search__clear {
  position: absolute;
  right: 10px;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-text-muted);
  color: var(--color-bg);
  border: none;
  border-radius: 50%;
  font-size: 10px;
  cursor: pointer;
  line-height: 1;
}

/* 建议列表 */
.suggestions-list {
  position: absolute;
  top: 48px;
  left: 0;
  right: 0;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  max-height: 240px;
  overflow-y: auto;
  z-index: 999;
  list-style: none;
  padding: var(--space-xs) 0;
  margin: 0;
  box-shadow: 0 10px 30px rgba(139, 92, 26, 0.08);
}

.suggestion-item {
  display: flex;
  align-items: center;
  padding: 10px var(--space-md);
  cursor: pointer;
  transition: background var(--duration-fast);
}

.suggestion-item:hover {
  background: var(--color-bg-card-alt);
}

.suggestion-item__name {
  flex: 1;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text);
  text-align: left;
}

.suggestion-item__code {
  font-size: 11px;
  margin-left: var(--space-sm);
  padding: 2px 6px;
  background: var(--color-bg-card-alt);
  border-radius: 4px;
  color: var(--color-text-secondary);
}

.suggestion-item__type {
  font-size: 11px;
  margin-left: var(--space-md);
  color: var(--color-text-muted);
}

/* fade transition */
.fade-enter-active,
.fade-leave-active {
  transition: opacity var(--duration-fast) var(--ease-out);
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>

