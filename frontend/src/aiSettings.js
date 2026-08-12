import {
  cancelAiTutorRun,
  createAiTutorRun,
  fetchAiTutorModels,
  streamAiTutorRun,
} from './api'

const LEGACY_AI_SETTINGS_KEY = 'interview_ai_settings'
const AI_SETTINGS_KEY = 'interview_ai_tutor_preferences'

try {
  localStorage.removeItem(LEGACY_AI_SETTINGS_KEY)
} catch {
  // localStorage may be unavailable in a restricted WebView.
}

function cleanText(value) {
  return typeof value === 'string' ? value.trim() : ''
}

function createUuid() {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID()
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (character) => {
    const random = Math.floor(Math.random() * 16)
    const value = character === 'x' ? random : (random & 0x3) | 0x8
    return value.toString(16)
  })
}

export function getAiSettings() {
  try {
    const saved = JSON.parse(localStorage.getItem(AI_SETTINGS_KEY) || '{}')
    return {
      model: cleanText(saved?.model),
      effort: cleanText(saved?.effort),
    }
  } catch {
    return { model: '', effort: '' }
  }
}

export function saveAiSettings(settings) {
  const normalized = {
    model: cleanText(settings?.model),
    effort: cleanText(settings?.effort),
  }
  localStorage.setItem(AI_SETTINGS_KEY, JSON.stringify(normalized))
  return normalized
}

export function isAiSettingsComplete(settings = getAiSettings()) {
  return Boolean(settings.model && settings.effort)
}

export function validateAiSettings(settings, catalog) {
  const selectedModel = catalog?.models?.find((option) => option.model === settings?.model)
  if (!selectedModel) {
    throw new Error('请选择可用模型')
  }
  if (!selectedModel.supportedReasoningEfforts?.includes(settings?.effort)) {
    throw new Error('请选择当前模型支持的 Effort')
  }
}

export function resolveAiSettings(catalog, settings = getAiSettings()) {
  const models = Array.isArray(catalog?.models) ? catalog.models : []
  if (!models.length) {
    throw new Error('AI 助教暂时没有可用模型')
  }
  const selectedModel = models.find((option) => option.model === settings.model)
    || models.find((option) => option.model === catalog.defaultModel)
    || models[0]
  const efforts = Array.isArray(selectedModel.supportedReasoningEfforts)
    ? selectedModel.supportedReasoningEfforts
    : []
  if (!efforts.length) {
    throw new Error('所选模型暂时没有可用 Effort')
  }
  const effort = efforts.includes(settings.effort)
    ? settings.effort
    : efforts.includes(selectedModel.defaultReasoningEffort)
      ? selectedModel.defaultReasoningEffort
      : efforts[0]
  return { model: selectedModel.model, effort }
}

export async function loadAiModelCatalog() {
  const catalog = await fetchAiTutorModels()
  if (!catalog || !Array.isArray(catalog.models) || !catalog.models.length) {
    throw new Error('AI 助教模型目录暂时不可用')
  }
  return catalog
}

export async function loadAiConfiguration({ persist = false } = {}) {
  const catalog = await loadAiModelCatalog()
  const settings = resolveAiSettings(catalog)
  if (persist) {
    saveAiSettings(settings)
  }
  return { catalog, settings }
}

function abortError() {
  return new DOMException('AI 助教请求已取消', 'AbortError')
}

function terminalMessage(event) {
  return event?.payload?.message
    || event?.payload?.error?.message
    || event?.payload?.errorMessage
    || 'AI 助教生成失败'
}

export async function createAiTutorCompletion({
  questionId,
  input,
  conversationId = null,
  model,
  effort,
  signal,
  onAccepted,
  onDelta,
}) {
  if (signal?.aborted) {
    throw abortError()
  }

  let accepted = null
  let answer = ''
  let terminalStatus = ''
  let terminalError = ''
  const cancelAcceptedRun = () => {
    if (accepted?.runId) {
      cancelAiTutorRun(accepted.runId).catch(() => {})
    }
  }
  signal?.addEventListener('abort', cancelAcceptedRun, { once: true })

  try {
    accepted = await createAiTutorRun({
      clientRequestId: createUuid(),
      conversationId,
      questionId,
      input,
      model,
      reasoningEffort: effort,
    }, { signal })
    onAccepted?.(accepted)

    if (signal?.aborted) {
      cancelAcceptedRun()
      throw abortError()
    }

    await streamAiTutorRun(accepted.runId, {
      signal,
      onEvent: (event) => {
        if (event?.type === 'item.delta' && typeof event?.payload?.delta === 'string') {
          answer += event.payload.delta
          onDelta?.(event.payload.delta, answer)
          return true
        }
        if (event?.type === 'run.completed') {
          terminalStatus = event.type
          return false
        }
        if (['run.failed', 'run.cancelled', 'run.timed_out'].includes(event?.type)) {
          terminalStatus = event.type
          terminalError = terminalMessage(event)
          return false
        }
        return true
      },
    })

    if (terminalStatus !== 'run.completed') {
      throw new Error(terminalError || 'AI 助教事件流意外中断')
    }
    if (!answer.trim()) {
      throw new Error('AI 助教没有返回可展示的内容')
    }
    return answer.trim()
  } finally {
    signal?.removeEventListener('abort', cancelAcceptedRun)
  }
}
