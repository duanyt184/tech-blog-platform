<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '../composables/useAuth'
import type { UserRole } from '../types/auth'

/**
 * 顶部导航。登录态由 useAuth 提供：
 * 未登录显示「登录 / 注册」，登录后显示用户名与「退出登录」。
 */
const router = useRouter()
const route = useRoute()
const { user, isAuthenticated, logoutUser } = useAuth()

const menuOpen = ref(false)
const loggingOut = ref(false)

const roleLabel = computed(() => {
  const labels: Record<UserRole, string> = { USER: '读者', EDITOR: '编辑', ADMIN: '管理员' }
  return user.value ? labels[user.value.role] : ''
})

const navLinks = [
  { to: '/', label: '首页' },
  { to: '/articles', label: '文章广场' },
  { to: '/write', label: '写文章' },
  { to: '/my-articles', label: '我的文章' },
]

function toggleMenu() {
  menuOpen.value = !menuOpen.value
}

function closeMenu() {
  menuOpen.value = false
}

async function handleLogout() {
  if (loggingOut.value) {
    return
  }
  loggingOut.value = true
  try {
    await logoutUser()
    closeMenu()
    // 当前页面需要登录态时回到首页，否则留在原地即可
    if (route.meta.requiresAuth) {
      await router.replace('/')
    }
  } finally {
    loggingOut.value = false
  }
}
</script>

<template>
  <header class="site-header">
    <div class="page-container header-inner">
      <RouterLink to="/" class="brand" @click="closeMenu">
        <span class="brand__mark">码</span>
        <span class="brand__text">
          <strong>码上</strong>
          <small>技术分享社区</small>
        </span>
      </RouterLink>

      <button
        type="button"
        class="menu-toggle"
        :aria-expanded="menuOpen"
        aria-label="展开导航"
        @click="toggleMenu"
      >
        <span></span><span></span><span></span>
      </button>

      <nav class="header-nav" :class="{ 'is-open': menuOpen }">
        <RouterLink
          v-for="link in navLinks"
          :key="link.to"
          :to="link.to"
          class="nav-link"
          @click="closeMenu"
        >
          {{ link.label }}
        </RouterLink>
      </nav>

      <div class="header-auth" :class="{ 'is-open': menuOpen }">
        <template v-if="isAuthenticated">
          <span class="auth-user">
            <span class="auth-user__name">{{ user?.username }}</span>
            <span class="auth-user__role">{{ roleLabel }}</span>
          </span>
          <button type="button" class="tb-btn tb-btn--ghost" :disabled="loggingOut" @click="handleLogout">
            {{ loggingOut ? '退出中…' : '退出登录' }}
          </button>
        </template>
        <template v-else>
          <RouterLink to="/login" class="tb-btn tb-btn--ghost" @click="closeMenu">登录</RouterLink>
          <RouterLink to="/register" class="tb-btn tb-btn--primary" @click="closeMenu">注册</RouterLink>
        </template>
      </div>
    </div>
  </header>
</template>

<style scoped>
.site-header {
  position: sticky;
  top: 0;
  z-index: 20;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--tb-border);
}

.header-inner {
  display: flex;
  align-items: center;
  gap: 20px;
  height: 64px;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.brand__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: var(--tb-primary);
  color: #ffffff;
  font-weight: 700;
}

.brand__text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.brand__text strong {
  font-size: 16px;
}

.brand__text small {
  font-size: 11px;
  color: var(--tb-muted);
}

.header-nav {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: 8px;
  flex: 1;
}

.nav-link {
  padding: 7px 12px;
  border-radius: 999px;
  font-size: 14px;
  color: var(--tb-muted);
  transition: background 0.16s ease, color 0.16s ease;
}

.nav-link:hover {
  background: var(--tb-primary-soft);
  color: var(--tb-primary);
}

.nav-link.router-link-active {
  background: var(--tb-primary-soft);
  color: var(--tb-primary);
  font-weight: 600;
}

.header-auth {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.auth-user {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-right: 4px;
}

.auth-user__name {
  font-weight: 600;
  font-size: 14px;
}

.auth-user__role {
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--tb-primary-soft);
  color: var(--tb-primary);
  font-size: 11px;
  font-weight: 600;
}

.menu-toggle {
  display: none;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
  width: 38px;
  height: 38px;
  margin-left: auto;
  padding: 0 8px;
  border: 1px solid var(--tb-border);
  border-radius: 10px;
  background: var(--tb-surface);
  cursor: pointer;
}

.menu-toggle span {
  display: block;
  height: 2px;
  border-radius: 2px;
  background: var(--tb-text);
}

@media (max-width: 900px) {
  .header-inner {
    flex-wrap: wrap;
    height: auto;
    padding-top: 12px;
    padding-bottom: 12px;
  }

  .menu-toggle {
    display: flex;
  }

  .header-nav,
  .header-auth {
    display: none;
    width: 100%;
  }

  .header-nav.is-open,
  .header-auth.is-open {
    display: flex;
  }

  .header-nav {
    flex-direction: column;
    align-items: stretch;
    gap: 2px;
    margin-left: 0;
    order: 3;
  }

  .nav-link {
    padding: 10px 12px;
    border-radius: 10px;
  }

  .header-auth {
    order: 4;
    padding-top: 8px;
    border-top: 1px solid var(--tb-border);
    justify-content: flex-start;
  }
}
</style>
