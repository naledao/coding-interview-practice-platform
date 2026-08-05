export const JOB_STATUS_TEXT = {
  PENDING: '等待执行',
  RUNNING: '处理中',
  SUCCEEDED: '处理成功',
  FAILED: '处理失败',
  CANCELLED: '已取消',
}

export const DOCUMENT_STATUS_TEXT = {
  UPLOADED: '已上传',
  PROCESSING: '处理中',
  PROCESSED: '已完成',
  FAILED: '失败',
}

export const PARSE_STATUS_TEXT = {
  QUEUED: '等待解析',
  PARSING: '正在解析',
  PARSED: '解析完成',
  FAILED: '解析失败',
}

export const QUESTION_STATUS_TEXT = {
  ACTIVE: '已启用',
  DISABLED: '已下线',
}

export const DIFFICULTY_TEXT = {
  EASY: '简单',
  MEDIUM: '中等',
  HARD: '困难',
}

export function formatDate(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}

export function formatBytes(value) {
  if (!Number.isFinite(value)) return '-'
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

export function formatTags(tags) {
  return (tags || []).map((tag) => tag.name).join(' · ') || '-'
}

export function compactText(value, limit = 76) {
  if (!value) return '-'
  return value.length > limit ? `${value.slice(0, limit)}…` : value
}

export function toApiDate(value) {
  return value ? new Date(value).toISOString() : ''
}
