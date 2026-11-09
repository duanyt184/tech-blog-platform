<script setup lang="ts">
import UserAvatar from './UserAvatar.vue'
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { http } from '../api/http'
import { useAuth } from '../composables/useAuth'
import {
  addComment,
  deleteComment,
  listComments,
  reportComment,
} from '../api/comments'
import { errorMessage, relativeTime } from '../utils/articles'
import type { ArticleComment } from '../types/comment'
import ArticleBody from './ArticleBody.vue'
const props = defineProps<{ articleId: number; quote: string }>(),
  emit = defineEmits<{ posted: []; removed: [] }>()
const { user, isEditor } = useAuth(),
  route = useRoute(),
  router = useRouter()
const items = ref<ArticleComment[]>([]),
  draft = ref(''),
  parent = ref<ArticleComment>(),
  editing = ref<ArticleComment>(),
  busy = ref(false),
  error = ref(''),
  notice = ref(''),
  preview = ref(false),
  reporting = ref<ArticleComment>(),
  reason = ref(''),
  likes = ref<Record<number, { count: number; liked: boolean }>>({}),
  page = ref(0),
  more = ref(false)
const ordered = computed(() => [...items.value].sort((a, b) => a.id - b.id))
async function load() {
  try {
    const result = (
      await http.get(`/articles/${props.articleId}/comments`, {
        params: { page: page.value, size: 50 },
      })
    ).data
    items.value = [
      ...new Map(
        (page.value ? [...items.value, ...result] : result).map(
          (c: ArticleComment) => [c.id, c],
        ),
      ).values(),
    ] as ArticleComment[]
    more.value = result.length === 50
    await Promise.all(
      result.map(async (c: ArticleComment) => {
        likes.value[c.id] = (await http.get(`/comments/${c.id}/likes`)).data
      }),
    )
    await nextTick()
    if (route.hash)
      document.getElementById(route.hash.slice(1))?.scrollIntoView()
  } catch (e) {
    error.value = errorMessage(e)
  }
}
onMounted(load)
watch(
  () => props.quote,
  (value) => {
    draft.value = value
    editing.value = undefined
    parent.value = undefined
  },
)
async function login() {
  if (user.value) return true
  await router.push({ path: '/login', query: { redirect: route.fullPath } })
  return false
}
async function send() {
  if (busy.value || !draft.value.trim()) return
  busy.value = true
  error.value = ''
  try {
    let saved: ArticleComment
    if (editing.value)
      saved = (
        await http.put(`/comments/${editing.value.id}`, {
          content: draft.value,
          parentId: editing.value.parentId,
        })
      ).data
    else {
      saved = await addComment(props.articleId, draft.value, parent.value?.id)
      emit('posted')
    }
    draft.value = ''
    parent.value = undefined
    editing.value = undefined
    page.value = 0
    await load()
    if (!items.value.some((c) => c.id === saved.id)) items.value.push(saved)
    notice.value = 'Reply saved.'
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
async function respond(c: ArticleComment, quote = false) {
  if (!(await login())) return
  parent.value = c
  editing.value = undefined
  draft.value = quote
    ? `> ${c.author} wrote:\n> ${c.content.slice(0, 600).replace(/\n/g, '\n> ')}\n\n`
    : ''
  document
    .getElementById('reply-composer')
    ?.scrollIntoView({ behavior: 'smooth' })
  document.getElementById('reply-text')?.focus()
}
function edit(c: ArticleComment) {
  editing.value = c
  parent.value = undefined
  draft.value = c.content
  document
    .getElementById('reply-composer')
    ?.scrollIntoView({ behavior: 'smooth' })
}
async function remove(c: ArticleComment) {
  if (!window.confirm('Delete this reply and its replies?')) return
  try {
    await deleteComment(c.id)
    page.value = 0
    await load()
    emit('removed')
  } catch (e) {
    error.value = errorMessage(e)
  }
}
async function like(c: ArticleComment) {
  if (!(await login()) || busy.value) return
  busy.value = true
  try {
    likes.value[c.id] = (
      await http[likes.value[c.id]?.liked ? 'delete' : 'put'](
        `/comments/${c.id}/likes`,
      )
    ).data
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
async function report() {
  if (!reporting.value || busy.value) return
  busy.value = true
  try {
    await reportComment(reporting.value.id, reason.value)
    reporting.value = undefined
    reason.value = ''
    notice.value = 'Report sent to moderators.'
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
function loadMore() {
  page.value++
  void load()
}
function cancel() {
  editing.value = undefined
  parent.value = undefined
  draft.value = ''
}
</script>
<template>
  <section class="forum-replies">
    <p v-if="error" class="error-message" role="alert">{{ error }}</p>
    <p v-if="notice" class="success-message" role="status">{{ notice }}</p>
    <article
      v-for="c in ordered"
      :id="`comment-${c.id}`"
      :key="c.id"
      class="forum-post"
    >
      <RouterLink :to="`/u/${c.authorId}`" class="avatar post-avatar"
        ><UserAvatar :user-id="c.authorId" :username="c.author"
      /></RouterLink>
      <div class="post-main">
        <div class="post-meta">
          <RouterLink :to="`/u/${c.authorId}`"
            ><strong>{{ c.author }}</strong></RouterLink
          ><a
            v-if="c.parentId"
            :href="`#comment-${c.parentId}`"
            class="reply-parent"
            >↶ reply #{{ c.parentId }}</a
          ><time>{{ relativeTime(c.createdAt) }}</time>
        </div>
        <ArticleBody :content="c.content" content-format="MARKDOWN" />
        <div class="post-controls">
          <button
            class="tb-btn"
            :aria-pressed="!!likes[c.id]?.liked"
            :disabled="busy"
            @click="like(c)"
          >
            ♡ {{ likes[c.id]?.count || 0 }}</button
          ><button
            v-if="user?.id === c.authorId"
            class="tb-btn"
            @click="edit(c)"
          >
            Edit</button
          ><button
            v-if="user?.id === c.authorId || isEditor"
            class="tb-btn"
            @click="remove(c)"
          >
            Delete</button
          ><button
            class="tb-btn"
            @click="
              async () => {
                if (await login()) reporting = c
              }
            "
          >
            ⚑ Flag</button
          ><button class="tb-btn" @click="respond(c, true)">❞ Quote</button
          ><button class="tb-btn" @click="respond(c)">↶ Reply</button>
        </div>
      </div>
    </article>
    <button v-if="more" class="tb-btn" @click="loadMore">
      Load more replies
    </button>
    <form
      v-if="user"
      id="reply-composer"
      class="reply-composer"
      @submit.prevent="send"
    >
      <header>
        <h3>
          {{
            editing
              ? 'Edit reply'
              : parent
                ? 'Reply to ' + parent.author
                : 'Reply to this topic'
          }}
        </h3>
        <button type="button" class="tb-btn" @click="preview = !preview">
          {{ preview ? 'Write' : 'Preview' }}
        </button>
      </header>
      <ArticleBody
        v-if="preview"
        :content="draft || 'Your preview will appear here.'"
        content-format="MARKDOWN"
      /><textarea
        v-else
        id="reply-text"
        v-model="draft"
        required
        maxlength="1000"
        rows="6"
        aria-label="Reply content"
        placeholder="Type here. Markdown is supported."
      ></textarea>
      <div class="editor-actions">
        <button
          class="tb-btn tb-btn--primary"
          :disabled="busy || !draft.trim()"
        >
          {{ busy ? 'Saving…' : editing ? 'Save edit' : '↶ Reply' }}</button
        ><button
          v-if="editing || parent"
          type="button"
          class="tb-btn"
          @click="cancel"
        >
          Cancel</button
        ><span>{{ draft.length }} / 1000</span>
      </div>
    </form>
    <div v-else id="reply-composer" class="join-discussion">
      <h3>Join the conversation</h3>
      <p>Log in to reply, like, and bookmark topics.</p>
      <RouterLink
        :to="{ path: '/login', query: { redirect: route.fullPath } }"
        class="tb-btn tb-btn--primary"
        >Log In</RouterLink
      ><RouterLink to="/register" class="tb-btn">Sign Up</RouterLink>
    </div>
    <div
      v-if="reporting"
      class="report-overlay"
      @click.self="reporting = undefined"
    >
      <form
        class="report-dialog"
        role="dialog"
        aria-modal="true"
        aria-label="Report reply"
        @submit.prevent="report"
      >
        <h2>Flag reply</h2>
        <textarea
          v-model="reason"
          aria-label="Report reason"
          required
          maxlength="200"
          rows="4"
          placeholder="Why should moderators review this reply?"
        ></textarea>
        <div class="row-actions">
          <button type="button" class="tb-btn" @click="reporting = undefined">
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
  </section>
</template>
