import { useCallback, useEffect, useState } from 'react'
import { fetchJob, fetchJobLogs, fetchJobQuestions, retryJob as retryImportJob } from '../api'
import { Alert, DetailGrid, EmptyState, Icon, LoadingBlock, Markdown, PageHeader, Pagination, Panel, RouteLink, StatusBadge } from '../components/Common'
import { DIFFICULTY_TEXT, formatDate, formatTags, JOB_STATUS_TEXT, QUESTION_STATUS_TEXT } from '../utils'

const QUESTION_PAGE_SIZE = 20
const STATUS_DESCRIPTION = {
  PENDING: '任务已经创建，正在等待 Codex 接手处理。',
  RUNNING: 'Codex 正在读取文档、补充资料并生成题目。',
  SUCCEEDED: '任务处理成功，生成的题目已经进入题库。',
  FAILED: '任务处理失败，请检查失败原因与执行日志。',
  CANCELLED: '任务已经取消。',
}

function payloadText(payload) {
  if (!payload) return ''
  try {
    return JSON.stringify(JSON.parse(payload), null, 2)
  } catch {
    return payload
  }
}

export default function JobDetailPage({ jobId }) {
  const [job, setJob] = useState(null)
  const [logs, setLogs] = useState([])
  const [questions, setQuestions] = useState({ items: [], page: 1, pageSize: QUESTION_PAGE_SIZE, total: 0 })
  const [loading, setLoading] = useState(true)
  const [retrying, setRetrying] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const load = useCallback(async ({ silent = false, questionPage = questions.page } = {}) => {
    if (!silent) setLoading(true)
    setError('')
    try {
      const [nextJob, nextLogs, nextQuestions] = await Promise.all([
        fetchJob(jobId),
        fetchJobLogs(jobId),
        fetchJobQuestions(jobId, questionPage, QUESTION_PAGE_SIZE),
      ])
      setJob(nextJob)
      setLogs(nextLogs)
      setQuestions(nextQuestions)
    } catch (loadError) {
      setError(loadError.message)
    } finally {
      if (!silent) setLoading(false)
    }
  }, [jobId, questions.page])

  useEffect(() => { load({ questionPage: 1 }) }, [jobId]) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    if (!['PENDING', 'RUNNING'].includes(job?.status)) return undefined
    const timer = window.setTimeout(() => load({ silent: true }), 5000)
    return () => window.clearTimeout(timer)
  }, [job, load])

  async function retry() {
    setRetrying(true)
    setError('')
    setNotice('')
    try {
      const created = await retryImportJob(jobId)
      setNotice(`已创建重试任务 #${created.newImportJobId}`)
    } catch (actionError) {
      setError(actionError.message)
    } finally {
      setRetrying(false)
    }
  }

  return (
    <div className="page page-detail">
      <PageHeader
        eyebrow="导入任务"
        title={job ? `任务 #${job.id}` : `任务 #${jobId}`}
        description={job ? STATUS_DESCRIPTION[job.status] : '查看任务详情、题目和执行日志。'}
        backTo="/jobs"
        actions={(
          <>
            <button className="button button-ghost" type="button" disabled={loading} onClick={() => load()}><Icon name="refresh" />刷新</button>
            <button className="button button-primary" type="button" disabled={job?.status !== 'FAILED' || retrying} onClick={retry}>{retrying ? <span className="spinner spinner-light" /> : <Icon name="retry" />}{retrying ? '创建中' : '重试任务'}</button>
          </>
        )}
      />
      <Alert>{error}</Alert>
      <Alert type="success">{notice}</Alert>

      {loading && !job ? <LoadingBlock /> : job && (
        <>
          <section className={`job-hero job-${job.status.toLowerCase()}`}>
            <div className="job-status-icon">{job.status === 'RUNNING' ? <span className="spinner" /> : <Icon name={job.status === 'FAILED' ? 'close' : 'check'} size={30} />}</div>
            <div><p>当前状态</p><h2>{JOB_STATUS_TEXT[job.status]}</h2><span>{STATUS_DESCRIPTION[job.status]}</span></div>
            <div className="job-count"><strong>{job.generatedQuestionCount}</strong><small>已生成题目</small></div>
          </section>

          <Panel title="任务信息">
            <DetailGrid items={[
              { label: '任务 ID', value: `#${job.id}` },
              { label: '文档', value: <RouteLink to={`/documents/${job.documentId}`}>{job.documentName}</RouteLink> },
              { label: '状态', value: <StatusBadge status={job.status} label={JOB_STATUS_TEXT[job.status]} /> },
              { label: 'Codex Session', value: job.codexSessionId || '-' },
              { label: '创建时间', value: formatDate(job.createdAt) },
              { label: '开始时间', value: formatDate(job.startedAt) },
              { label: '结束时间', value: formatDate(job.finishedAt) },
              { label: '失败原因', value: job.failedReason || '-', wide: true },
            ]} />
          </Panel>

          <Panel title="生成题目" meta={`共 ${questions.total} 道`}>
            {questions.items.length ? (
              <div className="question-cards compact-question-cards">
                {questions.items.map((question) => (
                  <RouteLink className="question-list-card" key={question.id} to={`/questions/${question.id}`}>
                    <div className="question-card-top"><span># {question.id}</span><StatusBadge status={question.status} label={QUESTION_STATUS_TEXT[question.status]} /></div>
                    <Markdown className="question-preview">{question.stem}</Markdown>
                    <div className="question-meta"><span>{DIFFICULTY_TEXT[question.difficulty] || question.difficulty}</span><span>{question.knowledgePoint}</span><span>{formatTags(question.tags)}</span></div>
                  </RouteLink>
                ))}
              </div>
            ) : <EmptyState title="暂无生成题目" description={['PENDING', 'RUNNING'].includes(job.status) ? '任务执行过程中，题目会逐步出现在这里。' : '该任务没有写入题目。'} />}
            <Pagination page={questions.page} pageSize={questions.pageSize} total={questions.total} loading={loading} onChange={(page) => load({ questionPage: page })} />
          </Panel>

          <Panel title="执行日志" meta={`${logs.length} 条日志`}>
            {logs.length ? (
              <div className="log-timeline">
                {logs.map((log) => (
                  <article key={log.id}>
                    <span className={`log-dot log-${log.level?.toLowerCase()}`} />
                    <div className="log-time">{formatDate(log.createdAt)}</div>
                    <div className="log-content"><div><strong>{log.level}</strong><p>{log.message}</p></div>{payloadText(log.payload) && <pre>{payloadText(log.payload)}</pre>}</div>
                  </article>
                ))}
              </div>
            ) : <EmptyState title="暂无执行日志" />}
          </Panel>
        </>
      )}
    </div>
  )
}
