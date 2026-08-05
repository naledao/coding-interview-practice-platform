import { useCallback, useEffect, useState } from 'react'
import { fetchQuestions } from '../api'
import { Alert, EmptyState, Icon, LoadingBlock, Markdown, PageHeader, Pagination, Panel, RouteLink, StatusBadge } from '../components/Common'
import { DIFFICULTY_TEXT, formatDate, QUESTION_STATUS_TEXT } from '../utils'

const PAGE_SIZE = 20

export default function QuestionsPage() {
  const [status, setStatus] = useState('')
  const [importJobId, setImportJobId] = useState('')
  const [appliedJobId, setAppliedJobId] = useState('')
  const [result, setResult] = useState({ items: [], page: 1, pageSize: PAGE_SIZE, total: 0 })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async (page = 1, nextStatus = status, nextJobId = appliedJobId) => {
    setLoading(true)
    setError('')
    try {
      setResult(await fetchQuestions(page, PAGE_SIZE, nextStatus, nextJobId))
    } catch (loadError) {
      setError(loadError.message)
    } finally {
      setLoading(false)
    }
  }, [status, appliedJobId])

  useEffect(() => { load(1, '', '') }, []) // eslint-disable-line react-hooks/exhaustive-deps

  function applyFilters(event) {
    event.preventDefault()
    setAppliedJobId(importJobId.trim())
    load(1, status, importJobId.trim())
  }

  function changeStatus(nextStatus) {
    setStatus(nextStatus)
    load(1, nextStatus, appliedJobId)
  }

  function resetFilters() {
    setStatus('')
    setImportJobId('')
    setAppliedJobId('')
    load(1, '', '')
  }

  return (
    <div className="page">
      <PageHeader title="题库管理" description="检查 Codex 生成的题干、选项、解析和 Review 结果。" actions={<button className="button button-ghost" type="button" disabled={loading} onClick={() => load(result.page)}><Icon name="refresh" />刷新</button>} />
      <Alert>{error}</Alert>

      <Panel className="filter-panel">
        <form className="filters filters-short" onSubmit={applyFilters}>
          <label className="field"><span>题目状态</span><select value={status} onChange={(event) => changeStatus(event.target.value)}><option value="">全部状态</option><option value="ACTIVE">已启用</option><option value="DISABLED">已下线</option></select></label>
          <label className="field"><span>来源任务 ID</span><input inputMode="numeric" value={importJobId} onChange={(event) => setImportJobId(event.target.value.replace(/\D/g, ''))} placeholder="例如 42" /></label>
          <div className="filter-buttons"><button className="button button-primary" type="submit">筛选</button><button className="button button-ghost" type="button" onClick={resetFilters}>重置</button></div>
        </form>
      </Panel>

      <Panel title="题目列表" meta={`共 ${result.total} 道题`}>
        {loading && !result.items.length ? <LoadingBlock /> : result.items.length ? (
          <div className="question-cards">
            {result.items.map((question) => (
              <RouteLink className="question-list-card" key={question.id} to={`/questions/${question.id}`}>
                <div className="question-card-top">
                  <div><span className={`difficulty difficulty-${question.difficulty?.toLowerCase()}`}>{DIFFICULTY_TEXT[question.difficulty] || question.difficulty}</span><span>#{question.id}</span></div>
                  <StatusBadge status={question.status} label={QUESTION_STATUS_TEXT[question.status]} />
                </div>
                <Markdown className="question-preview">{question.stem}</Markdown>
                <div className="tag-row">{(question.tags || []).map((tag) => <span key={tag.id}>{tag.name}</span>)}</div>
                <div className="question-card-footer"><span>知识点：{question.knowledgePoint || '-'}</span><span>任务 #{question.sourceImportJobId}</span><span>{formatDate(question.createdAt)}</span><Icon name="chevron" /></div>
              </RouteLink>
            ))}
          </div>
        ) : <EmptyState title="没有匹配的题目" description="调整筛选条件或等待导入任务生成题目。" />}
        <Pagination page={result.page} pageSize={result.pageSize} total={result.total} loading={loading} onChange={(page) => load(page)} />
      </Panel>
    </div>
  )
}
