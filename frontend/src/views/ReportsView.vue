<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listReports, resolveReport } from '../api/comments'
import { errorMessage, formatTime } from '../utils/articles'
import type { CommentReport } from '../types/comment'

const reports = ref<CommentReport[]>([])
const loading = ref(true)
const error = ref('')
const busyId = ref(0)

async function load() {
  loading.value = true
  error.value = ''
  try {
    reports.value = await listReports()
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function handle(item: CommentReport, action: 'DELETE' | 'DISMISS') {
  if (busyId.value) {
    return
  }
  if (action === 'DELETE' && !window.confirm('删除这条评论？它下面的回复也会一起隐藏。')) {
    return
  }
  busyId.value = item.id
  error.value = ''
  try {
    await resolveReport(item.id, action)
    reports.value = reports.value.filter((report) => report.id !== item.id)
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    busyId.value = 0
  }
}
</script>

<template>
  <div class="work-page">
    <header class="work-heading">
      <div>
        <p class="eyebrow">社区维护</p>
        <h1>举报处理</h1>
        <p>读者举报的评论会出现在这里：可以删除评论，也可以忽略举报。</p>
      </div>
    </header>

    <p v-if="loading" class="empty-state">正在加载举报…</p>
    <p v-else-if="error" class="error-message" role="alert">{{ error }}</p>
    <div v-else-if="reports.length" class="report-list">
      <article v-for="item in reports" :key="item.id" class="report-card">
        <div class="report-card__head">
          <RouterLink
            :to="{ path: `/article/${item.articleId}`, hash: `#comment-${item.commentId}` }"
            class="report-card__title"
          >
            {{ item.articleTitle }}
          </RouterLink>
          <time :datetime="item.createdAt">{{ formatTime(item.createdAt) }}</time>
        </div>
        <p class="report-card__reason">
          举报原因：<strong>{{ item.reason }}</strong> · 举报人 {{ item.reporter }}
        </p>
        <blockquote class="report-card__quote">
          <strong>{{ item.commentAuthor }}</strong>
          <p>{{ item.commentContent }}</p>
        </blockquote>
        <div class="report-card__actions">
          <button
            type="button"
            class="tb-btn"
            :disabled="busyId === item.id"
            @click="handle(item, 'DISMISS')"
          >
            忽略举报
          </button>
          <button
            type="button"
            class="tb-btn danger-button"
            :disabled="busyId === item.id"
            @click="handle(item, 'DELETE')"
          >
            {{ busyId === item.id ? '处理中…' : '删除评论' }}
          </button>
        </div>
      </article>
    </div>
    <div v-else class="empty-state">
      <h3>暂无待处理举报</h3>
      <p>有读者举报评论时会出现在这里。</p>
    </div>
  </div>
</template>
