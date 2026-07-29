<script setup>
import { computed, onMounted, ref } from 'vue'
import { Eye, ListChecks, RefreshCw, RotateCcw, Search, X } from '@lucide/vue'
import { fetchImportJobs, retryImportJob } from '../api'
import { openDetailPage, pageHref } from '../navigation'

const jobs = ref([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const errorMessage = ref('')
const actionMessage = ref('')
const retryingJobId = ref(null)
const filters = ref({
  status: '',
  documentName: '',
  createdFrom: '',
  createdTo: '',
})

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'PENDING', label: '等待执行' },
  { value: 'RUNNING', label: 'Codex 正在处理' },
  { value: 'SUCCEEDED', label: '处理成功' },
  { value: 'FAILED', label: '处理失败' },
  { value: 'CANCELLED', label: '已取消' },
]

const statusText = {
  PENDING: '等待执行',
  RUNNING: 'Codex 正在处理',
  SUCCEEDED: '处理成功',
  FAILED: '处理失败',
  CANCELLED: '已取消',
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const hasActiveFilters = computed(() =>
  Boolean(filters.value.status || filters.value.documentName.trim() || filters.value.createdFrom || filters.value.createdTo)
)

onMounted(loadJobs)

async function loadJobs() {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await fetchImportJobs({
      page: page.value,
      pageSize: pageSize.value,
      status: filters.value.status,
      documentName: filters.value.documentName,
      createdFrom: toApiDateTime(filters.value.createdFrom),
      createdTo: toApiDateTime(filters.value.createdTo),
    })
    jobs.value = result.items
    total.value = result.total
    page.value = result.page
    pageSize.value = result.pageSize
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  page.value = 1
  loadJobs()
}

function resetFilters() {
  filters.value = {
    status: '',
    documentName: '',
    createdFrom: '',
    createdTo: '',
  }
  applyFilters()
}

async function retryJob(job) {
  retryingJobId.value = job.id
  actionMessage.value = ''
  errorMessage.value = ''
  try {
    const result = await retryImportJob(job.id)
    actionMessage.value = `已创建重试任务 #${result.newImportJobId}`
    await loadJobs()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    retryingJobId.value = null
  }
}

function previousPage() {
  if (page.value <= 1) {
    return
  }
  page.value -= 1
  loadJobs()
}

function nextPage() {
  if (page.value >= totalPages.value) {
    return
  }
  page.value += 1
  loadJobs()
}

function toApiDateTime(value) {
  return value ? new Date(value).toISOString() : ''
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString() : '-'
}

function failedSummary(value) {
  if (!value) {
    return '-'
  }
  return value.length > 80 ? `${value.slice(0, 80)}...` : value
}
</script>

<template>
  <div class="page wide-page">
    <header class="page-header action-header">
      <div>
        <p>管理员</p>
        <h1>导入任务</h1>
      </div>
      <div class="header-actions">
        <button class="secondary-button" type="button" :disabled="loading" @click="loadJobs">
          <RefreshCw :size="17" />
          <span>刷新</span>
        </button>
      </div>
    </header>

    <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
    <p v-if="actionMessage" class="notice-message">{{ actionMessage }}</p>

    <section class="tool-panel">
      <div class="filter-grid">
        <label>
          <span>状态</span>
          <select v-model="filters.status" class="select-control" @change="applyFilters">
            <option v-for="option in statusOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>
        <label>
          <span>文档名称</span>
          <input v-model="filters.documentName" class="text-control" type="search" placeholder="jvm.md" @keyup.enter="applyFilters" />
        </label>
        <label>
          <span>创建时间起</span>
          <input v-model="filters.createdFrom" class="text-control" type="datetime-local" />
        </label>
        <label>
          <span>创建时间止</span>
          <input v-model="filters.createdTo" class="text-control" type="datetime-local" />
        </label>
        <div class="filter-actions">
          <button class="primary-button" type="button" :disabled="loading" @click="applyFilters">
            <Search :size="17" />
            <span>筛选</span>
          </button>
          <button class="secondary-button" type="button" :disabled="loading || !hasActiveFilters" @click="resetFilters">
            <X :size="17" />
            <span>重置</span>
          </button>
        </div>
      </div>
    </section>

    <section class="table-panel">
      <div class="section-title">
        <ListChecks :size="20" />
        <h2>任务列表</h2>
        <span>{{ total }} 条</span>
      </div>
      <div class="data-table import-job-table">
        <div class="table-row table-head">
          <span>ID</span>
          <span>文档</span>
          <span>状态</span>
          <span>生成题目</span>
          <span>开始时间</span>
          <span>结束时间</span>
          <span>失败原因</span>
          <span>操作</span>
        </div>
        <div v-for="job in jobs" :key="job.id" class="table-row">
          <span>#{{ job.id }}</span>
          <a
            :href="pageHref('admin-document-detail.html', { documentId: job.documentId })"
            @click.prevent="openDetailPage('document', job.documentId)"
          >
            {{ job.documentName }}
          </a>
          <span>
            <span class="status-pill" :class="`status-${job.status?.toLowerCase()}`">
              {{ statusText[job.status] || job.status }}
            </span>
          </span>
          <span>{{ job.generatedQuestionCount }}</span>
          <span>{{ formatDate(job.startedAt) }}</span>
          <span>{{ formatDate(job.finishedAt) }}</span>
          <span :title="job.failedReason || ''">{{ failedSummary(job.failedReason) }}</span>
          <span class="row-actions">
            <a
              class="icon-button"
              :href="pageHref('admin-import-job-detail.html', { jobId: job.id })"
              title="查看详情"
              @click.prevent="openDetailPage('importJob', job.id)"
            >
              <Eye :size="16" />
            </a>
            <button
              class="icon-button"
              type="button"
              title="重试失败任务"
              :disabled="job.status !== 'FAILED' || retryingJobId === job.id"
              @click="retryJob(job)"
            >
              <RotateCcw :size="16" />
            </button>
          </span>
        </div>
        <p v-if="!loading && jobs.length === 0" class="empty-state">暂无导入任务</p>
      </div>
      <div class="pagination-bar">
        <button class="secondary-button" type="button" :disabled="loading || page <= 1" @click="previousPage">上一页</button>
        <span>第 {{ page }} / {{ totalPages }} 页</span>
        <button class="secondary-button" type="button" :disabled="loading || page >= totalPages" @click="nextPage">下一页</button>
      </div>
    </section>
  </div>
</template>
