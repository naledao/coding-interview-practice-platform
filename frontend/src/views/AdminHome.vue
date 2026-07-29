<script setup>
import { FileUp, ListChecks, Library } from '@lucide/vue'
import { navigateTo } from '../navigation'

defineProps({
  currentUser: {
    type: Object,
    default: null,
  },
})
</script>

<template>
  <div class="page">
    <header class="page-header">
      <p>管理首页</p>
      <h1>{{ currentUser?.nickname || '管理员' }}</h1>
    </header>

    <section class="quick-grid">
      <a class="action-tile" href="admin-documents-upload.html" @click.prevent="navigateTo('admin-documents-upload.html')">
        <FileUp :size="24" />
        <h2>文档上传</h2>
        <p>Markdown / ZIP</p>
      </a>
      <a class="action-tile" href="admin-import-jobs.html" @click.prevent="navigateTo('admin-import-jobs.html')">
        <ListChecks :size="24" />
        <h2>导入任务</h2>
        <p>查看 Codex 状态</p>
      </a>
      <a class="action-tile" href="admin-questions.html" @click.prevent="navigateTo('admin-questions.html')">
        <Library :size="24" />
        <h2>题库管理</h2>
        <p>查看生成题目</p>
      </a>
    </section>
  </div>
</template>
