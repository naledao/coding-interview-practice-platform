<script setup>
import { onMounted, ref } from 'vue'
import { BookOpenCheck, Eye, RefreshCw, RotateCcw, Trash2 } from '@lucide/vue'
import {
  fetchWrongQuestions,
  masterWrongQuestion,
  removeWrongQuestion,
  unmasterWrongQuestion,
} from '../api'
import MarkdownContent from '../components/MarkdownContent.vue'
import { navigateTo } from '../navigation'

const records = ref([])
const total = ref(0)
const loading = ref(false)
const actingQuestionId = ref(null)
const errorMessage = ref('')
const noticeMessage = ref('')
const mastered = ref('false')

onMounted(loadRecords)

async function loadRecords() {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await fetchWrongQuestions({
      page: 1,
      pageSize: 20,
      mastered: mastered.value,
    })
    records.value = result.items
    total.value = result.total
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

async function toggleMastered(record) {
  actingQuestionId.value = record.questionId
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    const updated = record.mastered
      ? await unmasterWrongQuestion(record.questionId)
      : await masterWrongQuestion(record.questionId)
    records.value = records.value.map((item) => (item.questionId === record.questionId ? updated : item))
    noticeMessage.value = updated.mastered ? '已标记掌握' : '已取消掌握'
    if (mastered.value !== '') {
      await loadRecords()
    }
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    actingQuestionId.value = null
  }
}

async function removeRecord(record) {
  actingQuestionId.value = record.questionId
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    await removeWrongQuestion(record.questionId)
    records.value = records.value.filter((item) => item.questionId !== record.questionId)
    total.value = Math.max(0, total.value - 1)
    noticeMessage.value = '已从错题本移除'
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    actingQuestionId.value = null
  }
}

function startPractice() {
  navigateTo('user.html', { mode: 'WRONG' })
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
</script>

<template>
  <div class="page wide-page">
    <header class="page-header action-header">
      <div class="review-page-heading">
        <h1>错题本</h1>
        <span>复习</span>
      </div>
      <div class="header-actions">
        <select v-model="mastered" class="select-control" @change="loadRecords">
          <option value="false">未掌握</option>
          <option value="true">已掌握</option>
          <option value="">全部</option>
        </select>
        <button class="primary-button" type="button" @click="startPractice">
          <BookOpenCheck :size="17" />
          <span>开始练习</span>
        </button>
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
        <BookOpenCheck :size="20" />
        <h2>错题列表</h2>
        <span>{{ total }} 道</span>
      </div>

      <div class="review-list">
        <article v-for="record in records" :key="record.questionId" class="review-item">
          <div class="review-main">
            <div class="review-title-row">
              <strong>{{ record.difficulty }}</strong>
              <span>{{ record.mastered ? '已掌握' : '未掌握' }}</span>
            </div>
            <MarkdownContent class="review-stem" :source="record.stem" />
            <p>{{ tagText(record.tags) }}</p>
          </div>

          <dl class="review-metrics">
            <div>
              <dt>错误次数</dt>
              <dd>{{ record.wrongCount }}</dd>
            </div>
            <div>
              <dt>答对次数</dt>
              <dd>{{ record.correctAfterWrongCount }}</dd>
            </div>
            <div>
              <dt>最近答错</dt>
              <dd>{{ formatDate(record.lastWrongAt) }}</dd>
            </div>
          </dl>

          <div class="review-actions">
            <button class="icon-button" type="button" title="查看题目" @click="viewQuestion(record)">
              <Eye :size="17" />
            </button>
            <button
              class="icon-button"
              type="button"
              :title="record.mastered ? '取消掌握' : '标记掌握'"
              :disabled="actingQuestionId === record.questionId"
              @click="toggleMastered(record)"
            >
              <RotateCcw :size="17" />
            </button>
            <button
              class="icon-button danger-icon"
              type="button"
              title="移除错题"
              :disabled="actingQuestionId === record.questionId"
              @click="removeRecord(record)"
            >
              <Trash2 :size="17" />
            </button>
          </div>
        </article>

        <p v-if="loading" class="empty-state">错题加载中...</p>
        <p v-else-if="records.length === 0" class="empty-state">当前筛选下暂无错题</p>
      </div>
    </section>
  </div>
</template>
