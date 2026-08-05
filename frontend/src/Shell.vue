<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  BarChart3,
  BookMarked,
  BookOpenCheck,
  BrainCircuit,
  ClipboardList,
  EyeOff,
  FileText,
  FileUp,
  History,
  Library,
  ListChecks,
  LogOut,
  Menu,
  Shield,
  UserRound,
  X,
} from '@lucide/vue'
import { clearToken, fetchCurrentUser, getToken, logout } from './api'
import { currentPageName, isCurrentPage, navigateTo, pageHref } from './navigation'
import { checkForUpdate, installUpdate, isAndroidApp } from './updateService'

const props = defineProps({
  component: {
    type: Object,
    required: true,
  },
  pageKey: {
    type: String,
    required: true,
  },
  publicPage: {
    type: Boolean,
    default: false,
  },
  adminOnly: {
    type: Boolean,
    default: false,
  },
  appOnly: {
    type: Boolean,
    default: false,
  },
})

const user = ref(null)
const loadingUser = ref(false)
const ready = ref(props.publicPage)
const updateInfo = ref(null)
const checkingUpdate = ref(false)
const updateError = ref('')
const pageMenuOpen = ref(false)

const isLoginPage = computed(() => props.pageKey === 'login')
const isPracticePage = computed(() => props.pageKey === 'user')

const navItems = computed(() => [
  { page: 'user.html', label: '刷题', icon: ClipboardList },
  { page: 'wrong-book.html', label: '错题', icon: BookOpenCheck },
  { page: 'favorites.html', label: '收藏', icon: BookMarked },
  { page: 'answered-questions.html', label: '已做', icon: History },
  { page: 'excluded-questions.html', label: '不再出现', icon: EyeOff, appOnly: true },
  { page: 'statistics.html', label: '统计', icon: BarChart3 },
  { page: 'ai-settings.html', label: 'AI 设置', icon: BrainCircuit },
  { page: 'admin.html', label: '管理', icon: Shield, admin: true },
  { page: 'admin-documents-upload.html', label: '上传', icon: FileUp, admin: true },
  { page: 'admin-documents.html', label: '文档', icon: FileText, admin: true },
  { page: 'admin-import-jobs.html', label: '任务', icon: ListChecks, admin: true },
  { page: 'admin-questions.html', label: '题库', icon: Library, admin: true },
  { page: 'profile.html', label: '我的', icon: UserRound },
])
const visibleNavItems = computed(() => navItems.value.filter((item) => (
  (!item.admin || user.value?.role === 'ADMIN')
  && (!item.appOnly || isAndroidApp())
)))

async function refreshUser() {
  if (!getToken()) {
    user.value = null
    ready.value = props.publicPage
    if (!props.publicPage) {
      navigateTo('login.html', {}, true)
    }
    return
  }

  loadingUser.value = true
  try {
    user.value = await fetchCurrentUser()
    ready.value = true
    if (props.adminOnly && user.value?.role !== 'ADMIN') {
      navigateTo('user.html', {}, true)
    }
  } catch {
    user.value = null
    clearToken()
    ready.value = props.publicPage
    if (!props.publicPage) {
      navigateTo('login.html', {}, true)
    }
  } finally {
    loadingUser.value = false
  }
}

async function checkUpdateSilently() {
  if (!isAndroidApp() || checkingUpdate.value) {
    return
  }

  checkingUpdate.value = true
  updateError.value = ''
  try {
    const result = await checkForUpdate()
    updateInfo.value = result.hasUpdate ? result.manifest : null
  } catch (error) {
    updateError.value = error.message
  } finally {
    checkingUpdate.value = false
  }
}

function applyUpdatedUser(updatedUser) {
  if (updatedUser) {
    user.value = updatedUser
    return
  }

  refreshUser()
}

async function handleLogout() {
  try {
    await logout()
  } catch {
    clearToken()
  }
  pageMenuOpen.value = false
  user.value = null
  navigateTo('login.html', {}, true)
}

function openPage(page) {
  pageMenuOpen.value = false
  navigateTo(page)
}

function startUpdate() {
  if (updateInfo.value?.downloadUrl) {
    installUpdate(updateInfo.value.downloadUrl)
  }
}

function dismissUpdate() {
  updateInfo.value = null
}

onMounted(() => {
  refreshUser()
  checkUpdateSilently()
  window.addEventListener('auth-expired', () => {
    if (currentPageName() !== 'login.html') {
      navigateTo('login.html', {}, true)
    }
  })
})
</script>

<template>
  <main class="shell" :class="{ 'shell-login': isLoginPage, 'shell-practice': isPracticePage }">
    <button
      v-if="!isLoginPage"
      class="shell-menu-button"
      type="button"
      aria-label="页面菜单"
      @click="pageMenuOpen = true"
    >
      <Menu :size="22" />
    </button>

    <div v-if="pageMenuOpen && !isLoginPage" class="page-menu-overlay" @click.self="pageMenuOpen = false">
      <section class="page-menu-sheet" aria-label="页面导航">
        <span class="sheet-handle"></span>
        <div class="page-menu-header">
          <strong>Java 面试刷题</strong>
          <button class="icon-button" type="button" aria-label="关闭菜单" @click="pageMenuOpen = false">
            <X :size="20" />
          </button>
        </div>

        <nav class="page-menu-nav" aria-label="主导航">
          <a
            v-for="item in visibleNavItems"
            :key="item.page"
            :class="{ active: isCurrentPage(item.page) }"
            :href="pageHref(item.page)"
            @click.prevent="openPage(item.page)"
          >
            <component :is="item.icon" :size="18" />
            <span>{{ item.label }}</span>
          </a>
        </nav>

        <button class="page-menu-logout" type="button" @click="handleLogout">
          <LogOut :size="18" />
          <span>退出登录</span>
        </button>
      </section>
    </div>

    <div v-if="updateInfo" class="update-dialog-overlay" role="presentation">
      <section class="update-dialog" role="dialog" aria-modal="true" aria-labelledby="update-dialog-title">
        <div class="update-dialog-copy">
          <strong id="update-dialog-title">发现新版本 {{ updateInfo.versionName }}</strong>
          <p>{{ updateInfo.releaseNotes || '可以下载安装最新版本。' }}</p>
        </div>
        <div class="update-dialog-actions">
          <button class="secondary-button" type="button" @click="dismissUpdate">
            <span>稍后</span>
          </button>
          <button class="primary-button" type="button" @click="startUpdate">
            <span>立即更新</span>
          </button>
        </div>
      </section>
    </div>

    <section class="content">
      <component
        :is="component"
        v-if="ready || publicPage"
        :current-user="user"
        :loading-user="loadingUser"
        @authenticated="refreshUser"
        @profile-updated="applyUpdatedUser"
      />
      <div v-else class="page">
        <p class="empty-state">页面加载中...</p>
      </div>
    </section>
  </main>
</template>
