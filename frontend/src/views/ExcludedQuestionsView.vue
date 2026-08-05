<script setup>
import { computed, onMounted, ref } from 'vue'
import { EyeOff, RefreshCw, RotateCcw } from '@lucide/vue'
import { fetchExcludedQuestions, restoreExcludedQuestion } from '../api'
import MarkdownContent from '../components/MarkdownContent.vue'

const records = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const actingQuestionId = ref(null)
const errorMessage = ref('')
const noticeMessage = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

onMounted(loadRecords)

async function loadRecords() {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await fetchExcludedQuestions(page.value, pageSize.value)
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

async function restoreQuestion(record) {
  actingQuestionId.value = record.questionId
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    await restoreExcludedQuestion(record.questionId)
    if (records.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    await loadRecords()
    noticeMessage.value = '已恢复显示，这道题会重新进入刷题范围'
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    actingQuestionId.value = null
  }
}

async function previousPage() {
  if (page.value <= 1) return
  page.value -= 1
  await loadRecords()
}

async function nextPage() {
  if (page.value >= totalPages.value) return
  page.value += 1
  await loadRecords()
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}

function tagText(tags) {
  return (tags || []).map((tag) => tag.name).join(' / ') || '-'
}
</script>

<template>
  <div class="page wide-page">
    <header class="page-header action-header">
      <div class="review-page-heading">
        <h1>不再出现</h1>
        <span>App 个人设置</span>
      </div>
      <div class="header-actions">
        <button class="secondary-button" type="button" :disabled="loading" @click="loadRecords">
          <RefreshCw :size="17" />
          <span>刷新</span>
        </button>
      </div>
    </header>

    <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
    <p v-if="noticeMessage" class="notice-message">{{ noticeMessage }}</p>

    <section class="table-panel">
      <div class="section-title">
        <EyeOff :size="20" />
        <h2>已屏蔽题目</h2>
        <span>{{ total }} 道</span>
      </div>

      <p class="excluded-question-hint">这些题目不会出现在你的随机、顺序、错题、收藏或标签刷题中。</p>

      <div class="review-list">
        <article v-for="record in records" :key="record.questionId" class="review-item excluded-question-item">
          <div class="review-main">
            <div class="review-title-row">
              <strong>{{ record.difficulty }}</strong>
              <span>{{ record.knowledgePoint || '未分类' }}</span>
              <span v-if="record.status !== 'ACTIVE'">题目已下线</span>
              <span>{{ formatDate(record.excludedAt) }}</span>
            </div>
            <MarkdownContent class="review-stem" :source="record.stem" />
            <p>{{ tagText(record.tags) }}</p>
          </div>

          <div class="review-actions">
            <button
              class="secondary-button excluded-restore-button"
              type="button"
              :disabled="actingQuestionId === record.questionId"
              @click="restoreQuestion(record)"
            >
              <RotateCcw :size="17" />
              <span>{{ actingQuestionId === record.questionId ? '恢复中' : '恢复出现' }}</span>
            </button>
          </div>
        </article>

        <p v-if="loading" class="empty-state">已屏蔽题目加载中...</p>
        <p v-else-if="records.length === 0" class="empty-state">暂无设为“不再出现”的题目</p>
      </div>

      <div class="pagination-bar">
        <button class="secondary-button" type="button" :disabled="loading || page <= 1" @click="previousPage">上一页</button>
        <span>第 {{ page }} / {{ totalPages }} 页</span>
        <button class="secondary-button" type="button" :disabled="loading || page >= totalPages" @click="nextPage">下一页</button>
      </div>
    </section>
  </div>
</template>
