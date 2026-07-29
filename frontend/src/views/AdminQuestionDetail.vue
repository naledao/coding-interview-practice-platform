<script setup>
import { onMounted, ref } from 'vue'
import { CheckCircle2, Circle, EyeOff, RefreshCw, RotateCcw } from '@lucide/vue'
import { disableAdminQuestion, enableAdminQuestion, fetchAdminQuestion } from '../api'
import MarkdownContent from '../components/MarkdownContent.vue'
import { getQueryParam, openDetailPage, pageHref } from '../navigation'

const questionId = getQueryParam('questionId')
const question = ref(null)
const loading = ref(false)
const errorMessage = ref('')
const actionMessage = ref('')

onMounted(loadQuestion)

async function loadQuestion() {
  loading.value = true
  errorMessage.value = ''
  try {
    question.value = await fetchAdminQuestion(questionId)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

async function changeStatus(nextStatus) {
  if (!question.value) {
    return
  }
  actionMessage.value = ''
  errorMessage.value = ''
  try {
    question.value = nextStatus === 'ACTIVE'
      ? await enableAdminQuestion(question.value.id)
      : await disableAdminQuestion(question.value.id)
    actionMessage.value = nextStatus === 'ACTIVE' ? '题目已恢复' : '题目已下线'
  } catch (error) {
    errorMessage.value = error.message
  }
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString() : '-'
}

function tagText(tags) {
  return (tags || []).map((tag) => tag.name).join(' / ') || '-'
}
</script>

<template>
  <div class="page">
    <header class="page-header action-header">
      <div>
        <p>题目详情</p>
        <h1>#{{ question?.id || questionId }}</h1>
      </div>
      <div class="header-actions">
        <button class="secondary-button" type="button" :disabled="loading" @click="loadQuestion">
          <RefreshCw :size="17" />
          <span>刷新</span>
        </button>
        <button
          v-if="question?.status === 'ACTIVE'"
          class="danger-button"
          type="button"
          @click="changeStatus('DISABLED')"
        >
          <EyeOff :size="17" />
          <span>下线</span>
        </button>
        <button
          v-if="question?.status === 'DISABLED'"
          class="secondary-button"
          type="button"
          @click="changeStatus('ACTIVE')"
        >
          <RotateCcw :size="17" />
          <span>恢复</span>
        </button>
      </div>
    </header>

    <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
    <p v-if="actionMessage" class="notice-message">{{ actionMessage }}</p>

    <section v-if="question" class="tool-panel">
      <dl class="detail-grid">
        <div>
          <dt>状态</dt>
          <dd>{{ question.status }}</dd>
        </div>
        <div>
          <dt>难度</dt>
          <dd>{{ question.difficulty }}</dd>
        </div>
        <div>
          <dt>知识点</dt>
          <dd>{{ question.knowledgePoint }}</dd>
        </div>
        <div>
          <dt>标签</dt>
          <dd>{{ tagText(question.tags) }}</dd>
        </div>
        <div>
          <dt>来源任务</dt>
          <dd>
            <a
              :href="pageHref('admin-import-job-detail.html', { jobId: question.sourceImportJobId })"
              @click.prevent="openDetailPage('importJob', question.sourceImportJobId)"
            >
              #{{ question.sourceImportJobId }}
            </a>
          </dd>
        </div>
        <div>
          <dt>来源文档</dt>
          <dd>
            <a
              :href="pageHref('admin-document-detail.html', { documentId: question.sourceDocumentId })"
              @click.prevent="openDetailPage('document', question.sourceDocumentId)"
            >
              #{{ question.sourceDocumentId }}
            </a>
          </dd>
        </div>
        <div>
          <dt>创建时间</dt>
          <dd>{{ formatDate(question.createdAt) }}</dd>
        </div>
      </dl>
    </section>

    <section v-if="question" class="tool-panel">
      <div class="section-title">
        <h2>题干</h2>
      </div>
      <MarkdownContent class="question-stem" :source="question.stem" />
      <div class="option-list">
        <div v-for="option in question.options" :key="option.id" class="option-row" :class="{ 'option-correct': option.correct }">
          <CheckCircle2 v-if="option.correct" :size="18" />
          <Circle v-else :size="18" />
          <strong>{{ option.optionKey }}</strong>
          <MarkdownContent class="option-markdown" :source="option.content" />
        </div>
      </div>
    </section>

    <section v-if="question" class="tool-panel">
      <div class="section-title">
        <h2>解析与 Review</h2>
      </div>
      <MarkdownContent class="analysis-text" :source="question.answerAnalysis" />
      <MarkdownContent class="review-text" :source="question.codexReviewSummary" />
    </section>
  </div>
</template>
