<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'
import PagePagination from './PagePagination.vue'
import { categories, categoryColor, errorMessage, formatCount, formatDate } from '../utils/articles'
import { useArticlePage } from '../composables/useArticlePage'
import { getCategoryStats, setBookmark } from '../api/articles'
import { useAuth } from '../composables/useAuth'
import type { Article } from '../types/article'
const route = useRoute()
const router = useRouter()
const { user } = useAuth()
const category = computed(() => String(route.query.category || ''))
const tag = computed(() => String(route.query.tag || ''))
const query = computed(() => String(route.query.q || '').trim())
const saved = computed(() => route.path === '/bookmarks')
const sort = computed(() => route.query.sort === 'popular' ? 'popular' : 'latest')
const { result, loading, error, reload } = useArticlePage(() => ({
  scope: saved.value ? 'bookmarks' : 'public',
  params: { category: category.value, tag: tag.value, q: query.value, sort: sort.value,
    // 一页 4 篇：不滚动就能看完当前页，更多内容通过翻页浏览
    page: Number(route.query.page || 0), size: 4 },
}))
// 欢迎横幅用「全站已发布文章数」判断：一篇都没发过才展示，发过就永久收起
const hasPublished = ref(true)
onMounted(async () => {
  try {
    const stats = await getCategoryStats()
    hasPublished.value = stats.total > 0
  } catch {
    // 统计失败时保持隐藏，避免在已有内容的站点上闪出横幅
  }
})

const articles = computed(() => result.value.items)
const heading = computed(() => saved.value ? '我的收藏' : category.value || '文章广场')
// 快捷收藏：未登录先送去登录页，收藏/取消成功后本地即时更新数字
const bookmarkBusy = ref(0)
const actionError = ref('')
async function toggleBookmark(article: Article) {
  if (!user.value) {
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (bookmarkBusy.value) {
    return
  }
  bookmarkBusy.value = article.id
  actionError.value = ''
  const next = !article.bookmarked
  try {
    await setBookmark(article.id, next)
    article.bookmarked = next
    article.bookmarkCount = Math.max(0, (article.bookmarkCount || 0) + (next ? 1 : -1))
    // 热门排序依赖收藏数，收藏变化后重新拉取当前页，避免顺序停留在旧值
    if (sort.value === 'popular') {
      await reload()
    }
  } catch (reason) {
    actionError.value = errorMessage(reason)
  } finally {
    bookmarkBusy.value = 0
  }
}
function updateFilter(key: string, value: string) {
  void router.push({ query: { ...route.query, page: undefined, [key]: value || undefined } })
}
function changePage(page: number) { void router.push({ query: { ...route.query, page: page || undefined } }) }
</script>
<template>
  <div class="list-page">
    <div class="page-kicker">
      <span class="status-dot">
      </span>分享 · 记录 · 一起成长
    </div>
    <section v-if="!hasPublished" class="welcome-banner">
      <div>
        <p class="eyebrow">HELLO, BUILDERS</p>
        <h1>好想法，值得被看见<span>。</span>
        </h1>
        <p class="welcome-description">从一行代码到一次突破，在这里分享你的技术探索。</p>
        <RouterLink to="/write" class="welcome-link">写下你的第一篇文章 <span>↗</span>
        </RouterLink>
      </div>
      <div class="banner-art" aria-hidden="true">
        <span class="art-small">{ }</span>
        <span class="art-code">&lt;/&gt;</span>
        <span class="art-star">✳</span>
        <span class="art-line">
        </span>
        <span class="art-label">ideas → words → impact</span>
      </div>
    </section>
    <div class="list-heading">
      <div>
        <h2>{{ heading }}</h2>
        <span>每一份经验，都能成为别人的起点</span>
      </div>
      <RouterLink to="/write" class="tb-btn tb-btn--primary">
        <AppIcon name="edit" :size="17" />写文章</RouterLink>
    </div>
    <div class="list-toolbar">
      <div class="sort-tabs" aria-label="文章排序">
        <button :class="{ active: sort === 'latest' }" :aria-pressed="sort === 'latest'" @click="updateFilter('sort', '')">最新发布</button>
        <button :class="{ active: sort === 'popular' }" :aria-pressed="sort === 'popular'" @click="updateFilter('sort', 'popular')">热门文章</button>
      </div>
      <label class="category-select">
        <span class="sr-only">筛选分类</span>
        <select :value="category" @change="updateFilter('category', ($event.target as HTMLSelectElement).value)">
          <option value="">全部分类</option>
          <option v-for="item in categories" :key="item.name">{{ item.name }}</option>
        </select>
      </label>
    </div>
    <div v-if="query || tag || saved" class="filter-summary">
      <span v-if="query">搜索「{{ query }}」</span>
      <span v-if="tag">标签：{{ tag }}</span>
      <span v-if="saved">已收藏的公开文章会显示在这里</span>
      <RouterLink to="/articles">清除筛选 <AppIcon name="close" :size="13" />
      </RouterLink>
    </div>
    <p v-if="actionError" class="error-message" role="alert">{{ actionError }}</p>
    <div v-if="loading" class="empty-state" role="status">正在加载文章…</div>
    <div v-else-if="error" class="empty-state" role="alert"><p>{{ error }}</p><button class="tb-btn" @click="reload">重试</button></div>
    <div v-else-if="articles.length" class="article-list">
      <article v-for="article in articles" :key="article.id" class="article-row">
        <div class="article-row-main">
          <div class="article-title-line">
            <RouterLink :to="`/article/${article.id}`" class="article-title">{{ article.title }}</RouterLink>
          </div>
          <p class="article-summary">{{ article.summary }}</p>
          <div class="article-taxonomy">
            <RouterLink :to="{ path: '/articles', query: { category: article.category } }" class="category-link">
              <span class="category-square" :style="{ background: categoryColor(article.category) }">
              </span>{{ article.category }}</RouterLink>
            <RouterLink v-for="item in article.tags" :key="item" :to="{ path: '/articles', query: { tag: item } }" class="article-tag">{{ item }}</RouterLink>
          </div>
          <div class="article-meta">
            <span class="meta-author">
              <span class="avatar">{{ article.author.slice(0, 1) }}</span>{{ article.author }}
            </span>
            <span class="meta-item" title="阅读数">
              <AppIcon name="eye" :size="14" />{{ formatCount(article.viewCount) }}
            </span>
            <span class="meta-item" title="收藏数">
              <AppIcon name="star" :size="14" />{{ formatCount(article.bookmarkCount || 0) }}
            </span>
            <time class="meta-item" :datetime="article.publishedAt || undefined">{{ formatDate(article.publishedAt) }}</time>
          </div>
        </div>
        <button
          type="button"
          class="bookmark-button"
          :class="{ 'is-active': article.bookmarked }"
          :aria-pressed="!!article.bookmarked"
          :aria-label="article.bookmarked ? '取消收藏' : '收藏文章'"
          :title="article.bookmarked ? '取消收藏' : '收藏文章'"
          :disabled="bookmarkBusy === article.id"
          @click="toggleBookmark(article)"
        >
          <AppIcon name="star" :size="15" />
          {{ bookmarkBusy === article.id ? '保存中…' : article.bookmarked ? '已收藏' : '收藏' }}
        </button>
      </article>
    </div>
    <div v-else class="empty-state">
      <AppIcon :name="saved ? 'bookmark' : 'search'" :size="32" />
      <h3>{{ saved ? '还没有收藏的文章' : '没有找到相关文章' }}</h3>
      <p>{{ saved ? '打开一篇文章，点击收藏，留给下次慢慢读。' : '试试其他关键词，或清除筛选看看全部文章；暂时没有内容时也可以开始创作。' }}</p>
      <RouterLink to="/articles" class="tb-btn">浏览全部文章</RouterLink>
    </div>
    <PagePagination v-if="!error" :page="result.page" :pages="result.totalPages" :total="result.totalElements" :disabled="loading" @change="changePage" />
    <section class="contribute-note">
      <div class="contribute-icon">
        <AppIcon name="book" :size="24" />
      </div>
      <div>
        <h3>你的下一次分享，可能正是别人需要的答案。</h3>
        <p>记录实践、整理笔记，让经验成为可以传递的知识。</p>
      </div>
      <RouterLink to="/write">开始创作 <span>↗</span>
      </RouterLink>
    </section>
    <footer class="page-footer">
      <span>© 2026 码上 · 让技术分享更有价值</span>
      <span>记录实践，分享知识</span>
    </footer>
  </div>
</template>
