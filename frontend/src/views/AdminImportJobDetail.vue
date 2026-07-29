<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ExternalLink, FileQuestion, RefreshCw, RotateCcw, ScrollText } from '@lucide/vue'
import { fetchImportJob, fetchImportJobLogs, fetchImportJobQuestions, retryImportJob } from '../api'
import MarkdownContent from '../components/MarkdownContent.vue'
import { getQueryParam, openDetailPage, pageHref } from '../navigation'

const jobId = getQueryParam('jobId')
const job = ref(null)
const logs = ref([])
const questions = ref([])
const questionPage = ref(1)
const questionPageSize = ref(20)
const questionTotal = ref(0)
const loading = ref(false)
const errorMessage = ref('')
const actionMessage = ref('')
const retrying = ref(false)
let refreshTimer = null

const statusText = {
  PENDING: '等待执行',
  RUNNING: 'Codex 正在处理',
  SUCCEEDED: '处理成功',
  FAILED: '处理失败',
  CANCELLED: '已取消',
}

const statusDescription = {
  PENDING: '任务已经创建，正在等待 Codex 接手处理。',
  RUNNING: 'Codex 正在读取文档、搜索资料并生成题目。',
  SUCCEEDED: '处理成功，可以查看本任务写入的题目。',
  FAILED: '处理失败，请查看失败原因和错误日志后重试。',
  CANCELLED: '任务已取消，当前版本暂不支持从页面取消任务。',
}

const canAutoRefresh = computed(() => job.value?.status === 'PENDING' || job.value?.status === 'RUNNING')
const questionTotalPages = computed(() => Math.max(1, Math.ceil(questionTotal.value / questionPageSize.value)))

onMounted(async () => {
  await loadJob()
  refreshTimer = window.setInterval(() => {
    if (canAutoRefresh.value && !loading.value) {
      loadJob({ silent: true })
    }
  }, 6000)
})

onBeforeUnmount(() => {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
  }
})

async function loadJob({ silent = false } = {}) {
  if (!silent) {
    loading.value = true
  }
  errorMessage.value = ''
  try {
    job.value = await fetchImportJob(jobId)
    logs.value = await fetchImportJobLogs(jobId)
    const questionResult = await fetchImportJobQuestions(jobId, questionPage.value, questionPageSize.value)
    questions.value = questionResult.items
    questionPage.value = questionResult.page
    questionPageSize.value = questionResult.pageSize
    questionTotal.value = questionResult.total
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    if (!silent) {
      loading.value = false
    }
  }
}

async function retryJob() {
  if (!job.value) {
    return
  }
  retrying.value = true
  actionMessage.value = ''
  errorMessage.value = ''
  try {
    const result = await retryImportJob(job.value.id)
    actionMessage.value = `已创建重试任务 #${result.newImportJobId}`
    await loadJob({ silent: true })
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    retrying.value = false
  }
}

function previousQuestionPage() {
  if (questionPage.value <= 1) {
    return
  }
  questionPage.value -= 1
  loadJob()
}

function nextQuestionPage() {
  if (questionPage.value >= questionTotalPages.value) {
    return
  }
  questionPage.value += 1
  loadJob()
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString() : '-'
}

function tagText(tags) {
  return (tags || []).map((tag) => tag.name).join(' / ') || '-'
}

function formatPayload(payload) {
  if (!payload) {
    return ''
  }
  try {
    return JSON.stringify(JSON.parse(payload), null, 2)
  } catch (error) {
    return payload
  }
}

function failedReasonText(value) {
  return value || (job.value?.status === 'FAILED' ? '未记录失败原因' : '-')
}
</script>

<template>
  <div class="page">
    <header class="page-header action-header">
      <div>
        <p>导入任务</p>
        <h1>#{{ job?.id || jobId }}</h1>
      </div>
      <div class="header-actions">
        <button class="secondary-button" type="button" :disabled="loading" @click="loadJob">
          <RefreshCw :size="17" />
          <span>刷新</span>
        </button>
        <button class="secondary-button" type="button" :disabled="job?.status !== 'FAILED' || retrying" @click="retryJob">
          <RotateCcw :size="17" />
          <span>重试</span>
        </button>
      </div>
    </header>

    <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
    <p v-if="actionMessage" class="notice-message">{{ actionMessage }}</p>

    <section v-if="job" class="tool-panel">
      <div class="status-summary">
        <div>
          <span class="status-pill" :class="`status-${job.status?.toLowerCase()}`">
            {{ statusText[job.status] || job.status }}
          </span>
          <h2>{{ statusText[job.status] || job.status }}</h2>
          <p>{{ statusDescription[job.status] || '任务状态已更新。' }}</p>
        </div>
        <div>
          <span>已生成</span>
          <strong>{{ job.generatedQuestionCount }}</strong>
          <small>题</small>
        </div>
      </div>
      <dl class="detail-grid">
        <div>
          <dt>任务 ID</dt>
          <dd>#{{ job.id }}</dd>
        </div>
        <div>
          <dt>文档</dt>
          <dd>
            <a
              :href="pageHref('admin-document-detail.html', { documentId: job.documentId })"
              @click.prevent="openDetailPage('document', job.documentId)"
            >
              {{ job.documentName }}
            </a>
          </dd>
        </div>
        <div>
          <dt>状态</dt>
          <dd>{{ statusText[job.status] || job.status }}</dd>
        </div>
        <div>
          <dt>生成题目</dt>
          <dd>{{ job.generatedQuestionCount }}</dd>
        </div>
        <div>
          <dt>Codex Session</dt>
          <dd>{{ job.codexSessionId || '-' }}</dd>
        </div>
        <div>
          <dt>开始时间</dt>
          <dd>{{ formatDate(job.startedAt) }}</dd>
        </div>
        <div>
          <dt>结束时间</dt>
          <dd>{{ formatDate(job.finishedAt) }}</dd>
        </div>
        <div class="wide-detail">
          <dt>失败原因</dt>
          <dd>{{ failedReasonText(job.failedReason) }}</dd>
        </div>
      </dl>
    </section>

    <section class="tool-panel">
      <div class="section-title">
        <FileQuestion :size="20" />
        <h2>生成题目</h2>
        <span>{{ questionTotal }} 道</span>
      </div>
      <div class="data-table job-question-table">
        <div class="table-row table-head">
          <span>ID</span>
          <span>题干</span>
          <span>难度</span>
          <span>知识点</span>
          <span>标签</span>
          <span>状态</span>
          <span>操作</span>
        </div>
        <a
          v-for="question in questions"
          :key="question.id"
          class="table-row"
          :href="pageHref('admin-question-detail.html', { questionId: question.id })"
          @click.prevent="openDetailPage('question', question.id)"
        >
          <span>#{{ question.id }}</span>
          <MarkdownContent class="table-markdown" :source="question.stem" />
          <span>{{ question.difficulty }}</span>
          <span>{{ question.knowledgePoint }}</span>
          <span>{{ tagText(question.tags) }}</span>
          <span>{{ question.status }}</span>
          <span class="icon-text-link">
            <ExternalLink :size="15" />
            查看
          </span>
        </a>
        <p v-if="!loading && questions.length === 0" class="empty-state">暂无生成题目</p>
      </div>
      <div class="pagination-bar">
        <button class="secondary-button" type="button" :disabled="loading || questionPage <= 1" @click="previousQuestionPage">上一页</button>
        <span>第 {{ questionPage }} / {{ questionTotalPages }} 页</span>
        <button class="secondary-button" type="button" :disabled="loading || questionPage >= questionTotalPages" @click="nextQuestionPage">下一页</button>
      </div>
    </section>

    <section class="tool-panel">
      <div class="section-title">
        <ScrollText :size="20" />
        <h2>任务日志</h2>
        <span>{{ logs.length }} 条</span>
      </div>
      <div class="log-list">
        <div v-for="log in logs" :key="log.id" class="log-row">
          <span>{{ formatDate(log.createdAt) }}</span>
          <strong :class="`log-${log.level?.toLowerCase()}`">{{ log.level }}</strong>
          <div>
            <p>{{ log.message }}</p>
            <pre v-if="formatPayload(log.payload)" class="log-payload">{{ formatPayload(log.payload) }}</pre>
          </div>
        </div>
        <p v-if="!loading && logs.length === 0" class="empty-state">暂无日志</p>
      </div>
    </section>
  </div>
</template>
