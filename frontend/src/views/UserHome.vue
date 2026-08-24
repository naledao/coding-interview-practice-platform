<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  Bot,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Circle,
  EyeOff,
  Filter,
  GripHorizontal,
  MessageSquareQuote,
  PanelRightClose,
  Plus,
  RefreshCw,
  Search,
  Send,
  Settings,
  Star,
  StarOff,
  Trash2,
  X,
  XCircle,
} from '@lucide/vue'
import {
  answerQuestion,
  excludeQuestion,
  favoriteQuestion,
  fetchNextPracticeQuestion,
  fetchPracticeQuestionCount,
  fetchQuestion,
  fetchTags,
  unfavoriteQuestion,
} from '../api'
import {
  createAiTutorCompletion,
  getAiSettings,
  isAiSettingsComplete,
  loadAiConfiguration,
} from '../aiSettings'
import MarkdownContent from '../components/MarkdownContent.vue'
import { getQuery, isAndroidApp, navigateTo } from '../navigation'

defineProps({
  currentUser: {
    type: Object,
    default: null,
  },
})

const SPLIT_STORAGE_KEY = 'practice-ai-split-percent'
const AI_INPUT_HEIGHT_STORAGE_KEY = 'practice-ai-input-height'
const AI_QUICK_PHRASES_STORAGE_KEY = 'practice-ai-quick-phrases-v1'
const MAX_AI_QUICK_PHRASES = 30
const MAX_AI_QUICK_PHRASE_LENGTH = 500
const SPLITTER_WIDTH = 12
const MIN_PANE_WIDTH = 124
const MIN_AI_INPUT_HEIGHT = 82
const DEFAULT_AI_INPUT_HEIGHT = 112
const androidApp = isAndroidApp()

const tags = ref([])
const selectedQuestion = ref(null)
const selectedOptionKey = ref('')
const answerResult = ref(null)
const settingsOpen = ref(false)
const explanationOpen = ref(true)
const total = ref(0)
const loadingQuestions = ref(false)
const loadingDetail = ref(false)
const submitting = ref(false)
const favoriteSubmitting = ref(false)
const excludeSubmitting = ref(false)
const errorMessage = ref('')
const noticeMessage = ref('')
const keyword = ref('')
const difficulty = ref('')
const selectedTagIds = ref([])
const mode = ref('RANDOM')
const excludeAnswered = ref(false)
const answeredInSession = ref(0)
const correctInSession = ref(0)
const questionStartedAt = ref(null)
const now = ref(Date.now())
const aiPanelOpen = ref(false)
const aiMessages = ref([])
const aiInput = ref('')
const aiSending = ref(false)
const aiErrorMessage = ref('')
const initialAiSettings = getAiSettings()
const aiConfigured = ref(isAiSettingsComplete(initialAiSettings))
const aiModelName = ref(initialAiSettings.model)
const aiEffort = ref(initialAiSettings.effort)
const aiConversationId = ref(null)
const aiMessageList = ref(null)
const aiQuickPhrases = ref(androidApp ? loadAiQuickPhrases() : [])
const aiQuickPhraseDraft = ref('')
const aiQuickPhraseError = ref('')
const aiQuickPhraseInput = ref(null)
const aiQuickPhraseManagerOpen = ref(false)
const practicePage = ref(null)
const aiInputHeight = ref(loadAiInputHeight())
const aiInputResizing = ref(false)
const splitPercent = ref(loadSplitPercent())
const splitLimits = ref({ min: 24, max: 76 })
const splitResizing = ref(false)
let timerId = null
let aiAbortController = null
let aiScrollFrame = null
let aiInputResizePointerId = null
let aiInputResizeStartHeight = 0
let aiInputResizeStartY = 0
let splitPointerId = null
let nextAiMessageId = 1

const correctOptionKey = computed(() => answerResult.value?.correctOptionKey || '')
const currentQuestionNumber = computed(() => Math.max(1, answeredInSession.value + (answerResult.value ? 0 : 1)))
const progressLabel = computed(() => {
  if (!total.value) {
    return `${currentQuestionNumber.value}/-`
  }
  return `${Math.min(currentQuestionNumber.value, total.value)}/${total.value}`
})
const progressPercent = computed(() => {
  if (!total.value) {
    return 0
  }
  return Math.min(100, Math.max(4, (Math.min(currentQuestionNumber.value, total.value) / total.value) * 100))
})
const questionAnswered = computed(() => Boolean(answerResult.value || selectedQuestion.value?.answered))
const visibleQuestionTags = computed(() => selectedQuestion.value?.tags?.slice(0, 2) || [])
const hiddenTagCount = computed(() => Math.max(0, (selectedQuestion.value?.tags?.length || 0) - visibleQuestionTags.value.length))
const modeOptions = [
  { value: 'RANDOM', label: '随机' },
  { value: 'SEQUENTIAL', label: '顺序' },
  { value: 'WRONG', label: '错题' },
  { value: 'FAVORITE', label: '收藏' },
  { value: 'TAG', label: '标签' },
  { value: 'SEARCH', label: '搜索' },
]
const difficultyOptions = [
  { value: '', label: '全部' },
  { value: 'EASY', label: 'EASY' },
  { value: 'MEDIUM', label: 'MEDIUM' },
  { value: 'HARD', label: 'HARD' },
]
const sessionAccuracy = computed(() => {
  if (answeredInSession.value === 0) {
    return '0%'
  }
  return `${Math.round((correctInSession.value / answeredInSession.value) * 100)}%`
})
const elapsedSeconds = computed(() => {
  if (!questionStartedAt.value || answerResult.value) {
    return answerResult.value?.timeSpentSeconds || 0
  }
  return Math.max(0, Math.round((now.value - questionStartedAt.value) / 1000))
})
const splitStyle = computed(() => {
  const splitterShare = (splitPercent.value / 100) * SPLITTER_WIDTH
  return {
    '--practice-pane-width': `calc(${splitPercent.value}% - ${splitterShare}px)`,
    '--practice-splitter-width': `${SPLITTER_WIDTH}px`,
  }
})

onMounted(async () => {
  const query = getQuery()
  const queryMode = query.get('mode')
  const queryTagIds = normalizeTagIds([
    ...query.getAll('tagIds'),
    query.get('tagId'),
  ])
  const queryQuestionId = query.get('questionId')
  if (queryMode) {
    mode.value = queryMode
  }
  if (queryTagIds.length) {
    selectedTagIds.value = queryTagIds
  }
  timerId = window.setInterval(() => {
    now.value = Date.now()
  }, 1000)
  window.addEventListener('focus', refreshAiConfiguration)
  window.addEventListener('resize', clampSplitToViewport)
  clampSplitToViewport()
  refreshAiConfiguration()
  await Promise.all([loadTags(), loadQuestionSummary()])
  if (queryQuestionId) {
    await openQuestion({ id: queryQuestionId })
  } else {
    await loadNextQuestion()
  }
})

onBeforeUnmount(() => {
  if (timerId) {
    window.clearInterval(timerId)
  }
  window.removeEventListener('focus', refreshAiConfiguration)
  window.removeEventListener('resize', clampSplitToViewport)
  stopAiInputResize()
  stopSplitResize()
  aiAbortController?.abort()
  if (aiScrollFrame !== null) {
    window.cancelAnimationFrame(aiScrollFrame)
  }
})

watch(
  () => aiMessages.value.length,
  scheduleAiScroll,
)

async function loadQuestionSummary() {
  loadingQuestions.value = true
  try {
    const result = await fetchPracticeQuestionCount({
      mode: mode.value,
      difficulty: difficulty.value,
      tagIds: selectedTagIds.value,
      keyword: keyword.value,
      excludeAnswered: excludeAnswered.value,
    })
    total.value = result.total
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loadingQuestions.value = false
  }
}

async function loadTags() {
  try {
    tags.value = await fetchTags()
  } catch {
    tags.value = []
  }
}

async function applyFilters() {
  if (mode.value === 'TAG' && selectedTagIds.value.length === 0) {
    selectedQuestion.value = null
    noticeMessage.value = '请选择标签后开始标签刷题'
    return false
  }
  await Promise.all([loadQuestionSummary(), loadNextQuestion({ fromCurrent: false })])
  return true
}

function setMode(nextMode) {
  mode.value = nextMode
}

function normalizeTagIds(values) {
  return [...new Set(
    (values || [])
      .flatMap((value) => String(value || '').split(','))
      .map((value) => value.trim())
      .filter((value) => /^[1-9]\d*$/.test(value)),
  )]
}

function isTagSelected(tagId) {
  return selectedTagIds.value.includes(String(tagId))
}

function toggleTag(tagId) {
  const normalizedTagId = String(tagId)
  selectedTagIds.value = isTagSelected(normalizedTagId)
    ? selectedTagIds.value.filter((selectedTagId) => selectedTagId !== normalizedTagId)
    : [...selectedTagIds.value, normalizedTagId]
}

async function toggleFavorite() {
  if (!selectedQuestion.value) {
    return
  }
  favoriteSubmitting.value = true
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    if (selectedQuestion.value.favorite) {
      await unfavoriteQuestion(selectedQuestion.value.id)
      selectedQuestion.value = { ...selectedQuestion.value, favorite: false }
      noticeMessage.value = '已取消收藏'
    } else {
      await favoriteQuestion(selectedQuestion.value.id)
      selectedQuestion.value = { ...selectedQuestion.value, favorite: true }
      noticeMessage.value = '已收藏题目'
    }
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    favoriteSubmitting.value = false
  }
}

async function excludeCurrentQuestion() {
  if (!androidApp || !selectedQuestion.value || excludeSubmitting.value) {
    return
  }
  const confirmed = window.confirm('确定让这道题以后不再出现吗？\n\n可以在 App 的“不再出现”页面恢复。')
  if (!confirmed) {
    return
  }

  excludeSubmitting.value = true
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    await excludeQuestion(selectedQuestion.value.id)
    await loadNextQuestion()
    await loadQuestionSummary()
    noticeMessage.value = selectedQuestion.value
      ? '已设为不再出现，并切换到下一题'
      : '已设为不再出现，当前条件下暂无其他题目'
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    excludeSubmitting.value = false
  }
}

async function loadNextQuestion({ fromCurrent = true } = {}) {
  clearAiConversation()
  loadingDetail.value = true
  errorMessage.value = ''
  noticeMessage.value = ''
  selectedOptionKey.value = ''
  answerResult.value = null
  explanationOpen.value = true
  try {
    const question = await fetchNextPracticeQuestion({
      mode: mode.value,
      difficulty: difficulty.value,
      tagIds: selectedTagIds.value,
      keyword: keyword.value,
      excludeAnswered: excludeAnswered.value,
      currentQuestionId: fromCurrent ? selectedQuestion.value?.id : '',
    })
    selectedQuestion.value = question
    questionStartedAt.value = question ? Date.now() : null
    if (!question) {
      noticeMessage.value = '当前条件下暂无题目'
    }
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loadingDetail.value = false
  }
}

async function openQuestion(question) {
  clearAiConversation()
  loadingDetail.value = true
  selectedOptionKey.value = ''
  answerResult.value = null
  explanationOpen.value = true
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    selectedQuestion.value = await fetchQuestion(question.id)
    questionStartedAt.value = Date.now()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loadingDetail.value = false
  }
}

async function submitAnswer() {
  if (!selectedQuestion.value || !selectedOptionKey.value) {
    errorMessage.value = '请选择一个答案'
    return
  }

  const timeSpentSeconds = Math.max(0, Math.round((Date.now() - questionStartedAt.value) / 1000))
  submitting.value = true
  errorMessage.value = ''
  try {
    const result = await answerQuestion(selectedQuestion.value.id, {
      selectedOptionKey: selectedOptionKey.value,
      mode: mode.value,
      timeSpentSeconds,
    })
    answerResult.value = {
      ...result,
      timeSpentSeconds,
    }
    selectedQuestion.value = {
      ...selectedQuestion.value,
      answered: true,
    }
    explanationOpen.value = true
    answeredInSession.value += 1
    if (result.correct) {
      correctInSession.value += 1
    }
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    submitting.value = false
  }
}

function formatTimer(seconds) {
  const safeSeconds = Math.max(0, Number(seconds) || 0)
  const minutes = Math.floor(safeSeconds / 60)
  const remainingSeconds = safeSeconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(remainingSeconds).padStart(2, '0')}`
}

function difficultyLabel(value) {
  return value || '全部'
}

function difficultyClass(value) {
  return `difficulty-${String(value || 'all').toLowerCase()}`
}

async function startPracticeFromSettings() {
  const applied = await applyFilters()
  if (applied) {
    settingsOpen.value = false
  }
}

async function resetSettings() {
  mode.value = 'RANDOM'
  difficulty.value = ''
  selectedTagIds.value = []
  keyword.value = ''
  excludeAnswered.value = false
  await applyFilters()
}

function optionClass(option) {
  if (!answerResult.value) {
    return {
      'option-selected': selectedOptionKey.value === option.optionKey,
    }
  }

  return {
    'option-correct': correctOptionKey.value === option.optionKey,
    'option-wrong': selectedOptionKey.value === option.optionKey && !answerResult.value.correct,
  }
}

async function refreshAiConfiguration() {
  try {
    const { settings } = await loadAiConfiguration({ persist: true })
    aiConfigured.value = true
    aiModelName.value = settings.model
    aiEffort.value = settings.effort
    return true
  } catch {
    aiConfigured.value = false
    return false
  }
}

async function openAiPanel() {
  aiPanelOpen.value = true
  nextTick(clampSplitToViewport)
  const configured = await refreshAiConfiguration()
  if (!configured) {
    aiErrorMessage.value = 'AI 助教模型目录暂时不可用'
  }
}

function closeAiPanel() {
  stopSplitResize()
  aiPanelOpen.value = false
}

function loadSplitPercent() {
  try {
    const value = Number(window.localStorage.getItem(SPLIT_STORAGE_KEY))
    return Number.isFinite(value) ? value : 50
  } catch {
    return 50
  }
}

function getSplitMetrics() {
  const rect = practicePage.value?.getBoundingClientRect()
  const width = rect?.width || window.innerWidth || 0
  const usableWidth = Math.max(1, width - SPLITTER_WIDTH)
  const minimumPercent = Math.min(49, Math.max(24, (MIN_PANE_WIDTH / usableWidth) * 100))
  const limits = { min: minimumPercent, max: 100 - minimumPercent }
  splitLimits.value = limits
  return { rect, width, usableWidth, limits }
}

function setSplitPercent(value, persist = false) {
  const { limits } = getSplitMetrics()
  splitPercent.value = Math.min(limits.max, Math.max(limits.min, Number(value) || 50))
  if (persist) {
    try {
      window.localStorage.setItem(SPLIT_STORAGE_KEY, String(splitPercent.value))
    } catch {
      // localStorage may be unavailable in a restricted WebView.
    }
  }
}

function clampSplitToViewport() {
  setSplitPercent(splitPercent.value)
  clampAiInputHeightToViewport()
}

function updateSplitFromPointer(event) {
  if (event.pointerId !== splitPointerId) {
    return
  }
  const { rect, usableWidth } = getSplitMetrics()
  if (!rect) {
    return
  }
  const pointerOffset = event.clientX - rect.left - SPLITTER_WIDTH / 2
  setSplitPercent((pointerOffset / usableWidth) * 100)
}

function startSplitResize(event) {
  if (event.pointerType === 'mouse' && event.button !== 0) {
    return
  }
  splitPointerId = event.pointerId
  splitResizing.value = true
  event.currentTarget.setPointerCapture?.(event.pointerId)
  document.documentElement.classList.add('practice-split-resizing')
  updateSplitFromPointer(event)
  event.preventDefault()
}

function finishSplitResize(event) {
  if (event && event.pointerId !== splitPointerId) {
    return
  }
  if (event && event.currentTarget.hasPointerCapture?.(event.pointerId)) {
    event.currentTarget.releasePointerCapture(event.pointerId)
  }
  const wasResizing = splitResizing.value
  stopSplitResize()
  if (wasResizing) {
    setSplitPercent(splitPercent.value, true)
  }
}

function stopSplitResize() {
  splitPointerId = null
  splitResizing.value = false
  document.documentElement.classList.remove('practice-split-resizing')
}

function handleSplitKeydown(event) {
  const step = event.shiftKey ? 5 : 2
  let nextPercent = splitPercent.value
  if (event.key === 'ArrowLeft') {
    nextPercent -= step
  } else if (event.key === 'ArrowRight') {
    nextPercent += step
  } else if (event.key === 'Home') {
    nextPercent = splitLimits.value.min
  } else if (event.key === 'End') {
    nextPercent = splitLimits.value.max
  } else {
    return
  }
  event.preventDefault()
  setSplitPercent(nextPercent, true)
}

function resetSplit() {
  setSplitPercent(50, true)
}

function loadAiInputHeight() {
  try {
    const value = Number(window.localStorage.getItem(AI_INPUT_HEIGHT_STORAGE_KEY))
    return Number.isFinite(value) ? value : DEFAULT_AI_INPUT_HEIGHT
  } catch {
    return DEFAULT_AI_INPUT_HEIGHT
  }
}

function maxAiInputHeight() {
  return Math.max(
    MIN_AI_INPUT_HEIGHT,
    Math.min(360, Math.floor((window.innerHeight || 800) * 0.45)),
  )
}

function setAiInputHeight(value, persist = false) {
  aiInputHeight.value = Math.min(
    maxAiInputHeight(),
    Math.max(MIN_AI_INPUT_HEIGHT, Number(value) || DEFAULT_AI_INPUT_HEIGHT),
  )
  if (persist) {
    try {
      window.localStorage.setItem(AI_INPUT_HEIGHT_STORAGE_KEY, String(aiInputHeight.value))
    } catch {
      // localStorage may be unavailable in a restricted WebView.
    }
  }
}

function clampAiInputHeightToViewport() {
  setAiInputHeight(aiInputHeight.value)
}

function updateAiInputHeightFromPointer(event) {
  if (event.pointerId !== aiInputResizePointerId) {
    return
  }
  setAiInputHeight(aiInputResizeStartHeight + event.clientY - aiInputResizeStartY)
}

function startAiInputResize(event) {
  if (event.pointerType === 'mouse' && event.button !== 0) {
    return
  }
  aiInputResizePointerId = event.pointerId
  aiInputResizeStartHeight = aiInputHeight.value
  aiInputResizeStartY = event.clientY
  aiInputResizing.value = true
  event.currentTarget.setPointerCapture?.(event.pointerId)
  document.documentElement.classList.add('ai-input-resizing')
  event.preventDefault()
}

function finishAiInputResize(event) {
  if (event && event.pointerId !== aiInputResizePointerId) {
    return
  }
  if (event && event.currentTarget.hasPointerCapture?.(event.pointerId)) {
    event.currentTarget.releasePointerCapture(event.pointerId)
  }
  const wasResizing = aiInputResizing.value
  stopAiInputResize()
  if (wasResizing) {
    setAiInputHeight(aiInputHeight.value, true)
  }
}

function stopAiInputResize() {
  aiInputResizePointerId = null
  aiInputResizing.value = false
  document.documentElement.classList.remove('ai-input-resizing')
}

function handleAiInputResizeKeydown(event) {
  const step = event.shiftKey ? 32 : 16
  let nextHeight = aiInputHeight.value
  if (event.key === 'ArrowUp') {
    nextHeight -= step
  } else if (event.key === 'ArrowDown') {
    nextHeight += step
  } else if (event.key === 'Home') {
    nextHeight = MIN_AI_INPUT_HEIGHT
  } else if (event.key === 'End') {
    nextHeight = maxAiInputHeight()
  } else {
    return
  }
  event.preventDefault()
  setAiInputHeight(nextHeight, true)
}

function resetAiInputHeight() {
  setAiInputHeight(DEFAULT_AI_INPUT_HEIGHT, true)
}

function normalizeAiQuickPhrase(value) {
  return typeof value === 'string' ? value.trim().slice(0, MAX_AI_QUICK_PHRASE_LENGTH) : ''
}

function loadAiQuickPhrases() {
  try {
    const saved = JSON.parse(window.localStorage.getItem(AI_QUICK_PHRASES_STORAGE_KEY) || '[]')
    if (!Array.isArray(saved)) {
      return []
    }
    return [...new Set(saved.map(normalizeAiQuickPhrase).filter(Boolean))]
      .slice(0, MAX_AI_QUICK_PHRASES)
  } catch {
    return []
  }
}

function persistAiQuickPhrases(phrases) {
  try {
    window.localStorage.setItem(AI_QUICK_PHRASES_STORAGE_KEY, JSON.stringify(phrases))
  } catch {
    throw new Error('常用语保存失败，请检查应用存储空间')
  }
}

async function openAiQuickPhraseManager() {
  aiQuickPhraseError.value = ''
  aiQuickPhraseManagerOpen.value = true
  await nextTick()
  if (!aiQuickPhrases.value.length) {
    aiQuickPhraseInput.value?.focus()
  }
}

function closeAiQuickPhraseManager() {
  aiQuickPhraseManagerOpen.value = false
  aiQuickPhraseDraft.value = ''
  aiQuickPhraseError.value = ''
}

async function saveAiQuickPhrase() {
  const phrase = normalizeAiQuickPhrase(aiQuickPhraseDraft.value)
  aiQuickPhraseError.value = ''
  if (!phrase) {
    aiQuickPhraseError.value = '请输入要保存的常用语'
    return
  }
  if (aiQuickPhrases.value.includes(phrase)) {
    aiQuickPhraseError.value = '这条常用语已经保存过了'
    return
  }
  if (aiQuickPhrases.value.length >= MAX_AI_QUICK_PHRASES) {
    aiQuickPhraseError.value = `最多保存 ${MAX_AI_QUICK_PHRASES} 条常用语，请先删除不需要的内容`
    return
  }

  const nextPhrases = [phrase, ...aiQuickPhrases.value]
  try {
    persistAiQuickPhrases(nextPhrases)
    aiQuickPhrases.value = nextPhrases
    aiQuickPhraseDraft.value = ''
    await nextTick()
    aiQuickPhraseInput.value?.focus()
  } catch (error) {
    aiQuickPhraseError.value = error.message
  }
}

function deleteAiQuickPhrase(phrase) {
  const singleLinePhrase = phrase.replace(/\s+/g, ' ')
  const preview = singleLinePhrase.slice(0, 36)
  if (!window.confirm(`确定删除常用语“${preview}${singleLinePhrase.length > preview.length ? '…' : ''}”吗？`)) {
    return
  }

  const nextPhrases = aiQuickPhrases.value.filter((item) => item !== phrase)
  aiQuickPhraseError.value = ''
  try {
    persistAiQuickPhrases(nextPhrases)
    aiQuickPhrases.value = nextPhrases
  } catch (error) {
    aiQuickPhraseError.value = error.message
  }
}

async function sendAiQuickPhrase(phrase) {
  if (aiSending.value) {
    return
  }
  aiInput.value = phrase
  closeAiQuickPhraseManager()
  await sendAiMessage()
}

function openAiSettings() {
  navigateTo('ai-settings.html')
}

function clearAiConversation() {
  aiAbortController?.abort()
  aiAbortController = null
  aiConversationId.value = null
  aiMessages.value = []
  aiInput.value = ''
  aiSending.value = false
  aiErrorMessage.value = ''
}

function scheduleAiScroll() {
  if (aiScrollFrame !== null) {
    return
  }
  aiScrollFrame = window.requestAnimationFrame(() => {
    aiScrollFrame = null
    if (aiMessageList.value) {
      aiMessageList.value.scrollTop = aiMessageList.value.scrollHeight
    }
  })
}

function currentQuestionPrompt() {
  if (!selectedQuestion.value) {
    return ''
  }
  return '请结合当前题目回答：\n\n'
}

async function insertCurrentQuestion() {
  if (!selectedQuestion.value) {
    return
  }
  aiInput.value = currentQuestionPrompt()
  await nextTick()
  document.querySelector('.ai-chat-input')?.focus()
}

async function sendAiMessage() {
  if (aiSending.value) {
    return
  }

  if (!(await refreshAiConfiguration())) {
    aiErrorMessage.value = 'AI 助教模型目录暂时不可用'
    return
  }

  if (!selectedQuestion.value?.id) {
    aiErrorMessage.value = '请先选择一道题目'
    return
  }

  const content = aiInput.value.trim()
  if (!content) {
    aiErrorMessage.value = '请输入要询问的问题'
    return
  }

  const userMessage = {
    id: nextAiMessageId++,
    role: 'user',
    content,
  }
  aiMessages.value.push(userMessage)
  const assistantMessage = {
    id: nextAiMessageId++,
    role: 'assistant',
    content: '',
  }
  aiMessages.value.push(assistantMessage)
  const reactiveAssistantMessage = aiMessages.value[aiMessages.value.length - 1]
  aiInput.value = ''
  aiErrorMessage.value = ''
  aiSending.value = true

  const controller = new AbortController()
  aiAbortController = controller
  try {
    const answer = await createAiTutorCompletion({
      questionId: selectedQuestion.value.id,
      input: content,
      conversationId: aiConversationId.value,
      model: aiModelName.value,
      effort: aiEffort.value,
      signal: controller.signal,
      onAccepted: (accepted) => {
        if (aiAbortController === controller) {
          aiConversationId.value = accepted.conversationId
        }
      },
      onDelta: (_delta, fullContent) => {
        if (aiAbortController !== controller) {
          return
        }
        reactiveAssistantMessage.content = fullContent
        scheduleAiScroll()
      },
    })
    if (aiAbortController !== controller) {
      return
    }
    reactiveAssistantMessage.content = answer
  } catch (error) {
    if (error?.name !== 'AbortError' && aiAbortController === controller) {
      aiMessages.value = aiMessages.value.filter(
        (message) => message.id !== userMessage.id && message.id !== assistantMessage.id,
      )
      aiInput.value = content
      aiErrorMessage.value = error.message
    }
  } finally {
    if (aiAbortController === controller) {
      aiAbortController = null
      aiSending.value = false
    }
  }
}

function stopAiGeneration() {
  aiAbortController?.abort()
}
</script>

<template>
  <div
    ref="practicePage"
    class="page practice-focus-page"
    :class="{
      'ai-panel-open': aiPanelOpen,
      'practice-android-app': androidApp,
      'split-resizing': splitResizing,
    }"
    :style="splitStyle"
  >
    <section class="practice-focus-shell">
      <div class="practice-scroll-content">
        <header class="practice-topbar">
          <span aria-hidden="true"></span>
          <strong>刷题</strong>
          <span class="practice-timer">{{ formatTimer(elapsedSeconds) }}</span>
        </header>

        <div class="practice-progress-row" aria-label="练习进度">
          <div class="practice-progress-track">
            <span :style="{ width: `${progressPercent}%` }"></span>
          </div>
          <strong>{{ progressLabel }}</strong>
        </div>

        <p v-if="errorMessage" class="error-message focus-message">{{ errorMessage }}</p>
        <p v-else-if="noticeMessage" class="notice-message focus-message">{{ noticeMessage }}</p>

        <div v-if="loadingDetail" class="focus-empty-state">题目加载中...</div>
        <template v-else-if="selectedQuestion">
          <div class="practice-chip-row">
            <span class="practice-chip" :class="difficultyClass(selectedQuestion.difficulty)">
              {{ selectedQuestion.difficulty }}
            </span>
            <span v-for="tag in visibleQuestionTags" :key="tag.id" class="practice-chip chip-tag">{{ tag.name }}</span>
            <span v-if="hiddenTagCount" class="practice-chip chip-muted">+{{ hiddenTagCount }}</span>
            <span class="practice-chip chip-muted">{{ questionAnswered ? '已做' : '未做' }}</span>
            <button
              v-if="androidApp"
              class="practice-exclude-button"
              type="button"
              :disabled="excludeSubmitting"
              aria-label="让这道题不再出现"
              @click="excludeCurrentQuestion"
            >
              <EyeOff :size="17" />
              <span>{{ excludeSubmitting ? '处理中' : '不再出现' }}</span>
            </button>
          </div>

          <article class="focus-question-card">
            <div class="question-stem focus-question-stem markdown-stem-row">
              <span class="question-stem-number">{{ currentQuestionNumber }}.</span>
              <MarkdownContent class="question-stem-markdown" :source="selectedQuestion.stem" />
            </div>

            <div class="option-list focus-option-list">
              <label
                v-for="option in selectedQuestion.options"
                :key="option.id"
                class="option-row practice-option"
                :class="optionClass(option)"
              >
                <XCircle
                  v-if="answerResult && selectedOptionKey === option.optionKey && !answerResult.correct"
                  :size="20"
                />
                <CheckCircle2 v-else-if="answerResult && correctOptionKey === option.optionKey" :size="20" />
                <Circle v-else :size="20" />
                <strong>{{ option.optionKey }}</strong>
                <MarkdownContent class="option-markdown" :source="option.content" />
                <input
                  v-model="selectedOptionKey"
                  class="visually-hidden"
                  type="radio"
                  name="selectedOption"
                  :value="option.optionKey"
                  :disabled="Boolean(answerResult)"
                />
              </label>
            </div>
          </article>

          <section
            v-if="answerResult"
            class="answer-panel focus-answer-panel"
            :class="{ 'answer-panel-wrong': !answerResult.correct }"
          >
            <button class="answer-toggle" type="button" @click="explanationOpen = !explanationOpen">
              <span>{{ answerResult.correct ? '回答正确' : '回答错误' }}</span>
              <ChevronUp v-if="explanationOpen" :size="18" />
              <ChevronDown v-else :size="18" />
            </button>
            <template v-if="explanationOpen">
              <p>你的选择：{{ answerResult.selectedOptionKey }}，正确答案：{{ answerResult.correctOptionKey }}</p>
              <MarkdownContent class="analysis-text" :source="answerResult.answerAnalysis" />
            </template>
          </section>
        </template>
        <div v-else class="focus-empty-state">
          <p>当前没有可展示的题目</p>
          <button class="secondary-button" type="button" @click="settingsOpen = true">调整练习设置</button>
        </div>
      </div>

      <div class="practice-bottom-bar">
        <button
          class="bottom-tool-button"
          type="button"
          :disabled="favoriteSubmitting || !selectedQuestion"
          @click="toggleFavorite"
        >
          <StarOff v-if="selectedQuestion?.favorite" :size="22" />
          <Star v-else :size="22" />
          <span>收藏</span>
        </button>

        <div class="practice-main-stack">
          <div class="practice-main-actions" :class="{ 'practice-main-actions-single': answerResult }">
            <button
              class="primary-button practice-main-action"
              type="button"
              :disabled="submitting || loadingDetail || !selectedQuestion || (!answerResult && !selectedOptionKey)"
              @click="answerResult ? loadNextQuestion() : submitAnswer()"
            >
              {{ answerResult ? '下一题' : '提交答案' }}
            </button>
            <button
              v-if="!answerResult"
              class="secondary-button practice-skip-action"
              type="button"
              :disabled="submitting || loadingDetail || !selectedQuestion"
              @click="loadNextQuestion()"
            >
              下一题
            </button>
          </div>
          <span v-if="answerResult" class="practice-session-line">
            本轮 {{ answeredInSession }} 题 · 正确率 {{ sessionAccuracy }}
          </span>
        </div>

        <button class="bottom-tool-button" type="button" @click="settingsOpen = true">
          <Filter :size="22" />
          <span>筛选</span>
        </button>
      </div>
    </section>

    <button
      v-if="!aiPanelOpen"
      class="ai-panel-launcher"
      type="button"
      aria-label="打开 AI 问答"
      @click="openAiPanel"
    >
      <Bot :size="23" />
      <span>AI 问答</span>
    </button>

    <button
      v-if="aiPanelOpen"
      class="practice-splitter"
      type="button"
      role="separator"
      aria-label="调整题目和 AI 助教区域宽度"
      aria-orientation="vertical"
      :aria-valuemin="Math.round(splitLimits.min)"
      :aria-valuemax="Math.round(splitLimits.max)"
      :aria-valuenow="Math.round(splitPercent)"
      title="左右拖动调整宽度，双击恢复均分"
      @dblclick="resetSplit"
      @keydown="handleSplitKeydown"
      @pointerdown="startSplitResize"
      @pointermove="updateSplitFromPointer"
      @pointerup="finishSplitResize"
      @pointercancel="finishSplitResize"
    >
      <span aria-hidden="true"></span>
    </button>

    <aside v-if="aiPanelOpen" class="ai-chat-panel" aria-label="AI 问答">
      <header class="ai-chat-header">
        <div class="ai-chat-title">
          <Bot :size="22" />
          <div>
            <strong>AI 助教</strong>
            <span>{{ aiModelName || '尚未配置模型' }}</span>
          </div>
        </div>
        <div class="ai-chat-header-actions">
          <button class="icon-button" type="button" aria-label="大模型设置" @click="openAiSettings">
            <Settings :size="19" />
          </button>
          <button class="icon-button" type="button" aria-label="关闭 AI 问答" @click="closeAiPanel">
            <PanelRightClose :size="19" />
          </button>
        </div>
      </header>

      <div ref="aiMessageList" class="ai-message-list" aria-live="polite">
        <div v-if="!aiConfigured" class="ai-config-empty">
          <Settings :size="26" />
          <strong>AI 助教暂时不可用</strong>
          <p>请检查平台后端与模型服务，或前往设置页重新加载模型目录。</p>
          <button class="secondary-button" type="button" @click="openAiSettings">前往设置</button>
        </div>

        <div v-else-if="!aiMessages.length" class="ai-chat-empty">
          <MessageSquareQuote :size="30" />
          <strong>针对当前题目继续追问</strong>
          <p>可以先引用题目，再补充“为什么”“有什么区别”或“给我一个代码示例”。</p>
        </div>

        <article
          v-for="message in aiMessages"
          :key="message.id"
          class="ai-message"
          :class="`ai-message-${message.role}`"
        >
          <span>{{ message.role === 'assistant' ? 'AI' : '我' }}</span>
          <MarkdownContent :source="message.content" />
        </article>

        <div v-if="aiSending" class="ai-thinking">
          <RefreshCw :size="17" />
          <span>{{ aiMessages[aiMessages.length - 1]?.content ? 'AI 正在流式输出...' : 'AI 正在思考...' }}</span>
        </div>
      </div>

      <footer class="ai-chat-composer">
        <p v-if="aiErrorMessage" class="error-message ai-chat-error">{{ aiErrorMessage }}</p>
        <div class="ai-composer-shortcuts">
          <button
            class="ai-quote-button"
            type="button"
            :disabled="!selectedQuestion || aiSending"
            @click="insertCurrentQuestion"
          >
            <MessageSquareQuote :size="17" />
            <span>引用当前题目</span>
          </button>
          <button
            v-if="androidApp"
            class="ai-quick-phrase-button"
            type="button"
            aria-haspopup="dialog"
            :disabled="aiSending"
            @click="openAiQuickPhraseManager"
          >
            <Star :size="17" />
            <span>常用语</span>
            <strong v-if="aiQuickPhrases.length">{{ aiQuickPhrases.length }}</strong>
          </button>
        </div>
        <div
          class="ai-chat-input-shell"
          :style="androidApp ? { height: `${aiInputHeight}px` } : undefined"
        >
          <textarea
            v-model="aiInput"
            class="ai-chat-input"
            rows="4"
            placeholder="输入你想追问的内容..."
            :disabled="aiSending"
            @keydown.enter.exact.prevent="sendAiMessage"
          ></textarea>
          <button
            v-if="androidApp"
            class="ai-input-resize-handle"
            type="button"
            aria-label="调整输入框高度"
            title="上下拖动调整输入框高度，双击恢复默认高度"
            @dblclick="resetAiInputHeight"
            @keydown="handleAiInputResizeKeydown"
            @pointerdown="startAiInputResize"
            @pointermove="updateAiInputHeightFromPointer"
            @pointerup="finishAiInputResize"
            @pointercancel="finishAiInputResize"
          >
            <GripHorizontal :size="20" />
          </button>
        </div>
        <button
          class="primary-button ai-send-button"
          type="button"
          :disabled="!aiSending && !aiInput.trim()"
          @click="aiSending ? stopAiGeneration() : sendAiMessage()"
        >
          <XCircle v-if="aiSending" :size="18" />
          <Send v-else :size="18" />
          <span>{{ aiSending ? '停止生成' : '发送' }}</span>
        </button>
      </footer>
    </aside>

    <div
      v-if="androidApp && aiQuickPhraseManagerOpen"
      class="ai-quick-phrase-overlay"
      @click.self="closeAiQuickPhraseManager"
      @keydown.esc="closeAiQuickPhraseManager"
    >
      <section
        class="ai-quick-phrase-sheet"
        role="dialog"
        aria-modal="true"
        aria-labelledby="ai-quick-phrase-title"
      >
        <span class="sheet-handle"></span>
        <header class="ai-quick-phrase-header">
          <div>
            <h2 id="ai-quick-phrase-title">常用语</h2>
            <p>保存在当前设备，点击已有语句即可发送。</p>
          </div>
          <button class="icon-button" type="button" aria-label="关闭常用语" @click="closeAiQuickPhraseManager">
            <X :size="20" />
          </button>
        </header>

        <form class="ai-quick-phrase-form" @submit.prevent="saveAiQuickPhrase">
          <label for="ai-quick-phrase-input">新增常用语</label>
          <textarea
            id="ai-quick-phrase-input"
            ref="aiQuickPhraseInput"
            v-model="aiQuickPhraseDraft"
            rows="3"
            :maxlength="MAX_AI_QUICK_PHRASE_LENGTH"
            placeholder="例如：请逐项解释为什么其他选项不正确"
          ></textarea>
          <div class="ai-quick-phrase-form-footer">
            <span>{{ aiQuickPhraseDraft.length }}/{{ MAX_AI_QUICK_PHRASE_LENGTH }} · 最多 {{ MAX_AI_QUICK_PHRASES }} 条</span>
            <button
              class="primary-button"
              type="submit"
              :disabled="!aiQuickPhraseDraft.trim() || aiQuickPhrases.length >= MAX_AI_QUICK_PHRASES"
            >
              <Plus :size="17" />
              <span>保存</span>
            </button>
          </div>
          <p v-if="aiQuickPhraseError" class="error-message ai-quick-phrase-error">{{ aiQuickPhraseError }}</p>
        </form>

        <div class="ai-quick-phrase-list-heading">
          <strong>已保存</strong>
          <span>{{ aiQuickPhrases.length }}/{{ MAX_AI_QUICK_PHRASES }}</span>
        </div>
        <p v-if="!aiQuickPhrases.length" class="ai-quick-phrase-empty">
          暂无常用语，先保存一条经常向 AI 助教提问的内容吧。
        </p>
        <ul v-else class="ai-quick-phrase-list">
          <li v-for="phrase in aiQuickPhrases" :key="phrase">
            <button
              class="ai-quick-phrase-send"
              type="button"
              :disabled="aiSending"
              @click="sendAiQuickPhrase(phrase)"
            >
              <span>{{ phrase }}</span>
              <small>点击发送</small>
            </button>
            <button
              class="ai-quick-phrase-delete"
              type="button"
              :aria-label="`删除常用语：${phrase}`"
              @click="deleteAiQuickPhrase(phrase)"
            >
              <Trash2 :size="18" />
            </button>
          </li>
        </ul>
      </section>
    </div>

    <div v-if="settingsOpen" class="settings-overlay" @click.self="settingsOpen = false">
      <section class="practice-settings-sheet" aria-label="练习设置">
        <span class="sheet-handle"></span>
        <h2>练习设置</h2>

        <div class="settings-block">
          <span>模式</span>
          <div class="mode-tabs settings-mode-tabs" aria-label="刷题模式">
            <button
              v-for="option in modeOptions"
              :key="option.value"
              type="button"
              :class="{ 'mode-tab-active': mode === option.value }"
              @click="setMode(option.value)"
            >
              {{ option.label }}
            </button>
          </div>
        </div>

        <div class="settings-block">
          <span>难度</span>
          <div class="settings-chip-grid">
            <button
              v-for="option in difficultyOptions"
              :key="option.value || 'all'"
              class="settings-chip"
              :class="{ 'settings-chip-active': difficulty === option.value }"
              type="button"
              @click="difficulty = option.value"
            >
              {{ difficultyLabel(option.value) }}
            </button>
          </div>
        </div>

        <label class="search-control settings-search">
          <Search :size="17" />
          <input v-model="keyword" type="search" placeholder="搜索题干或知识点" />
        </label>

        <div class="settings-block">
          <span>标签{{ selectedTagIds.length ? `（已选 ${selectedTagIds.length}）` : '' }}</span>
          <div class="settings-tag-list">
            <button
              class="settings-tag"
              :class="{ 'settings-chip-active': selectedTagIds.length === 0 }"
              type="button"
              :aria-pressed="selectedTagIds.length === 0"
              @click="selectedTagIds = []"
            >
              全部
            </button>
            <button
              v-for="tag in tags"
              :key="tag.id"
              class="settings-tag"
              :class="{ 'settings-chip-active': isTagSelected(tag.id) }"
              type="button"
              :aria-pressed="isTagSelected(tag.id)"
              @click="toggleTag(tag.id)"
            >
              {{ tag.name }}
            </button>
          </div>
        </div>

        <label class="settings-switch-row">
          <span>跳过已做</span>
          <input v-model="excludeAnswered" type="checkbox" />
        </label>

        <div class="settings-actions">
          <button class="secondary-button" type="button" :disabled="loadingQuestions || loadingDetail" @click="resetSettings">
            <RefreshCw :size="17" />
            <span>重置</span>
          </button>
          <button class="primary-button" type="button" :disabled="loadingQuestions || loadingDetail" @click="startPracticeFromSettings">
            开始练习
          </button>
        </div>
      </section>
    </div>
  </div>
</template>
