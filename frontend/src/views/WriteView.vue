<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  isNavigationFailure,
  NavigationFailureType,
  onBeforeRouteLeave,
  onBeforeRouteUpdate,
  useRoute,
  useRouter,
} from 'vue-router'
import { getArticle, saveArticle, publishTopic, deleteArticle } from '../api/articles'
import MarkdownEditor from '../components/MarkdownEditor.vue'
import AppIcon from '../components/AppIcon.vue'
import TopicTaxonomy from '../components/TopicTaxonomy.vue'
import { useTopicDraft } from '../composables/useTopicDraft'
import { useAuth } from '../composables/useAuth'
import { http } from '../api/http'

import {
  categories,
  loadCategories,
  errorMessage,
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
const ownerId = useAuth().user.value!.id
const draftKey = `topic-draft:${ownerId}:${route.params.id || 'new'}`
const draft = useTopicDraft(form, article, () => draftKey)
const titleInput = ref<HTMLInputElement | null>(null)
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const visible = ref(false)
const resizing = ref(false)
let openingFrame = 0
let disposed = false
onMounted(async () => {
  await nextTick()
  if (disposed) return
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    visible.value = true
    return
  }
  // Paint the collapsed panel before changing its height.
  openingFrame = requestAnimationFrame(() => {
    openingFrame = requestAnimationFrame(() => { visible.value = true })
  })
})
const minimized = ref(false)
const expanded = ref(false)
const preview = ref(window.innerWidth >= 640)
const confirmDiscard = ref(false)
const discardDialog = ref<HTMLElement | null>(null)
const discardButton = ref<HTMLButtonElement | null>(null)
watch(confirmDiscard, async open => {
  await nextTick()
  if (open) discardDialog.value?.querySelector<HTMLButtonElement>('button')?.focus()
  else discardButton.value?.focus({ preventScroll: true })
})
function trapDiscardFocus(event: KeyboardEvent) {
  if (event.key !== 'Tab') return
  const buttons = Array.from(discardDialog.value?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)') || [])
  const first = buttons[0]
  const last = buttons[buttons.length - 1]
  if (!first || !last) { event.preventDefault(); return }
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
}
const stopNavigationListener = router.afterEach((to, _from, failure) => {
  if (to.name === 'write' && isNavigationFailure(failure, NavigationFailureType.duplicated)) minimized.value = false
})
function toggleExpanded() {
  expanded.value = !expanded.value
  minimized.value = false
}
// Match the supplied reference: the panel occupies about 64% of the page viewport.
const panelHeight = ref(Math.min(Math.max(300, Math.round(window.innerHeight * 0.64)), window.innerHeight - 50))
const tagSuggestions = ref<{ name: string }[]>([])
const previousPath = String(router.options.history.state.back || '/latest')
async function closeComposer() {
  await router.push(previousPath.startsWith('/write') ? '/latest' : previousPath)
}
function requestDiscard() {
  if (form.title || form.content || form.tags || article.value) confirmDiscard.value = true
  else void closeComposer()
}
async function discard() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    // Wait for an in-flight autosave before deleting its returned draft/version.
    await draft.flush()
    if (article.value && article.value.status !== 'PUBLISHED') await deleteArticle(article.value)
    draft.clear()
    confirmDiscard.value = false
  } catch (reason) {
    error.value = errorMessage(reason)
    busy.value = false
    return
  }
  busy.value = false
  await closeComposer()
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
  resizing.value = true
  let lastY = event.clientY
  const move = (next: PointerEvent) => {
    resizeBy(lastY - next.clientY)
    lastY = next.clientY
  }
  const stop = () => {
    resizing.value = false
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
  draft.ready.value = false
  try {
    await loadCategories()
    tagSuggestions.value = (await http.get('/community/tags')).data
    const recovery = draft.read()
    let data = id || recovery?.articleId ? await getArticle(id || recovery!.articleId!, 'mine') : null
    if (request !== requestId) return
    if (!id && data?.status === 'PUBLISHED') {
      window.localStorage.removeItem(draftKey)
      data = null
    }
    if (recovery && data && recovery.version !== data.version && JSON.stringify(recovery.form) !== recovery.savedSnapshot) {
      throw new Error('draft-conflict')
    }
    article.value = data
    Object.assign(form, {
      title: data?.title || '',
      summary: data?.summary || '',
      content: data?.content || '',
      contentFormat: data ? data.contentFormat || 'MARKDOWN' : 'MARKDOWN',
      category:
        data?.category ||
        categories.find((c) => c.name === route.query.category)?.name ||
        '',
      tags: data?.tags.join(', ') || '',
    })
    const serverSnapshot = draft.snapshot()
    if (recovery && (!data && !recovery.articleId || (data && recovery.articleId === data.id && recovery.version === data.version))) Object.assign(form, recovery.form)
    draft.initialize(serverSnapshot)

  } catch (reason) {
    if (request === requestId) error.value = reason instanceof Error && reason.message === 'draft-conflict'
      ? '服务器上的草稿已更新，本机未同步内容已保留。请先在其他标签页完成编辑，再重新打开。'
      : errorMessage(reason)
  } finally {
    if (request === requestId) {
      loading.value = false
      await nextTick()
      titleInput.value?.focus({ preventScroll: true })
    }
  }
}
watch(() => route.params.id, load, { immediate: true })
async function canLeave() {
  if (busy.value) return false
  draft.persist()
  await draft.flush()
  if (draft.dirty.value && !draft.locallySaved.value) return window.confirm('草稿保存失败，离开会丢失修改。确定离开吗？')
  cancelAnimationFrame(openingFrame)
  visible.value = false
  if (!window.matchMedia('(prefers-reduced-motion: reduce)').matches) await new Promise(resolve => setTimeout(resolve, 200))
  return true
}
onBeforeRouteLeave(canLeave)
onBeforeRouteUpdate(canLeave)
function beforeUnload(event: BeforeUnloadEvent) {
  draft.persist()
  if ((draft.dirty.value && !draft.locallySaved.value) || busy.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => {
  disposed = true
  cancelAnimationFrame(openingFrame)
  requestId++
  draft.persist()
  stopResize?.()
  stopNavigationListener()
  window.removeEventListener('beforeunload', beforeUnload)
})
async function save(publish: boolean) {
  if (busy.value) return
  const tags = draft.tags()
  if (!form.title.trim()) {
    error.value = '请输入话题标题'
    return
  }
  if (!form.category) {
    error.value = '请选择分类'
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

  let saved: Article | null = null
  let completed = false
  try {
    await draft.flush()
    const payload: ArticleWrite = {
      ...form,
      tags,
      version: article.value?.version,
    }
    saved = await saveArticle(payload, article.value?.id)
    article.value = saved

    draft.persist()
    if (publish && saved.status !== 'PUBLISHED')
      article.value = await publishTopic(saved)
    draft.clear()
    completed = true
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    busy.value = false
  }
  if (completed && publish && article.value?.status === 'PUBLISHED') {
    await router.push(`/t/${article.value.id}`)
    return
  }

}
</script>
<template>
  <div class="composer-page" :class="{ 'composer-page-minimized': minimized }">
    <section
      class="topic-composer"
      :class="{ 'is-visible': visible, 'is-minimized': minimized, 'is-expanded': expanded, 'is-resizing': resizing, 'has-preview': preview }"
      :style="{ '--composer-height': `${panelHeight}px` }"
      aria-label="Topic composer"
      :data-draft-id="article?.id"
      @keydown.esc.prevent="minimized = !minimized"
    >
      <div
        class="composer-resize"
        role="separator"
        aria-label="Resize editor"
        aria-orientation="horizontal"
        tabindex="0"
        :inert="confirmDiscard"
        aria-keyshortcuts="Enter ArrowUp ArrowDown"
        @keydown.enter.prevent="toggleExpanded"
        @pointerdown="startResize"
        @dblclick="toggleExpanded"
        @keydown.up.prevent="resizeBy(30)"
        @keydown.down.prevent="resizeBy(-30)"
      ></div>
      <div class="composer-inner">
        <header class="composer-heading" :inert="confirmDiscard" @dblclick="toggleExpanded">
          <h1>
            <AppIcon name="compose" :size="17" />{{
              article?.status === 'PUBLISHED'
                ? 'Edit topic'
                : 'Create topic'
            }}<span v-if="minimized && form.title"> — {{ form.title }}</span>
          </h1>
          <div class="composer-window-actions">
            <button
              type="button"
              class="icon-button composer-preview-control"
              :aria-label="preview ? 'Hide preview' : 'Show preview'"
              :title="preview ? 'Hide preview' : 'Show preview'"
              :aria-pressed="preview"
              @click="preview = !preview"
            >
              <AppIcon :name="preview ? 'double-left' : 'double-right'" :size="18" />
            </button>
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
            v-else-if="error && !draft.ready.value"
            class="error-message"
          >
            {{ error }} <button @click="load">Retry</button>
          </div>
          <form v-else class="writing-form" @submit.prevent="save(true)">
            <p v-if="error" class="error-message" role="alert">{{ error }}</p>

            <fieldset :disabled="busy" :inert="confirmDiscard">
              <div class="composer-fields">
                <label class="composer-title">
                  <span class="sr-only">Topic title</span>
                  <input ref="titleInput" class="topic-title-input" v-model="form.title"
                    required maxlength="255" placeholder="Title" />
                </label>
                <TopicTaxonomy v-model:category="form.category" v-model:tags="form.tags" :suggestions="tagSuggestions" />
              </div>
              <MarkdownEditor
                v-model="form.content"
                v-model:content-format="form.contentFormat"
                :disabled="busy || !form.category"
                :placeholder="!form.category ? 'Select a category before typing here.' : undefined"
                :preview="preview"
                compact
              />
            </fieldset>
            <footer class="editor-actions" :inert="confirmDiscard">
              <button class="tb-btn tb-btn--primary" type="submit" :disabled="busy">
                <AppIcon name="compose" :size="16" />{{
                  busy ? 'Saving…' : article?.status === 'PUBLISHED' ? 'Save changes' : 'Create Topic'
                }}
              </button>
              <button ref="discardButton" type="button" class="composer-text-button" :disabled="busy" @click="requestDiscard">Discard</button>
              <span class="draft-status" role="status" aria-live="polite">{{ draft.status.value }}</span>
              <button v-if="draft.failure.value" type="button" class="composer-text-button" :disabled="busy" @click="draft.flush">Retry</button>
            </footer>
            <div v-if="confirmDiscard" class="composer-discard-backdrop" @keydown.esc.stop.prevent="!busy && (confirmDiscard = false)" @keydown="trapDiscardFocus">
              <section ref="discardDialog" class="composer-discard-dialog" role="alertdialog" aria-modal="true" aria-labelledby="discard-title">
                <h2 id="discard-title">{{ article?.status === 'PUBLISHED' ? 'Discard these changes?' : 'Discard this draft?' }}</h2>
                <p>{{ article?.status === 'PUBLISHED' ? 'Your published topic will stay unchanged.' : 'Your saved draft and unsaved changes will be removed.' }}</p>
                <div>
                  <button type="button" class="tb-btn" :disabled="busy" @click="confirmDiscard = false">Keep editing</button>
                  <button type="button" class="tb-btn tb-btn--primary" :disabled="busy" @click="discard">Discard draft</button>
                </div>
              </section>
            </div>
          </form>
        </div>
      </div>
    </section>
  </div>
</template>
