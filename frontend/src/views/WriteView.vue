<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import {
  onBeforeRouteLeave,
  onBeforeRouteUpdate,
  useRoute,
  useRouter,
} from 'vue-router'
import { getArticle, saveArticle, publishTopic } from '../api/articles'
import MarkdownEditor from '../components/MarkdownEditor.vue'
import AppIcon from '../components/AppIcon.vue'
import CategoriesView from './CategoriesView.vue'
import { http } from '../api/http'

import {
  categories,
  loadCategories,
  errorMessage,
  categoryColor,
} from '../utils/articles'
import type { Article, ArticleWrite, ContentFormat } from '../types/article'
const route = useRoute()
const router = useRouter()
const article = ref<Article | null>(null)
const form = reactive({
  title: '',
  summary: '',
  content: '',
  contentFormat: 'MARKDOWN' as ContentFormat,
  category: '',
  tags: '',
})
const snapshot = () => JSON.stringify(form)
const original = ref(snapshot())
const dirty = computed(() => snapshot() !== original.value)
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const notice = ref('')
const minimized = ref(false)
const expanded = ref(false)
function toggleExpanded() {
  expanded.value = !expanded.value
  minimized.value = false
}
const panelHeight = ref(Math.min(560, window.innerHeight - 70))
const tagSuggestions = ref<{ name: string }[]>([])
const previousPath = String(router.options.history.state.back || '/latest')
function closeComposer() {
  void router.push(previousPath.startsWith('/write') ? '/latest' : previousPath)
}
function resizeBy(amount: number) {
  panelHeight.value = Math.max(
    300,
    Math.min(window.innerHeight - 55, panelHeight.value + amount),
  )
}
let stopResize: (() => void) | undefined
function startResize(event: PointerEvent) {
  if (expanded.value || minimized.value) return
  event.preventDefault()
  stopResize?.()
  let lastY = event.clientY
  const move = (next: PointerEvent) => {
    resizeBy(lastY - next.clientY)
    lastY = next.clientY
  }
  const stop = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', stop)
    window.removeEventListener('pointercancel', stop)
  }
  stopResize = stop
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', stop, { once: true })
  window.addEventListener('pointercancel', stop, { once: true })
}
let requestId = 0
async function load() {
  const id = String(route.params.id || '')
  if (id && String(article.value?.id) === id) return
  const request = ++requestId
  article.value = null
  loading.value = true
  error.value = ''
  notice.value = ''
  try {
    await loadCategories()
    tagSuggestions.value = (await http.get('/community/tags')).data
    const data = id ? await getArticle(id, 'mine') : null
    if (request !== requestId) return
    article.value = data
    Object.assign(form, {
      title: data?.title || '',
      summary: data?.summary || '',
      content: data?.content || '',
      contentFormat: data ? data.contentFormat || 'MARKDOWN' : 'MARKDOWN',
      category:
        data?.category ||
        categories.find((c) => c.name === route.query.category)?.name ||
        categories.find((c) => c.name === '编程入门')?.name ||
        categories[0]?.name ||
        '',
      tags: data?.tags.join(', ') || '',
    })
    original.value = snapshot()
  } catch (reason) {
    if (request === requestId) error.value = errorMessage(reason)
  } finally {
    if (request === requestId) loading.value = false
  }
}
watch(() => route.params.id, load, { immediate: true })
function canLeave() {
  if (busy.value) return false
  return !dirty.value || window.confirm('还有未保存的修改，确定离开吗？')
}
onBeforeRouteLeave(canLeave)
onBeforeRouteUpdate(canLeave)
function beforeUnload(event: BeforeUnloadEvent) {
  if (dirty.value || busy.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => {
  requestId++
  stopResize?.()
  window.removeEventListener('beforeunload', beforeUnload)
})
async function save(publish: boolean) {
  if (busy.value) return
  const tags = [
    ...new Set(
      form.tags
        .split(/[,，]/)
        .map((tag) => tag.trim().toLowerCase())
        .filter(Boolean),
    ),
  ]
  if (!form.title.trim()) {
    error.value = '请输入话题标题'
    return
  }
  if (tags.length > 5 || tags.some((tag) => tag.length > 24)) {
    error.value = '最多 5 个标签，每个不超过 24 个字符'
    return
  }
  if (publish && !form.content.trim()) {
    error.value = '发布前请填写正文'
    return
  }
  busy.value = true
  error.value = ''
  notice.value = ''
  let saved: Article | null = null
  try {
    const payload: ArticleWrite = {
      ...form,
      tags,
      version: article.value?.version,
    }
    saved = await saveArticle(payload, article.value?.id)
    article.value = saved
    original.value = snapshot()
    notice.value = '草稿已保存'
    if (publish && saved.status !== 'PUBLISHED')
      article.value = await publishTopic(saved)
    notice.value =
      article.value.status === 'PUBLISHED'
        ? 'Topic published. Your changes are live.'
        : 'Draft saved.'
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    busy.value = false
  }
  if (publish && article.value?.status === 'PUBLISHED') {
    await router.push(`/t/${article.value.id}`)
    return
  }
  if (saved && route.params.id !== String(saved.id))
    await router.replace(`/write/${saved.id}`)
}
</script>
<template>
  <div class="composer-page" :class="{ 'composer-page-minimized': minimized }">
    <CategoriesView class="composer-context" aria-hidden="true" inert />
    <section
      class="topic-composer"
      :class="{ 'is-minimized': minimized, 'is-expanded': expanded }"
      :style="{ '--composer-height': `${panelHeight}px` }"
      aria-label="Topic composer"
    >
      <div
        class="composer-resize"
        role="separator"
        aria-label="Resize editor"
        aria-orientation="horizontal"
        tabindex="0"
        @pointerdown="startResize"
        @keydown.up.prevent="resizeBy(30)"
        @keydown.down.prevent="resizeBy(-30)"
      ></div>
      <div class="composer-inner">
        <header class="composer-heading">
          <h1>
            <AppIcon name="comment" :size="17" />{{
              article?.status === 'PUBLISHED'
                ? 'Edit topic'
                : 'Create a new topic'
            }}<span v-if="minimized && form.title"> — {{ form.title }}</span>
          </h1>
          <div class="composer-window-actions">
            <button
              type="button"
              class="icon-button"
              :aria-label="minimized ? 'Restore editor' : 'Minimize editor'"
              :title="minimized ? 'Restore editor' : 'Minimize editor'"
              @click="minimized = !minimized"
            >
              <AppIcon :name="minimized ? 'chevron' : 'minimize'" :size="18" />
            </button>
            <button
              type="button"
              class="icon-button"
              :aria-label="expanded ? 'Exit full screen' : 'Expand editor'"
              :title="expanded ? 'Exit full screen' : 'Expand editor'"
              @click="toggleExpanded"
            >
              <AppIcon name="expand" :size="17" />
            </button>
            <button
              type="button"
              class="icon-button"
              aria-label="Close editor"
              title="Close editor"
              :disabled="busy"
              @click="closeComposer"
            >
              <AppIcon name="close" :size="20" />
            </button>
          </div>
        </header>
        <div v-show="!minimized" class="composer-content">
          <p v-if="loading" class="empty-state">Loading editor…</p>
          <div
            v-else-if="error && route.params.id && !article"
            class="error-message"
          >
            {{ error }} <button @click="load">Retry</button>
          </div>
          <form v-else class="writing-form" @submit.prevent="save(true)">
            <p v-if="error" class="error-message" role="alert">{{ error }}</p>
            <p v-if="notice" class="composer-notice" role="status">
              {{ notice }}
            </p>
            <fieldset :disabled="busy">
              <label class="composer-title"
                ><span class="sr-only">Topic title</span
                ><input
                  class="topic-title-input"
                  v-model="form.title"
                  required
                  maxlength="255"
                  placeholder="What is this discussion about in one brief sentence?"
              /></label>
              <div class="composer-taxonomy">
                <label class="composer-category"
                  ><span class="sr-only">Category</span
                  ><span
                    class="category-square"
                    :style="{ background: categoryColor(form.category) }"
                  ></span
                  ><select
                    v-model="form.category"
                    aria-label="Category"
                    required
                  >
                    <option v-for="c in categories" :key="c.id">
                      {{ c.name }}
                    </option>
                  </select></label
                >
                <label class="composer-tags"
                  ><span class="sr-only">Tags</span
                  ><input
                    v-model="form.tags"
                    list="composer-tag-suggestions"
                    maxlength="124"
                    placeholder="optional tags"
                    aria-label="Tags" /><datalist id="composer-tag-suggestions">
                    <option
                      v-for="tag in tagSuggestions"
                      :key="tag.name"
                      :value="tag.name"
                    /></datalist
                ></label>
              </div>
              <MarkdownEditor
                v-model="form.content"
                v-model:content-format="form.contentFormat"
                :disabled="busy"
                compact
              />
            </fieldset>
            <footer class="editor-actions">
              <button
                class="tb-btn tb-btn--primary"
                type="submit"
                :disabled="busy"
              >
                <AppIcon name="comment" :size="16" />{{
                  busy
                    ? 'Saving…'
                    : article?.status === 'PUBLISHED'
                      ? 'Save changes'
                      : 'Create Topic'
                }}
              </button>
              <button
                type="button"
                class="composer-text-button"
                :disabled="busy"
                @click="closeComposer"
              >
                Cancel
              </button>
              <button
                v-if="article?.status !== 'PUBLISHED'"
                type="button"
                class="composer-text-button"
                :disabled="busy"
                @click="save(false)"
              >
                Save Draft
              </button>
              <span>{{
                dirty ? 'Unsaved changes' : article ? 'Saved' : ''
              }}</span>
            </footer>
          </form>
        </div>
      </div>
    </section>
  </div>
</template>
