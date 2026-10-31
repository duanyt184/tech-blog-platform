<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getArticle, reviewArticle } from '../api/articles'
import { useAuth } from '../composables/useAuth'
import ArticleBody from '../components/ArticleBody.vue'
import ReviewHistory from '../components/ReviewHistory.vue'
import { errorMessage, statusLabels } from '../utils/articles'
import type { Article } from '../types/article'
const route = useRoute()
const { user } = useAuth()
const article = ref<Article | null>(null)
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const notice = ref('')
const comment = ref('')
let requestId = 0
async function load() {
  const request = ++requestId
  loading.value = true
  error.value = ''
  article.value = null
  notice.value = ''
  comment.value = ''
  try {
    const data = await getArticle(String(route.params.id), 'editor')
    if (request === requestId) article.value = data
  } catch (reason) { if (request === requestId) error.value = errorMessage(reason) }
  finally { if (request === requestId) loading.value = false }
}
watch(() => route.params.id, load, { immediate: true })
onBeforeUnmount(() => requestId++)
async function decide(decision: 'APPROVE' | 'REJECT') {
  if (busy.value || !article.value) return
  if (decision === 'REJECT' && !comment.value.trim()) { error.value = '请填写具体修改意见，再退回文章'; return }
  const current = requestId
  busy.value = true
  error.value = ''
  try {
    const data = await reviewArticle(article.value, decision, comment.value.trim())
    if (current !== requestId) return
    article.value = data
    notice.value = decision === 'APPROVE' ? '审核通过，文章已公开发布' : '已退回，作者可以查看修改意见'
  } catch (reason) { if (current === requestId) error.value = errorMessage(reason) }
  finally { busy.value = false }
}
</script>
<template>
  <div class="work-page review-page">
    <header class="work-heading"><h1>投稿审核</h1><RouterLink to="/editor" class="tb-btn">返回队列</RouterLink></header>
    <p v-if="loading" class="empty-state" role="status">正在加载投稿…</p>
    <div v-if="error" class="error-message" role="alert"><p>{{ error }}</p><button class="tb-btn" :disabled="busy" @click="load">重新加载最新版本</button></div>
    <p v-if="notice" class="success-message" role="status">{{ notice }}</p>
    <template v-if="article && !loading">
      <header class="article-header"><span class="status-badge" :data-status="article.status">{{ statusLabels[article.status] }}</span><h1>{{ article.title }}</h1><p>{{ article.author }} · {{ article.category }} · 版本 {{ article.version }}</p><p class="article-deck">{{ article.summary }}</p></header>
      <ArticleBody :content="article.content || ''" :content-format="article.contentFormat" />
      <section v-if="article.status === 'PENDING'" class="review-decision">
        <p v-if="article.authorId === user?.id" class="workflow-notice">这是你自己的投稿，需要由其他编辑审核。</p>
        <template v-else><label>审核意见（退回时必填）<textarea v-model="comment" rows="4" maxlength="2000" :disabled="busy" placeholder="请给作者具体、可执行的建议"></textarea></label><div class="row-actions"><button class="tb-btn" :disabled="busy" @click="decide('REJECT')">退回修改</button><button class="tb-btn tb-btn--primary" :disabled="busy" @click="decide('APPROVE')">{{ busy ? '正在处理…' : '审核通过并发布' }}</button></div></template>
      </section>
      <ReviewHistory :events="article.reviews || []" />
    </template>
  </div>
</template>
