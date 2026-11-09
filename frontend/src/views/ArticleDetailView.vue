<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getArticle,
  setBookmark,
  listArticles,
  deleteArticle,
} from '../api/articles'
import { http } from '../api/http'
import { useAuth } from '../composables/useAuth'
import ArticleBody from '../components/ArticleBody.vue'
import ForumReplies from '../components/ForumReplies.vue'
import LikeButton from '../components/LikeButton.vue'
import TopicTable from '../components/TopicTable.vue'
import {
  categoryColor,
  errorMessage,
  relativeTime,
  formatDate,
} from '../utils/articles'
import type { Article } from '../types/article'
const route = useRoute(),
  router = useRouter(),
  { user } = useAuth()
const article = ref<Article>(),
  related = ref<Article[]>([]),
  loading = ref(false),
  error = ref(''),
  notice = ref(''),
  busy = ref(false),
  reporting = ref(false),
  reason = ref(''),
  quote = ref('')
let request = 0
async function load() {
  const current = ++request
  loading.value = true
  error.value = ''
  notice.value = ''
  article.value = undefined
  related.value = []
  try {
    const a = await getArticle(String(route.params.id))
    if (current !== request) return
    article.value = a
    const topics = await listArticles('public', {
      category: a.category,
      size: 6,
    })
    if (current === request)
      related.value = topics.items.filter((t) => t.id !== a.id).slice(0, 5)
  } catch (e) {
    if (current === request) error.value = errorMessage(e)
  } finally {
    if (current === request) loading.value = false
  }
}
watch(() => route.params.id, load, { immediate: true })
onBeforeUnmount(() => request++)
async function auth() {
  if (user.value) return true
  await router.push({ path: '/login', query: { redirect: route.fullPath } })
  return false
}
async function bookmark() {
  if (!(await auth()) || !article.value || busy.value) return
  busy.value = true
  try {
    await setBookmark(article.value.id, !article.value.bookmarked)
    article.value.bookmarked = !article.value.bookmarked
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
async function report() {
  if (!article.value || busy.value) return
  busy.value = true
  try {
    await http.post(`/articles/${article.value.id}/reports`, {
      reason: reason.value,
    })
    reporting.value = false
    notice.value = 'Thank you. Your report has been sent to the review queue.'
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
async function remove() {
  if (!article.value || !window.confirm('Delete this topic?')) return
  try {
    await deleteArticle(article.value)
    await router.push('/my-articles')
  } catch (e) {
    error.value = errorMessage(e)
  }
}
async function share() {
  try {
    await navigator.clipboard.writeText(window.location.href)
    notice.value = 'Topic link copied.'
  } catch {
    notice.value = window.location.href
  }
}
async function refreshCounts() {
  if (!article.value) return
  try {
    const { data } = await http.get(`/articles/${article.value.id}/stats`)
    article.value.replyCount = data.replies
    article.value.likeCount = data.likes
  } catch (e) {
    error.value = errorMessage(e)
  }
}
function reply(withQuote = false) {
  quote.value = withQuote
    ? `> ${article.value?.author} wrote:\n> ${(window.getSelection()?.toString() || article.value?.content || '').slice(0, 600).replace(/\n/g, '\n> ')}\n\n`
    : ''
  document
    .getElementById('reply-composer')
    ?.scrollIntoView({ behavior: 'smooth' })
}
</script>
<template>
  <p v-if="loading" class="empty-state">Loading topic…</p>
  <p v-if="error" class="error-message" role="alert">
    {{ error }} <button v-if="!article" @click="load">Retry</button>
  </p>
  <template v-if="article"
    ><header class="topic-header">
      <h1>{{ article.title }}</h1>
      <div class="topic-taxonomy">
        <RouterLink
          :to="{ path: '/articles', query: { category: article.category } }"
          ><span
            class="category-square"
            :style="{ background: categoryColor(article.category) }"
          ></span
          >{{ article.category }}</RouterLink
        ><RouterLink
          v-for="tag in article.tags"
          :key="tag"
          :to="{ path: '/articles', query: { tag } }"
          >{{ tag }}</RouterLink
        >
      </div>
    </header>
    <div class="discussion-layout">
      <div class="discussion-main">
        <article class="forum-post">
          <RouterLink
            :to="`/u/${article.authorId}`"
            class="avatar post-avatar"
            :style="{ background: categoryColor(article.category) }"
            >{{ article.author.slice(0, 1).toUpperCase() }}</RouterLink
          >
          <div class="post-main">
            <div class="post-meta">
              <RouterLink :to="`/u/${article.authorId}`"
                ><strong>{{ article.author }}</strong></RouterLink
              ><time :datetime="article.createdAt">{{
                relativeTime(article.createdAt)
              }}</time>
            </div>
            <ArticleBody
              :content="article.content || ''"
              :content-format="article.contentFormat"
            />
            <div class="post-controls">
              <LikeButton
                :key="article.id"
                :article-id="article.id"
                @changed="article.likeCount = $event"
              /><button
                class="tb-btn"
                :aria-pressed="!!article.bookmarked"
                :disabled="busy"
                @click="bookmark"
              >
                {{ article.bookmarked ? '▣ Saved' : '♧ Bookmark' }}</button
              ><button class="tb-btn" @click="share">↗ Share</button
              ><button
                class="tb-btn"
                @click="
                  async () => {
                    if (await auth()) reporting = true
                  }
                "
              >
                ⚑ Flag</button
              ><template v-if="user?.id === article.authorId"
                ><RouterLink :to="`/write/${article.id}`" class="tb-btn"
                  >Edit</RouterLink
                ><button class="tb-btn danger-button" @click="remove">
                  Delete
                </button></template
              ><button class="tb-btn" @click="reply(true)">❞ Quote</button
              ><button class="tb-btn" @click="reply()">↶ Reply</button>
            </div>
          </div>
        </article>
        <div class="topic-summary">
          <div>
            <small>created</small
            ><span>{{ formatDate(article.createdAt) }}</span>
          </div>
          <div>
            <small>last activity</small
            ><span>{{ relativeTime(article.updatedAt) }}</span>
          </div>
          <div>
            <strong>{{ article.replyCount }}</strong
            ><small>replies</small>
          </div>
          <div>
            <strong>{{ article.viewCount }}</strong
            ><small>views</small>
          </div>
          <div>
            <strong>{{ article.likeCount }}</strong
            ><small>likes</small>
          </div>
        </div>
        <p v-if="notice" role="status" class="success-message">{{ notice }}</p>
        <ForumReplies
          :key="article.id"
          :article-id="article.id"
          :quote="quote"
          @posted="refreshCounts"
          @removed="refreshCounts"
        />
        <section v-if="related.length" class="related-section">
          <h2>Related Topics</h2>
          <TopicTable :items="related" />
        </section>
      </div>
      <aside class="topic-timeline">
        <div>
          <time>{{ formatDate(article.createdAt) }}</time>
          <div class="timeline-track">
            <strong>1 / {{ article.replyCount + 1 }}</strong
            ><span>{{ relativeTime(article.createdAt) }}</span>
          </div>
          <button class="tb-btn" @click="reply()">↶ Reply</button>
        </div>
      </aside>
    </div></template
  >
  <div
    v-if="reporting"
    class="report-overlay"
    @click.self="reporting = false"
    @keydown.esc="reporting = false"
  >
    <form
      class="report-dialog"
      role="dialog"
      aria-modal="true"
      aria-label="Report topic"
      @submit.prevent="report"
    >
      <h2>Flag this topic</h2>
      <p>Tell the moderators why this needs attention.</p>
      <textarea
        v-model="reason"
        required
        maxlength="500"
        rows="4"
        aria-label="Report reason"
        autofocus
      ></textarea>
      <div class="row-actions">
        <button class="tb-btn" type="button" @click="reporting = false">
          Cancel</button
        ><button
          class="tb-btn tb-btn--primary"
          :disabled="busy || !reason.trim()"
        >
          Send report
        </button>
      </div>
    </form>
  </div>
</template>
