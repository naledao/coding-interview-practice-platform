<script setup>
import { ref } from 'vue'
import { BrainCircuit, Eye, EyeOff, KeyRound, Link2, Save } from '@lucide/vue'
import { getAiSettings, saveAiSettings, validateAiSettings } from '../aiSettings'

const savedSettings = getAiSettings()
const baseUrl = ref(savedSettings.baseUrl)
const apiKey = ref(savedSettings.apiKey)
const model = ref(savedSettings.model)
const effort = ref(savedSettings.effort)
const apiKeyVisible = ref(false)
const errorMessage = ref('')
const noticeMessage = ref('')

const effortOptions = [
  { value: '', label: '不传递（使用模型默认值）' },
  { value: 'none', label: 'none' },
  { value: 'minimal', label: 'minimal' },
  { value: 'low', label: 'low' },
  { value: 'medium', label: 'medium' },
  { value: 'high', label: 'high' },
  { value: 'xhigh', label: 'xhigh' },
]

function saveSettings() {
  errorMessage.value = ''
  noticeMessage.value = ''
  const settings = {
    baseUrl: baseUrl.value.trim().replace(/\/+$/, ''),
    apiKey: apiKey.value.trim(),
    model: model.value.trim(),
    effort: effort.value,
  }

  try {
    validateAiSettings(settings)
    const saved = saveAiSettings(settings)
    baseUrl.value = saved.baseUrl
    apiKey.value = saved.apiKey
    model.value = saved.model
    effort.value = saved.effort
    noticeMessage.value = '大模型设置已保存到本机'
  } catch (error) {
    errorMessage.value = error.message
  }
}
</script>

<template>
  <div class="page">
    <header class="page-header">
      <p>AI 助教</p>
      <h1>大模型设置</h1>
    </header>

    <section class="ai-settings-layout">
      <div class="ai-settings-intro">
        <BrainCircuit :size="28" />
        <div>
          <h2>OpenAI 兼容接口</h2>
          <p>设置只保存在当前设备的 WebView 本地存储中，不会写入业务后端。</p>
        </div>
      </div>

      <form class="ai-settings-form" @submit.prevent="saveSettings">
        <label>
          <span><Link2 :size="17" />基础请求地址</span>
          <input
            v-model="baseUrl"
            autocomplete="url"
            inputmode="url"
            name="aiBaseUrl"
            placeholder="https://api.openai.com/v1"
          />
          <small>仅填写域名时自动补 /v1/chat/completions；也可以直接填写完整接口地址。</small>
        </label>

        <label>
          <span><KeyRound :size="17" />SK</span>
          <div class="secret-input-row">
            <input
              v-model="apiKey"
              :type="apiKeyVisible ? 'text' : 'password'"
              autocomplete="off"
              name="aiApiKey"
              placeholder="sk-..."
            />
            <button class="icon-button" type="button" :aria-label="apiKeyVisible ? '隐藏 SK' : '显示 SK'" @click="apiKeyVisible = !apiKeyVisible">
              <EyeOff v-if="apiKeyVisible" :size="19" />
              <Eye v-else :size="19" />
            </button>
          </div>
        </label>

        <label>
          <span>模型名称</span>
          <input v-model="model" autocomplete="off" name="aiModel" placeholder="例如：gpt-5" />
        </label>

        <label>
          <span>Effort</span>
          <select v-model="effort" name="aiEffort">
            <option v-for="option in effortOptions" :key="option.value || 'default'" :value="option.value">
              {{ option.label }}
            </option>
          </select>
          <small>发送时映射为 reasoning_effort；可用值取决于所选模型和兼容服务。</small>
        </label>

        <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
        <p v-if="noticeMessage" class="notice-message">{{ noticeMessage }}</p>

        <button class="primary-button ai-settings-save" type="submit">
          <Save :size="18" />
          <span>保存设置</span>
        </button>
      </form>

      <p class="ai-settings-security-note">
        SK 以明文形式保存在 WebView localStorage 中。请只在你信任的设备上使用。
      </p>
    </section>
  </div>
</template>
