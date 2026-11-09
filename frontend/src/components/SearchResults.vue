<script setup lang="ts">
import UserAvatar from './UserAvatar.vue'
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import type { Article } from '../types/article'
import { http } from '../api/http'
import {
  categories,
  categoryColor,
  errorMessage,
  relativeTime,
} from '../utils/articles'
const props = defineProps<{ items: Article[] }>()
const route = useRoute(),
  users = ref<any[]>([]),
  tags = ref<any[]>([]),
  error = ref(''),
  loading = ref(false)
const kind = computed(() => String(route.query.type || 'posts'))
const query = computed(() => String(route.query.q || '').toLowerCase())
const matchedCategories = computed(() =>
  categories.filter((c) => c.name.toLowerCase().includes(query.value)),
)
const matchedTags = computed(() =>
  tags.value.filter((t) => t.name.toLowerCase().includes(query.value)),
)
let request = 0
watch(
  () => [route.query.type, route.query.q],
  async () => {
    const current = ++request
    error.value = ''
    loading.value = true
    try {
      if (kind.value === 'users') {
        const data = (
          await http.get('/community/users', {
            params: { q: route.query.q || '' },
          })
        ).data
        if (current === request) users.value = data
      } else if (kind.value === 'taxonomy') {
        const data = (await http.get('/community/tags')).data
        if (current === request) tags.value = data
      }
    } catch (e) {
      if (current === request) error.value = errorMessage(e)
    } finally {
      if (current === request) loading.value = false
    }
  },
  { immediate: true },
)
</script>
<template>
  <p v-if="error" class="error-message" role="alert">{{ error }}</p>
  <p v-if="loading" class="empty-state">Loading…</p>
  <template v-else-if="kind === 'users'"
    ><article v-for="u in users" :key="u.id" class="search-result">
      <RouterLink :to="`/u/${u.id}`" class="avatar"
        ><UserAvatar :user-id="u.id" :username="u.username"
      /></RouterLink>
      <div>
        <RouterLink :to="`/u/${u.id}`"
          ><h2>{{ u.displayName || u.username }}</h2></RouterLink
        >
        <p>@{{ u.username }}</p>
        <p>{{ u.bio }}</p>
      </div>
    </article>
    <p v-if="!users.length" class="empty-state">No users found.</p></template
  ><template v-else-if="kind === 'taxonomy'"
    ><h2>Categories</h2>
    <article v-for="c in matchedCategories" :key="c.id" class="search-result">
      <span class="category-square" :style="{ background: c.color }"></span>
      <div>
        <RouterLink :to="{ path: '/articles', query: { category: c.name } }"
          ><h2>{{ c.name }}</h2></RouterLink
        >
        <p>{{ c.description }}</p>
      </div>
    </article>
    <h2>Tags</h2>
    <div class="tag-directory">
      <RouterLink
        v-for="tag in matchedTags"
        :key="tag.name"
        :to="{ path: '/articles', query: { tag: tag.name } }"
        class="tag-directory-item"
        >{{ tag.name }} × {{ tag.count }}</RouterLink
      >
    </div></template
  ><template v-else
    ><article v-for="a in props.items" :key="a.id" class="search-result">
      <RouterLink
        :to="`/u/${a.authorId}`"
        class="avatar"
        :style="{ background: categoryColor(a.category) }"
        ><UserAvatar :user-id="a.authorId" :username="a.author"
      /></RouterLink>
      <div>
        <RouterLink :to="`/t/${a.id}`"
          ><h2>{{ a.title }}</h2></RouterLink
        >
        <div class="topic-taxonomy">
          <RouterLink
            :to="{ path: '/articles', query: { category: a.category } }"
            ><span
              class="category-square"
              :style="{ background: categoryColor(a.category) }"
            ></span
            >{{ a.category }}</RouterLink
          ><RouterLink
            v-for="tag in a.tags"
            :key="tag"
            :to="{ path: '/articles', query: { tag } }"
            >{{ tag }}</RouterLink
          >
        </div>
        <p class="search-excerpt">
          {{ relativeTime(a.updatedAt) }} ·
          {{ a.summary || 'Posted by ' + a.author }} ·
          {{ a.replyCount }} replies
        </p>
      </div>
    </article>
    <p v-if="!props.items.length" class="empty-state">
      No topics found. Try a different search.
    </p></template
  >
</template>
