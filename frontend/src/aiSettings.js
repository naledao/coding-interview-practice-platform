const AI_SETTINGS_KEY = 'interview_ai_settings'

const DEFAULT_SETTINGS = Object.freeze({
  baseUrl: 'https://api.openai.com/v1',
  apiKey: '',
  model: '',
  effort: 'medium',
})

const nativeAiStreams = new Map()
let nativeRequestSequence = 1

function cleanText(value) {
  return typeof value === 'string' ? value.trim() : ''
}

function cleanBaseUrl(value) {
  return cleanText(value).replace(/\/+$/, '')
}

export function getAiSettings() {
  let saved = {}
  try {
    const parsed = JSON.parse(localStorage.getItem(AI_SETTINGS_KEY) || '{}')
    saved = parsed && typeof parsed === 'object' ? parsed : {}
  } catch {
    saved = {}
  }

  const savedEffort = Object.prototype.hasOwnProperty.call(saved, 'effort')
    ? cleanText(saved.effort)
    : DEFAULT_SETTINGS.effort

  return {
    baseUrl: cleanBaseUrl(saved.baseUrl) || DEFAULT_SETTINGS.baseUrl,
    apiKey: cleanText(saved.apiKey),
    model: cleanText(saved.model),
    effort: savedEffort,
  }
}

export function saveAiSettings(settings) {
  const normalized = {
    baseUrl: cleanBaseUrl(settings?.baseUrl),
    apiKey: cleanText(settings?.apiKey),
    model: cleanText(settings?.model),
    effort: cleanText(settings?.effort),
  }
  localStorage.setItem(AI_SETTINGS_KEY, JSON.stringify(normalized))
  return normalized
}

export function isAiSettingsComplete(settings = getAiSettings()) {
  return Boolean(settings.baseUrl && settings.apiKey && settings.model)
}

export function validateAiSettings(settings) {
  if (!settings?.baseUrl || !/^https?:\/\//i.test(settings.baseUrl)) {
    throw new Error('基础请求地址必须是有效的 HTTP 或 HTTPS 地址')
  }
  if (!settings.apiKey) {
    throw new Error('请填写 SK')
  }
  if (!settings.model) {
    throw new Error('请填写模型名称')
  }
}

function chatCompletionsUrl(baseUrl) {
  const normalized = cleanBaseUrl(baseUrl)
  const url = new URL(normalized)
  const path = url.pathname.replace(/\/+$/, '')
  if (/\/chat\/completions$/i.test(path)) {
    return url.toString()
  }
  url.pathname = `${path || '/v1'}/chat/completions`
  return url.toString()
}

function assistantContent(payload) {
  const message = payload?.choices?.[0]?.message
  const content = message?.content
  if (typeof content === 'string' && content.trim()) {
    return content.trim()
  }
  if (Array.isArray(content)) {
    const text = content
      .map((part) => (typeof part === 'string' ? part : part?.text || ''))
      .join('')
      .trim()
    if (text) {
      return text
    }
  }
  if (typeof message?.refusal === 'string' && message.refusal.trim()) {
    return message.refusal.trim()
  }
  throw new Error('大模型响应中没有可展示的文本')
}

function streamChunkContent(payload) {
  if (payload?.error) {
    throw new Error(payload.error.message || '大模型流式响应返回错误')
  }

  const choice = payload?.choices?.[0]
  const delta = choice?.delta
  const content = delta?.content
  if (typeof content === 'string') {
    return content
  }
  if (Array.isArray(content)) {
    return content
      .map((part) => (typeof part === 'string' ? part : part?.text?.value || part?.text || ''))
      .join('')
  }
  if (typeof delta?.refusal === 'string') {
    return delta.refusal
  }
  if (choice?.message) {
    try {
      return assistantContent({ choices: [{ message: choice.message }] })
    } catch {
      return ''
    }
  }
  return typeof choice?.text === 'string' ? choice.text : ''
}

function abortError() {
  return new DOMException('大模型请求已取消', 'AbortError')
}

function isAndroidStreamingAvailable() {
  return (
    typeof window !== 'undefined' &&
    typeof window.AndroidBridge?.startAiChatStream === 'function' &&
    typeof window.AndroidBridge?.cancelAiChatStream === 'function'
  )
}

function finishNativeStream(requestId, action) {
  const pending = nativeAiStreams.get(requestId)
  if (!pending) {
    return
  }
  nativeAiStreams.delete(requestId)
  pending.signal?.removeEventListener('abort', pending.abortHandler)
  action(pending)
}

if (typeof window !== 'undefined') {
  window.__onAndroidAiStream = (event) => {
    const payload = typeof event === 'string' ? JSON.parse(event) : event
    const pending = nativeAiStreams.get(payload?.requestId)
    if (!pending) {
      return
    }

    if (payload.type === 'delta') {
      const text = typeof payload.text === 'string' ? payload.text : ''
      if (text) {
        pending.content += text
        pending.onDelta?.(text, pending.content)
      }
      return
    }
    if (payload.type === 'complete') {
      finishNativeStream(payload.requestId, (stream) => {
        if (!stream.content.trim()) {
          stream.reject(new Error('大模型流式响应中没有可展示的文本'))
          return
        }
        stream.resolve(stream.content.trim())
      })
      return
    }
    if (payload.type === 'error') {
      finishNativeStream(payload.requestId, (stream) => {
        stream.reject(new Error(payload.message || 'Android 原生大模型请求失败'))
      })
    }
  }
}

function createAndroidChatCompletion(endpoint, apiKey, requestBody, { signal, onDelta }) {
  return new Promise((resolve, reject) => {
    const requestId = `ai-${Date.now()}-${nativeRequestSequence++}`
    const abortHandler = () => {
      if (!nativeAiStreams.has(requestId)) {
        return
      }
      nativeAiStreams.delete(requestId)
      try {
        window.AndroidBridge.cancelAiChatStream(requestId)
      } catch {
        // The Activity may already be closing.
      }
      reject(abortError())
    }

    if (signal?.aborted) {
      reject(abortError())
      return
    }

    nativeAiStreams.set(requestId, {
      abortHandler,
      content: '',
      onDelta,
      reject,
      resolve,
      signal,
    })
    signal?.addEventListener('abort', abortHandler, { once: true })

    try {
      window.AndroidBridge.startAiChatStream(
        requestId,
        endpoint,
        apiKey,
        JSON.stringify(requestBody),
      )
    } catch (error) {
      finishNativeStream(requestId, (stream) => {
        stream.reject(new Error(error?.message || '无法调用 Android 原生大模型请求'))
      })
    }
  })
}

function parseJsonError(text, status) {
  try {
    const payload = JSON.parse(text)
    return payload?.error?.message || payload?.message || `大模型请求失败（HTTP ${status}）`
  } catch {
    return text.trim() || `大模型请求失败（HTTP ${status}）`
  }
}

async function createBrowserChatCompletion(endpoint, apiKey, requestBody, { signal, onDelta }) {
  let response
  try {
    response = await fetch(endpoint, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${apiKey}`,
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
      },
      body: JSON.stringify(requestBody),
      signal,
    })
  } catch (error) {
    if (error?.name === 'AbortError') {
      throw error
    }
    throw new Error('无法连接大模型服务，请检查请求地址、网络或服务端 CORS 设置')
  }

  if (!response.ok) {
    throw new Error(parseJsonError(await response.text(), response.status))
  }

  if (response.headers.get('content-type')?.includes('application/json')) {
    const answer = assistantContent(await response.json())
    onDelta?.(answer, answer)
    return answer
  }
  if (!response.body) {
    throw new Error('当前环境无法读取大模型流式响应')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let answer = ''

  const consumeEvent = (eventBlock) => {
    const data = eventBlock
      .split(/\r?\n/)
      .filter((line) => line.startsWith('data:'))
      .map((line) => line.slice(5).trim())
      .join('\n')
      .trim()
    if (!data || data === '[DONE]') {
      return data === '[DONE]'
    }
    const delta = streamChunkContent(JSON.parse(data))
    if (delta) {
      answer += delta
      onDelta?.(delta, answer)
    }
    return false
  }

  while (true) {
    const { value, done } = await reader.read()
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done })
    const events = buffer.split(/\r?\n\r?\n/)
    buffer = events.pop() || ''
    let completed = false
    for (const eventBlock of events) {
      completed = consumeEvent(eventBlock) || completed
    }
    if (done || completed) {
      break
    }
  }
  if (buffer.trim()) {
    consumeEvent(buffer)
  }
  if (!answer.trim()) {
    throw new Error('大模型流式响应中没有可展示的文本')
  }
  return answer.trim()
}

export async function createAiChatCompletion(messages, { signal, onDelta } = {}) {
  const settings = getAiSettings()
  validateAiSettings(settings)

  const requestBody = {
    model: settings.model,
    messages,
    stream: true,
  }
  if (settings.effort) {
    requestBody.reasoning_effort = settings.effort
  }
  const endpoint = chatCompletionsUrl(settings.baseUrl)

  if (isAndroidStreamingAvailable()) {
    return createAndroidChatCompletion(endpoint, settings.apiKey, requestBody, { signal, onDelta })
  }
  return createBrowserChatCompletion(endpoint, settings.apiKey, requestBody, { signal, onDelta })
}
