<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useArticlePage } from '../composables/useArticlePage'
import { categories } from '../utils/articles'
import SearchResults from './SearchResults.vue'
import TopicTable from './TopicTable.vue'
import ForumNav from './ForumNav.vue'
import PagePagination from './PagePagination.vue'
const route = useRoute(),
  router = useRouter()
const isSearch = computed(() => route.path === '/search'),
  saved = computed(() => route.path === '/bookmarks')
const form = reactive({
  q: '',
  author: '',
  after: '',
  before: '',
  category: '',
  tag: '',
  replies: 'all',
  sort: 'latest',
})
watch(
  () => route.query,
  () => {
    for (const key of Object.keys(form) as (keyof typeof form)[])
      form[key] = String(
        route.query[key] ||
          (key === 'replies' ? 'all' : key === 'sort' ? 'latest' : ''),
      )
  },
  { immediate: true },
)
const { result, loading, error, reload } = useArticlePage(() => ({
  scope: saved.value ? 'bookmarks' : 'public',
  params: {
    ...route.query,
    sort: route.path === '/top' ? 'popular' : route.query.sort || 'latest',
    page: Number(route.query.page || 0),
    size: 20,
  },
}))
function search() {
  void router.push({
    path: '/search',
    query: {
      ...Object.fromEntries(Object.entries(form).filter(([, v]) => v)),
      type: route.query.type,
    },
  })
}
function changePage(page: number) {
  void router.push({ query: { ...route.query, page: page || undefined } })
}
</script>
<template>
  <section class="list-page" :class="{ 'search-page': isSearch }">
    <h1 v-if="saved" class="page-title">Bookmarks</h1>
    <template v-if="isSearch"
      ><h1 class="sr-only">Search</h1>
      <nav class="status-tabs search-tabs">
        <RouterLink
          v-for="(label, key) in {
            posts: 'Posts',
            taxonomy: 'Categories & tags',
            users: 'Users',
          }"
          :key="key"
          :to="{ query: { ...route.query, type: key } }"
          :class="{ active: (route.query.type || 'posts') === key }"
          >{{ label }}</RouterLink
        >
      </nav>
      <form class="search-form" @submit.prevent="search">
        <div class="search-main">
          <input
            v-model="form.q"
            aria-label="Search keywords"
            placeholder="Search topics, replies, tags or users"
            maxlength="100"
          /><button class="tb-btn tb-btn--primary">Search</button>
        </div>
        <details
          v-if="!route.query.type || route.query.type === 'posts'"
          class="advanced-search"
          :open="route.query.expanded === 'true'"
        >
          <summary>Advanced filters</summary>
          <div class="search-filters">
            <label
              >Posted by<input
                v-model="form.author"
                placeholder="username"
                maxlength="50" /></label
            ><label
              >Category<select v-model="form.category">
                <option value="">All categories</option>
                <option v-for="c in categories" :key="c.id">
                  {{ c.name }}
                </option>
              </select></label
            ><label
              >Tag<input
                v-model="form.tag"
                maxlength="24"
                placeholder="tag" /></label
            ><label>After<input v-model="form.after" type="date" /></label
            ><label>Before<input v-model="form.before" type="date" /></label
            ><label
              >Replies<select v-model="form.replies">
                <option value="all">All topics</option>
                <option value="yes">With replies</option>
                <option value="no">Without replies</option>
              </select></label
            ><label
              >Sort by<select v-model="form.sort">
                <option value="latest">Latest activity</option>
                <option value="newest">Newest topics</option>
                <option value="popular">Most popular</option>
              </select></label
            >
          </div>
        </details>
      </form></template
    ><ForumNav v-else />
    <div v-if="route.query.category" class="category-heading">
      <h1>{{ route.query.category }}</h1>
      <p>
        {{
          categories.find((c) => c.name === route.query.category)?.description
        }}
      </p>
      <div class="subcategory-links">
        <RouterLink
          v-for="c in categories.filter(
            (c) =>
              c.parentId ===
              categories.find((p) => p.name === route.query.category)?.id,
          )"
          :key="c.id"
          :to="{ path: '/articles', query: { category: c.name } }"
          >{{ c.name }}</RouterLink
        >
      </div>
    </div>
    <div v-if="route.query.tag || route.query.q" class="filter-summary">
      <span
        >{{
          route.query.tag
            ? '#' + route.query.tag
            : 'Results for “' + route.query.q + '”'
        }}
        · {{ result.totalElements }} topics</span
      ><RouterLink to="/latest">Clear filters ×</RouterLink>
    </div>
    <p v-if="loading" class="empty-state" role="status">Loading topics…</p>
    <div v-else-if="error" class="error-message" role="alert">
      {{ error }} <button class="tb-btn" @click="reload">Retry</button>
    </div>
    <SearchResults v-else-if="isSearch" :items="result.items" /><TopicTable
      v-else-if="result.items.length"
      :items="result.items"
    />
    <div v-else class="empty-state">
      <h2>{{ saved ? 'No bookmarks yet' : 'No topics found' }}</h2>
      <p>
        {{
          saved
            ? 'Save a topic to come back to it later.'
            : 'Try a different filter, or start a new conversation.'
        }}
      </p>
      <RouterLink to="/write" class="tb-btn">＋ New Topic</RouterLink>
    </div>
    <PagePagination
      v-if="!isSearch || !route.query.type || route.query.type === 'posts'"
      :page="result.page"
      :pages="result.totalPages"
      :total="result.totalElements"
      :disabled="loading"
      @change="changePage"
    />
  </section>
</template>
