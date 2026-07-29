<script setup>
import { onMounted, ref } from 'vue'
import { BookMarked, Eye, RefreshCw, StarOff } from '@lucide/vue'
import { fetchFavorites, unfavoriteQuestion } from '../api'
import MarkdownContent from '../components/MarkdownContent.vue'
import { navigateTo } from '../navigation'

const favorites = ref([])
const total = ref(0)
const loading = ref(false)
const actingQuestionId = ref(null)
const errorMessage = ref('')
const noticeMessage = ref('')

onMounted(loadFavorites)

async function loadFavorites() {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await fetchFavorites(1, 20)
    favorites.value = result.items
    total.value = result.total
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

async function removeFavorite(item) {
  actingQuestionId.value = item.questionId
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    await unfavoriteQuestion(item.questionId)
    favorites.value = favorites.value.filter((favorite) => favorite.questionId !== item.questionId)
    total.value = Math.max(0, total.value - 1)
    noticeMessage.value = '已取消收藏'
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    actingQuestionId.value = null
  }
}

function startPractice() {
  navigateTo('user.html', { mode: 'FAVORITE' })
}

function viewQuestion(item) {
  navigateTo('user.html', { questionId: item.questionId })
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
        <h1>收藏题目</h1>
        <span>复习</span>
      </div>
      <div class="header-actions">
        <button class="primary-button" type="button" @click="startPractice">
          <BookMarked :size="17" />
          <span>开始练习</span>
        </button>
        <button class="secondary-button" type="button" :disabled="loading" @click="loadFavorites">
          <RefreshCw :size="17" />
          <span>刷新</span>
        </button>
      </div>
    </header>

    <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
    <p v-if="noticeMessage" class="notice-message">{{ noticeMessage }}</p>

    <section class="table-panel">
      <div class="section-title">
        <BookMarked :size="20" />
        <h2>收藏列表</h2>
        <span>{{ total }} 道</span>
      </div>

      <div class="review-list">
        <article v-for="item in favorites" :key="item.questionId" class="review-item">
          <div class="review-main">
            <div class="review-title-row">
              <strong>{{ item.difficulty }}</strong>
              <span>{{ formatDate(item.favoriteAt) }}</span>
            </div>
            <MarkdownContent class="review-stem" :source="item.stem" />
            <p>{{ tagText(item.tags) }}</p>
          </div>

          <div class="review-actions">
            <button class="icon-button" type="button" title="查看题目" @click="viewQuestion(item)">
              <Eye :size="17" />
            </button>
            <button
              class="icon-button danger-icon"
              type="button"
              title="取消收藏"
              :disabled="actingQuestionId === item.questionId"
              @click="removeFavorite(item)"
            >
              <StarOff :size="17" />
            </button>
          </div>
        </article>

        <p v-if="loading" class="empty-state">收藏加载中...</p>
        <p v-else-if="favorites.length === 0" class="empty-state">暂无收藏题目</p>
      </div>
    </section>
  </div>
</template>
