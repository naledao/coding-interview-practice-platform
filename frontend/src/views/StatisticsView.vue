<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  BarChart3,
  BookMarked,
  BookOpenCheck,
  CalendarDays,
  CheckCircle2,
  Flame,
  RefreshCw,
  Target,
  Tags,
  TrendingDown,
  XCircle,
} from '@lucide/vue'
import {
  fetchStatisticsDaily,
  fetchStatisticsOverview,
  fetchStatisticsTags,
} from '../api'
import { navigateTo } from '../navigation'

const overview = ref(null)
const tagStats = ref([])
const dailyStats = ref([])
const loading = ref(false)
const sortMode = ref('answered_desc')
const errorMessage = ref('')

const maxDailyAnswered = computed(() => (
  Math.max(1, ...dailyStats.value.map((item) => item.answeredTotal || 0))
))

const overviewCards = computed(() => {
  const data = overview.value || emptyOverview()
  return [
    {
      key: 'answeredTotal',
      label: '总答题数',
      value: `${data.answeredTotal} 道`,
      icon: BarChart3,
      page: 'answered-questions.html',
    },
    {
      key: 'accuracy',
      label: '总正确率',
      value: formatPercent(data.accuracy),
      icon: Target,
    },
    {
      key: 'todayAnswered',
      label: '今日答题数',
      value: `${data.todayAnswered} 道`,
      icon: CalendarDays,
    },
    {
      key: 'wrongBookCount',
      label: '错题数',
      value: `${data.wrongBookCount} 道`,
      icon: BookOpenCheck,
      page: 'wrong-book.html',
    },
    {
      key: 'favoriteCount',
      label: '收藏数',
      value: `${data.favoriteCount} 道`,
      icon: BookMarked,
      page: 'favorites.html',
    },
    {
      key: 'streakDays',
      label: '连续刷题',
      value: `${data.streakDays} 天`,
      icon: Flame,
    },
  ]
})

onMounted(loadStatistics)

async function loadStatistics() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [overviewResult, tagsResult, dailyResult] = await Promise.all([
      fetchStatisticsOverview(),
      fetchStatisticsTags(sortMode.value),
      fetchStatisticsDaily(7),
    ])
    overview.value = overviewResult
    tagStats.value = tagsResult
    dailyStats.value = dailyResult
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

async function changeSort(nextSort) {
  sortMode.value = nextSort
  errorMessage.value = ''
  try {
    tagStats.value = await fetchStatisticsTags(sortMode.value)
  } catch (error) {
    errorMessage.value = error.message
  }
}

function openCard(card) {
  if (card.page) {
    navigateTo(card.page)
  }
}

function startTagPractice(tag) {
  navigateTo('user.html', {
    mode: 'TAG',
    tagId: tag.tagId,
  })
}

function formatPercent(value) {
  return `${Math.round((value || 0) * 100)}%`
}

function formatDate(value) {
  if (!value) {
    return '-'
  }
  const [, month, day] = value.split('-')
  return `${month}-${day}`
}

function emptyOverview() {
  return {
    answeredTotal: 0,
    correctTotal: 0,
    wrongTotal: 0,
    accuracy: 0,
    todayAnswered: 0,
    wrongBookCount: 0,
    favoriteCount: 0,
    streakDays: 0,
  }
}
</script>

<template>
  <div class="page wide-page">
    <header class="page-header action-header">
      <div>
        <p>学习统计</p>
        <h1>进度与薄弱点</h1>
      </div>
      <button class="secondary-button" type="button" :disabled="loading" @click="loadStatistics">
        <RefreshCw :size="17" />
        <span>刷新</span>
      </button>
    </header>

    <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>

    <section class="stats-card-grid">
      <button
        v-for="card in overviewCards"
        :key="card.key"
        class="stat-card"
        :class="{ 'stat-card-clickable': card.page }"
        type="button"
        @click="openCard(card)"
      >
        <component :is="card.icon" :size="22" />
        <span>{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
      </button>
    </section>

    <section class="statistics-layout">
      <article class="tool-panel daily-panel">
        <div class="section-title">
          <CalendarDays :size="20" />
          <h2>最近 7 天</h2>
          <span>{{ loading ? '加载中' : '答题量' }}</span>
        </div>

        <div class="daily-bars" aria-label="最近 7 天答题数量">
          <div v-for="item in dailyStats" :key="item.date" class="daily-bar-item">
            <div class="daily-bar-track">
              <div
                class="daily-bar-fill"
                :style="{
                  height: item.answeredTotal === 0
                    ? '0%'
                    : `${Math.max(8, (item.answeredTotal / maxDailyAnswered) * 100)}%`,
                }"
              />
            </div>
            <strong>{{ item.answeredTotal }}</strong>
            <span>{{ formatDate(item.date) }}</span>
          </div>
          <p v-if="!loading && dailyStats.length === 0" class="empty-state">暂无最近答题记录</p>
        </div>
      </article>

      <article class="tool-panel summary-panel">
        <div class="section-title">
          <CheckCircle2 :size="20" />
          <h2>答题结果</h2>
          <span>{{ formatPercent(overview?.accuracy) }}</span>
        </div>

        <dl class="metric-grid">
          <div>
            <dt>正确数</dt>
            <dd>{{ overview?.correctTotal || 0 }}</dd>
          </div>
          <div>
            <dt>错误数</dt>
            <dd>{{ overview?.wrongTotal || 0 }}</dd>
          </div>
          <div>
            <dt>未掌握错题</dt>
            <dd>{{ overview?.wrongBookCount || 0 }}</dd>
          </div>
          <div>
            <dt>收藏题目</dt>
            <dd>{{ overview?.favoriteCount || 0 }}</dd>
          </div>
        </dl>
      </article>
    </section>

    <section class="table-panel">
      <div class="section-title">
        <Tags :size="20" />
        <h2>标签统计</h2>
        <span>{{ tagStats.length }} 个标签</span>
      </div>

      <div class="mode-tabs statistics-tabs" aria-label="标签统计排序">
        <button
          type="button"
          :class="{ 'mode-tab-active': sortMode === 'answered_desc' }"
          @click="changeSort('answered_desc')"
        >
          答题数
        </button>
        <button
          type="button"
          :class="{ 'mode-tab-active': sortMode === 'accuracy_asc' }"
          @click="changeSort('accuracy_asc')"
        >
          薄弱优先
        </button>
      </div>

      <div class="data-table tag-stat-table">
        <div class="table-row table-head">
          <span>标签</span>
          <span>已答</span>
          <span>正确</span>
          <span>错误</span>
          <span>正确率</span>
          <span>练习</span>
        </div>
        <div v-for="tag in tagStats" :key="tag.tagId" class="table-row">
          <strong>{{ tag.tagName }}</strong>
          <span>{{ tag.answeredTotal }}</span>
          <span class="stat-good">
            <CheckCircle2 :size="16" />
            {{ tag.correctTotal }}
          </span>
          <span class="stat-bad">
            <XCircle :size="16" />
            {{ tag.wrongTotal }}
          </span>
          <span class="accuracy-cell">
            <TrendingDown v-if="tag.accuracy < 0.6" :size="16" />
            {{ formatPercent(tag.accuracy) }}
          </span>
          <button class="icon-button" type="button" title="按标签练习" @click="startTagPractice(tag)">
            <Tags :size="17" />
          </button>
        </div>
      </div>

      <p v-if="loading" class="empty-state">统计加载中...</p>
      <p v-else-if="tagStats.length === 0" class="empty-state">暂无标签答题统计</p>
    </section>
  </div>
</template>
