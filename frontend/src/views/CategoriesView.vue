<script setup lang="ts">
import UserAvatar from '../components/UserAvatar.vue'
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { categories, loadCategories, errorMessage } from '../utils/articles'
import { getCategoryStats, listArticles } from '../api/articles'
import type { Article } from '../types/article'
import ForumNav from '../components/ForumNav.vue'
import TopicTable from '../components/TopicTable.vue'
const route = useRoute(),
  counts = ref<Record<string, number>>({}),
  latest = ref<Article[]>([]),
  featured = ref<Article[]>([]),
  error = ref(''),
  loading = ref(true)
const parents = computed(() => categories.filter((c) => !c.parentId))
function count(id: number) {
  const c = categories.find((c) => c.id === id)
  return (
    (counts.value[c?.name || ''] || 0) +
    categories
      .filter((c) => c.parentId === id)
      .reduce((sum, c) => sum + (counts.value[c.name] || 0), 0)
  )
}
async function load() {
  loading.value = true
  error.value = ''
  try {
    await loadCategories()
    const [stats, topics, top] = await Promise.all([
      getCategoryStats(),
      listArticles('public', { size: 12 }),
      listArticles('public', { size: 3, sort: 'popular' }),
    ])
    counts.value = Object.fromEntries(
      stats.categories.map((c) => [c.name, c.count]),
    )
    latest.value = topics.items
    featured.value = top.items
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>
<template>
  <section class="forum-home">
    <section
      v-if="route.path === '/' && featured.length"
      class="featured-section"
    >
      <h2>Popular Posts</h2>
      <div class="featured-cards">
        <RouterLink
          v-for="a in featured"
          :key="a.id"
          :to="`/t/${a.id}`"
          class="featured-card"
          ><h3>{{ a.title }}</h3>
          <div>
            <span class="avatar"
              ><UserAvatar :user-id="a.authorId" :username="a.author" /></span
            ><span>{{ a.author }}</span>
          </div></RouterLink
        >
      </div>
    </section>
    <ForumNav />
    <p v-if="loading" class="empty-state" role="status">Loading community…</p>
    <div v-else-if="error" class="error-message" role="alert">
      {{ error }} <button class="tb-btn" @click="load">Retry</button>
    </div>
    <div v-else class="category-latest-grid">
      <section>
        <div class="column-label"><span>Category</span><span>Topics</span></div>
        <article
          v-for="c in parents"
          :key="c.id"
          class="category-entry"
          :style="{ '--category-color': c.color }"
        >
          <div class="category-entry-heading">
            <RouterLink :to="{ path: '/articles', query: { category: c.name } }"
              ><span
                class="category-square"
                :style="{ background: c.color }"
              ></span
              ><strong>{{ c.name }}</strong></RouterLink
            ><span>{{ count(c.id) }}</span>
          </div>
          <p>{{ c.description }}</p>
          <div class="subcategory-links">
            <RouterLink
              v-for="child in categories.filter(
                (child) => child.parentId === c.id,
              )"
              :key="child.id"
              :to="{ path: '/articles', query: { category: child.name } }"
              ><span
                class="category-square"
                :style="{ background: child.color }"
              ></span
              >{{ child.name }}</RouterLink
            >
          </div>
        </article>
      </section>
      <section>
        <div class="column-label"><span>Latest</span></div>
        <TopicTable :items="latest" compact />
        <div v-if="!latest.length" class="empty-state">
          <h3>Welcome to the community</h3>
          <p>
            Start a conversation. Ask a question. Share something you built.
          </p>
          <RouterLink to="/write" class="tb-btn tb-btn--primary"
            >＋ Create your first topic</RouterLink
          >
        </div>
        <RouterLink v-else to="/latest" class="more-topics"
          >More topics →</RouterLink
        >
      </section>
    </div>
  </section>
</template>
