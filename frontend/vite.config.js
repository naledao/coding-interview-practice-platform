import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'node:path'

const apiTarget = process.env.VITE_API_TARGET || 'http://127.0.0.1:8904'
const pages = [
  'index',
  'login',
  'user',
  'wrong-book',
  'favorites',
  'answered-questions',
  'statistics',
  'ai-settings',
  'admin',
  'admin-documents-upload',
  'admin-document-upload-result',
  'admin-documents',
  'admin-document-detail',
  'admin-import-jobs',
  'admin-import-job-detail',
  'admin-questions',
  'admin-question-detail',
  'profile',
]

export default defineConfig({
  base: './',
  plugins: [vue()],
  build: {
    rollupOptions: {
      input: Object.fromEntries(
        pages.map((page) => [page, resolve(__dirname, `${page}.html`)]),
      ),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true,
      },
    },
  },
})
