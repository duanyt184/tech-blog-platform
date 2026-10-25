<script setup lang="ts">
import AppHeader from './components/AppHeader.vue'
</script>

<template>
  <div class="app-shell">
    <AppHeader />
    <main class="app-main">
      <RouterView />
    </main>
    <footer class="site-footer">
      <div class="page-container footer-content">
        <div>
          <strong>码上</strong>
          <span>让每一次技术分享都值得被看见</span>
        </div>
        <span>MVP · 注册登录已就绪</span>
      </div>
    </footer>
  </div>
</template>

