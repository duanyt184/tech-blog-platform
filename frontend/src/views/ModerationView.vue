<script setup lang="ts">
import { ref, watch } from 'vue'
import { http } from '../api/http'
import { errorMessage, formatTime } from '../utils/articles'
import ArticleBody from '../components/ArticleBody.vue'
const status = ref('PENDING'),
  items = ref<any[]>([]),
  page = ref(0),
  loading = ref(false),
  busy = ref(false),
  error = ref(''),
  note = ref<Record<number, string>>({})
async function load() {
  loading.value = true
  error.value = ''
  try {
    items.value = (
      await http.get('/editor/moderation', {
        params: { status: status.value, page: page.value },
      })
    ).data
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}
watch(
  status,
  () => {
    page.value = 0
    void load()
  },
  { immediate: true },
)
async function resolve(id: number, action: string) {
  if (busy.value) return
  if (
    action === 'DELETE' &&
    !window.confirm('Remove this content from the community?')
  )
    return
  busy.value = true
  try {
    await http.post(`/editor/moderation/${id}/resolve`, {
      action,
      note: note.value[id] || '',
    })
    await load()
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
function changePage(delta:number){ page.value+=delta; void load() }
</script>
<template>
  <section class="work-page">
    <header class="work-heading">
      <div>
        <h1>Review queue</h1>
        <p>Review community reports and automatic content flags.</p>
      </div>
      <button class="tb-btn" @click="load">Refresh</button>
    </header>
    <div class="status-tabs">
      <button
        v-for="s in ['PENDING', 'DISMISSED', 'DELETED']"
        :key="s"
        :class="{ active: status === s }"
        @click="status = s"
      >
        {{
          s === 'PENDING'
            ? 'Needs review'
            : s === 'DISMISSED'
              ? 'Dismissed'
              : 'Removed'
        }}
      </button>
    </div>
    <p v-if="error" class="error-message" role="alert">{{ error }}</p>
    <p v-if="loading" class="empty-state">Loading queue…</p>
    <template v-else
      ><article v-for="item in items" :key="item.id" class="moderation-card">
        <header>
          <span class="status-badge">{{
            item.source === 'AUTO' ? 'Automatic detection' : 'User report'
          }}</span
          ><time>{{ formatTime(item.created_at) }}</time>
        </header>
        <RouterLink
          :to="`/t/${item.article_id}${item.comment_id ? '#comment-' + item.comment_id : ''}`"
          ><h2>{{ item.title }}</h2></RouterLink
        >
        <p><strong>Reason:</strong> {{ item.reason }}</p>
        <p class="field-hint">
          {{ item.comment_id ? 'Reply #' + item.comment_id : 'Topic' }} ·
          {{ item.reporter || 'Community rules' }}
        </p>
        <details>
          <summary>Review content</summary>
          <ArticleBody :content="item.content" content-format="MARKDOWN" />
        </details>
        <template v-if="status === 'PENDING'">
          <textarea
            v-model="note[item.id]"
            class="moderation-note"
            rows="2"
            maxlength="500"
            aria-label="Moderator note"
            placeholder="Decision note (optional)"
          ></textarea>
          <div class="row-actions">
            <button
              class="tb-btn"
              :disabled="busy"
              @click="resolve(item.id, 'DISMISS')"
            >
              Dismiss report · Keep content</button
            ><button
              class="tb-btn danger-button"
              :disabled="busy"
              @click="resolve(item.id, 'DELETE')"
            >
              Remove content
            </button>
          </div></template
        >
        <p v-else>{{ item.resolution || 'No additional note.' }}</p>
      </article>
      <p v-if="!items.length" class="empty-state">
        All caught up. There are no reports in this queue.
      </p></template
    >
    <div class="pagination">
      <button
        class="tb-btn"
        :disabled="!page || loading"
        @click="changePage(-1)"
      >
        Previous</button
      ><span>Page {{ page + 1 }}</span
      ><button
        class="tb-btn"
        :disabled="items.length < 50 || loading"
        @click="changePage(1)"
      >
        Next
      </button>
    </div>
  </section>
</template>
