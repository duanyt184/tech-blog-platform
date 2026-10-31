<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useArticlePage } from '../composables/useArticlePage'
import PagePagination from '../components/PagePagination.vue'
import { formatTime, statusLabels } from '../utils/articles'
const route = useRoute()
const router = useRouter()
const filters = { PENDING: '待审核', REJECTED: '已退回', PUBLISHED: '已发布' }
const { result, loading, error, reload } = useArticlePage(() => ({ scope: 'editor', params: {
  status: route.query.status || 'PENDING', page: Number(route.query.page || 0), size: 20,
} }))
function filter(status: string) { void router.push({ query: { status } }) }
function changePage(page: number) { void router.push({ query: { ...route.query, page: page || undefined } }) }
</script>
<template>
  <div class="work-page">
    <header class="work-heading"><div><p class="eyebrow">编辑空间</p><h1>审核工作台</h1><p>阅读投稿、提供修改建议，让有价值的内容被看见。</p></div><button class="tb-btn" :disabled="loading" @click="reload">刷新队列</button></header>
    <div class="status-tabs"><button v-for="(label, status) in filters" :key="status" :class="{ active: (route.query.status || 'PENDING') === status }" @click="filter(status)">{{ label }}</button></div>
    <p v-if="loading" class="empty-state" role="status">正在加载投稿…</p>
    <div v-else-if="error" class="empty-state" role="alert"><p>{{ error }}</p><button class="tb-btn" @click="reload">重试</button></div>
    <div v-else-if="!result.items.length" class="empty-state"><h2>当前没有待处理的文章</h2><p>可以切换状态查看其他投稿。</p></div>
    <div v-else class="management-list">
      <article v-for="article in result.items" :key="article.id" class="management-row">
        <div><RouterLink :to="`/editor/${article.id}`"><h2>{{ article.title }}</h2></RouterLink><p>{{ article.author }} · {{ article.category }} · {{ formatTime(article.updatedAt) }}</p><span class="status-badge" :data-status="article.status">{{ statusLabels[article.status] }}</span></div>
        <RouterLink :to="`/editor/${article.id}`" class="tb-btn">{{ article.status === 'PENDING' ? '阅读并审核' : '查看记录' }}</RouterLink>
      </article>
    </div>
    <PagePagination v-if="!error" :page="result.page" :pages="result.totalPages" :total="result.totalElements" :disabled="loading" @change="changePage" />
  </div>
</template>
