<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<template>
  <section class="page-container">
    <div class="panel placeholder">
      <span class="eyebrow">MVP v0.1 · 功能预告</span>
      <h1>{{ route.meta.title }}</h1>
      <p>{{ route.meta.description }}</p>
      <RouterLink class="button" to="/">返回首页</RouterLink>
    </div>
  </section>
</template>
