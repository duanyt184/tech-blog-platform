<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useArticlePage } from '../composables/useArticlePage'
import { deleteArticle, submitArticle, withdrawArticle } from '../api/articles'
import { errorMessage, formatTime, statusLabels } from '../utils/articles'
import PagePagination from '../components/PagePagination.vue'
import type { Article } from '../types/article'
const route = useRoute()
const router = useRouter()
const { result, loading, error, reload } = useArticlePage(() => ({ scope: 'mine', params: {
  status: route.query.status || '', page: Number(route.query.page || 0), size: 20,
} }))
const busy = ref<number | null>(null)
const actionError = ref('')
const notice = ref('')
function filter(value: string) { void router.push({ query: { status: value || undefined } }) }
function changePage(page: number) { void router.push({ query: { ...route.query, page: page || undefined } }) }
async function act(article: Article, action: 'submit' | 'withdraw' | 'delete') {
  if (busy.value !== null) return
  if (action === 'delete' && !window.confirm(`确定删除草稿「${article.title}」？`)) return
  if (action === 'withdraw' && !window.confirm('撤回后文章将变为草稿，已发布内容会从广场下架。继续吗？')) return
  busy.value = article.id
  actionError.value = ''
  notice.value = ''
  try {
    if (action === 'delete') await deleteArticle(article)
    else if (action === 'withdraw') await withdrawArticle(article)
    else await submitArticle(article)
    notice.value = action === 'delete' ? '文章已删除' : action === 'withdraw' ? '已撤回为草稿' : '已提交审核'
    await reload()
    if (!result.value.items.length && result.value.page > 0) changePage(result.value.page - 1)
  } catch (reason) { actionError.value = errorMessage(reason) }
  finally { busy.value = null }
}
</script>
<template>
  <div class="work-page">
    <header class="work-heading"><div><p class="eyebrow">创作空间</p><h1>我的文章</h1><p>管理草稿、查看审核反馈，记录每一次分享。</p></div><RouterLink to="/write" class="tb-btn tb-btn--primary">写文章</RouterLink></header>
    <div class="status-tabs"><button :class="{ active: !route.query.status }" @click="filter('')">全部</button><button v-for="(label, status) in statusLabels" :key="status" :class="{ active: route.query.status === status }" @click="filter(status)">{{ label }}</button></div>
    <p v-if="actionError" role="alert" class="error-message">{{ actionError }}</p>
    <p v-if="notice" role="status" class="success-message">{{ notice }}</p>
    <p v-if="loading" class="empty-state" role="status">正在加载文章…</p>
    <div v-else-if="error" class="empty-state" role="alert"><p>{{ error }}</p><button class="tb-btn" @click="reload">重试</button></div>
    <div v-else-if="!result.items.length" class="empty-state"><h2>这里还没有文章</h2><p>从一份草稿开始，写下你的技术实践。</p><RouterLink to="/write" class="tb-btn">开始写作</RouterLink></div>
    <div v-else class="management-list">
      <article v-for="article in result.items" :key="article.id" class="management-row">
        <div><RouterLink :to="`/write/${article.id}`"><h2>{{ article.title }}</h2></RouterLink><p>{{ article.category }} · 更新于 {{ formatTime(article.updatedAt) }}</p><span class="status-badge" :data-status="article.status">{{ statusLabels[article.status] }}</span></div>
        <div class="row-actions">
          <RouterLink :to="`/write/${article.id}`" class="tb-btn">{{ ['DRAFT', 'REJECTED'].includes(article.status) ? '编辑 / 查看反馈' : '查看记录' }}</RouterLink>
          <template v-if="['DRAFT', 'REJECTED'].includes(article.status)"><button class="tb-btn tb-btn--primary" :disabled="busy !== null" @click="act(article, 'submit')">投稿</button><button class="tb-btn danger-button" :disabled="busy !== null" @click="act(article, 'delete')">删除</button></template>
          <button v-else class="tb-btn" :disabled="busy !== null" @click="act(article, 'withdraw')">撤回</button>
          <RouterLink v-if="article.status === 'PUBLISHED'" :to="`/article/${article.id}`" class="tb-btn">公开页面</RouterLink>
        </div>
      </article>
    </div>
    <PagePagination v-if="!error" :page="result.page" :pages="result.totalPages" :total="result.totalElements" :disabled="loading || busy !== null" @change="changePage" />
  </div>
</template>
