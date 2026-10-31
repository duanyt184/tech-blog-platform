<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getArticle, setBookmark } from '../api/articles'
import { useAuth } from '../composables/useAuth'
import AppIcon from '../components/AppIcon.vue'
import ArticleBody from '../components/ArticleBody.vue'
import { renderArticleBody } from '../utils/articleBody'
import { categoryColor, errorMessage, formatCount, formatDate } from '../utils/articles'
import type { Article } from '../types/article'
const route = useRoute()
const router = useRouter()
const { user } = useAuth()
const article = ref<Article | null>(null)
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const busy = ref(false)
const activeSection = ref('')
const rendered = computed(() => renderArticleBody(article.value?.content || '', article.value?.contentFormat))
const headings = computed(() => rendered.value.headings)
let requestId = 0
async function load() {
  const request = ++requestId
  loading.value = true
  error.value = ''
  article.value = null
  actionError.value = ''
  try {
    const data = await getArticle(String(route.params.id))
    if (request === requestId) { article.value = data; activeSection.value = headings.value[0]?.id || '' }
  } catch (reason) { if (request === requestId) error.value = errorMessage(reason) }
  finally { if (request === requestId) loading.value = false }
}
watch([() => route.params.id, () => user.value?.id], load, { immediate: true })
onBeforeUnmount(() => requestId++)
async function toggleBookmark() {
  if (!user.value) { await router.push({ path: '/login', query: { redirect: route.fullPath } }); return }
  if (busy.value || !article.value) return
  const current = article.value
  busy.value = true
  actionError.value = ''
  try {
    await setBookmark(current.id, !current.bookmarked)
    if (article.value === current) current.bookmarked = !current.bookmarked
  } catch (reason) { actionError.value = errorMessage(reason) }
  finally { busy.value = false }
}
function jumpTo(id: string) {
  activeSection.value = id
  document.getElementById(id)?.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' })
  document.getElementById(id)?.focus({ preventScroll: true })
}
</script>
<template>
  <p v-if="loading" class="empty-state" role="status">正在加载文章…</p>
  <div v-else-if="error" class="empty-state" role="alert"><h1>暂时无法阅读这篇文章</h1><p>{{ error }}</p><button class="tb-btn" @click="load">重试</button><RouterLink to="/articles">返回文章广场</RouterLink></div>
  <div v-else-if="article" class="detail-page">
    <nav class="breadcrumbs" aria-label="面包屑"><RouterLink to="/articles"><AppIcon name="arrow" :size="16" />文章广场</RouterLink><AppIcon name="chevron" :size="12" /><RouterLink :to="{ path: '/articles', query: { category: article.category } }">{{ article.category }}</RouterLink></nav>
    <div class="detail-grid">
      <article class="article-content">
        <header class="article-header">
          <div class="article-taxonomy"><span class="category-link"><span class="category-square" :style="{ background: categoryColor(article.category) }"></span>{{ article.category }}</span></div>
          <h1>{{ article.title }}</h1><p class="article-deck">{{ article.summary }}</p>
          <div class="article-byline"><span class="avatar avatar-large">{{ article.author.slice(0, 1) }}</span><div><strong>{{ article.author }}</strong><p><time :datetime="article.publishedAt || undefined">{{ formatDate(article.publishedAt) }}</time></p></div><span class="detail-views"><AppIcon name="eye" :size="16" />{{ formatCount(article.viewCount) }}</span></div>
        </header>
        <ArticleBody :content="article.content || ''" :content-format="article.contentFormat" :rendered="rendered" />
        <div class="article-closing"><span>—</span><p>感谢阅读，愿这份记录对你有所启发。</p></div>
        <div class="article-bottom-actions"><div class="article-taxonomy"><RouterLink v-for="tag in article.tags" :key="tag" :to="{ path: '/articles', query: { tag } }" class="article-tag"># {{ tag }}</RouterLink></div><button class="tb-btn" :class="{ 'is-bookmarked': article.bookmarked }" :aria-pressed="!!article.bookmarked" :disabled="busy" @click="toggleBookmark"><AppIcon :name="article.bookmarked ? 'check' : 'bookmark'" :size="17" />{{ busy ? '正在保存…' : article.bookmarked ? '已收藏' : '收藏文章' }}</button></div>
        <p v-if="actionError" class="error-message" role="alert">{{ actionError }}</p>
        <p v-else class="local-storage-note" aria-live="polite">{{ article.bookmarked ? '已保存到你的账号，可在「我的收藏」中查看。' : '登录后可收藏文章，下次继续阅读。' }}</p>
        <div class="author-card"><span class="avatar avatar-large">{{ article.author.slice(0, 1) }}</span><div><small>本文作者</small><h3>{{ article.author }}</h3></div><AppIcon name="code" :size="30" /></div>
      </article>
      <aside class="reading-sidebar"><div class="reading-sidebar-inner"><p class="nav-section-label">本文目录</p><nav v-if="headings.length" aria-label="文章目录"><button v-for="heading in headings" :key="heading.id" :style="{ paddingLeft: `${12 + (heading.depth - 1) * 10}px` }" :class="{ active: activeSection === heading.id }" @click="jumpTo(heading.id)">{{ heading.text }}</button></nav><p v-else class="field-hint">本文没有分节标题</p><div class="reading-sidebar-divider"></div><p class="reading-sidebar-label">值得记录，也值得分享。</p><p class="reading-sidebar-copy">将你的技术实践整理成文章，<br />让更多人从中获得启发。</p><RouterLink to="/write" class="tb-btn"><AppIcon name="edit" :size="16" />开始写作</RouterLink></div></aside>
    </div>
    <footer class="page-footer"><span>© 2026 码上 · 让技术分享更有价值</span><RouterLink to="/articles">返回文章广场 ↑</RouterLink></footer>
  </div>
</template>
