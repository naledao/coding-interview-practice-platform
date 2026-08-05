import { useCallback, useEffect, useState } from 'react'
import { disableQuestion, enableQuestion, fetchQuestion } from '../api'
import { Alert, DetailGrid, Icon, LoadingBlock, Markdown, PageHeader, Panel, RouteLink, StatusBadge } from '../components/Common'
import { DIFFICULTY_TEXT, formatDate, formatTags, QUESTION_STATUS_TEXT } from '../utils'

export default function QuestionDetailPage({ questionId }) {
  const [question, setQuestion] = useState(null)
  const [loading, setLoading] = useState(true)
  const [changing, setChanging] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setQuestion(await fetchQuestion(questionId))
    } catch (loadError) {
      setError(loadError.message)
    } finally {
      setLoading(false)
    }
  }, [questionId])

  useEffect(() => { load() }, [load])

  async function changeStatus() {
    const enabling = question.status === 'DISABLED'
    if (!enabling && !window.confirm('确认下线这道题吗？下线后普通用户将无法刷到该题。')) return
    setChanging(true)
    setError('')
    setNotice('')
    try {
      const updated = enabling ? await enableQuestion(questionId) : await disableQuestion(questionId)
      setQuestion(updated)
      setNotice(enabling ? '题目已恢复上线' : '题目已下线')
    } catch (actionError) {
      setError(actionError.message)
    } finally {
      setChanging(false)
    }
  }

  return (
    <div className="page page-detail">
      <PageHeader
        eyebrow="题目详情"
        title={`题目 #${questionId}`}
        description="核对题干、答案、解析和 Codex Review。"
        backTo="/questions"
        actions={(
          <>
            <button className="button button-ghost" type="button" disabled={loading} onClick={load}><Icon name="refresh" />刷新</button>
            {question && <button className={`button ${question.status === 'ACTIVE' ? 'button-danger' : 'button-primary'}`} type="button" disabled={changing} onClick={changeStatus}>{changing ? <span className="spinner spinner-light" /> : <Icon name={question.status === 'ACTIVE' ? 'close' : 'check'} />}{question.status === 'ACTIVE' ? '下线题目' : '恢复题目'}</button>}
          </>
        )}
      />
      <Alert>{error}</Alert>
      <Alert type="success">{notice}</Alert>

      {loading && !question ? <LoadingBlock /> : question && (
        <>
          <Panel title="题目信息">
            <DetailGrid items={[
              { label: '状态', value: <StatusBadge status={question.status} label={QUESTION_STATUS_TEXT[question.status]} /> },
              { label: '难度', value: DIFFICULTY_TEXT[question.difficulty] || question.difficulty },
              { label: '题型', value: question.type },
              { label: '知识点', value: question.knowledgePoint },
              { label: '来源文档', value: <RouteLink to={`/documents/${question.sourceDocumentId}`}>#{question.sourceDocumentId}</RouteLink> },
              { label: '来源任务', value: <RouteLink to={`/jobs/${question.sourceImportJobId}`}>#{question.sourceImportJobId}</RouteLink> },
              { label: '标签', value: formatTags(question.tags), wide: true },
              { label: '创建时间', value: formatDate(question.createdAt), wide: true },
            ]} />
          </Panel>

          <Panel title="题干与选项">
            <Markdown className="question-stem">{question.stem}</Markdown>
            <div className="option-list">
              {(question.options || []).map((option) => (
                <article className={`option ${option.correct ? 'option-correct' : ''}`} key={option.id}>
                  <span>{option.optionKey}</span>
                  <Markdown>{option.content}</Markdown>
                  {option.correct && <strong><Icon name="check" size={15} />正确答案</strong>}
                </article>
              ))}
            </div>
          </Panel>

          <section className="analysis-grid">
            <Panel title="答案解析"><Markdown>{question.answerAnalysis}</Markdown></Panel>
            <Panel title="Codex Review"><Markdown>{question.codexReviewSummary}</Markdown></Panel>
          </section>
        </>
      )}
    </div>
  )
}
