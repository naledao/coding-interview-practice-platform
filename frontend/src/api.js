const TOKEN_KEY = 'interview_auth_token'
const API_BASE_URL_KEY = 'interview_api_base_url'
const REDIRECT_AFTER_LOGIN_KEY = 'interview_redirect_after_login'

function cleanBaseUrl(value) {
  return value?.trim().replace(/\/+$/, '') || ''
}

function isAndroidAssetOrigin() {
  return window.location.protocol === 'file:' || window.location.hostname === 'appassets.androidplatform.net'
}

export function getApiBaseUrl() {
  const savedBaseUrl = cleanBaseUrl(localStorage.getItem(API_BASE_URL_KEY))
  if (savedBaseUrl) {
    return savedBaseUrl
  }

  const configuredBaseUrl = cleanBaseUrl(import.meta.env.VITE_API_BASE_URL)
  if (configuredBaseUrl) {
    return configuredBaseUrl
  }

  return isAndroidAssetOrigin() ? 'http://127.0.0.1:8904' : ''
}

export function setApiBaseUrl(baseUrl) {
  const cleanedBaseUrl = cleanBaseUrl(baseUrl)
  if (cleanedBaseUrl) {
    localStorage.setItem(API_BASE_URL_KEY, cleanedBaseUrl)
    return
  }
  localStorage.removeItem(API_BASE_URL_KEY)
}

function resolveApiPath(path) {
  if (/^https?:\/\//i.test(path)) {
    return path
  }

  const apiBaseUrl = getApiBaseUrl()
  if (!apiBaseUrl || !path.startsWith('/')) {
    return path
  }

  return `${apiBaseUrl}${path}`
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

export function setRedirectAfterLogin(page) {
  if (page) {
    localStorage.setItem(REDIRECT_AFTER_LOGIN_KEY, page)
  }
}

export function takeRedirectAfterLogin() {
  const page = localStorage.getItem(REDIRECT_AFTER_LOGIN_KEY)
  localStorage.removeItem(REDIRECT_AFTER_LOGIN_KEY)
  return page
}

export async function apiRequest(path, options = {}) {
  const headers = new Headers(options.headers || {})
  const token = getToken()

  if (!headers.has('Content-Type') && options.body && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }

  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  let response
  try {
    response = await fetch(resolveApiPath(path), {
      ...options,
      headers,
    })
  } catch (error) {
    throw new Error('网络连接失败，请检查后端服务或网络后重试')
  }

  const payload = await response.json().catch(() => ({
    code: response.status,
    message: '请求失败',
    data: null,
  }))

  if (response.status === 401) {
    clearToken()
    window.dispatchEvent(new CustomEvent('auth-expired'))
  }

  if (!response.ok) {
    throw new Error(payload.message || '请求失败')
  }

  return payload.data
}

export function sendLoginCode(email) {
  return apiRequest('/api/auth/send-login-code', {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
}

export function login(email, code) {
  return apiRequest('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, code }),
  })
}

export function fetchCurrentUser() {
  return apiRequest('/api/auth/me')
}

export function updateNickname(nickname) {
  return apiRequest('/api/auth/me/nickname', {
    method: 'PATCH',
    body: JSON.stringify({ nickname }),
  })
}

export function logout() {
  return apiRequest('/api/auth/logout', {
    method: 'POST',
  })
}

export function uploadAdminDocument(file, autoStart = true) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('autoStart', String(autoStart))

  return apiRequest('/api/admin/documents', {
    method: 'POST',
    body: formData,
  })
}

export function fetchDocumentUpload(uploadId) {
  return apiRequest(`/api/admin/document-uploads/${uploadId}`)
}

export function fetchAdminDocuments(page = 1, pageSize = 20) {
  const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) })
  return apiRequest(`/api/admin/documents?${params}`)
}

export function fetchAdminDocument(documentId) {
  return apiRequest(`/api/admin/documents/${documentId}`)
}

export function createDocumentImportJob(documentId) {
  return apiRequest(`/api/admin/documents/${documentId}/import-jobs`, {
    method: 'POST',
  })
}

export function fetchImportJobs(options = {}) {
  const {
    page = 1,
    pageSize = 20,
    status = '',
    documentName = '',
    createdFrom = '',
    createdTo = '',
  } = options
  const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) })
  if (status) {
    params.set('status', status)
  }
  if (documentName?.trim()) {
    params.set('documentName', documentName.trim())
  }
  if (createdFrom) {
    params.set('createdFrom', createdFrom)
  }
  if (createdTo) {
    params.set('createdTo', createdTo)
  }
  return apiRequest(`/api/admin/import-jobs?${params}`)
}

export function fetchImportJob(jobId) {
  return apiRequest(`/api/admin/import-jobs/${jobId}`)
}

export function fetchImportJobLogs(jobId) {
  return apiRequest(`/api/admin/import-jobs/${jobId}/logs`)
}

export function fetchImportJobQuestions(jobId, page = 1, pageSize = 20) {
  const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) })
  return apiRequest(`/api/admin/import-jobs/${jobId}/questions?${params}`)
}

export function retryImportJob(jobId) {
  return apiRequest(`/api/admin/import-jobs/${jobId}/retry`, {
    method: 'POST',
  })
}

export function fetchAdminQuestions(page = 1, pageSize = 20, status = '', importJobId = '') {
  const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) })
  if (status) {
    params.set('status', status)
  }
  if (importJobId) {
    params.set('importJobId', String(importJobId))
  }
  return apiRequest(`/api/admin/questions?${params}`)
}

export function fetchAdminQuestion(questionId) {
  return apiRequest(`/api/admin/questions/${questionId}`)
}

export function disableAdminQuestion(questionId) {
  return apiRequest(`/api/admin/questions/${questionId}/disable`, {
    method: 'POST',
  })
}

export function enableAdminQuestion(questionId) {
  return apiRequest(`/api/admin/questions/${questionId}/enable`, {
    method: 'POST',
  })
}

export function fetchQuestions({ page = 1, pageSize = 20, difficulty = '', tagId = '', keyword = '' } = {}) {
  const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) })
  if (difficulty) {
    params.set('difficulty', difficulty)
  }
  if (tagId) {
    params.set('tagId', String(tagId))
  }
  if (keyword?.trim()) {
    params.set('keyword', keyword.trim())
  }
  return apiRequest(`/api/questions?${params}`)
}

export function fetchQuestion(questionId) {
  return apiRequest(`/api/questions/${questionId}`)
}

export function favoriteQuestion(questionId) {
  return apiRequest(`/api/favorites/${questionId}`, {
    method: 'POST',
  })
}

export function unfavoriteQuestion(questionId) {
  return apiRequest(`/api/favorites/${questionId}`, {
    method: 'DELETE',
  })
}

export function fetchFavorites(page = 1, pageSize = 20) {
  const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) })
  return apiRequest(`/api/favorites?${params}`)
}

export function fetchWrongQuestions({ page = 1, pageSize = 20, mastered = 'false' } = {}) {
  const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) })
  if (mastered !== '') {
    params.set('mastered', String(mastered))
  }
  return apiRequest(`/api/wrong-questions?${params}`)
}

export function fetchAnsweredQuestions(page = 1, pageSize = 20) {
  const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) })
  return apiRequest(`/api/practice/answered-questions?${params}`)
}

export function fetchExcludedQuestions(page = 1, pageSize = 20) {
  const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) })
  return apiRequest(`/api/excluded-questions?${params}`)
}

export function excludeQuestion(questionId) {
  return apiRequest(`/api/excluded-questions/${questionId}`, {
    method: 'POST',
  })
}

export function restoreExcludedQuestion(questionId) {
  return apiRequest(`/api/excluded-questions/${questionId}`, {
    method: 'DELETE',
  })
}

export function masterWrongQuestion(questionId) {
  return apiRequest(`/api/wrong-questions/${questionId}/master`, {
    method: 'POST',
  })
}

export function unmasterWrongQuestion(questionId) {
  return apiRequest(`/api/wrong-questions/${questionId}/unmaster`, {
    method: 'POST',
  })
}

export function removeWrongQuestion(questionId) {
  return apiRequest(`/api/wrong-questions/${questionId}`, {
    method: 'DELETE',
  })
}

export function fetchNextPracticeQuestion({
  mode = 'RANDOM',
  difficulty = '',
  tagId = '',
  keyword = '',
  excludeAnswered = false,
  currentQuestionId = '',
} = {}) {
  const params = new URLSearchParams({
    mode,
    excludeAnswered: String(excludeAnswered),
  })
  if (difficulty) {
    params.set('difficulty', difficulty)
  }
  if (tagId) {
    params.set('tagId', String(tagId))
  }
  if (keyword?.trim()) {
    params.set('keyword', keyword.trim())
  }
  if (currentQuestionId) {
    params.set('currentQuestionId', String(currentQuestionId))
  }
  return apiRequest(`/api/practice/next?${params}`)
}

export function fetchPracticeQuestionCount({
  mode = 'RANDOM',
  difficulty = '',
  tagId = '',
  keyword = '',
  excludeAnswered = false,
} = {}) {
  const params = new URLSearchParams({
    mode,
    excludeAnswered: String(excludeAnswered),
  })
  if (difficulty) {
    params.set('difficulty', difficulty)
  }
  if (tagId) {
    params.set('tagId', String(tagId))
  }
  if (keyword?.trim()) {
    params.set('keyword', keyword.trim())
  }
  return apiRequest(`/api/practice/count?${params}`)
}

export function answerQuestion(questionId, { selectedOptionKey, mode = 'RANDOM', timeSpentSeconds = null } = {}) {
  const body = { selectedOptionKey, mode }
  if (timeSpentSeconds !== null && timeSpentSeconds !== undefined) {
    body.timeSpentSeconds = timeSpentSeconds
  }
  return apiRequest(`/api/questions/${questionId}/answer`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function fetchTags() {
  return apiRequest('/api/tags')
}

export function fetchStatisticsOverview() {
  return apiRequest('/api/statistics/overview')
}

export function fetchStatisticsTags(sort = '') {
  const params = new URLSearchParams()
  if (sort) {
    params.set('sort', sort)
  }
  const query = params.toString()
  return apiRequest(`/api/statistics/tags${query ? `?${query}` : ''}`)
}

export function fetchStatisticsDaily(days = 7) {
  const params = new URLSearchParams({ days: String(days) })
  return apiRequest(`/api/statistics/daily?${params}`)
}
