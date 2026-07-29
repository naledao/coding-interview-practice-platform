import { createApp } from 'vue'
import Shell from './Shell.vue'
import './styles.css'

import AdminDocumentDetail from './views/AdminDocumentDetail.vue'
import AdminDocumentList from './views/AdminDocumentList.vue'
import AdminDocumentUpload from './views/AdminDocumentUpload.vue'
import AdminHome from './views/AdminHome.vue'
import AdminImportJobDetail from './views/AdminImportJobDetail.vue'
import AdminImportJobList from './views/AdminImportJobList.vue'
import AdminQuestionDetail from './views/AdminQuestionDetail.vue'
import AdminQuestionList from './views/AdminQuestionList.vue'
import AdminUploadResult from './views/AdminUploadResult.vue'
import AiSettingsView from './views/AiSettingsView.vue'
import AnsweredQuestionsView from './views/AnsweredQuestionsView.vue'
import FavoritesView from './views/FavoritesView.vue'
import LoginView from './views/LoginView.vue'
import ProfileView from './views/ProfileView.vue'
import StatisticsView from './views/StatisticsView.vue'
import UserHome from './views/UserHome.vue'
import WrongBookView from './views/WrongBookView.vue'
import { getToken, setRedirectAfterLogin } from './api'
import { navigateTo } from './navigation'

const pages = {
  'login.html': {
    component: LoginView,
    pageKey: 'login',
    publicPage: true,
  },
  'user.html': {
    component: UserHome,
    pageKey: 'user',
  },
  'wrong-book.html': {
    component: WrongBookView,
    pageKey: 'wrong-book',
  },
  'favorites.html': {
    component: FavoritesView,
    pageKey: 'favorites',
  },
  'answered-questions.html': {
    component: AnsweredQuestionsView,
    pageKey: 'answered-questions',
  },
  'statistics.html': {
    component: StatisticsView,
    pageKey: 'statistics',
  },
  'ai-settings.html': {
    component: AiSettingsView,
    pageKey: 'ai-settings',
  },
  'admin.html': {
    component: AdminHome,
    pageKey: 'admin',
    adminOnly: true,
  },
  'admin-documents-upload.html': {
    component: AdminDocumentUpload,
    pageKey: 'admin-documents-upload',
    adminOnly: true,
  },
  'admin-document-upload-result.html': {
    component: AdminUploadResult,
    pageKey: 'admin-document-upload-result',
    adminOnly: true,
  },
  'admin-documents.html': {
    component: AdminDocumentList,
    pageKey: 'admin-documents',
    adminOnly: true,
  },
  'admin-document-detail.html': {
    component: AdminDocumentDetail,
    pageKey: 'admin-document-detail',
    adminOnly: true,
  },
  'admin-import-jobs.html': {
    component: AdminImportJobList,
    pageKey: 'admin-import-jobs',
    adminOnly: true,
  },
  'admin-import-job-detail.html': {
    component: AdminImportJobDetail,
    pageKey: 'admin-import-job-detail',
    adminOnly: true,
  },
  'admin-questions.html': {
    component: AdminQuestionList,
    pageKey: 'admin-questions',
    adminOnly: true,
  },
  'admin-question-detail.html': {
    component: AdminQuestionDetail,
    pageKey: 'admin-question-detail',
    adminOnly: true,
  },
  'profile.html': {
    component: ProfileView,
    pageKey: 'profile',
  },
}

function pageName() {
  const pathname = window.location.pathname
  const filename = pathname.substring(pathname.lastIndexOf('/') + 1)
  return filename || 'index.html'
}

const config = pages[pageName()] || pages['index.html']

if (pageName() === 'index.html') {
  navigateTo(getToken() ? 'user.html' : 'login.html', {}, true)
} else if (!config.publicPage && !getToken()) {
  setRedirectAfterLogin(`${pageName()}${window.location.search}`)
  navigateTo('login.html', {}, true)
} else {
  createApp(Shell, config).mount('#app')
}
