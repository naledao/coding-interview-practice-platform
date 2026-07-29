<script setup>
import { ref } from 'vue'
import { Globe2, KeyRound, LogIn, Save } from '@lucide/vue'
import {
  getApiBaseUrl,
  login,
  sendLoginCode,
  setApiBaseUrl,
  setToken,
  takeRedirectAfterLogin,
} from '../api'
import { navigateHref, navigateTo } from '../navigation'

const emit = defineEmits(['authenticated'])

const email = ref('admin@example.com')
const code = ref('123456')
const submitting = ref(false)
const sendingCode = ref(false)
const errorMessage = ref('')
const noticeMessage = ref('')
const apiBaseUrlInput = ref(getApiBaseUrl())

async function requestCode() {
  errorMessage.value = ''
  noticeMessage.value = ''
  sendingCode.value = true
  try {
    await sendLoginCode(email.value.trim())
    noticeMessage.value = '验证码已发送'
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    sendingCode.value = false
  }
}

async function submit() {
  errorMessage.value = ''
  noticeMessage.value = ''
  submitting.value = true
  try {
    const result = await login(email.value.trim(), code.value.trim())
    setToken(result.token)
    emit('authenticated')
    const redirectPage = takeRedirectAfterLogin()
    if (redirectPage) {
      navigateHref(redirectPage, true)
    } else {
      navigateTo(result.user.role === 'ADMIN' ? 'admin.html' : 'user.html', {}, true)
    }
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    submitting.value = false
  }
}

function saveApiBaseUrl() {
  setApiBaseUrl(apiBaseUrlInput.value)
  apiBaseUrlInput.value = getApiBaseUrl()
  errorMessage.value = ''
  noticeMessage.value = apiBaseUrlInput.value
    ? '后端地址已保存'
    : '已恢复默认后端地址'
}
</script>

<template>
  <div class="login-screen">
    <section class="login-panel">
      <div class="login-copy">
        <p>Java Interview Practice</p>
        <h1>登录</h1>
      </div>

      <form class="login-form" @submit.prevent="submit">
        <label>
          <span>邮箱</span>
          <input v-model="email" autocomplete="email" inputmode="email" name="email" type="email" />
        </label>

        <label>
          <span>验证码</span>
          <div class="code-row">
            <input v-model="code" autocomplete="one-time-code" inputmode="numeric" name="code" />
            <button class="secondary-button" type="button" :disabled="sendingCode" @click="requestCode">
              <KeyRound :size="17" />
              <span>{{ sendingCode ? '发送中' : '获取' }}</span>
            </button>
          </div>
        </label>

        <section class="login-api-settings">
          <div class="section-title">
            <Globe2 :size="18" />
            <h2>后端地址</h2>
          </div>
          <label>
            <span>请求地址</span>
            <div class="code-row">
              <input
                v-model="apiBaseUrlInput"
                autocomplete="url"
                inputmode="url"
                name="apiBaseUrl"
                placeholder="http://127.0.0.1:8904"
              />
              <button class="secondary-button" type="button" @click="saveApiBaseUrl">
                <Save :size="17" />
                <span>保存</span>
              </button>
            </div>
          </label>
        </section>

        <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
        <p v-if="noticeMessage" class="notice-message">{{ noticeMessage }}</p>

        <button class="primary-button" type="submit" :disabled="submitting">
          <LogIn :size="18" />
          <span>{{ submitting ? '登录中' : '登录' }}</span>
        </button>
      </form>
    </section>
  </div>
</template>
