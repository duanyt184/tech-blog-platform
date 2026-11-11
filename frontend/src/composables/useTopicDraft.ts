import { computed, onBeforeUnmount, ref, watch, type Ref } from 'vue'
import { saveArticle } from '../api/articles'
import { errorMessage } from '../utils/articles'
import type { Article, ArticleWrite, ContentFormat } from '../types/article'

export interface TopicForm {
  title: string
  summary: string
  content: string
  contentFormat: ContentFormat
  category: string
  tags: string
}
export function useTopicDraft(form: TopicForm, article: Ref<Article | null>, key: () => string) {
  const ready = ref(false)
  const savedSnapshot = ref('')
  const localSnapshot = ref('')
  const saving = ref(false)
  const failure = ref('')
  const storageFailed = ref(false)
  const snapshot = () => JSON.stringify(form)
  const dirty = computed(() => snapshot() !== savedSnapshot.value)
  const locallySaved = computed(() => snapshot() === localSnapshot.value)
  const hasContent = () => !!(form.title || form.content || form.summary || form.tags)
  const tags = () => [...new Set(form.tags.split(/[,，]/).map(t => t.trim().toLowerCase()).filter(Boolean))]
  const canSync = () => !!form.title.trim() && !!form.category && tags().length <= 5 && tags().every(t => t.length <= 24)
  let timer: ReturnType<typeof setTimeout> | undefined
  let pending: Promise<void> | undefined
  let stopped = false

  function read() {
    try {
      const data = JSON.parse(window.localStorage.getItem(key()) || 'null')
      if (!data || !data.form || !Object.keys(form).every(k => typeof data.form[k] === 'string')) return null
      if (!['MARKDOWN', 'PLAIN_TEXT'].includes(data.form.contentFormat)) return null
      return data as { form: TopicForm; articleId?: number; version?: number; savedSnapshot: string }
    } catch { return null }
  }
  function persist() {
    if (!ready.value || stopped || (!hasContent() && !dirty.value)) return
    try {
      const value = JSON.stringify({ form: { ...form }, articleId: article.value?.id, version: article.value?.version, savedSnapshot: savedSnapshot.value })
      window.localStorage.setItem(key(), value)
      if (article.value) window.localStorage.setItem(key().replace(/[^:]+$/, String(article.value.id)), value)
      localSnapshot.value = snapshot()
      storageFailed.value = false
    } catch { storageFailed.value = true }
  }
  function initialize(serverSnapshot: string) {
    savedSnapshot.value = serverSnapshot
    ready.value = true
    persist()
    schedule()
  }
  function schedule() {
    clearTimeout(timer)
    if (ready.value && dirty.value && !stopped) timer = setTimeout(() => void flush(), 800)
  }
  async function flush() {
    clearTimeout(timer)
    if (pending) { await pending; return flush() }
    // Published edits stay private in the recovery buffer until explicit submission.
    if (!ready.value || stopped || !dirty.value || article.value?.status === 'PUBLISHED' || !canSync() || (!article.value && !hasContent())) return
    const sent = snapshot()
    const payload: ArticleWrite = { ...form, tags: tags(), version: article.value?.version }
    saving.value = true
    failure.value = ''
    pending = (async () => {
      try {
        article.value = await saveArticle(payload, article.value?.id)
        savedSnapshot.value = sent
        persist()
      } catch (reason) {
        failure.value = errorMessage(reason)
      } finally { saving.value = false }
    })()
    await pending
    pending = undefined
    // An edit made during the request must be sent with the returned version.
    if (!failure.value && dirty.value) await flush()
  }
  function clear() {
    stopped = true
    clearTimeout(timer)
    savedSnapshot.value = snapshot()
    try {
      const prefix = key().slice(0, key().lastIndexOf(':') + 1)
      const keys = Object.keys(window.localStorage).filter(k => k.startsWith(prefix))
      for (const k of keys) {
        if (k === key() || (article.value && JSON.parse(window.localStorage.getItem(k) || 'null')?.articleId === article.value.id)) window.localStorage.removeItem(k)
      }
    } catch { /* already saved on server */ }
  }
  watch(form, () => { persist(); schedule() }, { flush: 'sync' })
  onBeforeUnmount(() => { clearTimeout(timer); stopped = true })
  const status = computed(() => {
    if (saving.value) return 'Saving…'
    if (failure.value) return locallySaved.value ? 'Saved on this device · sync failed' : 'Draft could not be saved'
    if (storageFailed.value && dirty.value) return 'Draft could not be saved on this device'
    if (dirty.value && locallySaved.value) return article.value?.status === 'PUBLISHED' ? 'Changes saved on this device' : canSync() ? 'Saving…' : 'Draft saved on this device'
    return article.value ? 'Saved' : ''
  })
  return { ready, dirty, locallySaved, saving, failure, snapshot, read, persist, initialize, flush, clear, status, tags }
}
