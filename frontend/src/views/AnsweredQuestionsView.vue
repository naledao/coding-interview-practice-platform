<script setup>
import { computed, onMounted, ref } from 'vue'
import { CheckCircle2, Eye, History, RefreshCw, XCircle } from '@lucide/vue'
import { fetchAnsweredQuestions } from '../api'
import MarkdownContent from '../components/MarkdownContent.vue'
import { navigateTo } from '../navigation'

const records = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const errorMessage = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

onMounted(loadRecords)

async function loadRecords() {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await fetchAnsweredQuestions(page.value, pageSize.value)
    records.value = result.items
    total.value = result.total
    page.value = result.page
    pageSize.value = result.pageSize
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

async function previousPage() {
  if (page.value <= 1) {
    return
  }
  page.value -= 1
  await loadRecords()
}

async function nextPage() {
  if (page.value >= totalPages.value) {
    return
  }
  page.value += 1
  await loadRecords()
}

function viewQuestion(record) {
  navigateTo('user.html', { questionId: record.questionId })
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString() : '-'
}

function tagText(tags) {
  return (tags || []).map((tag) => tag.name).join(' / ') || '-'
}

function resultText(record) {
  if (record.lastCorrect === true) {
    return '最近答对'
  }
  if (record.lastCorrect === false) {
    return '最近答错'
  }
  return '最近答题'
}
</script>

<template>
  <div class="page wide-page">
    <header class="page-header action-header">
      <div class="review-page-heading">
        <h1>已做题目</h1>
        <span>答题记录</span>
      </div>
      <div class="header-actions">
        <button class="secondary-button" type="button" :disabled="loading" @click="loadRecords">
          <RefreshCw :size="17" />
          <span>刷新</span>
        </button>
      </div>
    </header>

    <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>

    <section class="table-panel">
      <div class="section-title">
        <History :size="20" />
        <h2>已答题目</h2>
        <span>{{ total }} 道</span>
      </div>

      <div class="review-list">
        <article v-for="record in records" :key="record.questionId" class="review-item">
          <div class="review-main">
            <div class="review-title-row">
              <strong>{{ record.difficulty }}</strong>
              <span>{{ resultText(record) }}</span>
              <span v-if="record.favorite">已收藏</span>
            </div>
            <MarkdownContent class="review-stem" :source="record.stem" />
            <p>{{ tagText(record.tags) }}</p>
          </div>

          <dl class="review-metrics answered-question-metrics">
            <div>
              <dt>答题次数</dt>
              <dd>{{ record.answerCount }}</dd>
            </div>
            <div>
              <dt>正确 / 错误</dt>
              <dd>{{ record.correctCount }} / {{ record.wrongCount }}</dd>
            </div>
            <div>
              <dt>最近选择</dt>
              <dd>
                <CheckCircle2 v-if="record.lastCorrect" :size="15" class="stat-good" />
                <XCircle v-else :size="15" class="stat-bad" />
                {{ record.lastSelectedOptionKey || '-' }}
              </dd>
            </div>
            <div>
              <dt>最近答题</dt>
              <dd>{{ formatDate(record.lastAnsweredAt) }}</dd>
            </div>
          </dl>

          <div class="review-actions">
            <button class="icon-button" type="button" title="查看题目" @click="viewQuestion(record)">
              <Eye :size="17" />
            </button>
          </div>
        </article>

        <p v-if="loading" class="empty-state">已做题目加载中...</p>
        <p v-else-if="records.length === 0" class="empty-state">暂无已做题目</p>
      </div>

      <div class="pagination-bar">
        <button class="secondary-button" type="button" :disabled="loading || page <= 1" @click="previousPage">上一页</button>
        <span>第 {{ page }} / {{ totalPages }} 页</span>
        <button class="secondary-button" type="button" :disabled="loading || page >= totalPages" @click="nextPage">下一页</button>
      </div>
    </section>
  </div>
</template>
