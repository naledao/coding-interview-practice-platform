<script setup>
import { computed, ref } from 'vue'
import { CheckCircle2, FileArchive, FileText, UploadCloud } from '@lucide/vue'
import { uploadAdminDocument } from '../api'
import { openDetailPage } from '../navigation'

const selectedFile = ref(null)
const autoStart = ref(true)
const uploading = ref(false)
const errorMessage = ref('')
const uploadResult = ref(null)

const fileLabel = computed(() => {
  if (!selectedFile.value) {
    return '选择 Markdown 或 ZIP 文件'
  }
  return `${selectedFile.value.name} · ${formatBytes(selectedFile.value.size)}`
})

function handleFileChange(event) {
  errorMessage.value = ''
  uploadResult.value = null
  selectedFile.value = event.target.files?.[0] || null
}

async function submit() {
  errorMessage.value = ''
  uploadResult.value = null

  if (!selectedFile.value) {
    errorMessage.value = '请选择文件'
    return
  }

  const lowerName = selectedFile.value.name.toLowerCase()
  if (!lowerName.endsWith('.md') && !lowerName.endsWith('.zip')) {
    errorMessage.value = '仅支持 Markdown 文件或 ZIP 压缩包'
    return
  }

  uploading.value = true
  try {
    const result = await uploadAdminDocument(selectedFile.value, autoStart.value)
    uploadResult.value = result
    openDetailPage('documentUpload', result.uploadId)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    uploading.value = false
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
</script>

<template>
  <div class="page">
    <header class="page-header">
      <p>管理员</p>
      <h1>文档上传</h1>
    </header>

    <section class="tool-panel">
      <form class="upload-form" @submit.prevent="submit">
        <label class="file-drop">
          <input accept=".md,.zip,text/markdown,application/zip" type="file" @change="handleFileChange" />
          <span class="file-drop-icon">
            <UploadCloud :size="28" />
          </span>
          <strong>{{ fileLabel }}</strong>
          <span>支持 .md 和 .zip，ZIP 内每个 Markdown 会生成独立导入任务</span>
        </label>

        <label class="toggle-row">
          <input v-model="autoStart" type="checkbox" />
          <span>解析成功后自动触发 Codex</span>
        </label>

        <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>

        <div class="form-actions">
          <button class="primary-button" type="submit" :disabled="uploading">
            <UploadCloud :size="18" />
            <span>{{ uploading ? '上传中' : '上传' }}</span>
          </button>
        </div>
      </form>
    </section>

    <section v-if="uploadResult" class="tool-panel">
      <div class="section-title">
        <CheckCircle2 :size="20" />
        <h2>解析结果</h2>
      </div>
      <div class="metric-grid">
        <div>
          <span>上传类型</span>
          <strong>{{ uploadResult.uploadType }}</strong>
        </div>
        <div>
          <span>解析状态</span>
          <strong>{{ uploadResult.parseStatus }}</strong>
        </div>
        <div>
          <span>解析任务</span>
          <strong>{{ uploadResult.parseTaskStatus }}</strong>
        </div>
        <div>
          <span>文档数量</span>
          <strong>{{ uploadResult.documentCount }}</strong>
        </div>
        <div>
          <span>忽略文件</span>
          <strong>{{ uploadResult.ignoredFileCount }}</strong>
        </div>
        <div>
          <span>跳过文件</span>
          <strong>{{ uploadResult.skippedFileCount }}</strong>
        </div>
      </div>

      <div class="compact-list">
        <div v-for="document in uploadResult.documents" :key="document.documentId" class="compact-row">
          <FileText :size="18" />
          <span>{{ document.archiveEntryPath || document.originalFilename }}</span>
          <strong>{{ document.jobStatus }}</strong>
        </div>
        <div v-if="uploadResult.uploadType === 'ZIP'" class="compact-row muted-row">
          <FileArchive :size="18" />
          <span>{{ uploadResult.originalFilename }}</span>
          <strong>ZIP 来源</strong>
        </div>
      </div>
    </section>
  </div>
</template>
