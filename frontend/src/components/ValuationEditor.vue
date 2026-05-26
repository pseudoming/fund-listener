<template>
  <div class="valuation-editor card">
    <div class="editor-header">
      <h3>自定义估值设置</h3>
      <div class="sum-badge" :class="{ 'is-valid': totalWeight === 100 }">
        总权重: {{ totalWeight }}%
      </div>
    </div>

    <van-skeleton title :row="3" :loading="loading">
      <div class="rules-list">
        <div v-for="(rule, index) in rules" :key="index" class="rule-item glass-panel">
          <div class="rule-row">
            <van-field
              v-model="rule.type"
              label="类型"
              is-link
              readonly
              @click="showTypePicker(index)"
            />
            <van-field
              v-model="rule.targetCode"
              label="标的代码"
              placeholder="例如: sh000300 或 NVDA"
            />
          </div>
          <div class="rule-row align-center">
            <div class="weight-control">
              <span class="label">权重 (%)</span>
              <van-stepper v-model="rule.weight" :min="0" :max="100" />
            </div>
            <van-button type="danger" size="small" icon="delete" @click="removeRule(index)" plain round />
          </div>
        </div>
        <van-empty v-if="rules.length === 0" description="暂未添加估值标的" image-size="60" />
      </div>

      <div class="actions">
        <van-button type="default" plain block icon="plus" @click="addRule">
          添加标的
        </van-button>
        <van-button type="primary" block @click="saveRules" :loading="saving" :disabled="totalWeight !== 100">
          保存规则
        </van-button>
      </div>
    </van-skeleton>

    <van-popup v-model:show="showPicker" position="bottom" round>
      <van-picker
        :columns="typeColumns"
        @confirm="onTypeConfirm"
        @cancel="showPicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getValuationRules, updateValuationRules } from '../api/fund'
import { showToast } from 'vant'

const props = defineProps({
  fundCode: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['saved', 'close'])

const loading = ref(true)
const saving = ref(false)
const rules = ref([])

const showPicker = ref(false)
const editingIndex = ref(-1)
const typeColumns = [
  { text: 'STOCK', value: 'STOCK' },
  { text: 'INDEX', value: 'INDEX' },
  { text: 'CASH', value: 'CASH' }
]

const totalWeight = computed(() => {
  return rules.value.reduce((sum, rule) => sum + Number(rule.weight || 0), 0)
})

onMounted(async () => {
  await loadRules()
})

const loadRules = async () => {
  try {
    loading.value = true
    const res = await getValuationRules(props.fundCode)
    if (Array.isArray(res)) {
      rules.value = res.map(r => ({
        type: r.componentType || 'STOCK',
        targetCode: r.targetCode || '',
        weight: r.weightPercent || 0
      }))
    } else {
      rules.value = []
    }
  } catch (err) {
    console.error('Failed to load rules', err)
    showToast('获取规则失败')
    rules.value = []
  } finally {
    loading.value = false
  }
}

const addRule = () => {
  rules.value.push({ type: 'STOCK', targetCode: '', weight: 0 })
}

const removeRule = (index) => {
  rules.value.splice(index, 1)
}

const showTypePicker = (index) => {
  editingIndex.value = index
  showPicker.value = true
}

const onTypeConfirm = ({ selectedValues }) => {
  if (editingIndex.value >= 0 && selectedValues && selectedValues.length > 0) {
    rules.value[editingIndex.value].type = selectedValues[0]
  }
  showPicker.value = false
}

const saveRules = async () => {
  if (totalWeight.value !== 100) {
    showToast('总权重必须为 100%')
    return
  }
  
  try {
    saving.value = true
    const payload = rules.value.map(r => ({
      fundCode: props.fundCode,
      componentType: r.type,
      targetCode: r.targetCode,
      weightPercent: Number(r.weight),
      createdAt: 0
    }))
    await updateValuationRules(props.fundCode, payload)
    showToast('保存成功')
    emit('saved')
    emit('close')
  } catch (err) {
    showToast('保存规则失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.valuation-editor {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  margin-top: var(--space-md);
}

.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-md);
}

.editor-header h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
  color: var(--color-text);
}

.sum-badge {
  font-size: 14px;
  font-weight: 500;
  padding: 4px 10px;
  border-radius: 12px;
  background: var(--color-rise-bg);
  color: var(--color-rise);
  transition: all 0.3s ease;
}

.sum-badge.is-valid {
  background: var(--color-fall-bg);
  color: var(--color-fall);
}

.rules-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  max-height: 50vh;
  overflow-y: auto;
  margin-bottom: var(--space-md);
  padding: 4px;
}

.rule-item {
  padding: var(--space-md);
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  transition: transform var(--duration-fast);
}

.rule-item:hover {
  transform: translateY(-2px);
}

.rule-row {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}

.rule-row.align-center {
  flex-direction: row;
  justify-content: space-between;
  align-items: center;
  margin-top: var(--space-sm);
  padding-top: var(--space-sm);
  border-top: 1px solid var(--color-border);
}

.weight-control {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.weight-control .label {
  font-size: 14px;
  color: var(--color-text-secondary);
}

.actions {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

/* Override van-field background for glass panel */
:deep(.van-cell) {
  background: transparent;
  padding: 8px 0;
}
:deep(.van-cell::after) {
  border-color: var(--color-border);
  left: 0;
  right: 0;
}
:deep(.van-field__label) {
  color: var(--color-text-secondary);
}
:deep(.van-field__control) {
  color: var(--color-text);
}
</style>
