<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useArticlePage } from '../composables/useArticlePage'
import { deleteArticle, publishTopic } from '../api/articles'
import { errorMessage, relativeTime } from '../utils/articles'
import PagePagination from '../components/PagePagination.vue'
import type { Article } from '../types/article'
const route = useRoute(),
  router = useRouter(),
  drafts = computed(() => route.path === '/drafts')
const { result, loading, error, reload } = useArticlePage(() => ({
  scope: 'mine',
  params: {
    status: drafts.value ? 'DRAFT' : 'PUBLISHED',
    page: Number(route.query.page || 0),
    size: 20,
  },
}))
const busy = ref(false),
  actionError = ref('')
async function act(a: Article, remove = false) {
  if (busy.value) return
  if (remove && !window.confirm(`Delete “${a.title}”?`)) return
  busy.value = true
  actionError.value = ''
  try {
    if (remove) await deleteArticle(a)
    else await publishTopic(a)
    await reload()
  } catch (e) {
    actionError.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
function page(value: number) {
  void router.push({ query: { page: value || undefined } })
}
</script>
<template>
  <section class="work-page">
    <header class="work-heading">
      <h1>{{ drafts ? 'My Drafts' : 'My Posts' }}</h1>
      <RouterLink to="/write" class="tb-btn tb-btn--primary"
        >＋ New Topic</RouterLink
      >
    </header>
    <nav class="status-tabs">
      <RouterLink to="/my-articles" :class="{ active: !drafts }"
        >My Posts</RouterLink
      ><RouterLink to="/drafts" :class="{ active: drafts }"
        >My Drafts</RouterLink
      ><RouterLink to="/comments">My Replies</RouterLink>
    </nav>
    <p v-if="actionError || error" class="error-message" role="alert">
      {{ actionError || error }} <button @click="reload">Retry</button>
    </p>
    <p v-if="loading" class="empty-state">Loading…</p>
    <template v-else
      ><article v-for="a in result.items" :key="a.id" class="management-row">
        <div>
          <RouterLink :to="drafts ? `/write/${a.id}` : `/t/${a.id}`"
            ><h2>{{ a.title }}</h2></RouterLink
          >
          <p>
            {{ a.category }} · {{ drafts ? 'Draft saved' : 'Updated' }}
            {{ relativeTime(a.updatedAt) }}
          </p>
        </div>
        <div class="row-actions">
          <RouterLink :to="`/write/${a.id}`" class="tb-btn">Edit</RouterLink
          ><button
            v-if="drafts"
            class="tb-btn tb-btn--primary"
            :disabled="busy"
            @click="act(a)"
          >
            Publish</button
          ><button
            class="tb-btn danger-button"
            :disabled="busy"
            @click="act(a, true)"
          >
            Delete
          </button>
        </div>
      </article>
      <div v-if="!result.items.length && !error" class="empty-state">
        <h2>{{ drafts ? 'No drafts yet' : 'No posts yet' }}</h2>
        <p>
          {{
            drafts
              ? 'Your saved drafts are private until you publish them.'
              : 'Start a new conversation with the community.'
          }}
        </p>
        <RouterLink to="/write" class="tb-btn">＋ New Topic</RouterLink>
      </div></template
    ><PagePagination
      :page="result.page"
      :pages="result.totalPages"
      :total="result.totalElements"
      :disabled="loading"
      @change="page"
    />
  </section>
</template>
