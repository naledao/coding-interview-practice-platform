import { useCallback, useEffect, useState } from 'react'
import { createImportJob, fetchDocument } from '../api'
import { Alert, DetailGrid, Icon, LoadingBlock, Markdown, PageHeader, Panel, RouteLink, StatusBadge } from '../components/Common'
import { DOCUMENT_STATUS_TEXT, formatBytes, formatDate, JOB_STATUS_TEXT } from '../utils'

export default function DocumentDetailPage({ documentId }) {
  const [document, setDocument] = useState(null)
  const [loading, setLoading] = useState(true)
  const [importing, setImporting] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setDocument(await fetchDocument(documentId))
    } catch (loadError) {
      setError(loadError.message)
    } finally {
      setLoading(false)
    }
  }, [documentId])

  useEffect(() => { load() }, [load])

  async function startImport() {
    setImporting(true)
    setError('')
    setNotice('')
    try {
      const created = await createImportJob(documentId)
      setNotice(`已创建导入任务 #${created.importJobId}`)
      await load()
    } catch (actionError) {
      setError(actionError.message)
    } finally {
      setImporting(false)
    }
  }

  return (
    <div className="page page-detail">
      <PageHeader
        eyebrow="文档详情"
        title={document?.originalFilename || `文档 #${documentId}`}
        description="查看文档来源、内容与最近一次生成任务。"
        backTo="/documents"
        actions={(
          <>
            <button className="button button-ghost" type="button" disabled={loading} onClick={load}><Icon name="refresh" />刷新</button>
            <button className="button button-primary" type="button" disabled={!document || importing} onClick={startImport}>{importing ? <span className="spinner spinner-light" /> : <Icon name="play" />}{importing ? '创建中' : '重新导入'}</button>
          </>
        )}
      />
      <Alert>{error}</Alert>
      <Alert type="success">{notice}</Alert>

      {loading && !document ? <LoadingBlock /> : document && (
        <>
          <section className="detail-summary">
            <div><span className="file-symbol file-symbol-large">MD</span><div><p>文档状态</p><StatusBadge status={document.status} label={DOCUMENT_STATUS_TEXT[document.status]} /></div></div>
            <div><p>生成题目</p><strong>{document.generatedQuestionCount}</strong><small>道</small></div>
            <div><p>文件大小</p><strong className="summary-text">{formatBytes(document.fileSize)}</strong></div>
          </section>

          <Panel title="基础信息">
            <DetailGrid items={[
              { label: '文档 ID', value: `#${document.id}` },
              { label: '上传批次', value: <RouteLink to={`/uploads/${document.uploadId}`}>#{document.uploadId}</RouteLink> },
              { label: '来源类型', value: document.sourceType },
              { label: '上传人', value: document.uploadedBy },
              { label: '上传时间', value: formatDate(document.createdAt) },
              { label: '压缩包', value: document.archiveOriginalFilename, hidden: document.sourceType !== 'ZIP' },
              { label: '包内路径', value: document.archiveEntryPath, wide: true, hidden: document.sourceType !== 'ZIP' },
              { label: '内容 SHA-256', value: <code>{document.contentSha256}</code>, wide: true },
              { label: '后端存储路径', value: <code>{document.storedPath}</code>, wide: true },
            ]} />
          </Panel>

          {document.latestJob && (
            <Panel title="最近导入任务">
              <RouteLink className="latest-job-card" to={`/jobs/${document.latestJob.id}`}>
                <span><Icon name="jobs" /></span>
                <div><strong>任务 #{document.latestJob.id}</strong><small>已生成 {document.latestJob.generatedQuestionCount} 道题</small></div>
                <StatusBadge status={document.latestJob.status} label={JOB_STATUS_TEXT[document.latestJob.status]} />
                <Icon name="chevron" />
              </RouteLink>
            </Panel>
          )}

          <Panel title="Markdown 内容" meta="后端保存的原始文档内容">
            <Markdown className="document-content">{document.content}</Markdown>
          </Panel>
        </>
      )}
    </div>
  )
}
