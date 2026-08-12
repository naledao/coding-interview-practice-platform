<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { BrainCircuit, RefreshCw, Save } from '@lucide/vue'
import {
  getAiSettings,
  loadAiModelCatalog,
  resolveAiSettings,
  saveAiSettings,
  validateAiSettings,
} from '../aiSettings'

const savedSettings = getAiSettings()
const catalog = ref(null)
const model = ref(savedSettings.model)
const effort = ref(savedSettings.effort)
const loading = ref(false)
const errorMessage = ref('')
const noticeMessage = ref('')

const selectedModel = computed(() => (
  catalog.value?.models?.find((option) => option.model === model.value) || null
))
const effortOptions = computed(() => selectedModel.value?.supportedReasoningEfforts || [])

onMounted(loadCatalog)

watch(model, () => {
  const option = selectedModel.value
  if (!option || option.supportedReasoningEfforts?.includes(effort.value)) {
    return
  }
  effort.value = option.supportedReasoningEfforts?.includes(option.defaultReasoningEffort)
    ? option.defaultReasoningEffort
    : option.supportedReasoningEfforts?.[0] || ''
})

async function loadCatalog() {
  loading.value = true
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    catalog.value = await loadAiModelCatalog()
    const resolved = resolveAiSettings(catalog.value, { model: model.value, effort: effort.value })
    model.value = resolved.model
    effort.value = resolved.effort
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

function saveSettings() {
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    const settings = { model: model.value, effort: effort.value }
    validateAiSettings(settings, catalog.value)
    saveAiSettings(settings)
    noticeMessage.value = 'AI 助教偏好已保存到本机'
  } catch (error) {
    errorMessage.value = error.message
  }
}
</script>

<template>
  <div class="page">
    <header class="page-header">
      <p>AI 助教</p>
      <h1>模型设置</h1>
    </header>

    <section class="ai-settings-layout">
      <div class="ai-settings-intro">
        <BrainCircuit :size="28" />
        <div>
          <h2>Codex AI 助教</h2>
          <p>可用选项由平台后端实时读取，只需选择模型和推理强度。</p>
        </div>
      </div>

      <form class="ai-settings-form" @submit.prevent="saveSettings">
        <label>
          <span>模型</span>
          <select v-model="model" name="aiModel" :disabled="loading || !catalog">
            <option v-for="option in catalog?.models || []" :key="option.model" :value="option.model">
              {{ option.displayName || option.model }}
            </option>
          </select>
        </label>

        <label>
          <span>Effort</span>
          <select v-model="effort" name="aiEffort" :disabled="loading || !selectedModel">
            <option v-for="option in effortOptions" :key="option" :value="option">
              {{ option }}
            </option>
          </select>
          <small>Effort 选项会随当前模型自动更新。</small>
        </label>

        <p v-if="loading" class="notice-message">
          <RefreshCw :size="16" />
          正在读取模型目录...
        </p>
        <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
        <p v-if="noticeMessage" class="notice-message">{{ noticeMessage }}</p>

        <button
          v-if="errorMessage && !catalog"
          class="secondary-button"
          type="button"
          :disabled="loading"
          @click="loadCatalog"
        >
          重新加载
        </button>
        <button class="primary-button ai-settings-save" type="submit" :disabled="loading || !catalog">
          <Save :size="18" />
          <span>保存设置</span>
        </button>
      </form>

      <p class="ai-settings-security-note">
        App 只连接面试平台后端；本机不会保存 AI 服务地址或密钥。
      </p>
    </section>
  </div>
</template>
