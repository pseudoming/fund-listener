<template>
  <div class="ocr-upload card">
    <div class="ocr-upload__header">
      <span class="ocr-upload__title">OCR 批量持仓导入</span>
      <span class="ocr-upload__subtitle text-muted">支持选择多张支付宝持仓截图导入</span>
    </div>

    <!-- 选择区 -->
    <!-- 【遵循修复原则重写】彻底删除原本封装的上传卡片，使用最基础的原生 input -->
    <div
      v-if="selectedFiles.length === 0 && !parsedPositions.length"
      style="padding: 24px 16px; border: 2px dashed var(--color-border); border-radius: 8px; margin: 16px; text-align: center; background: var(--color-bg-card);"
    >
      <div style="font-size: 32px; margin-bottom: 8px;">📸</div>
      <div style="margin-bottom: 16px; font-size: 14px; color: var(--color-text);">请点击下方按钮选择支付宝持仓截图</div>
      <!-- 直接暴露原生控件，不加任何透明度或代理点击 -->
      <input
        type="file"
        accept="image/png, image/jpeg, image/jpg"
        multiple
        @change="onFilesSelected"
        class="native-file-input"
      />
    </div>

    <!-- 预览与准备解析 -->
    <div v-if="selectedFiles.length > 0 && !parsedPositions.length" class="ocr-upload__preview-list">
      <div class="ocr-preview-grid">
        <div v-for="(url, idx) in previewUrls" :key="idx" class="ocr-preview-item">
          <img :src="url" class="ocr-preview-img" />
          <button class="ocr-preview-remove" @click.stop="removeFile(idx)">✕</button>
        </div>
      </div>
      
      <div class="ocr-upload__actions">
        <button class="btn btn--secondary" style="flex: 1;" @click="reset">
          清空重选
        </button>
        <button
          class="btn btn--primary"
          style="flex: 1;"
          :disabled="loading"
          @click="uploadAndParseAll"
        >
          {{ loading ? `解析中...` : `开始解析 (${selectedFiles.length}张图片)` }}
        </button>
      </div>
    </div>

    <!-- Loading skeleton -->
    <div v-if="loading" class="ocr-upload__loading">
      <div class="skeleton" style="width: 80%; height: 14px; margin: 0 auto; background: var(--color-border); border-radius: 4px;"></div>
      <div class="skeleton" style="width: 60%; height: 14px; margin: 8px auto 0; background: var(--color-border); border-radius: 4px;"></div>
      <div class="skeleton" style="width: 90%; height: 40px; margin-top: 12px; background: var(--color-border); border-radius: 4px;"></div>
    </div>

    <!-- 解析结果列表与确认导入 -->
    <div v-if="parsedPositions.length > 0 && !imported" class="ocr-upload__result-list">
      <div class="ocr-result-info text-muted">
        共识别出 {{ parsedPositions.length }} 只基金持仓。请勾选确认导入配置。
      </div>

      <div class="ocr-funds-container">
        <div
          v-for="(item, idx) in parsedPositions"
          :key="idx"
          class="ocr-fund-card"
          :class="{ 'ocr-fund-card--duplicate': item.isDuplicate }"
        >
          <!-- 头部：勾选 + 名称 -->
          <div class="ocr-fund-card__header">
            <label class="ocr-fund-checkbox-label">
              <input type="checkbox" v-model="item.enabled" class="ocr-fund-checkbox" />
              <template v-if="item.candidates && item.candidates.length > 0">
                <div 
                  class="form-select fake-select" 
                  :style="{ borderColor: !item.fundCode ? 'var(--color-warn)' : 'var(--color-border)' }"
                  @click.stop.prevent="openCandidateSelect(item)"
                >
                  {{ item.fundCode ? `${item.fundName} (${item.fundCode})` : '⚠️ 请点击选择准确的基金' }}
                </div>
              </template>
              <template v-else>
                <span class="ocr-fund-name">{{ item.fundName }}</span>
                <span class="ocr-fund-code text-muted font-mono">({{ item.fundCode || '未知代码' }})</span>
              </template>
            </label>
            <div v-if="item.isDuplicate" class="badge badge--warn">已持仓</div>
            <div v-else class="badge badge--success">新持仓</div>
          </div>

          <!-- 表单详情网格 -->
          <div class="ocr-fund-card__grid" v-if="item.enabled">
            <div class="ocr-grid-field">
              <span class="ocr-grid-field__label">当前金额</span>
              <input v-model="item.amount" @input="onAmountInput(item)" class="ocr-grid-field__input font-mono" />
            </div>
            <div class="ocr-grid-field">
              <span class="ocr-grid-field__label">当前盈亏</span>
              <input v-model="item.holdingReturn" @input="onHoldingReturnInput(item)" class="ocr-grid-field__input font-mono" />
            </div>
            <div class="ocr-grid-field">
              <span class="ocr-grid-field__label">预估成本</span>
              <input v-model="item.cost" @input="onCostInput(item)" class="ocr-grid-field__input font-mono" />
            </div>
            <div class="ocr-grid-field">
              <span class="ocr-grid-field__label">收益率</span>
              <input v-model="item.holdingReturnPercent" class="ocr-grid-field__input font-mono" readonly style="opacity: 0.8; font-weight: 500;" />
            </div>
            <div class="ocr-grid-field" style="grid-column: span 2;">
              <span class="ocr-grid-field__label">预估份额</span>
              <input v-model="item.shares" @input="onSharesInput(item)" class="ocr-grid-field__input font-mono" />
            </div>
          </div>

          <!-- 重复项处理配置 -->
          <div class="ocr-fund-card__dup-actions" v-if="item.enabled && item.isDuplicate">
            <span class="dup-action-label text-muted">检测到系统已有持仓：(当前份额 {{ item.existingShares }})</span>
            <div class="dup-radio-group">
              <label class="dup-radio-label">
                <input type="radio" value="skip" v-model="item.dupAction" />
                <span>跳过此项</span>
              </label>
              <label class="dup-radio-label">
                <input type="radio" value="overwrite" v-model="item.dupAction" />
                <span>覆盖现有持仓</span>
              </label>
              <label class="dup-radio-label">
                <input type="radio" value="append" v-model="item.dupAction" />
                <span>追加买入</span>
              </label>
            </div>
          </div>
        </div>
      </div>

      <!-- 统一导入参数 -->
      <div class="ocr-global-config card alt">
        <div class="ocr-grid-field" style="justify-content: flex-start; gap: var(--space-md);">
          <span class="ocr-grid-field__label" style="width: 72px;">成交日期</span>
          <input type="date" v-model="globalTradeDate" class="ocr-grid-field__input font-mono" style="text-align: left; max-width: 150px; border: 1px solid var(--color-border); border-radius: var(--radius-sm); padding: 4px var(--space-sm); outline: none; background: transparent; color: var(--color-text);" />
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="ocr-upload__actions">
        <button class="btn btn--secondary" style="flex: 1;" @click="reset">
          放弃返回
        </button>
        <button
          class="btn btn--primary"
          style="flex: 1;"
          :disabled="submitting || !hasEnabledItems"
          @click="submitBatchImport"
        >
          {{ submitting ? '批量导入中...' : '确认导入已选项目' }}
        </button>
      </div>
    </div>

    <!-- 导入成功结果 -->
    <div v-if="imported" class="ocr-upload__success">
      <div class="ocr-upload__success-icon">✓</div>
      <div class="ocr-upload__success-text">批量导入成功</div>
      <div class="ocr-upload__success-sub text-muted" style="font-size: 12px; margin-top: 8px;">
        成功处理了 {{ importStats.success }} 只基金持仓。
      </div>
      <button class="btn btn--primary" style="margin-top: 16px; width: 100%;" @click="reset">
        继续导入
      </button>
    </div>

    <!-- 隐藏的 file input 已移至 label 内部 -->

    <!-- 候选基金选择弹窗 -->
    <van-popup 
      v-model:show="showCandidatePopup" 
      round 
      teleport="body"
      style="width: 90%; max-width: 400px; max-height: 70vh; display: flex; flex-direction: column; background: var(--color-bg); overflow: hidden;"
    >
      <div style="padding: 16px; text-align: center; font-weight: bold; border-bottom: 1px solid var(--color-border); font-size: 16px; flex-shrink: 0; background: var(--color-bg-card); color: var(--color-text);">
        请选择匹配的基金
      </div>
      <div class="candidate-list">
        <div 
          v-for="cand in currentCandidateItem?.candidates" 
          :key="cand.fundCode"
          class="candidate-item"
          :class="{ 'is-selected': cand.fundCode === currentCandidateItem?.fundCode }"
          @click="selectCandidate(cand)"
        >
          <div class="cand-name">{{ cand.fundName }}</div>
          <div class="cand-code text-muted font-mono">{{ cand.fundCode }}</div>
          <div class="cand-check" v-if="cand.fundCode === currentCandidateItem?.fundCode">✓</div>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { parseOcrImage } from '../api/ocr'
import { buyPosition, resetPosition } from '../api/position'
import { fetchFundTrend } from '../api/fund'
import { showToast } from 'vant'

const emit = defineEmits(['imported'])

const fileInput = ref(null)
const selectedFiles = ref([])
const previewUrls = ref([])
const parsedPositions = ref([])
const loading = ref(false)
const submitting = ref(false)
const error = ref(null)
const imported = ref(false)

// 候选选择状态
const showCandidatePopup = ref(false)
const currentCandidateItem = ref(null)

function openCandidateSelect(item) {
  currentCandidateItem.value = item
  showCandidatePopup.value = true
}

function selectCandidate(cand) {
  if (currentCandidateItem.value) {
    currentCandidateItem.value.fundCode = cand.fundCode
    currentCandidateItem.value.fundName = cand.fundName
    currentCandidateItem.value.selectedCandidateCode = cand.fundCode
  }
  showCandidatePopup.value = false
}

const getToday = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const globalTradeDate = ref(getToday())

watch(globalTradeDate, async (newDate) => {
  if (!newDate || parsedPositions.value.length === 0) return
  const codes = parsedPositions.value.map(p => p.fundCode).filter(c => c && c.length === 6)
  const uniqueCodes = [...new Set(codes)]
  if (uniqueCodes.length === 0) return
  
  showToast({ type: 'loading', message: '正在同步历史净值...', duration: 0 })
  
  try {
    const trendPromises = uniqueCodes.map(code => 
      fetchFundTrend(code).then(res => ({ code, data: res })).catch(() => ({ code, data: [] }))
    )
    const trendsArray = await Promise.all(trendPromises)
    const trendMap = {}
    trendsArray.forEach(t => trendMap[t.code] = t.data)
    
    parsedPositions.value.forEach(item => {
      if (!item.fundCode || !trendMap[item.fundCode]) return
      const trendData = trendMap[item.fundCode]
      
      // 寻找严格小于 newDate 的最近一条数据（即 T-1 净值）
      let match = null
      let maxDateStr = ""
      for (const d of trendData) {
        const dObj = new Date(d.x)
        const dStr = `${dObj.getFullYear()}-${String(dObj.getMonth() + 1).padStart(2, '0')}-${String(dObj.getDate()).padStart(2, '0')}`
        if (dStr < newDate && dStr > maxDateStr) {
          maxDateStr = dStr
          match = d
        }
      }

      if (match && match.y) {
        item.nav = match.y.toFixed(4)
        const amt = parseFloat(item.amount) || 0
        const nav = parseFloat(item.nav)
        if (nav > 0) {
          item.shares = (amt / nav).toFixed(4)
        }
      }
    })
    showToast('已同步该日期的历史净值并重算份额')
  } catch (err) {
    showToast('同步历史净值失败')
  }
})

const importStats = reactive({
  success: 0,
  failed: 0
})

const hasEnabledItems = computed(() => {
  return parsedPositions.value.some(p => p.enabled)
})

function triggerInput() {
  fileInput.value?.click()
}

function onFilesSelected(e) {
  const files = Array.from(e.target.files || [])
  if (files.length === 0) return
  selectedFiles.value.push(...files)
  
  const urls = files.map(file => URL.createObjectURL(file))
  previewUrls.value.push(...urls)
  error.value = null
}

function onVantRead(result) {
  const items = Array.isArray(result) ? result : [result]
  const files = items.map(item => item.file).filter(Boolean)
  if (files.length === 0) return
  
  selectedFiles.value.push(...files)
  const urls = files.map(file => URL.createObjectURL(file))
  previewUrls.value.push(...urls)
  error.value = null
}

function removeFile(idx) {
  selectedFiles.value.splice(idx, 1)
  previewUrls.value.splice(idx, 1)
}

function reset() {
  selectedFiles.value = []
  previewUrls.value = []
  parsedPositions.value = []
  error.value = null
  imported.value = false
  importStats.success = 0
  importStats.failed = 0
  if (fileInput.value) fileInput.value.value = ''
}

async function uploadAndParseAll() {
  if (selectedFiles.value.length === 0) return
  loading.value = true
  error.value = null
  parsedPositions.value = []

  try {
    const promises = selectedFiles.value.map(file => parseOcrImage(file, globalTradeDate.value))
    const results = await Promise.all(promises)
    
    const aggregated = []
    
    results.forEach(res => {
      if (res && res.parsedFunds) {
        res.parsedFunds.forEach(fund => {
          const existing = aggregated.find(a => a.fundCode === fund.fundCode && fund.fundCode !== '')
          if (existing) {
            // Overlapping image detection: do NOT sum values to avoid double-counting.
            // Keep the one with the larger amount to ensure we use the fully-visible row if one was cropped.
            if (parseFloat(fund.amount) > parseFloat(existing.amount)) {
              existing.amount = fund.amount
              existing.holdingReturn = fund.holdingReturn
              existing.holdingReturnPercent = fund.holdingReturnPercent
              existing.cost = fund.cost
              existing.shares = fund.shares
              existing.nav = fund.nav
            }
          } else {
            aggregated.push({
              ...fund,
              selectedCandidateCode: fund.fundCode || '',
              enabled: true,
              dupAction: fund.isDuplicate ? 'skip' : 'append'
            })
          }
        })
      }
    })

    if (aggregated.length === 0) {
      showToast('未识别到有效的基金持仓数据，请确保上传了支付宝持仓列表截图')
    } else {
      parsedPositions.value = aggregated
    }
  } catch (e) {
    error.value = e.message || 'OCR 解析失败，请重试'
    showToast(error.value)
  } finally {
    loading.value = false
  }
}

function onCandidateChange(item) {
  if (!item.candidates) return
  const selected = item.candidates.find(c => c.fundCode === item.selectedCandidateCode)
  if (selected) {
    item.fundCode = selected.fundCode
    item.fundName = selected.fundName
  } else {
    item.fundCode = ''
  }
}

function onAmountInput(item) {
  const amt = parseFloat(item.amount) || 0
  const ret = parseFloat(item.holdingReturn) || 0
  const nav = parseFloat(item.nav) || 1.0
  
  item.cost = (amt - ret).toFixed(2)
  if (nav > 0) {
    item.shares = (amt / nav).toFixed(4)
  }
  updateHoldingReturnPercent(item)
}

function onHoldingReturnInput(item) {
  const amt = parseFloat(item.amount) || 0
  const ret = parseFloat(item.holdingReturn) || 0
  
  item.cost = (amt - ret).toFixed(2)
  updateHoldingReturnPercent(item)
}

function onCostInput(item) {
  const amt = parseFloat(item.amount) || 0
  const cost = parseFloat(item.cost) || 0
  
  item.holdingReturn = (amt - cost).toFixed(2)
  updateHoldingReturnPercent(item)
}

function onSharesInput(item) {
  const amt = parseFloat(item.amount) || 0
  const sh = parseFloat(item.shares) || 0
  if (sh > 0) {
    item.nav = (amt / sh).toFixed(4)
  }
}

function updateHoldingReturnPercent(item) {
  const cost = parseFloat(item.cost) || 0
  const ret = parseFloat(item.holdingReturn) || 0
  if (cost > 0) {
    const pct = (ret / cost) * 100.0
    const sign = pct >= 0 ? '+' : ''
    item.holdingReturnPercent = `${sign}${pct.toFixed(2)}%`
  } else {
    item.holdingReturnPercent = '0.00%'
  }
}

async function submitBatchImport() {
  const itemsToImport = parsedPositions.value.filter(p => p.enabled)
  if (itemsToImport.length === 0) return

  submitting.value = true
  error.value = null
  importStats.success = 0
  importStats.failed = 0

  const tradeDate = globalTradeDate.value || getToday()

  for (const item of itemsToImport) {
    if (!item.fundCode || item.fundCode.length !== 6) {
      importStats.failed++
      continue
    }

    try {
      const costVal = parseFloat(item.cost) || 0
      let sharesVal = parseFloat(item.shares) || 0
      let calculatedNav = item.nav && parseFloat(item.nav) > 0 ? parseFloat(item.nav) : 1.0

      if (sharesVal <= 0 && costVal > 0) {
        sharesVal = costVal / calculatedNav
      } else if (sharesVal > 0) {
        calculatedNav = costVal / sharesVal
      }

      const payload = {
        amount: costVal.toFixed(2),
        shares: sharesVal.toFixed(4),
        nav: calculatedNav.toFixed(4),
        fee: '0.00',
        tradeDate: tradeDate,
        fundName: item.fundName
      }

      if (item.isDuplicate) {
        if (item.dupAction === 'skip') {
          continue
        } else if (item.dupAction === 'overwrite') {
          await resetPosition(item.fundCode, payload)
        } else {
          await buyPosition(item.fundCode, payload)
        }
      } else {
        await buyPosition(item.fundCode, payload)
      }
      importStats.success++
    } catch (e) {
      console.error(`Failed to import ${item.fundCode}:`, e)
      importStats.failed++
    }
  }

  submitting.value = false
  imported.value = true
  emit('imported')
}
</script>

<style scoped>
.native-file-input {
  display: block;
  width: 100%;
  margin: 0 auto;
  padding: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg);
  font-size: 13px;
  color: transparent; /* 隐藏右侧的未选择任何文件文字 */
  cursor: pointer;
  overflow: hidden;
  transition: border-color var(--duration-fast);
}

.native-file-input:hover {
  border-color: var(--color-accent);
}

.native-file-input::file-selector-button {
  display: block;
  width: 100%;
  margin: 0;
  border: none;
  background: var(--color-accent);
  padding: 14px 20px;
  color: #fff;
  cursor: pointer;
  font-size: 15px;
  font-weight: 500;
  text-align: center;
  transition: all 0.2s ease-in-out;
}

.native-file-input::file-selector-button:hover {
  opacity: 0.9;
}

.ocr-upload {
  margin-top: var(--space-md);
  padding: 0;
  overflow: hidden;
}

.ocr-upload__header {
  padding: var(--space-md) var(--space-lg);
  border-bottom: 1px solid var(--color-border);
  display: flex;
  align-items: baseline;
  gap: var(--space-sm);
}

.ocr-upload__title {
  font-size: 13px;
  font-weight: 600;
}

.ocr-upload__subtitle {
  font-size: 11px;
}

/* 上传区 */
.ocr-upload__zone {
  margin: var(--space-xl);
  padding: var(--space-2xl);
  border: 2px dashed var(--color-border);
  border-radius: var(--radius-md);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-sm);
  cursor: pointer;
  transition: border-color var(--duration-fast);
  -webkit-tap-highlight-color: transparent;
}
.ocr-upload__zone:active {
  border-color: var(--color-accent);
}

.ocr-upload__zone-icon {
  font-size: 32px;
}

.ocr-upload__zone-text {
  font-size: 13px;
  color: var(--color-text-secondary);
}

/* 预览列表 */
.ocr-upload__preview-list {
  padding: var(--space-lg);
}

.ocr-preview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(80px, 1fr));
  gap: var(--space-md);
  margin-bottom: var(--space-lg);
}

.ocr-preview-item {
  position: relative;
  aspect-ratio: 9/16;
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--color-bg-card-alt);
  border: 1px solid var(--color-border);
}

.ocr-preview-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.ocr-preview-remove {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  border: none;
  font-size: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

/* 结果列表 */
.ocr-upload__result-list {
  padding: var(--space-lg);
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.ocr-result-info {
  font-size: 12px;
}

.ocr-funds-container {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  max-height: 400px;
  overflow-y: auto;
  padding-right: var(--space-xs);
}

/* 基金卡片 */
.ocr-fund-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--space-md);
  background: var(--color-bg-card-alt);
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  transition: all var(--duration-fast);
}

.ocr-fund-card--duplicate {
  border-left: 3px solid var(--color-warn);
}

.ocr-fund-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-sm);
}

.ocr-fund-checkbox-label {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  cursor: pointer;
  flex: 1;
}

.ocr-fund-checkbox {
  width: 16px;
  height: 16px;
  border-radius: 4px;
  accent-color: var(--color-accent);
}

.ocr-fund-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}

.ocr-fund-code {
  font-size: 11px;
}

/* Badges */
.badge {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 500;
}
.badge--warn {
  background: rgba(230, 162, 44, 0.15);
  color: var(--color-warn);
}
.badge--success {
  background: rgba(103, 194, 58, 0.15);
  color: var(--color-fall);
}

/* 卡片内字段网格 */
.ocr-fund-card__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-md);
}

.ocr-grid-field {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-sm);
  background: var(--color-bg-card);
  padding: 6px var(--space-md);
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
}

.ocr-grid-field__label {
  font-size: 11px;
  color: var(--color-text-secondary);
  flex-shrink: 0;
}

.ocr-grid-field__input {
  flex: 1;
  border: none;
  background: transparent;
  text-align: right;
  font-size: 12px;
  color: var(--color-text);
  outline: none;
}

/* 重复项操作 */
.ocr-fund-card__dup-actions {
  font-size: 11px;
  border-top: 1px dashed var(--color-border);
  padding-top: var(--space-sm);
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.dup-radio-group {
  display: flex;
  gap: var(--space-md);
}

.dup-radio-label {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
}

.dup-radio-label input {
  accent-color: var(--color-accent);
}

/* 全局配置卡片 */
.ocr-global-config {
  padding: var(--space-md);
  background: var(--color-bg-card-alt);
}

/* 按钮 */
.ocr-upload__actions {
  display: flex;
  gap: var(--space-sm);
  margin-top: var(--space-md);
}

/* 加载 */
.ocr-upload__loading {
  padding: var(--space-xl);
  text-align: center;
}

/* 成功 */
.ocr-upload__success {
  padding: var(--space-2xl) var(--space-lg);
  text-align: center;
}

.ocr-upload__success-icon {
  font-size: 40px;
  color: var(--color-fall);
  margin-bottom: var(--space-sm);
}

.ocr-upload__success-text {
  font-size: 14px;
  font-weight: 500;
}

.ocr-upload__input-hidden {
  display: none;
}

/* 浮层下拉框伪装样式 */
.fake-select {
  cursor: pointer;
  user-select: none;
  display: flex;
  align-items: center;
}

/* 浮层候选列表 */
.candidate-list {
  padding: var(--space-md);
  max-height: 60vh;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  background: var(--color-bg);
}

.candidate-item {
  padding: var(--space-md);
  border-radius: var(--radius-md);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  cursor: pointer;
  transition: all var(--duration-fast);
}

.candidate-item:active {
  transform: scale(0.98);
}

.candidate-item.is-selected {
  border-color: var(--color-accent);
  background: var(--color-accent-soft);
}

.cand-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  flex: 1;
}

.cand-code {
  font-size: 12px;
  margin-left: var(--space-md);
}

.cand-check {
  color: var(--color-accent);
  font-weight: bold;
  margin-left: var(--space-sm);
  font-size: 16px;
}
</style>
