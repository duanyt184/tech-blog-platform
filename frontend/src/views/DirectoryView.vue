<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { http } from '../api/http'
import { errorMessage } from '../utils/articles'
const route = useRoute(),
  items = ref<any[]>([]),
  error = ref(''),
  filter = ref(''),
  sort = ref('count'),
  loading = ref(false)
const isTags = computed(() => route.path === '/tags')
const grouped = computed(() => {
  const groups: Record<string, any[]> = {}
  for (const item of filtered.value) {
    ;(groups[item.groupName || 'Other Tags'] ||= []).push(item)
  }
  return groups
})
const filtered = computed(() =>
  items.value
    .filter((i) =>
      String(i.name || i.title)
        .toLowerCase()
        .includes(filter.value.toLowerCase()),
    )
    .sort((a, b) =>
      sort.value === 'name'
        ? a.name?.localeCompare(b.name)
        : Number(b.count) - Number(a.count),
    ),
)
async function load() {
  loading.value = true
  error.value = ''
  try {
    items.value = (
      await http.get(isTags.value ? '/community/tags' : '/community/rules')
    ).data
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}
watch(isTags, load, { immediate: true })
</script>
<template>
  <section class="work-page">
    <h1 class="page-title">{{ isTags ? 'Tags' : 'Community Guidelines' }}</h1>
    <div v-if="isTags" class="tag-sort">
      Sort by:
      <button @click="sort = 'count'" :class="{ active: sort === 'count' }">
        count
      </button>
      <button @click="sort = 'name'" :class="{ active: sort === 'name' }">
        name
      </button>
    </div>
    <p v-if="!isTags" class="page-intro">
      {{
        isTags
          ? 'Explore the conversations that interest you.'
          : 'A place for thoughtful, helpful conversations. Please follow these guidelines when participating.'
      }}
    </p>
    <input
      v-if="isTags"
      v-model="filter"
      class="directory-search"
      aria-label="Filter tags"
      placeholder="Filter tags"
    />
    <p v-if="loading" class="empty-state">Loading…</p>
    <p v-else-if="error" class="error-message">
      {{ error }} <button @click="load">Retry</button>
    </p>
    <div v-else-if="isTags" class="tag-groups">
      <section v-for="(group, name) in grouped" :key="name">
        <h2>{{ name }}</h2>
        <div class="tag-directory">
          <RouterLink
            v-for="item in group"
            :key="item.name"
            :to="{ path: '/articles', query: { tag: item.name } }"
            class="tag-directory-item"
            :title="item.description"
            >{{ item.name }} <small>× {{ item.count }}</small></RouterLink
          >
        </div>
      </section>
      <p v-if="!filtered.length" class="empty-state">No tags found.</p>
    </div>
    <div v-else :class="isTags ? 'tag-directory' : 'guidelines-list'">
      <template v-for="item in filtered" :key="item.name || item.id"
        ><RouterLink
          v-if="isTags"
          :to="{ path: '/articles', query: { tag: item.name } }"
          class="tag-directory-item"
          ><strong>{{ item.name }}</strong
          ><span>× {{ item.count }}</span>
          <p>{{ item.description }}</p></RouterLink
        >
        <article v-else>
          <h2>{{ item.title }}</h2>
          <p>{{ item.description }}</p>
        </article></template
      >
      <p v-if="!filtered.length" class="empty-state">Nothing here yet.</p>
    </div>
  </section>
</template>
