<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppIcon from './AppIcon.vue'
import { categories } from '../utils/articles'
import { useAuth } from '../composables/useAuth'
import { getCategoryStats } from '../api/articles'
import type { CategoryStats } from '../types/article'
const { isEditor } = useAuth()
const route = useRoute()
defineProps<{ open: boolean }>()
defineEmits<{ close: [] }>()

// 分类右侧的数字来自公开统计接口，只统计已发布文章
const stats = ref<CategoryStats | null>(null)
let refreshTimer: number | undefined

const countOf = (name: string) => stats.value?.categories.find(item => item.name === name)?.count ?? 0

async function refreshStats() {
  try {
    stats.value = await getCategoryStats()
  } catch {
    // 统计失败不影响导航，保留上一次的数字
  }
}

function handleVisible() {
  if (document.visibilityState === 'visible') {
    void refreshStats()
  }
}

onMounted(() => {
  void refreshStats()
  // 别人发布文章后无需手动刷新：定时轮询 + 页面重新可见时立即拉取
  refreshTimer = window.setInterval(() => { void refreshStats() }, 60_000)
  document.addEventListener('visibilitychange', handleVisible)
})

onBeforeUnmount(() => {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
  }
  document.removeEventListener('visibilitychange', handleVisible)
})

// 站内跳转（投稿、审核通过后回到列表等）都会重新拉取，数字立刻更新
watch(() => route.fullPath, () => { void refreshStats() })
</script>
<template>
  <aside id="site-sidebar" class="site-sidebar" :class="{ 'is-open': open }" aria-label="主导航">
    <nav @click="$emit('close')">
      <p class="nav-section-label">探索</p>
      <RouterLink to="/" class="sidebar-link" :class="{ selected: (route.path === '/' || route.path === '/articles') && !route.query.category && !route.query.tag }">
        <AppIcon name="home" :size="18" />文章广场</RouterLink>
      <RouterLink to="/bookmarks" class="sidebar-link" :class="{ selected: route.path === '/bookmarks' }">
        <AppIcon name="bookmark" :size="18" />我的收藏
      </RouterLink>
      <div class="nav-divider">
      </div>
      <p class="nav-section-label">技术分类 <span>{{ stats ? stats.total : '—' }}</span>
      </p>
      <RouterLink v-for="category in categories" :key="category.name" :to="{ path: '/articles', query: { category: category.name } }" class="sidebar-link category-nav" :class="{ selected: route.query.category === category.name }">
        <span class="category-square" :style="{ background: category.color }">
        </span>{{ category.name }}
        <span v-if="stats" class="category-count">{{ countOf(category.name) }}</span>
      </RouterLink>
      <div class="nav-divider">
      </div>
      <p class="nav-section-label">创作空间</p>
      <RouterLink to="/write" class="sidebar-link" active-class="selected">
        <AppIcon name="edit" :size="18" />写文章</RouterLink>
      <RouterLink to="/my-articles" class="sidebar-link" active-class="selected">
        <AppIcon name="file" :size="18" />我的文章</RouterLink>
      <RouterLink to="/comments" class="sidebar-link" active-class="selected">
        <AppIcon name="comment" :size="18" />评论</RouterLink>
      <RouterLink v-if="isEditor" to="/editor" class="sidebar-link" active-class="selected">
        <AppIcon name="check" :size="18" />审核工作台
      </RouterLink>
      <RouterLink v-if="isEditor" to="/reports" class="sidebar-link" active-class="selected">
        <AppIcon name="flag" :size="18" />举报处理
      </RouterLink>
    </nav>
    <div class="sidebar-bottom">
      <span class="sidebar-motto">把经验写下来，<br />让知识流动起来。</span>
      <p>
        <span class="status-dot">
        </span>技术分享 · 投稿协作</p>
      <small>码上 / 技术分享与投稿协作</small>
    </div>
  </aside>
</template>
