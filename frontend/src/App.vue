<script setup lang="ts">
import { computed, defineAsyncComponent, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter, type RouteLocationNormalizedLoaded } from 'vue-router'
import ForumPage from './components/ForumPage.vue'
const ArticlesView = defineAsyncComponent(() => import('./views/ArticlesView.vue'))
import AppHeader from './components/AppHeader.vue'
import AppSidebar from './components/AppSidebar.vue'
import { useTheme } from './composables/useTheme'
useTheme()
const route = useRoute()
const router = useRouter()
const composing = computed(() => route.name === 'write')
const fallback = router.resolve('/latest')
const background = shallowRef<RouteLocationNormalizedLoaded>({
  ...fallback,
  name: fallback.name ?? undefined,
  matched: fallback.matched.map(record => ({ ...record, components: { default: ArticlesView } })),
})
watch(() => route.fullPath, () => {
  if (!composing.value) background.value = { ...route }
}, { immediate: true })
const menuOpen = ref(window.innerWidth > 900)
</script>
<template>
  <div
    class="app-shell"
    :class="{ 'sidebar-open': menuOpen }"
  >
    <a class="skip-link" href="#main-content">跳转到内容</a>
    <AppHeader :menu-open="menuOpen" @toggle-menu="menuOpen = !menuOpen" />
    <div class="workspace" :class="{ 'nav-expanded': menuOpen }">
      <button
        type="button"
        class="sidebar-backdrop"
        :disabled="!menuOpen"
        :aria-hidden="!menuOpen"
        :tabindex="menuOpen ? 0 : -1"
        aria-label="关闭导航"
        @click="menuOpen = false"
      ></button>
      <AppSidebar :open="menuOpen" />
      <main id="main-content" class="app-main" tabindex="-1">
        <ForumPage :route="background" />
        <RouterView v-if="composing" :key="String(route.params.id || 'new')" />
      </main>
    </div>
  </div>
</template>
