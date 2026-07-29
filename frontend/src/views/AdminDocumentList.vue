<script setup>
import { onMounted, ref } from 'vue'
import { FileText, Play, RefreshCw } from '@lucide/vue'
import { createDocumentImportJob, fetchAdminDocuments } from '../api'
import { openDetailPage, pageHref } from '../navigation'

const documents = ref([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const errorMessage = ref('')
const actionMessage = ref('')

onMounted(loadDocuments)

async function loadDocuments() {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await fetchAdminDocuments(page.value, pageSize.value)
    documents.value = result.items
    total.value = result.total
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

async function triggerImport(documentId) {
  actionMessage.value = ''
  errorMessage.value = ''
  try {
    const result = await createDocumentImportJob(documentId)
    actionMessage.value = `已创建导入任务 #${result.importJobId}`
    await loadDocuments()
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
</script>

<template>
  <div class="page wide-page">
    <header class="page-header action-header">
      <div>
        <p>管理员</p>
        <h1>知识文档</h1>
      </div>
      <button class="secondary-button" type="button" :disabled="loading" @click="loadDocuments">
        <RefreshCw :size="17" />
        <span>刷新</span>
      </button>
    </header>

    <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
    <p v-if="actionMessage" class="notice-message">{{ actionMessage }}</p>

    <section class="table-panel">
      <div class="section-title">
        <FileText :size="20" />
        <h2>文档列表</h2>
        <span>{{ total }} 条</span>
      </div>
      <div class="data-table document-table">
        <div class="table-row table-head">
          <span>文件名</span>
          <span>来源</span>
          <span>包内路径</span>
          <span>大小</span>
          <span>上传人</span>
          <span>上传时间</span>
          <span>文档状态</span>
          <span>任务</span>
          <span>操作</span>
        </div>
        <div v-for="document in documents" :key="document.id" class="table-row">
          <a
            :href="pageHref('admin-document-detail.html', { documentId: document.id })"
            @click.prevent="openDetailPage('document', document.id)"
          >
            {{ document.originalFilename }}
          </a>
          <span>{{ document.sourceType }}</span>
          <span>{{ document.archiveEntryPath || '-' }}</span>
          <span>{{ formatBytes(document.fileSize) }}</span>
          <span>{{ document.uploadedBy }}</span>
          <span>{{ formatDate(document.createdAt) }}</span>
          <span>{{ document.status }}</span>
          <a
            v-if="document.latestJob"
            :href="pageHref('admin-import-job-detail.html', { jobId: document.latestJob.id })"
            @click.prevent="openDetailPage('importJob', document.latestJob.id)"
          >
            #{{ document.latestJob.id }} · {{ document.latestJob.status }}
          </a>
          <span v-else>-</span>
          <button class="icon-button" type="button" title="重新触发导入" @click="triggerImport(document.id)">
            <Play :size="16" />
          </button>
        </div>
        <p v-if="!loading && documents.length === 0" class="empty-state">暂无文档</p>
      </div>
    </section>
  </div>
</template>
