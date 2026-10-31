<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '../composables/useAuth'
import AppIcon from './AppIcon.vue'
defineProps<{ menuOpen: boolean }>()
defineEmits<{ toggleMenu: [] }>()
const route = useRoute()
const router = useRouter()
const { user, isAuthenticated, logoutUser } = useAuth()
const query = ref('')
const loggingOut = ref(false)
const logoutError = ref('')
watch(() => route.query.q, value => { query.value = typeof value === 'string' ? value : '' }, { immediate: true })
function search() { void router.push({ path: '/articles', query: query.value.trim() ? { q: query.value.trim() } : {} }) }
async function handleLogout() {
  loggingOut.value = true
  logoutError.value = ''
  try {
    await logoutUser()
    if (route.meta.requiresAuth) await router.replace('/')
  } catch { logoutError.value = '退出请求失败，请稍后重试。' }
  finally { loggingOut.value = false }
}
</script>
<template>
  <header class="site-header">
    <div class="header-inner">
      <button class="icon-button mobile-menu" type="button" :aria-expanded="menuOpen" aria-controls="site-sidebar" aria-label="切换导航" @click="$emit('toggleMenu')">
        <AppIcon :name="menuOpen ? 'close' : 'menu'" />
      </button>
      <RouterLink to="/" class="brand" aria-label="码上首页">
        <span class="brand-mark">
          <AppIcon name="code" :size="24" />
        </span>
        <strong>码上<span class="brand-dot">.</span>
        </strong>
        <span class="brand-caption">技术因分享而生长</span>
      </RouterLink>
      <form class="header-search" role="search" @submit.prevent="search">
        <AppIcon name="search" :size="18" />
        <input v-model="query" type="search" aria-label="搜索文章" placeholder="搜索文章、标签或作者…" />
        <button type="submit" aria-label="提交搜索">搜索</button>
      </form>
      <div class="header-actions">
        <RouterLink to="/write" class="header-write">
          <AppIcon name="edit" :size="17" />
          <span>写文章</span>
        </RouterLink>
        <template v-if="isAuthenticated">
          <span class="header-username">{{ user?.username }}</span>
          <button class="tb-btn" :disabled="loggingOut" @click="handleLogout">{{ loggingOut ? '退出中…' : '退出' }}</button>
        </template>
        <template v-else>
          <RouterLink to="/login" class="login-link">登录</RouterLink>
          <RouterLink to="/register" class="tb-btn tb-btn--primary">注册</RouterLink>
        </template>
      </div>
    </div>
    <p v-if="logoutError" class="header-error" role="alert">{{ logoutError }}</p>
  </header>
</template>
