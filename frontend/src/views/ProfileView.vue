<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { Check, Download, Globe2, LogOut, Pencil, RefreshCw, Save, ShieldCheck, UserRound, X } from '@lucide/vue'
import { clearToken, getApiBaseUrl, logout, setApiBaseUrl, updateNickname } from '../api'
import { navigateTo } from '../navigation'
import { checkForUpdate, getCurrentVersionName, installUpdate } from '../updateService'

const props = defineProps({
  currentUser: {
    type: Object,
    default: null,
  },
  loadingUser: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['profile-updated'])
const editingNickname = ref(false)
const nicknameInput = ref('')
const savingNickname = ref(false)
const errorMessage = ref('')
const noticeMessage = ref('')
const appVersion = ref('')
const apiBaseUrlInput = ref(getApiBaseUrl())
const checkingUpdate = ref(false)
const updateManifest = ref(null)

const displayNickname = computed(() => props.currentUser?.nickname || (props.loadingUser ? '加载中' : '未登录'))
const isAndroidApp = computed(() => typeof window.AndroidBridge !== 'undefined')

watch(
  () => props.currentUser?.nickname,
  (nickname) => {
    if (!editingNickname.value) {
      nicknameInput.value = nickname || ''
    }
  },
  { immediate: true },
)

onMounted(() => {
  if (isAndroidApp.value) {
    appVersion.value = getCurrentVersionName() || window.AndroidBridge.getAppVersion?.() || ''
  }
})

function startEditNickname() {
  nicknameInput.value = props.currentUser?.nickname || ''
  editingNickname.value = true
  errorMessage.value = ''
  noticeMessage.value = ''
}

function cancelEditNickname() {
  editingNickname.value = false
  nicknameInput.value = props.currentUser?.nickname || ''
  errorMessage.value = ''
}

async function saveNickname() {
  errorMessage.value = ''
  noticeMessage.value = ''

  const nickname = nicknameInput.value.trim()
  if (!nickname) {
    errorMessage.value = '昵称不能为空'
    return
  }

  savingNickname.value = true
  try {
    const updatedUser = await updateNickname(nickname)
    editingNickname.value = false
    noticeMessage.value = '昵称已更新'
    emit('profile-updated', updatedUser)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    savingNickname.value = false
  }
}

async function handleLogout() {
  try {
    await logout()
  } catch {
    clearToken()
  }
  navigateTo('login.html', {}, true)
}

function saveApiBaseUrl() {
  setApiBaseUrl(apiBaseUrlInput.value)
  apiBaseUrlInput.value = getApiBaseUrl()
  noticeMessage.value = '后端地址已保存'
  errorMessage.value = ''
}

function openInstallPermissionSettings() {
  if (isAndroidApp.value && typeof window.AndroidBridge.openInstallPermissionSettings === 'function') {
    window.AndroidBridge.openInstallPermissionSettings()
  }
}

async function handleCheckUpdate() {
  checkingUpdate.value = true
  errorMessage.value = ''
  noticeMessage.value = ''
  updateManifest.value = null
  try {
    const result = await checkForUpdate()
    if (result.hasUpdate) {
      updateManifest.value = result.manifest
      noticeMessage.value = `发现新版本 ${result.manifest.versionName}`
    } else {
      noticeMessage.value = '当前已是最新版本'
    }
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    checkingUpdate.value = false
  }
}

function handleInstallUpdate() {
  if (updateManifest.value?.downloadUrl) {
    installUpdate(updateManifest.value.downloadUrl)
  }
}
</script>

<template>
  <div class="page">
    <header class="page-header">
      <p>我的</p>
      <h1>{{ displayNickname }}</h1>
    </header>

    <section class="profile-layout">
      <div class="profile-main">
        <div class="avatar" aria-hidden="true">
          <UserRound :size="34" />
        </div>
        <div class="profile-identity">
          <div class="profile-title-row">
            <h2>{{ displayNickname }}</h2>
            <button
              v-if="currentUser && !editingNickname"
              class="icon-button"
              type="button"
              title="修改昵称"
              aria-label="修改昵称"
              @click="startEditNickname"
            >
              <Pencil :size="17" />
            </button>
          </div>
          <p>{{ currentUser?.email || '-' }}</p>
        </div>
      </div>

      <form v-if="editingNickname" class="nickname-form" @submit.prevent="saveNickname">
        <label>
          <span>昵称</span>
          <input v-model="nicknameInput" autocomplete="nickname" maxlength="64" name="nickname" />
        </label>

        <div class="nickname-actions">
          <button class="primary-button" type="submit" :disabled="savingNickname">
            <Check :size="18" />
            <span>{{ savingNickname ? '保存中' : '保存' }}</span>
          </button>
          <button class="secondary-button" type="button" :disabled="savingNickname" @click="cancelEditNickname">
            <X :size="18" />
            <span>取消</span>
          </button>
        </div>
      </form>

      <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
      <p v-if="noticeMessage" class="notice-message">{{ noticeMessage }}</p>

      <dl class="profile-stats">
        <div>
          <dt>当前角色</dt>
          <dd>
            <ShieldCheck :size="18" />
            <span>{{ currentUser?.role || '-' }}</span>
          </dd>
        </div>
        <div>
          <dt>总答题数</dt>
          <dd>0</dd>
        </div>
        <div>
          <dt>正确率</dt>
          <dd>0%</dd>
        </div>
      </dl>

      <section class="app-settings">
        <div class="section-title">
          <Globe2 :size="20" />
          <h2>APP 设置</h2>
        </div>

        <form class="nickname-form" @submit.prevent="saveApiBaseUrl">
          <label>
            <span>后端地址</span>
            <input v-model="apiBaseUrlInput" inputmode="url" name="apiBaseUrl" placeholder="http://127.0.0.1:8904" />
          </label>
          <button class="secondary-button" type="submit">
            <Save :size="18" />
            <span>保存地址</span>
          </button>
        </form>

        <div class="compact-list">
          <div class="compact-row">
            <span>APP 版本</span>
            <strong>{{ appVersion || 'Web' }}</strong>
          </div>
          <button v-if="isAndroidApp" class="compact-row compact-button" type="button" @click="openInstallPermissionSettings">
            <Download :size="18" />
            <span>安装权限</span>
            <strong>打开设置</strong>
          </button>
          <button v-if="isAndroidApp" class="compact-row compact-button" type="button" :disabled="checkingUpdate" @click="handleCheckUpdate">
            <RefreshCw :size="18" />
            <span>版本更新</span>
            <strong>{{ checkingUpdate ? '检查中' : '检查更新' }}</strong>
          </button>
          <button
            v-if="updateManifest"
            class="compact-row compact-button"
            type="button"
            @click="handleInstallUpdate"
          >
            <Download :size="18" />
            <span>{{ updateManifest.versionName }}</span>
            <strong>下载安装</strong>
          </button>
        </div>
      </section>

      <button class="danger-button" type="button" @click="handleLogout">
        <LogOut :size="18" />
        <span>退出登录</span>
      </button>
    </section>
  </div>
</template>
