<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { FileText, Play, RefreshCw } from '@lucide/vue'
import { createDocumentImportJob, fetchDocumentUpload } from '../api'
import { getQueryParam, openDetailPage, pageHref } from '../navigation'

const uploadId = getQueryParam('uploadId')
const upload = ref(null)
const loading = ref(false)
const errorMessage = ref('')
const actionMessage = ref('')
let refreshTimer = null

const documents = computed(() => upload.value?.documents || [])

onMounted(loadUpload)
onBeforeUnmount(stopAutoRefresh)

async function loadUpload() {
  loading.value = true
  errorMessage.value = ''
  try {
    upload.value = await fetchDocumentUpload(uploadId)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
    scheduleAutoRefresh()
  }
}

async function triggerImport(documentId) {
  actionMessage.value = ''
  errorMessage.value = ''
  try {
    const result = await createDocumentImportJob(documentId)
    actionMessage.value = `已创建导入任务 #${result.importJobId}`
    await loadUpload()
  } catch (error) {
    errorMessage.value = error.message
  }
}

function formatBytes(value) {
  if (!Number.isFinite(value)) {
    return '-'
  }
  if (value < 1024) {
    return `${value} B`
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`
  }
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString() : '-'
}

function shouldAutoRefresh() {
  return ['QUEUED', 'PARSING'].includes(upload.value?.parseStatus)
    || ['PENDING', 'RUNNING'].includes(upload.value?.parseTaskStatus)
}

function scheduleAutoRefresh() {
  stopAutoRefresh()
  if (!shouldAutoRefresh()) {
    return
  }
  refreshTimer = window.setTimeout(() => {
    refreshTimer = null
    loadUpload()
  }, 2000)
}

function stopAutoRefresh() {
  if (refreshTimer) {
    window.clearTimeout(refreshTimer)
    refreshTimer = null
  }
}
</script>

<template>
  <div class="page wide-page">
    <header class="page-header action-header">
      <div>
        <p>上传结果</p>
        <h1>{{ upload?.originalFilename || '解析结果' }}</h1>
      </div>
      <button class="secondary-button" type="button" :disabled="loading" @click="loadUpload">
        <RefreshCw :size="17" />
        <span>刷新</span>
      </button>
    </header>

    <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
    <p v-if="actionMessage" class="notice-message">{{ actionMessage }}</p>

    <section v-if="upload" class="tool-panel">
      <div class="metric-grid">
        <div>
          <span>上传类型</span>
          <strong>{{ upload.uploadType }}</strong>
        </div>
        <div>
          <span>原文件大小</span>
          <strong>{{ formatBytes(upload.fileSize) }}</strong>
        </div>
        <div>
          <span>解析状态</span>
          <strong>{{ upload.parseStatus }}</strong>
        </div>
        <div>
          <span>解析任务</span>
          <strong>{{ upload.parseTaskStatus || '-' }}</strong>
        </div>
        <div>
          <span>上传时间</span>
          <strong>{{ formatDate(upload.createdAt) }}</strong>
        </div>
        <div>
          <span>识别 Markdown</span>
          <strong>{{ upload.documentCount }}</strong>
        </div>
        <div>
          <span>忽略文件</span>
          <strong>{{ upload.ignoredFileCount }}</strong>
        </div>
        <div>
          <span>跳过文件</span>
          <strong>{{ upload.skippedFileCount }}</strong>
        </div>
        <div>
          <span>上传人</span>
          <strong>{{ upload.uploadedBy }}</strong>
        </div>
        <div v-if="upload.parseFailedReason">
          <span>失败原因</span>
          <strong>{{ upload.parseFailedReason }}</strong>
        </div>
      </div>
    </section>

    <section v-if="upload?.skippedFiles?.length" class="tool-panel">
      <div class="section-title">
        <h2>跳过文件</h2>
      </div>
      <div class="compact-list">
        <div v-for="file in upload.skippedFiles" :key="file.archiveEntryPath" class="compact-row">
          <span>{{ file.archiveEntryPath }}</span>
          <strong>{{ file.reason }}</strong>
        </div>
      </div>
    </section>

    <section class="table-panel">
      <div class="section-title">
        <FileText :size="20" />
        <h2>Markdown 文档</h2>
      </div>
      <div class="data-table">
        <div class="table-row table-head">
          <span>文件</span>
          <span>包内路径</span>
          <span>大小</span>
          <span>文档状态</span>
          <span>任务</span>
          <span>操作</span>
        </div>
        <div v-for="document in documents" :key="document.documentId" class="table-row">
          <a
            :href="pageHref('admin-document-detail.html', { documentId: document.documentId })"
            @click.prevent="openDetailPage('document', document.documentId)"
          >
            {{ document.originalFilename }}
          </a>
          <span>{{ document.archiveEntryPath || '-' }}</span>
          <span>{{ formatBytes(document.fileSize) }}</span>
          <span>{{ document.documentStatus }}</span>
          <a
            v-if="document.latestJob"
            :href="pageHref('admin-import-job-detail.html', { jobId: document.latestJob.id })"
            @click.prevent="openDetailPage('importJob', document.latestJob.id)"
          >
            #{{ document.latestJob.id }} · {{ document.latestJob.status }}
          </a>
          <span v-else>-</span>
          <button class="icon-button" type="button" title="重新触发导入" @click="triggerImport(document.documentId)">
            <Play :size="16" />
          </button>
        </div>
        <p v-if="!loading && documents.length === 0" class="empty-state">暂无 Markdown 文档</p>
      </div>
    </section>
  </div>
</template>
