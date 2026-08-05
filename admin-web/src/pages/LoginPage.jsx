import { useEffect, useState } from 'react'
import { login, sendLoginCode, setToken } from '../api'
import { Alert, Icon } from '../components/Common'

export default function LoginPage({ initialError = '', onAuthenticated }) {
  const [email, setEmail] = useState('admin@example.com')
  const [code, setCode] = useState('')
  const [error, setError] = useState(initialError)
  const [notice, setNotice] = useState('')
  const [sending, setSending] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => setError(initialError), [initialError])

  async function requestCode() {
    if (!email.trim()) {
      setError('请输入管理员邮箱')
      return
    }
    setSending(true)
    setError('')
    setNotice('')
    try {
      await sendLoginCode(email.trim())
      setNotice('验证码已发送，请检查邮箱')
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setSending(false)
    }
  }

  async function submit(event) {
    event.preventDefault()
    if (!email.trim() || !code.trim()) {
      setError('请输入邮箱和验证码')
      return
    }
    setSubmitting(true)
    setError('')
    setNotice('')
    try {
      const result = await login(email.trim(), code.trim())
      if (result.user?.role !== 'ADMIN') {
        throw new Error('当前账号不是管理员')
      }
      setToken(result.token)
      await onAuthenticated()
    } catch (loginError) {
      setError(loginError.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="login-page">
      <section className="login-visual">
        <div className="login-brand"><span>J</span> Java Practice</div>
        <div className="login-copy">
          <p>KNOWLEDGE TO QUESTIONS</p>
          <h1>把知识文档，变成可练习的题库。</h1>
          <p>统一管理文档上传、Codex 生成任务、执行日志与题目质量。</p>
        </div>
        <div className="login-flow">
          <span>上传文档</span><i />
          <span>自动产题</span><i />
          <span>进入题库</span>
        </div>
      </section>

      <section className="login-form-wrap">
        <form className="login-card" onSubmit={submit}>
          <div className="login-card-icon"><Icon name="shield" size={26} /></div>
          <p className="eyebrow">ADMIN CONSOLE</p>
          <h2>管理员登录</h2>
          <p className="login-subtitle">使用管理员邮箱验证码进入控制台</p>

          <label className="field">
            <span>邮箱</span>
            <input type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="admin@example.com" />
          </label>
          <label className="field">
            <span>验证码</span>
            <div className="code-input">
              <input value={code} autoComplete="one-time-code" inputMode="numeric" onChange={(event) => setCode(event.target.value)} placeholder="6 位验证码" />
              <button type="button" disabled={sending} onClick={requestCode}>{sending ? '发送中' : '获取验证码'}</button>
            </div>
          </label>

          <Alert>{error}</Alert>
          <Alert type="success">{notice}</Alert>

          <button className="button button-primary button-login" type="submit" disabled={submitting}>
            {submitting ? <span className="spinner spinner-light" /> : <Icon name="shield" />}
            {submitting ? '正在登录' : '进入管理控制台'}
          </button>
          <p className="same-origin-note">生产环境由 Spring Boot 托管，本页面与管理 API 使用同一服务地址。</p>
        </form>
      </section>
    </main>
  )
}
