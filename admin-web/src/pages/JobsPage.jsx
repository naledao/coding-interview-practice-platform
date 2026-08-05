import { useCallback, useEffect, useState } from 'react'
import { fetchJobs, retryJob as retryImportJob } from '../api'
import { Alert, EmptyState, Icon, LoadingBlock, PageHeader, Pagination, Panel, RouteLink, StatusBadge } from '../components/Common'
import { compactText, formatDate, JOB_STATUS_TEXT, toApiDate } from '../utils'

const PAGE_SIZE = 20
const EMPTY_FILTERS = { status: '', documentName: '', createdFrom: '', createdTo: '' }

export default function JobsPage() {
  const [filters, setFilters] = useState(EMPTY_FILTERS)
  const [applied, setApplied] = useState(EMPTY_FILTERS)
  const [result, setResult] = useState({ items: [], page: 1, pageSize: PAGE_SIZE, total: 0 })
  const [loading, setLoading] = useState(true)
  const [retryingId, setRetryingId] = useState(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const load = useCallback(async (page = 1, nextFilters = applied) => {
    setLoading(true)
    setError('')
    try {
      setResult(await fetchJobs({
        page,
        pageSize: PAGE_SIZE,
        ...nextFilters,
        createdFrom: toApiDate(nextFilters.createdFrom),
        createdTo: toApiDate(nextFilters.createdTo),
      }))
    } catch (loadError) {
      setError(loadError.message)
    } finally {
      setLoading(false)
    }
  }, [applied])

  useEffect(() => { load(1, EMPTY_FILTERS) }, []) // eslint-disable-line react-hooks/exhaustive-deps

  function applyFilters(event) {
    event.preventDefault()
    setApplied(filters)
    load(1, filters)
  }

  function resetFilters() {
    setFilters(EMPTY_FILTERS)
    setApplied(EMPTY_FILTERS)
    load(1, EMPTY_FILTERS)
  }

  async function retry(job) {
    setRetryingId(job.id)
    setError('')
    setNotice('')
    try {
      const created = await retryImportJob(job.id)
      setNotice(`已创建重试任务 #${created.newImportJobId}`)
      await load(result.page)
    } catch (actionError) {
      setError(actionError.message)
    } finally {
      setRetryingId(null)
    }
  }

  return (
    <div className="page">
      <PageHeader title="导入任务" description="跟踪文档解析、Codex 产题进度和失败日志。" actions={<button className="button button-ghost" type="button" disabled={loading} onClick={() => load(result.page)}><Icon name="refresh" />刷新</button>} />
      <Alert>{error}</Alert>
      <Alert type="success">{notice}</Alert>

      <Panel className="filter-panel">
        <form className="filters" onSubmit={applyFilters}>
          <label className="field"><span>任务状态</span><select value={filters.status} onChange={(event) => setFilters({ ...filters, status: event.target.value })}><option value="">全部状态</option><option value="PENDING">等待执行</option><option value="RUNNING">处理中</option><option value="SUCCEEDED">处理成功</option><option value="FAILED">处理失败</option><option value="CANCELLED">已取消</option></select></label>
          <label className="field"><span>文档名称</span><input type="search" value={filters.documentName} onChange={(event) => setFilters({ ...filters, documentName: event.target.value })} placeholder="输入文件名" /></label>
          <label className="field"><span>开始时间</span><input type="datetime-local" value={filters.createdFrom} onChange={(event) => setFilters({ ...filters, createdFrom: event.target.value })} /></label>
          <label className="field"><span>结束时间</span><input type="datetime-local" value={filters.createdTo} onChange={(event) => setFilters({ ...filters, createdTo: event.target.value })} /></label>
          <div className="filter-buttons"><button className="button button-primary" type="submit">筛选</button><button className="button button-ghost" type="button" onClick={resetFilters}>重置</button></div>
        </form>
      </Panel>

      <Panel title="任务列表" meta={`共 ${result.total} 个任务`}>
        {loading && !result.items.length ? <LoadingBlock /> : result.items.length ? (
          <div className="table-scroll">
            <table className="data-table">
              <thead><tr><th>任务</th><th>状态</th><th>生成题目</th><th>开始时间</th><th>结束时间</th><th>失败原因</th><th className="align-right">操作</th></tr></thead>
              <tbody>{result.items.map((job) => (
                <tr key={job.id}>
                  <td><RouteLink className="primary-cell" to={`/jobs/${job.id}`}><span className="file-symbol"><Icon name="jobs" size={17} /></span><div><strong>{job.documentName}</strong><small>任务 #{job.id} · 文档 #{job.documentId}</small></div></RouteLink></td>
                  <td><StatusBadge status={job.status} label={JOB_STATUS_TEXT[job.status]} /></td>
                  <td><strong>{job.generatedQuestionCount}</strong> 道</td>
                  <td>{formatDate(job.startedAt)}</td>
                  <td>{formatDate(job.finishedAt)}</td>
                  <td className="failure-cell" title={job.failedReason || ''}>{compactText(job.failedReason, 48)}</td>
                  <td className="align-right"><div className="row-actions"><RouteLink className="icon-button" to={`/jobs/${job.id}`} title="查看任务"><Icon name="eye" /></RouteLink><button className="icon-button" type="button" title="重试失败任务" disabled={job.status !== 'FAILED' || retryingId === job.id} onClick={() => retry(job)}>{retryingId === job.id ? <span className="spinner" /> : <Icon name="retry" />}</button></div></td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        ) : <EmptyState title="没有匹配的任务" description="调整筛选条件，或先上传一份知识文档。" />}
        <Pagination page={result.page} pageSize={result.pageSize} total={result.total} loading={loading} onChange={(page) => load(page)} />
      </Panel>
    </div>
  )
}
