const TOKEN_KEY = 'interview_auth_token'
const configuredBaseUrl = (import.meta.env.VITE_API_BASE_URL || '').trim().replace(/\/+$/, '')

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

function resolveUrl(path) {
  return configuredBaseUrl && path.startsWith('/') ? `${configuredBaseUrl}${path}` : path
}

export async function apiRequest(path, options = {}) {
  const headers = new Headers(options.headers || {})
  const token = getToken()

  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  let response
  try {
    response = await fetch(resolveUrl(path), { ...options, headers })
  } catch {
    throw new Error('无法连接后端服务，请检查网络或服务状态')
  }

  const payload = await response.json().catch(() => ({
    code: response.status,
    message: '服务器返回了无法解析的响应',
    data: null,
  }))

  if (response.status === 401) {
    clearToken()
    window.dispatchEvent(new CustomEvent('admin-auth-expired'))
  }
  if (!response.ok || payload.code !== 0) {
    throw new Error(payload.message || `请求失败（${response.status}）`)
  }

  return payload.data
}

function queryString(values) {
  const params = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => {
    if (value !== '' && value !== null && value !== undefined) {
      params.set(key, String(value))
    }
  })
  const query = params.toString()
  return query ? `?${query}` : ''
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

export function logout() {
  return apiRequest('/api/auth/logout', { method: 'POST' })
}

export function uploadDocument(file, autoStart = true) {
  const body = new FormData()
  body.append('file', file)
  body.append('autoStart', String(autoStart))
  return apiRequest('/api/admin/documents', { method: 'POST', body })
}

export function fetchUpload(uploadId) {
  return apiRequest(`/api/admin/document-uploads/${encodeURIComponent(uploadId)}`)
}

export function fetchDocuments(page = 1, pageSize = 20) {
  return apiRequest(`/api/admin/documents${queryString({ page, pageSize })}`)
}

export function fetchDocument(documentId) {
  return apiRequest(`/api/admin/documents/${encodeURIComponent(documentId)}`)
}

export function createImportJob(documentId) {
  return apiRequest(`/api/admin/documents/${encodeURIComponent(documentId)}/import-jobs`, {
    method: 'POST',
  })
}

export function fetchJobs({
  page = 1,
  pageSize = 20,
  status = '',
  documentName = '',
  createdFrom = '',
  createdTo = '',
} = {}) {
  return apiRequest(`/api/admin/import-jobs${queryString({
    page,
    pageSize,
    status,
    documentName,
    createdFrom,
    createdTo,
  })}`)
}

export function fetchJob(jobId) {
  return apiRequest(`/api/admin/import-jobs/${encodeURIComponent(jobId)}`)
}

export function fetchJobLogs(jobId) {
  return apiRequest(`/api/admin/import-jobs/${encodeURIComponent(jobId)}/logs`)
}

export function fetchJobQuestions(jobId, page = 1, pageSize = 20) {
  return apiRequest(`/api/admin/import-jobs/${encodeURIComponent(jobId)}/questions${queryString({ page, pageSize })}`)
}

export function retryJob(jobId) {
  return apiRequest(`/api/admin/import-jobs/${encodeURIComponent(jobId)}/retry`, {
    method: 'POST',
  })
}

export function fetchQuestions(page = 1, pageSize = 20, status = '', importJobId = '') {
  return apiRequest(`/api/admin/questions${queryString({ page, pageSize, status, importJobId })}`)
}

export function fetchQuestion(questionId) {
  return apiRequest(`/api/admin/questions/${encodeURIComponent(questionId)}`)
}

export function disableQuestion(questionId) {
  return apiRequest(`/api/admin/questions/${encodeURIComponent(questionId)}/disable`, {
    method: 'POST',
  })
}

export function enableQuestion(questionId) {
  return apiRequest(`/api/admin/questions/${encodeURIComponent(questionId)}/enable`, {
    method: 'POST',
  })
}
