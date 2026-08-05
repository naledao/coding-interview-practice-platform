import { useCallback, useEffect, useState } from 'react'
import { createImportJob, fetchUpload } from '../api'
import { Alert, EmptyState, Icon, LoadingBlock, PageHeader, Panel, RouteLink, StatusBadge } from '../components/Common'
import { DOCUMENT_STATUS_TEXT, formatBytes, formatDate, JOB_STATUS_TEXT, PARSE_STATUS_TEXT } from '../utils'

function isActive(upload) {
  return ['QUEUED', 'PARSING'].includes(upload?.parseStatus) || ['PENDING', 'RUNNING'].includes(upload?.parseTaskStatus)
}

export default function UploadDetailPage({ uploadId }) {
  const [upload, setUpload] = useState(null)
  const [loading, setLoading] = useState(true)
  const [runningId, setRunningId] = useState(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const load = useCallback(async ({ silent = false } = {}) => {
    if (!silent) setLoading(true)
    setError('')
    try {
      setUpload(await fetchUpload(uploadId))
    } catch (loadError) {
      setError(loadError.message)
    } finally {
      if (!silent) setLoading(false)
    }
  }, [uploadId])

  useEffect(() => { load() }, [load])
  useEffect(() => {
    if (!isActive(upload)) return undefined
    const timer = window.setTimeout(() => load({ silent: true }), 2500)
    return () => window.clearTimeout(timer)
  }, [upload, load])

  async function startImport(documentId) {
    setRunningId(documentId)
    setError('')
    setNotice('')
    try {
      const created = await createImportJob(documentId)
      setNotice(`已创建导入任务 #${created.importJobId}`)
      await load({ silent: true })
    } catch (actionError) {
      setError(actionError.message)
    } finally {
      setRunningId(null)
    }
  }

  return (
    <div className="page">
      <PageHeader
        eyebrow="上传批次"
        title={upload?.originalFilename || `上传 #${uploadId}`}
        description={isActive(upload) ? '后端正在解析文件，本页面会自动刷新。' : '查看解析结果和批次内文档。'}
        backTo="/upload"
        actions={<button className="button button-ghost" type="button" disabled={loading} onClick={() => load()}><Icon name="refresh" />刷新</button>}
      />
      <Alert>{error}</Alert>
      <Alert type="success">{notice}</Alert>

      {loading && !upload ? <LoadingBlock /> : upload && (
        <>
          <section className="upload-result-hero">
            <div className={`result-icon ${upload.parseStatus === 'FAILED' ? 'result-failed' : ''}`}>{isActive(upload) ? <span className="spinner" /> : <Icon name={upload.parseStatus === 'FAILED' ? 'close' : 'check'} size={30} />}</div>
            <div><p>解析状态</p><h2>{PARSE_STATUS_TEXT[upload.parseStatus] || upload.parseStatus}</h2><span>上传批次 #{upload.id} · {formatDate(upload.createdAt)}</span></div>
            <StatusBadge status={upload.parseStatus} label={PARSE_STATUS_TEXT[upload.parseStatus]} />
          </section>

          <section className="result-metrics">
            <article><span>上传类型</span><strong>{upload.uploadType}</strong></article>
            <article><span>文件大小</span><strong>{formatBytes(upload.fileSize)}</strong></article>
            <article><span>识别文档</span><strong>{upload.documentCount}</strong></article>
            <article><span>忽略文件</span><strong>{upload.ignoredFileCount}</strong></article>
            <article><span>跳过文件</span><strong>{upload.skippedFileCount}</strong></article>
            <article><span>上传人</span><strong>{upload.uploadedBy}</strong></article>
          </section>

          <Alert type="error">{upload.parseFailedReason}</Alert>

          {upload.skippedFiles?.length > 0 && (
            <Panel title="跳过的文件" meta="以下文件未进入知识库">
              <div className="skipped-list">{upload.skippedFiles.map((item) => <div key={item.archiveEntryPath}><code>{item.archiveEntryPath}</code><span>{item.reason}</span></div>)}</div>
            </Panel>
          )}

          <Panel title="批次文档" meta={`${upload.documents?.length || 0} 份 Markdown 文档`}>
            {upload.documents?.length ? (
              <div className="table-scroll">
                <table className="data-table">
                  <thead><tr><th>文档</th><th>大小</th><th>文档状态</th><th>导入任务</th><th className="align-right">操作</th></tr></thead>
                  <tbody>{upload.documents.map((document) => (
                    <tr key={document.documentId}>
                      <td><RouteLink className="primary-cell" to={`/documents/${document.documentId}`}><span className="file-symbol">MD</span><div><strong>{document.originalFilename}</strong><small>{document.archiveEntryPath || `文档 #${document.documentId}`}</small></div></RouteLink></td>
                      <td>{formatBytes(document.fileSize)}</td>
                      <td><StatusBadge status={document.documentStatus} label={DOCUMENT_STATUS_TEXT[document.documentStatus]} /></td>
                      <td>{document.latestJob ? <RouteLink className="inline-job" to={`/jobs/${document.latestJob.id}`}>#{document.latestJob.id} · {JOB_STATUS_TEXT[document.latestJob.status]}</RouteLink> : <span className="muted">尚未创建</span>}</td>
                      <td className="align-right"><button className="button button-small button-ghost" type="button" disabled={runningId === document.documentId} onClick={() => startImport(document.documentId)}>{runningId === document.documentId ? <span className="spinner" /> : <Icon name="play" />}创建任务</button></td>
                    </tr>
                  ))}</tbody>
                </table>
              </div>
            ) : <EmptyState title={isActive(upload) ? '正在解析文档' : '没有可用文档'} description={isActive(upload) ? '解析完成后，识别出的 Markdown 会显示在这里。' : '请检查上传文件和解析失败原因。'} />}
          </Panel>
        </>
      )}
    </div>
  )
}
