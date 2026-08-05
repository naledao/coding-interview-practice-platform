import { useCallback, useEffect, useState } from 'react'
import { clearToken, fetchCurrentUser, getToken, logout } from './api'
import AdminLayout from './components/AdminLayout'
import { LoadingBlock } from './components/Common'
import DashboardPage from './pages/DashboardPage'
import DocumentDetailPage from './pages/DocumentDetailPage'
import DocumentsPage from './pages/DocumentsPage'
import JobDetailPage from './pages/JobDetailPage'
import JobsPage from './pages/JobsPage'
import LoginPage from './pages/LoginPage'
import QuestionDetailPage from './pages/QuestionDetailPage'
import QuestionsPage from './pages/QuestionsPage'
import UploadDetailPage from './pages/UploadDetailPage'
import UploadPage from './pages/UploadPage'
import { navigate, routeId, useRoute } from './router'

function resolvePage(route) {
  const uploadId = routeId(route, 'uploads')
  if (uploadId) return <UploadDetailPage uploadId={uploadId} />
  const documentId = routeId(route, 'documents')
  if (documentId) return <DocumentDetailPage documentId={documentId} />
  const jobId = routeId(route, 'jobs')
  if (jobId) return <JobDetailPage jobId={jobId} />
  const questionId = routeId(route, 'questions')
  if (questionId) return <QuestionDetailPage questionId={questionId} />

  switch (route) {
    case '/overview': return <DashboardPage />
    case '/upload': return <UploadPage />
    case '/documents': return <DocumentsPage />
    case '/jobs': return <JobsPage />
    case '/questions': return <QuestionsPage />
    default:
      navigate('/overview', { replace: true })
      return <DashboardPage />
  }
}

export default function App() {
  const route = useRoute()
  const [user, setUser] = useState(null)
  const [checkingAuth, setCheckingAuth] = useState(Boolean(getToken()))
  const [authError, setAuthError] = useState('')

  const verifyUser = useCallback(async () => {
    if (!getToken()) {
      setCheckingAuth(false)
      setUser(null)
      return
    }
    setCheckingAuth(true)
    try {
      const currentUser = await fetchCurrentUser()
      if (currentUser.role !== 'ADMIN') {
        clearToken()
        setUser(null)
        setAuthError('当前账号不是管理员，无法进入管理控制台')
        return
      }
      setAuthError('')
      setUser(currentUser)
    } catch (error) {
      clearToken()
      setUser(null)
      setAuthError(error.message)
    } finally {
      setCheckingAuth(false)
    }
  }, [])

  useEffect(() => {
    verifyUser()
    const onExpired = () => {
      setUser(null)
      setAuthError('登录已过期，请重新登录')
    }
    window.addEventListener('admin-auth-expired', onExpired)
    return () => window.removeEventListener('admin-auth-expired', onExpired)
  }, [verifyUser])

  async function handleLogout() {
    try {
      await logout()
    } catch {
      // The local token must still be cleared when the server is unavailable.
    }
    clearToken()
    setUser(null)
    setAuthError('')
  }

  if (checkingAuth) {
    return <div className="app-loading"><LoadingBlock label="正在验证管理员身份…" /></div>
  }

  if (!user) {
    return <LoginPage initialError={authError} onAuthenticated={verifyUser} />
  }

  return (
    <AdminLayout user={user} route={route} onLogout={handleLogout}>
      {resolvePage(route)}
    </AdminLayout>
  )
}
