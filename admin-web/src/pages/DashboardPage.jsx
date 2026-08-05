import { useCallback, useEffect, useState } from 'react'
import { fetchDocuments, fetchJobs, fetchQuestions } from '../api'
import { Alert, EmptyState, Icon, LoadingBlock, PageHeader, Panel, RouteLink, StatusBadge } from '../components/Common'
import { formatDate, JOB_STATUS_TEXT } from '../utils'

export default function DashboardPage() {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [documents, jobs, questions, runningJobs, failedJobs] = await Promise.all([
        fetchDocuments(1, 1),
        fetchJobs({ page: 1, pageSize: 6 }),
        fetchQuestions(1, 1),
        fetchJobs({ page: 1, pageSize: 1, status: 'RUNNING' }),
        fetchJobs({ page: 1, pageSize: 1, status: 'FAILED' }),
      ])
      setData({ documents, jobs, questions, runningJobs, failedJobs })
    } catch (loadError) {
      setError(loadError.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load() }, [load])

  return (
    <div className="page">
      <PageHeader
        title="管理总览"
        description="查看文档、生成任务和题库的实时状态。"
        actions={(
          <>
            <button className="button button-ghost" type="button" disabled={loading} onClick={load}><Icon name="refresh" />刷新</button>
            <RouteLink className="button button-primary" to="/upload"><Icon name="upload" />上传文档</RouteLink>
          </>
        )}
      />
      <Alert>{error}</Alert>

      {loading && !data ? <LoadingBlock /> : (
        <>
          <section className="metric-cards">
            <article className="metric-card metric-teal">
              <span className="metric-icon"><Icon name="file" /></span>
              <div><p>知识文档</p><strong>{data?.documents.total ?? 0}</strong><small>已入库文档</small></div>
            </article>
            <article className="metric-card metric-blue">
              <span className="metric-icon"><Icon name="question" /></span>
              <div><p>题库总量</p><strong>{data?.questions.total ?? 0}</strong><small>单选题</small></div>
            </article>
            <article className="metric-card metric-amber">
              <span className="metric-icon"><Icon name="jobs" /></span>
              <div><p>正在执行</p><strong>{data?.runningJobs.total ?? 0}</strong><small>Codex 任务</small></div>
            </article>
            <article className="metric-card metric-rose">
              <span className="metric-icon"><Icon name="retry" /></span>
              <div><p>失败任务</p><strong>{data?.failedJobs.total ?? 0}</strong><small>等待处理</small></div>
            </article>
          </section>

          <section className="dashboard-grid">
            <Panel
              className="recent-jobs"
              title="最近导入任务"
              meta={`共 ${data?.jobs.total ?? 0} 个任务`}
              actions={<RouteLink to="/jobs" className="text-link">查看全部 <Icon name="chevron" size={15} /></RouteLink>}
            >
              {data?.jobs.items?.length ? (
                <div className="compact-table">
                  {data.jobs.items.map((job) => (
                    <RouteLink className="compact-table-row" key={job.id} to={`/jobs/${job.id}`}>
                      <span className="file-symbol">{job.documentName?.slice(-2).toLowerCase() === 'md' ? 'MD' : 'DOC'}</span>
                      <div className="compact-main"><strong>{job.documentName}</strong><small>#{job.id} · {formatDate(job.createdAt)}</small></div>
                      <span className="question-count">{job.generatedQuestionCount} 题</span>
                      <StatusBadge status={job.status} label={JOB_STATUS_TEXT[job.status]} />
                      <Icon name="chevron" className="row-chevron" />
                    </RouteLink>
                  ))}
                </div>
              ) : <EmptyState title="暂无导入任务" description="上传第一份知识文档后，任务会显示在这里。" />}
            </Panel>

            <div className="dashboard-side">
              <Panel title="快捷操作">
                <div className="quick-actions">
                  <RouteLink to="/upload"><span><Icon name="upload" /></span><div><strong>上传知识文档</strong><small>支持 Markdown 与 ZIP</small></div><Icon name="chevron" /></RouteLink>
                  <RouteLink to="/documents"><span><Icon name="file" /></span><div><strong>管理已有文档</strong><small>查看内容与重新导入</small></div><Icon name="chevron" /></RouteLink>
                  <RouteLink to="/questions"><span><Icon name="question" /></span><div><strong>检查生成题目</strong><small>查看解析与上下线</small></div><Icon name="chevron" /></RouteLink>
                </div>
              </Panel>
              <Panel title="服务状态">
                <div className="service-status"><span className="online-dot" /><div><strong>后端服务正常</strong><small>管理 API 已连接</small></div><em>ONLINE</em></div>
              </Panel>
            </div>
          </section>
        </>
      )}
    </div>
  )
}
