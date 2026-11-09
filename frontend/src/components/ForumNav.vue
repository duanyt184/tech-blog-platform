<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { categories } from '../utils/articles'
const route = useRoute(),
  router = useRouter()
function category(event: Event) {
  void router.push({
    path: '/articles',
    query: { category: (event.target as HTMLSelectElement).value || undefined },
  })
}
</script>
<template>
  <div class="forum-nav">
    <select
      :value="route.query.category || ''"
      aria-label="Filter by category"
      @change="category"
    >
      <option value="">Categories</option>
      <option v-for="c in categories" :key="c.id" :value="c.name">
        {{ c.parentId ? '↳ ' : '' }}{{ c.name }}
      </option></select
    ><RouterLink to="/tags" class="tb-btn">Tags ›</RouterLink>
    <nav class="forum-tabs">
      <RouterLink
        to="/categories"
        :class="{ active: ['/', '/categories'].includes(route.path) }"
        >Categories</RouterLink
      ><RouterLink to="/top" :class="{ active: route.path === '/top' }"
        >Top</RouterLink
      ><RouterLink
        to="/latest"
        :class="{
          active: route.path === '/latest' || route.path === '/articles',
        }"
        >Latest</RouterLink
      >
    </nav>
    <RouterLink
      :to="{
        path: '/write',
        query: { category: route.query.category || undefined },
      }"
      class="tb-btn tb-btn--primary new-topic"
      >＋ New Topic</RouterLink
    >
  </div>
</template>
