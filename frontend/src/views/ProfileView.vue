<script setup lang="ts">
import UserAvatar from '../components/UserAvatar.vue'
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { http } from '../api/http'
import { useAuth } from '../composables/useAuth'
import { errorMessage, formatDate, relativeTime } from '../utils/articles'
import type { Article, PageResult } from '../types/article'
import TopicTable from '../components/TopicTable.vue'
import PagePagination from '../components/PagePagination.vue'
const route = useRoute(),
  router = useRouter(),
  { user } = useAuth()
const profile = ref<any>(),
  result = ref<PageResult<Article>>(),
  activity = ref<any[]>([]),
  summary = ref<any>(),
  error = ref(''),
  loading = ref(false)
let request = 0
async function load() {
  const current = ++request
  loading.value = true
  error.value = ''
  try {
    const [p, a, events, overview] = await Promise.all([
      http.get(`/profiles/${route.params.id}`),
      http.get(`/profiles/${route.params.id}/articles`, {
        params: { page: route.query.page || 0 },
      }),
      http.get(`/profiles/${route.params.id}/activity`, {
        params: { page: route.query.page || 0 },
      }),
      http.get(`/profiles/${route.params.id}/summary`),
    ])
    if (current === request) {
      profile.value = p.data
      result.value = a.data
      activity.value = events.data
      summary.value = overview.data
    }
  } catch (e) {
    if (current === request) error.value = errorMessage(e)
  } finally {
    if (current === request) loading.value = false
  }
}
watch(() => route.fullPath, load, { immediate: true })
function page(value: number) {
  void router.push({ query: { ...route.query, page: value || undefined } })
}
</script>
<template>
  <section class="work-page">
    <p v-if="loading" class="empty-state">Loading profile…</p>
    <p v-else-if="error" class="error-message">
      {{ error }} <button @click="load">Retry</button>
    </p>
    <template v-else-if="profile && result"
      ><header class="user-profile-header">
        <div class="avatar profile-avatar">
          <UserAvatar :user-id="profile.id" :username="profile.username" />
        </div>
        <div>
          <h1>{{ profile.username }}</h1>
          <h2>{{ profile.displayName }}</h2>
          <p>{{ profile.bio || 'Community member' }}</p>
          <div class="profile-stats">
            <span>Joined {{ formatDate(profile.joinedAt) }}</span
            ><span
              ><strong>{{ profile.articleCount }}</strong> topics</span
            ><span>{{
              profile.role === 'ADMIN'
                ? 'Administrator'
                : profile.role === 'EDITOR'
                  ? 'Moderator'
                  : 'Member'
            }}</span>
          </div>
        </div>
        <RouterLink v-if="user?.id === profile.id" to="/settings" class="tb-btn"
          >Preferences</RouterLink
        >
      </header>
      <nav class="status-tabs">
        <RouterLink :to="{ query: {} }" :class="{ active: !route.query.tab }"
          >Summary</RouterLink
        ><RouterLink
          :to="{ query: { tab: 'topics' } }"
          :class="{ active: route.query.tab === 'topics' }"
          >Topics</RouterLink
        ><RouterLink
          :to="{ query: { tab: 'activity' } }"
          :class="{ active: route.query.tab === 'activity' }"
          >Activity</RouterLink
        ><RouterLink v-if="user?.id === profile.id" to="/drafts"
          >Drafts</RouterLink
        ><RouterLink v-if="user?.id === profile.id" to="/bookmarks"
          >Bookmarks</RouterLink
        >
      </nav>
      <template v-if="route.query.tab === 'activity'"
        ><article
          v-for="event in activity"
          :key="`${event.type}-${event.topicId}-${event.commentId}`"
          class="activity-row"
        >
          <span>{{ event.type === 'TOPIC' ? '▤' : '↶' }}</span>
          <div>
            <small>{{
              event.type === 'TOPIC' ? 'Posted a topic' : 'Replied to'
            }}</small
            ><RouterLink
              :to="`/t/${event.topicId}${event.commentId ? '#comment-' + event.commentId : ''}`"
              ><h3>{{ event.title }}</h3></RouterLink
            >
          </div>
          <time>{{ relativeTime(event.createdAt) }}</time>
        </article>
        <p v-if="!activity.length" class="empty-state">
          No public activity yet.
        </p>
        <div class="pagination">
          <button
            class="tb-btn"
            :disabled="!Number(route.query.page || 0)"
            @click="page(Number(route.query.page || 0) - 1)"
          >
            Previous</button
          ><button
            class="tb-btn"
            :disabled="activity.length < 30"
            @click="page(Number(route.query.page || 0) + 1)"
          >
            Next
          </button>
        </div></template
      >
      <section v-else-if="!route.query.tab && summary" class="profile-summary">
        <h2>STATS</h2>
        <div class="summary-stats">
          <span
            ><strong>{{ summary.stats.topics }}</strong> topics created</span
          ><span
            ><strong>{{ summary.stats.replies }}</strong> replies posted</span
          ><span
            ><strong>{{ summary.stats.topicViews }}</strong> topic views</span
          ><span
            ><strong>{{ summary.stats.likesGiven }}</strong> likes given</span
          ><span
            ><strong>{{ summary.stats.likesReceived }}</strong> likes
            received</span
          >
        </div>
        <div class="profile-top-columns">
          <section>
            <h2>TOP REPLIES</h2>
            <RouterLink
              v-for="reply in summary.replies"
              :key="reply.id"
              :to="`/t/${reply.topicId}#comment-${reply.id}`"
              class="profile-top-item"
              ><small
                >{{ formatDate(reply.createdAt) }} · ♡ {{ reply.likes }}</small
              >
              <p>{{ reply.title }}</p></RouterLink
            >
            <p v-if="!summary.replies.length" class="field-hint">
              No public replies yet.
            </p>
          </section>
          <section>
            <h2>TOP TOPICS</h2>
            <RouterLink
              v-for="topic in summary.topics"
              :key="topic.id"
              :to="`/t/${topic.id}`"
              class="profile-top-item"
              ><small
                >{{ formatDate(topic.createdAt) }} · ♡ {{ topic.likes }}</small
              >
              <p>{{ topic.title }}</p></RouterLink
            >
            <p v-if="!summary.topics.length" class="field-hint">
              No public topics yet.
            </p>
          </section>
        </div>
      </section>
      <template v-else
        ><TopicTable v-if="result.items.length" :items="result.items" />
        <p v-else class="empty-state">No public topics yet.</p>
        <PagePagination
          :page="result.page"
          :pages="result.totalPages"
          :total="result.totalElements"
          :disabled="loading"
          @change="page" /></template
    ></template>
  </section>
</template>
