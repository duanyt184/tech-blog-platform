<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '../composables/useAuth'
import AppIcon from './AppIcon.vue'
defineProps<{ menuOpen: boolean }>()
defineEmits<{ toggleMenu: [] }>()
const route = useRoute(),
  router = useRouter(),
  { user, logoutUser } = useAuth()
const query = ref(''),
  accountOpen = ref(false),
  error = ref('')
watch(
  () => route.fullPath,
  () => {
    query.value = String(route.query.q || '')
    accountOpen.value = false
  },
  { immediate: true },
)
function search() {
  void router.push({ path: '/search', query: { q: query.value || undefined } })
}
function advancedSearch() {
  void router.push({
    path: '/search',
    query: { q: query.value || undefined, expanded: 'true' },
  })
}
async function logout() {
  try {
    await logoutUser()
    accountOpen.value = false
    await router.push('/')
  } catch {
    error.value = 'Unable to log out. Please try again.'
  }
}
</script>
<template>
  <header class="site-header">
    <div class="header-inner">
      <button
        class="icon-button mobile-menu"
        :aria-expanded="menuOpen"
        aria-controls="site-sidebar"
        aria-label="Toggle navigation"
        @click="$emit('toggleMenu')"
      >
        <AppIcon name="menu" :size="25" />
      </button>
      <RouterLink to="/" class="brand" aria-label="码上社区首页"
        ><svg
          class="brand-mark"
          viewBox="0 0 64 64"
          width="26"
          height="26"
          aria-hidden="true"
        >
          <rect width="64" height="64" rx="14" fill="currentColor" />
          <path
            fill="none"
            stroke="var(--tb-bg)"
            stroke-width="4"
            stroke-linecap="round"
            stroke-linejoin="round"
            d="m21 24-8 8 8 8M43 24l8 8-8 8M37 18 27 46"
          /></svg
        ><strong>码上</strong></RouterLink
      >
      <form class="header-search" role="search" @submit.prevent="search">
        <button
          class="mobile-search-trigger"
          type="button"
          aria-label="Open search"
          @click="router.push('/search')"
        >
          <AppIcon name="search" :size="18" /></button
        ><input
          v-model="query"
          aria-label="Search the forum"
          placeholder="Search"
          maxlength="100"
        /><button
          aria-label="Advanced search"
          title="Advanced search"
          type="button"
          @click="advancedSearch"
        >
          <AppIcon name="sliders" :size="18" />
        </button>
      </form>
      <div class="header-actions">
        <RouterLink to="/guidelines" class="header-pill desktop-only"
          >Forum Guidelines</RouterLink
        ><template v-if="!user"
          ><RouterLink to="/register" class="header-pill">Sign Up</RouterLink
          ><RouterLink to="/login" class="header-pill"
            >Log In</RouterLink
          ></template
        >
        <div v-else class="account-menu">
          <button
            class="avatar"
            :aria-expanded="accountOpen"
            aria-label="Account menu"
            @click="accountOpen = !accountOpen"
          >
            {{ user.username.slice(0, 1).toUpperCase() }}
          </button>
          <nav
            v-if="accountOpen"
            class="account-popover"
            @keydown.esc="accountOpen = false"
          >
            <strong>{{ user.username }}</strong
            ><RouterLink :to="`/u/${user.id}`">Profile & activity</RouterLink
            ><RouterLink to="/my-articles">My Posts</RouterLink
            ><RouterLink to="/drafts">My Drafts</RouterLink
            ><RouterLink to="/bookmarks">Bookmarks</RouterLink
            ><RouterLink to="/settings">Preferences</RouterLink
            ><button @click="logout">Log Out</button>
          </nav>
        </div>
      </div>
    </div>
    <p v-if="error" role="alert">{{ error }}</p>
  </header>
</template>
