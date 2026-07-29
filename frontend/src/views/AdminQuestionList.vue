<script setup>
import { onMounted, ref } from 'vue'
import { Library, RefreshCw } from '@lucide/vue'
import { fetchAdminQuestions } from '../api'
import MarkdownContent from '../components/MarkdownContent.vue'
import { openDetailPage, pageHref } from '../navigation'

const questions = ref([])
const total = ref(0)
const loading = ref(false)
const errorMessage = ref('')
const status = ref('')

onMounted(loadQuestions)

async function loadQuestions() {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await fetchAdminQuestions(1, 20, status.value)
    questions.value = result.items
    total.value = result.total
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
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
  <div class="page wide-page">
    <header class="page-header action-header">
      <div>
        <p>管理员</p>
        <h1>题库管理</h1>
      </div>
      <div class="header-actions">
        <select v-model="status" class="select-control" @change="loadQuestions">
          <option value="">全部状态</option>
          <option value="ACTIVE">ACTIVE</option>
          <option value="DISABLED">DISABLED</option>
        </select>
        <button class="secondary-button" type="button" :disabled="loading" @click="loadQuestions">
          <RefreshCw :size="17" />
          <span>刷新</span>
        </button>
      </div>
    </header>

    <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>

    <section class="table-panel">
      <div class="section-title">
        <Library :size="20" />
        <h2>题目列表</h2>
        <span>{{ total }} 道</span>
      </div>
      <div class="data-table question-table">
        <div class="table-row table-head">
          <span>ID</span>
          <span>题干</span>
          <span>难度</span>
          <span>知识点</span>
          <span>标签</span>
          <span>状态</span>
          <span>来源文档</span>
          <span>来源任务</span>
          <span>创建时间</span>
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
          <span>#{{ question.sourceDocumentId }}</span>
          <span>#{{ question.sourceImportJobId }}</span>
          <span>{{ formatDate(question.createdAt) }}</span>
        </a>
        <p v-if="!loading && questions.length === 0" class="empty-state">暂无题目</p>
      </div>
    </section>
  </div>
</template>
