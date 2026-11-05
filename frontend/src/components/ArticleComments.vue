<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { addComment, deleteComment, listComments, reportComment } from '../api/comments'
import { useAuth } from '../composables/useAuth'
import { errorMessage, formatTime } from '../utils/articles'
import type { ArticleComment } from '../types/comment'

interface CommentNode extends ArticleComment {
  children: CommentNode[]
}

interface CommentRow {
  kind: 'comment'
  node: CommentNode
  depth: number
  replyTo: string
}

interface MoreRow {
  kind: 'more'
  threadId: number
  remaining: number
  total: number
}

type Row = CommentRow | MoreRow

/** 顶层评论超过这个数量时折叠，点按钮展开全部 */
const COLLAPSED_THREADS = 5
/** 回复一次展开多少条 */
const REPLY_PAGE = 5
/** 回复数超过这个值就先折叠，不超过则直接展示 */
const REPLY_COLLAPSE_AT = 2

const props = defineProps<{ articleId: number }>()
const { user, isEditor } = useAuth()
const route = useRoute()

const REPORT_REASONS = ['垃圾广告', '人身攻击', '违法违规', '与主题无关', '其他']
const reporting = ref<ArticleComment | null>(null)
const reportReason = ref('')
const reportBusy = ref(false)
const reportError = ref('')
const notice = ref('')

const canDelete = (comment: ArticleComment) =>
  !!user.value && (user.value.id === comment.authorId || isEditor.value)
const canReport = (comment: ArticleComment) =>
  !!user.value && user.value.id !== comment.authorId

function openReport(comment: ArticleComment) {
  reporting.value = comment
  reportReason.value = ''
  reportError.value = ''
}

async function submitReport() {
  if (!reporting.value || reportBusy.value) {
    return
  }
  reportBusy.value = true
  reportError.value = ''
  try {
    await reportComment(reporting.value.id, reportReason.value)
    reporting.value = null
    notice.value = '举报已提交，管理员处理后会删除或忽略这条评论。'
    window.setTimeout(() => { notice.value = '' }, 4000)
  } catch (reason) {
    reportError.value = errorMessage(reason)
  } finally {
    reportBusy.value = false
  }
}

async function removeComment(comment: ArticleComment) {
  if (!window.confirm('删除这条评论？它下面的回复也会一起隐藏。')) {
    return
  }
  try {
    await deleteComment(comment.id)
    notice.value = '评论已删除'
    window.setTimeout(() => { notice.value = '' }, 3000)
    await load()
  } catch (reason) {
    error.value = errorMessage(reason)
  }
}

const comments = ref<ArticleComment[]>([])
const loading = ref(false)
const error = ref('')
const draft = ref('')
const busy = ref(false)
const expanded = ref(false)
const replyTarget = ref<number | null>(null)
const replyDraft = ref('')
const replyBusy = ref(false)
const replyError = ref('')

const threads = computed(() => {
  const nodes = new Map<number, CommentNode>()
  comments.value.forEach((item) => nodes.set(item.id, { ...item, children: [] }))
  const roots: CommentNode[] = []
  nodes.forEach((node) => {
    const parent = node.parentId ? nodes.get(node.parentId) : undefined
    if (parent) {
      parent.children.push(node)
    } else {
      roots.push(node)
    }
  })
  nodes.forEach((node) => {
    node.children.sort((a, b) => a.createdAt.localeCompare(b.createdAt))
  })
  return roots.sort((a, b) => b.createdAt.localeCompare(a.createdAt))
})

const authors = computed(() => {
  const map = new Map<number, string>()
  comments.value.forEach((item) => map.set(item.id, item.author))
  return map
})

const visibleThreads = computed(() =>
  expanded.value ? threads.value : threads.value.slice(0, COLLAPSED_THREADS),
)

/** 每个楼层的回复已展开多少条；未记录时按默认规则（<=2 全展开，否则折叠） */
const replyShown = ref<Record<number, number>>({})

/** 某条顶层评论下的全部回复（含更深层对话），按对话顺序展开 */
function descendantsOf(thread: CommentNode): { node: CommentNode; replyTo: string }[] {
  const list: { node: CommentNode; replyTo: string }[] = []
  const walk = (nodes: CommentNode[]) => {
    nodes.forEach((node) => {
      list.push({ node, replyTo: node.parentId ? authors.value.get(node.parentId) ?? '' : '' })
      walk(node.children)
    })
  }
  walk(thread.children)
  return list
}

const rows = computed<Row[]>(() => {
  const result: Row[] = []
  visibleThreads.value.forEach((thread) => {
    result.push({ kind: 'comment', node: thread, depth: 0, replyTo: '' })
    const replies = descendantsOf(thread)
    const total = replies.length
    if (total === 0) {
      return
    }
    const shown = replyShown.value[thread.id] ?? (total <= REPLY_COLLAPSE_AT ? total : 0)
    replies.slice(0, shown).forEach((item) => {
      result.push({ kind: 'comment', node: item.node, depth: 1, replyTo: item.replyTo })
    })
    if (shown < total) {
      result.push({ kind: 'more', threadId: thread.id, remaining: total - shown, total })
    }
  })
  return result
})

function showMoreReplies(row: MoreRow) {
  const shown = replyShown.value[row.threadId] ?? 0
  replyShown.value = { ...replyShown.value, [row.threadId]: shown + REPLY_PAGE }
}

async function load() {
  loading.value = true
  error.value = ''
  replyTarget.value = null
  try {
    comments.value = await listComments(props.articleId)
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
  // 等列表真正渲染出来再定位，否则 #comment-xx 还不存在
  await focusHash()
}

/** 从「评论」页跳过来时定位到具体评论，并短暂高亮 */
async function focusHash() {
  const match = /^#comment-(\d+)$/.exec(window.location.hash)
  if (!match) {
    return
  }
  const id = Number(match[1])
  const target = comments.value.find((item) => item.id === id)
  if (!target) {
    return
  }
  expanded.value = true
  // 目标藏在折叠的回复里时，先把所在楼层展开
  const byId = new Map(comments.value.map((item) => [item.id, item]))
  let root = target
  while (root.parentId && byId.has(root.parentId)) {
    root = byId.get(root.parentId)!
  }
  if (root.id !== target.id) {
    const thread = threads.value.find((item) => item.id === root.id)
    if (thread) {
      replyShown.value = { ...replyShown.value, [thread.id]: descendantsOf(thread).length }
    }
  }
  await nextTick()
  const element = document.getElementById(`comment-${id}`)
  if (!element) {
    return
  }
  element.scrollIntoView({ behavior: 'smooth', block: 'center' })
  element.classList.add('is-target')
  window.setTimeout(() => element.classList.remove('is-target'), 2200)
}

watch(() => props.articleId, load, { immediate: true })
// 文章不重新挂载、只变 hash 时（例如从评论页再点另一条）也能定位
watch(() => route.hash, () => { void focusHash() })

function startReply(node: CommentNode) {
  replyTarget.value = replyTarget.value === node.id ? null : node.id
  replyDraft.value = ''
  replyError.value = ''
}

function cancelReply() {
  replyTarget.value = null
  replyDraft.value = ''
}

async function submit() {
  if (!user.value || busy.value) {
    return
  }
  const content = draft.value.trim()
  if (!content) {
    return
  }
  busy.value = true
  error.value = ''
  try {
    const created = await addComment(props.articleId, content)
    comments.value = [created, ...comments.value]
    draft.value = ''
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    busy.value = false
  }
}

/** 发表回复后，把所在楼层展开到能看见这条新回复 */
function revealReply(comment: ArticleComment) {
  const byId = new Map(comments.value.map((item) => [item.id, item]))
  let root = byId.get(comment.id)
  while (root?.parentId && byId.has(root.parentId)) {
    root = byId.get(root.parentId)
  }
  if (!root) {
    return
  }
  const thread = threads.value.find((item) => item.id === root!.id)
  if (!thread) {
    return
  }
  const replies = descendantsOf(thread)
  const index = replies.findIndex((item) => item.node.id === comment.id)
  const shown = Math.min(replies.length, Math.max(REPLY_PAGE, Math.ceil((index + 1) / REPLY_PAGE) * REPLY_PAGE))
  replyShown.value = { ...replyShown.value, [thread.id]: shown }
}

async function submitReply(node: CommentNode) {
  if (!user.value || replyBusy.value) {
    return
  }
  const content = replyDraft.value.trim()
  if (!content) {
    return
  }
  replyBusy.value = true
  replyError.value = ''
  try {
    const created = await addComment(props.articleId, content, node.id)
    comments.value = [...comments.value, created]
    revealReply(created)
    replyDraft.value = ''
    replyTarget.value = null
  } catch (reason) {
    replyError.value = errorMessage(reason)
  } finally {
    replyBusy.value = false
  }
}
</script>

<template>
  <section class="comment-section" aria-label="评论">
    <h2 class="comment-title">评论 <span v-if="comments.length">{{ comments.length }}</span></h2>

    <div v-if="user" class="comment-form">
      <textarea
        v-model="draft"
        maxlength="1000"
        rows="3"
        placeholder="说说你的想法…"
        aria-label="评论内容"
      ></textarea>
      <div class="comment-form__foot">
        <span>{{ draft.length }}/1000</span>
        <button
          type="button"
          class="tb-btn tb-btn--primary"
          :disabled="!draft.trim() || busy"
          @click="submit"
        >
          {{ busy ? '发布中…' : '发布评论' }}
        </button>
      </div>
    </div>
    <p v-else class="comment-guest">
      登录后可以参与评论。
      <RouterLink :to="{ path: '/login', query: { redirect: `/article/${articleId}` } }">去登录</RouterLink>
    </p>

    <p v-if="error" class="error-message" role="alert">{{ error }}</p>
    <p v-if="loading" class="comment-empty">正在加载评论…</p>
    <ul v-else-if="rows.length" class="comment-list">
      <template v-for="row in rows" :key="row.kind === 'more' ? `more-${row.threadId}` : row.node.id">
        <li v-if="row.kind === 'more'" class="comment-more">
          <button type="button" class="comment-more-btn" @click="showMoreReplies(row)">
            {{ row.remaining === row.total ? `共 ${row.total} 条回复，点击查看` : `点击查看剩余 ${row.remaining} 条回复` }}
          </button>
        </li>
        <li
          v-else
          :id="`comment-${row.node.id}`"
          class="comment-item"
          :style="{ marginLeft: `${row.depth * 26}px` }"
        >
          <span class="avatar">{{ row.node.author.slice(0, 1) }}</span>
          <div class="comment-item__main">
            <p class="comment-meta">
              <strong>{{ row.node.author }}</strong>
              <span v-if="row.replyTo" class="comment-reply-to">回复 @{{ row.replyTo }}</span>
              <time :datetime="row.node.createdAt">{{ formatTime(row.node.createdAt) }}</time>
              <span v-if="user" class="comment-actions">
                <button
                  v-if="canDelete(row.node)"
                  type="button"
                  class="comment-reply-btn"
                  @click="removeComment(row.node)"
                >
                  删除
                </button>
                <button
                  v-if="canReport(row.node)"
                  type="button"
                  class="comment-reply-btn"
                  @click="openReport(row.node)"
                >
                  举报
                </button>
                <button type="button" class="comment-reply-btn" @click="startReply(row.node)">
                  回复
                </button>
              </span>
            </p>
            <p class="comment-body">{{ row.node.content }}</p>

            <div v-if="replyTarget === row.node.id" class="comment-reply-form">
              <textarea
                v-model="replyDraft"
                maxlength="1000"
                rows="2"
                :placeholder="`回复 @${row.node.author}…`"
                :aria-label="`回复 ${row.node.author}`"
              ></textarea>
              <div class="comment-form__foot">
                <span>{{ replyDraft.length }}/1000</span>
                <span class="comment-reply-actions">
                  <button type="button" class="tb-btn" @click="cancelReply">取消</button>
                  <button
                    type="button"
                    class="tb-btn tb-btn--primary"
                    :disabled="!replyDraft.trim() || replyBusy"
                    @click="submitReply(row.node)"
                  >
                    {{ replyBusy ? '发布中…' : '回复' }}
                  </button>
                </span>
              </div>
              <p v-if="replyError" class="error-message" role="alert">{{ replyError }}</p>
            </div>
          </div>
        </li>
      </template>
    </ul>
    <p v-else class="comment-empty">还没有评论，来说两句吧。</p>

    <button
      v-if="threads.length > COLLAPSED_THREADS"
      type="button"
      class="comment-toggle"
      @click="expanded = !expanded"
    >
      {{ expanded ? '收起评论' : `展开全部 ${comments.length} 条评论` }}
    </button>

    <p v-if="notice" class="comment-notice" role="status">{{ notice }}</p>

    <div v-if="reporting" class="report-overlay" @click.self="reporting = null">
      <div class="report-dialog" role="dialog" aria-modal="true" aria-label="举报评论">
        <h3 class="report-dialog__title">举报评论</h3>
        <p class="report-dialog__quote">{{ reporting.content }}</p>
        <label v-for="reason in REPORT_REASONS" :key="reason" class="report-option">
          <input v-model="reportReason" type="radio" :value="reason" />
          <span>{{ reason }}</span>
        </label>
        <p v-if="reportError" class="error-message" role="alert">{{ reportError }}</p>
        <div class="report-dialog__actions">
          <button type="button" class="tb-btn" @click="reporting = null">取消</button>
          <button
            type="button"
            class="tb-btn tb-btn--primary"
            :disabled="!reportReason || reportBusy"
            @click="submitReport"
          >
            {{ reportBusy ? '提交中…' : '提交举报' }}
          </button>
        </div>
      </div>
    </div>
  </section>
</template>
