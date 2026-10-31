import { onBeforeUnmount, ref, watch } from 'vue'
import { listArticles } from '../api/articles'
import { errorMessage } from '../utils/articles'
import type { Article, ArticleScope, PageResult } from '../types/article'

export function useArticlePage(source: () => { scope: ArticleScope; params: Record<string, unknown> }) {
  const result = ref<PageResult<Article>>({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
  const loading = ref(false)
  const error = ref('')
  let controller: AbortController | undefined
  async function reload() {
    controller?.abort()
    const current = new AbortController()
    controller = current
    loading.value = true
    error.value = ''
    result.value = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
    try {
      const { scope, params } = source()
      const data = await listArticles(scope, params, current.signal)
      if (!current.signal.aborted) result.value = data
    } catch (reason) {
      if (!current.signal.aborted) error.value = errorMessage(reason)
    } finally {
      if (!current.signal.aborted) loading.value = false
    }
  }
  watch(source, reload, { immediate: true, deep: true })
  onBeforeUnmount(() => controller?.abort())
  return { result, loading, error, reload }
}
