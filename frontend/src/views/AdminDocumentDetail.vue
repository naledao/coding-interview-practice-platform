<script setup>
import { onMounted, ref } from 'vue'
import { FileText, Play, RefreshCw } from '@lucide/vue'
import { createDocumentImportJob, fetchAdminDocument } from '../api'
import MarkdownContent from '../components/MarkdownContent.vue'
import { getQueryParam, openDetailPage, pageHref } from '../navigation'

const documentId = getQueryParam('documentId')
const document = ref(null)
const loading = ref(false)
const errorMessage = ref('')
const actionMessage = ref('')

onMounted(loadDocument)

async function loadDocument() {
  loading.value = true
  errorMessage.value = ''
  try {
    document.value = await fetchAdminDocument(documentId)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

async function triggerImport() {
  if (!document.value) {
    return
  }
  actionMessage.value = ''
  errorMessage.value = ''
  try {
    const result = await createDocumentImportJob(document.value.id)
    actionMessage.value = `已创建导入任务 #${result.importJobId}`
    await loadDocument()
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
  <div class="page">
    <header class="page-header action-header">
      <div>
        <p>文档详情</p>
        <h1>{{ document?.originalFilename || '知识文档' }}</h1>
      </div>
      <div class="header-actions">
        <button class="secondary-button" type="button" :disabled="loading" @click="loadDocument">
          <RefreshCw :size="17" />
          <span>刷新</span>
        </button>
        <button class="primary-button" type="button" @click="triggerImport">
          <Play :size="17" />
          <span>导入</span>
        </button>
      </div>
    </header>

    <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
    <p v-if="actionMessage" class="notice-message">{{ actionMessage }}</p>

    <section v-if="document" class="tool-panel">
      <div class="section-title">
        <FileText :size="20" />
        <h2>基础信息</h2>
      </div>
      <dl class="detail-grid">
        <div>
          <dt>文档 ID</dt>
          <dd>{{ document.id }}</dd>
        </div>
        <div>
          <dt>上传批次</dt>
          <dd>
            <a
              :href="pageHref('admin-document-upload-result.html', { uploadId: document.uploadId })"
              @click.prevent="openDetailPage('documentUpload', document.uploadId)"
            >
              #{{ document.uploadId }}
            </a>
          </dd>
        </div>
        <div>
          <dt>来源类型</dt>
          <dd>{{ document.sourceType }}</dd>
        </div>
        <div>
          <dt>文件大小</dt>
          <dd>{{ formatBytes(document.fileSize) }}</dd>
        </div>
        <div>
          <dt>文档状态</dt>
          <dd>{{ document.status }}</dd>
        </div>
        <div>
          <dt>上传人</dt>
          <dd>{{ document.uploadedBy }}</dd>
        </div>
        <div>
          <dt>上传时间</dt>
          <dd>{{ formatDate(document.createdAt) }}</dd>
        </div>
        <div>
          <dt>生成题目数</dt>
          <dd>{{ document.generatedQuestionCount }}</dd>
        </div>
        <div class="wide-detail">
          <dt>Markdown Hash</dt>
          <dd>{{ document.contentSha256 }}</dd>
        </div>
        <div class="wide-detail">
          <dt>存储路径</dt>
          <dd>{{ document.storedPath }}</dd>
        </div>
        <div v-if="document.sourceType === 'ZIP'">
          <dt>压缩包</dt>
          <dd>{{ document.archiveOriginalFilename }}</dd>
        </div>
        <div v-if="document.sourceType === 'ZIP'" class="wide-detail">
          <dt>包内路径</dt>
          <dd>{{ document.archiveEntryPath }}</dd>
        </div>
      </dl>
    </section>

    <section v-if="document?.latestJob" class="tool-panel">
      <div class="section-title">
        <h2>最近导入任务</h2>
      </div>
      <div class="compact-list">
        <a
          class="compact-row"
          :href="pageHref('admin-import-job-detail.html', { jobId: document.latestJob.id })"
          @click.prevent="openDetailPage('importJob', document.latestJob.id)"
        >
          <span>#{{ document.latestJob.id }}</span>
          <strong>{{ document.latestJob.status }}</strong>
        </a>
      </div>
    </section>

    <section v-if="document?.content" class="tool-panel">
      <div class="section-title">
        <FileText :size="20" />
        <h2>Markdown 预览</h2>
      </div>
      <MarkdownContent class="document-markdown" :source="document.content" />
    </section>
  </div>
</template>
