import { useCallback, useEffect, useState } from 'react'
import { createImportJob, fetchDocuments } from '../api'
import { Alert, EmptyState, Icon, LoadingBlock, PageHeader, Pagination, Panel, RouteLink, StatusBadge } from '../components/Common'
import { DOCUMENT_STATUS_TEXT, formatBytes, formatDate, JOB_STATUS_TEXT } from '../utils'

const PAGE_SIZE = 20

export default function DocumentsPage() {
  const [result, setResult] = useState({ items: [], page: 1, pageSize: PAGE_SIZE, total: 0 })
  const [loading, setLoading] = useState(true)
  const [runningId, setRunningId] = useState(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const load = useCallback(async (page = result.page) => {
    setLoading(true)
    setError('')
    try {
      setResult(await fetchDocuments(page, PAGE_SIZE))
    } catch (loadError) {
      setError(loadError.message)
    } finally {
      setLoading(false)
    }
  }, [result.page])

  useEffect(() => { load(1) }, []) // eslint-disable-line react-hooks/exhaustive-deps

  async function startImport(documentId) {
    setRunningId(documentId)
    setError('')
    setNotice('')
    try {
      const created = await createImportJob(documentId)
      setNotice(`已创建导入任务 #${created.importJobId}`)
      await load(result.page)
    } catch (actionError) {
      setError(actionError.message)
    } finally {
      setRunningId(null)
    }
  }

  return (
    <div className="page">
      <PageHeader
        title="知识文档"
        description="集中查看 App 与网页端上传的全部知识文档。"
        actions={(
          <>
            <button className="button button-ghost" type="button" disabled={loading} onClick={() => load(result.page)}><Icon name="refresh" />刷新</button>
            <RouteLink className="button button-primary" to="/upload"><Icon name="upload" />上传文档</RouteLink>
          </>
        )}
      />
      <Alert>{error}</Alert>
      <Alert type="success">{notice}</Alert>

      <Panel title="文档列表" meta={`共 ${result.total} 份文档`}>
        {loading && !result.items.length ? <LoadingBlock /> : result.items.length ? (
          <div className="table-scroll">
            <table className="data-table">
              <thead><tr><th>文档</th><th>来源</th><th>大小</th><th>上传人</th><th>文档状态</th><th>最近任务</th><th>上传时间</th><th className="align-right">操作</th></tr></thead>
              <tbody>
                {result.items.map((document) => (
                  <tr key={document.id}>
                    <td><RouteLink className="primary-cell" to={`/documents/${document.id}`}><span className="file-symbol">MD</span><div><strong>{document.originalFilename}</strong><small>{document.archiveEntryPath || `文档 #${document.id}`}</small></div></RouteLink></td>
                    <td>{document.sourceType}</td>
                    <td>{formatBytes(document.fileSize)}</td>
                    <td>{document.uploadedBy}</td>
                    <td><StatusBadge status={document.status} label={DOCUMENT_STATUS_TEXT[document.status]} /></td>
                    <td>{document.latestJob ? <RouteLink className="inline-job" to={`/jobs/${document.latestJob.id}`}>#{document.latestJob.id} · {JOB_STATUS_TEXT[document.latestJob.status]}</RouteLink> : <span className="muted">尚未导入</span>}</td>
                    <td>{formatDate(document.createdAt)}</td>
                    <td className="align-right"><div className="row-actions"><RouteLink className="icon-button" to={`/documents/${document.id}`} title="查看详情"><Icon name="eye" /></RouteLink><button className="icon-button" type="button" title="创建导入任务" disabled={runningId === document.id} onClick={() => startImport(document.id)}>{runningId === document.id ? <span className="spinner" /> : <Icon name="play" />}</button></div></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : <EmptyState title="暂无知识文档" description="可以从网页或 Android App 上传第一份文档。" />}
        <Pagination page={result.page} pageSize={result.pageSize} total={result.total} loading={loading} onChange={load} />
      </Panel>
    </div>
  )
}
