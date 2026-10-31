<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from './components/AppHeader.vue'
import AppSidebar from './components/AppSidebar.vue'
const menuOpen = ref(false)
const route = useRoute()
watch(() => route.fullPath, () => { menuOpen.value = false })
</script>
<template>
  <div class="app-shell" @keydown.esc="menuOpen = false">
    <a class="skip-link" href="#main-content">跳转到内容</a>
    <AppHeader :menu-open="menuOpen" @toggle-menu="menuOpen = !menuOpen" />
    <div class="workspace">
      <button v-if="menuOpen" type="button" class="sidebar-backdrop" aria-label="关闭导航" @click="menuOpen = false">
      </button>
      <AppSidebar :open="menuOpen" @close="menuOpen = false" />
      <main id="main-content" class="app-main" tabindex="-1">
        <RouterView />
      </main>
    </div>
  </div>
</template>
