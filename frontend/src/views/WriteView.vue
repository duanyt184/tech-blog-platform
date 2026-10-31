<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { getArticle, saveArticle, submitArticle } from '../api/articles'
import MarkdownEditor from '../components/MarkdownEditor.vue'
import ReviewHistory from '../components/ReviewHistory.vue'
import { categories, errorMessage, statusLabels } from '../utils/articles'
import type { Article, ArticleWrite, ContentFormat } from '../types/article'
const route = useRoute()
const router = useRouter()
const article = ref<Article | null>(null)
const form = reactive({ title: '', summary: '', content: '', contentFormat: 'PLAIN_TEXT' as ContentFormat, category: '学习笔记', tags: '' })
const snapshot = () => JSON.stringify(form)
const original = ref(snapshot())
const dirty = computed(() => snapshot() !== original.value)
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const notice = ref('')
const editable = computed(() => !article.value || ['DRAFT', 'REJECTED'].includes(article.value.status))
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
    const data = id ? await getArticle(id, 'mine') : null
    if (request !== requestId) return
    article.value = data
    Object.assign(form, { title: data?.title || '', summary: data?.summary || '', content: data?.content || '',
      contentFormat: data ? data.contentFormat || 'MARKDOWN' : 'PLAIN_TEXT',
      category: data?.category || '学习笔记', tags: data?.tags.join(', ') || '' })
    original.value = snapshot()
  } catch (reason) {
    if (request === requestId) error.value = errorMessage(reason)
  } finally { if (request === requestId) loading.value = false }
}
watch(() => route.params.id, load, { immediate: true })
function canLeave() {
  if (busy.value) return false
  return !dirty.value || window.confirm('还有未保存的修改，确定离开吗？')
}
onBeforeRouteLeave(canLeave)
onBeforeRouteUpdate(canLeave)
function beforeUnload(event: BeforeUnloadEvent) {
  if (dirty.value || busy.value) { event.preventDefault(); event.returnValue = '' }
}
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => { requestId++; window.removeEventListener('beforeunload', beforeUnload) })
async function save(submit: boolean) {
  if (busy.value || !editable.value) return
  const tags = [...new Set(form.tags.split(/[,，]/).map(tag => tag.trim().toLowerCase()).filter(Boolean))]
  if (!form.title.trim()) { error.value = '请输入文章标题'; return }
  if (tags.length > 5 || tags.some(tag => tag.length > 24)) { error.value = '最多 5 个标签，每个不超过 24 个字符'; return }
  if (submit && !form.content.trim()) { error.value = '投稿前请填写正文'; return }
  busy.value = true
  error.value = ''
  notice.value = ''
  let saved: Article | null = null
  try {
    const payload: ArticleWrite = { ...form, tags, version: article.value?.version }
    saved = await saveArticle(payload, article.value?.id)
    article.value = saved
    original.value = snapshot()
    notice.value = '草稿已保存'
    if (submit) {
      article.value = await submitArticle(saved)
      notice.value = '投稿成功，请在我的文章中查看审核结果'
    }
  } catch (reason) { error.value = errorMessage(reason) }
  finally { busy.value = false }
  if (saved && route.params.id !== String(saved.id)) await router.replace(`/write/${saved.id}`)
}
</script>
<template>
  <div class="work-page">
    <header class="work-heading">
      <div><p class="eyebrow">创作空间</p><h1>{{ article ? '编辑文章' : '写一篇新文章' }}</h1></div>
      <RouterLink to="/my-articles" class="tb-btn">我的文章</RouterLink>
    </header>
    <p v-if="loading" class="empty-state" role="status">正在加载文章…</p>
    <div v-else-if="error && route.params.id && !article" class="state-box" role="alert">
      <p>{{ error }}</p><button class="tb-btn" @click="load">重试</button>
    </div>
    <form v-else class="writing-form" @submit.prevent="save(false)">
      <div v-if="article" class="workflow-notice">
        <span class="status-badge" :data-status="article.status">{{ statusLabels[article.status] }}</span>
        <span>{{ editable ? '保存后可提交审核，审核通过后公开展示。' : '当前内容已锁定。如需修改，请在我的文章中先撤回。' }}</span>
      </div>
      <p v-if="error" class="error-message" role="alert">{{ error }}</p>
      <p v-if="notice" class="success-message" role="status">{{ notice }}</p>
      <fieldset :disabled="busy || !editable">
        <label>文章标题 <input v-model="form.title" maxlength="255" required placeholder="用一个清晰的标题介绍你的技术分享" /></label>
        <label>摘要 <textarea v-model="form.summary" maxlength="500" rows="2" placeholder="简要介绍文章的内容与收获（选填）"></textarea></label>
        <div class="form-grid">
          <label>技术分类 <select v-model="form.category"><option v-for="item in categories" :key="item.name">{{ item.name }}</option></select></label>
          <label>标签 <input v-model="form.tags" maxlength="124" placeholder="例如：vue, typescript（最多 5 个）" /></label>
        </div>
      </fieldset>
      <MarkdownEditor v-model="form.content" v-model:content-format="form.contentFormat" :disabled="busy || !editable" />
      <div class="editor-actions"><span>{{ form.content.length }} / 100000 字符 · {{ dirty ? '有未保存的修改' : '已与保存内容一致' }}</span><button v-if="editable" type="submit" class="tb-btn" :disabled="busy">{{ busy ? '正在保存…' : '保存草稿' }}</button><button v-if="editable" type="button" class="tb-btn tb-btn--primary" :disabled="busy" @click="save(true)">保存并投稿</button></div>
      <ReviewHistory :events="article?.reviews || []" />
    </form>
  </div>
</template>
