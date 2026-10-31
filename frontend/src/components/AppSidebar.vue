<script setup lang="ts">
import { useRoute } from 'vue-router'
import AppIcon from './AppIcon.vue'
import { categories } from '../utils/articles'
import { useAuth } from '../composables/useAuth'
const { isEditor } = useAuth()
const route = useRoute()
defineProps<{ open: boolean }>()
defineEmits<{ close: [] }>()
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
      <p class="nav-section-label">技术分类 <span>{{ categories.length }}</span>
      </p>
      <RouterLink v-for="category in categories" :key="category.name" :to="{ path: '/articles', query: { category: category.name } }" class="sidebar-link category-nav" :class="{ selected: route.query.category === category.name }">
        <span class="category-square" :style="{ background: category.color }">
        </span>{{ category.name }}</RouterLink>
      <div class="nav-divider">
      </div>
      <p class="nav-section-label">创作空间</p>
      <RouterLink to="/write" class="sidebar-link" active-class="selected">
        <AppIcon name="edit" :size="18" />写文章</RouterLink>
      <RouterLink to="/my-articles" class="sidebar-link" active-class="selected">
        <AppIcon name="file" :size="18" />我的文章</RouterLink>
      <RouterLink v-if="isEditor" to="/editor" class="sidebar-link" active-class="selected">
        <AppIcon name="check" :size="18" />审核工作台
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
