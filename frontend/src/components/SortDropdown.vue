<template>
  <div class="sort-dropdown-container">
    <button class="sort-dropdown-btn" @click.stop="toggleDropdown">
      <span>{{ currentLabel }}</span>
      <span class="select-arrow-mini">▼</span>
    </button>
    <Transition name="fade-slide">
      <div v-show="showDropdown" class="sort-dropdown-menu">
        <div 
          v-for="opt in options" 
          :key="opt.value" 
          class="sort-dropdown-item"
          :class="{ 'is-active': modelValue === opt.value }"
          @click.stop="selectOption(opt.value)"
        >
          <span class="item-text">{{ opt.label }}</span>
          <span v-if="modelValue === opt.value" class="item-check">✓</span>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  modelValue: {
    type: String,
    required: true
  },
  options: {
    type: Array,
    required: true,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const showDropdown = ref(false)

const currentLabel = computed(() => {
  return props.options.find(o => o.value === props.modelValue)?.label || ''
})

const toggleDropdown = () => {
  showDropdown.value = !showDropdown.value
}

const selectOption = (val) => {
  emit('update:modelValue', val)
  emit('change', val)
  showDropdown.value = false
}

const closeDropdown = (e) => {
  if (!e.target.closest('.sort-dropdown-container')) {
    showDropdown.value = false
  }
}

onMounted(() => {
  window.addEventListener('click', closeDropdown)
})

onUnmounted(() => {
  window.removeEventListener('click', closeDropdown)
})
</script>

<style scoped>
/* Custom Dropdown Styling */
.sort-dropdown-container {
  position: relative;
  display: inline-block;
}

.sort-dropdown-btn {
  background: var(--color-bg-card-alt);
  border: 1px solid var(--color-border);
  border-radius: 16px;
  padding: 4px 12px;
  font-size: 11px;
  font-weight: 700;
  color: var(--color-text-secondary);
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  transition: all var(--duration-fast);
  outline: none;
  height: 28px;
}

.sort-dropdown-btn:hover {
  border-color: var(--color-accent);
  background: var(--color-accent-soft);
  color: var(--color-accent);
}

.select-arrow-mini {
  font-size: 8px;
  color: var(--color-text-muted);
  transition: transform var(--duration-fast);
}

.sort-dropdown-menu {
  position: absolute;
  top: calc(100% + 4px);
  right: 0;
  background: #ffffff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-card);
  min-width: 110px;
  z-index: 100;
  overflow: hidden;
  padding: 4px 0;
  transform-origin: top right;
}

.sort-dropdown-item {
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-secondary);
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
  transition: all var(--duration-fast);
}

.sort-dropdown-item:hover {
  background: var(--color-accent-soft);
  color: var(--color-accent);
}

.sort-dropdown-item.is-active {
  color: var(--color-accent);
  font-weight: 600;
}
</style>
