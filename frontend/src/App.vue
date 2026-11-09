<script setup lang="ts">
import { ref } from 'vue'
import AppHeader from './components/AppHeader.vue'
import AppSidebar from './components/AppSidebar.vue'
import { useTheme } from './composables/useTheme'
useTheme()
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
        <RouterView />
      </main>
    </div>
  </div>
</template>
