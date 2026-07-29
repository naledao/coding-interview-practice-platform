export function getQuery() {
  return new URLSearchParams(window.location.search)
}

export function getQueryParam(name, fallback = '') {
  return getQuery().get(name) || fallback
}

export function pageHref(page, params = {}) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.set(key, String(value))
    }
  })
  const suffix = query.toString()
  return suffix ? `${page}?${suffix}` : page
}

export function navigateHref(href, replace = false) {
  const [page, query = ''] = href.split('?')
  navigateTo(page || 'index.html', Object.fromEntries(new URLSearchParams(query)), replace)
}

export function currentPageName() {
  const pathname = window.location.pathname
  const filename = pathname.substring(pathname.lastIndexOf('/') + 1)
  return filename || 'index.html'
}

export function isCurrentPage(page) {
  return currentPageName() === page
}

export function isAndroidApp() {
  return typeof window.AndroidBridge !== 'undefined'
}

export function navigateTo(page, params = {}, replace = false) {
  const href = pageHref(page, params)
  if (isAndroidApp() && typeof window.AndroidBridge.openPage === 'function') {
    window.AndroidBridge.openPage(page, JSON.stringify(params || {}), replace)
    return
  }
  if (replace) {
    window.location.replace(href)
    return
  }
  window.location.href = href
}

export function openDetailPage(kind, id, replace = false) {
  const pages = {
    documentUpload: 'admin-document-upload-result.html',
    document: 'admin-document-detail.html',
    importJob: 'admin-import-job-detail.html',
    question: 'admin-question-detail.html',
  }
  const keys = {
    documentUpload: 'uploadId',
    document: 'documentId',
    importJob: 'jobId',
    question: 'questionId',
  }
  navigateTo(pages[kind], { [keys[kind]]: id }, replace)
}
