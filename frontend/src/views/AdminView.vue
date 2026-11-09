<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { http } from '../api/http'
import { useAuth } from '../composables/useAuth'
import {
  categories,
  loadCategories,
  errorMessage,
  statusLabels,
} from '../utils/articles'
import type { Category } from '../utils/articles'
import type { Article, PageResult } from '../types/article'
import AdminTaxonomy from '../components/AdminTaxonomy.vue'
import PagePagination from '../components/PagePagination.vue'
interface AdminUser {
  id: number
  username: string
  email: string
  role: string
  enabled: boolean
}
interface AdminComment {
  id: number
  articleId: number
  title: string
  author: string
  content: string
}
const route = useRoute(),
  router = useRouter(),
  { user } = useAuth()
const tabs = {
  users: '用户',
  articles: '文章',
  categories: '分类',
  comments: '回复',
  tags: '标签',
  rules: '社区规则',
}
type Tab = keyof typeof tabs
const tab = computed<Tab>(() =>
  Object.prototype.hasOwnProperty.call(tabs, String(route.query.tab))
    ? (route.query.tab as Tab)
    : 'users',
)
const search = ref(''),
  status = ref(''),
  page = ref(0),
  total = ref(0),
  pages = ref(0)
const users = ref<AdminUser[]>([]),
  articles = ref<Article[]>([]),
  comments = ref<AdminComment[]>([])
const loading = ref(false),
  busy = ref(false),
  error = ref(''),
  notice = ref('')
const editing = ref<number | null>(null),
  categoryForm = reactive({
    name: '',
    description: '',
    color: '#3b82f6',
    parentId: null as number | null,
  })
let request = 0
async function load() {
  const current = ++request
  loading.value = true
  error.value = ''
  try {
    if (tab.value === 'tags' || tab.value === 'rules') {
      total.value = 0
      pages.value = 0
      return
    }
    if (tab.value === 'categories') {
      await loadCategories()
      total.value = categories.length
      pages.value = 0
    } else {
      const endpoint =
        tab.value === 'articles' ? '/editor/articles' : `/admin/${tab.value}`
      const { data } = await http.get<
        PageResult<AdminUser | Article | AdminComment>
      >(endpoint, {
        params: {
          page: page.value,
          size: 20,
          q: search.value,
          status: status.value,
        },
      })
      if (current !== request) return
      total.value = data.totalElements
      pages.value = data.totalPages
      if (tab.value === 'users') users.value = data.items as AdminUser[]
      else if (tab.value === 'articles')
        articles.value = data.items as Article[]
      else comments.value = data.items as AdminComment[]
    }
  } catch (e) {
    if (current === request) error.value = errorMessage(e)
  } finally {
    if (current === request) loading.value = false
  }
}
watch(
  tab,
  () => {
    page.value = 0
    search.value = ''
    status.value = ''
    notice.value = ''
    void load()
  },
  { immediate: true },
)
async function action(work: () => Promise<unknown>, message: string) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await work()
    await load()
    notice.value = message
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
function saveUser(u: AdminUser) {
  if (window.confirm(`更新 ${u.username} 的角色和账号状态？`))
    void action(
      () =>
        http.put(`/admin/users/${u.id}`, { role: u.role, enabled: u.enabled }),
      '账号权限已更新，将在下一次请求生效。',
    )
}
function removeArticle(a: Article) {
  if (window.confirm(`下架并删除「${a.title}」？该文章将不再公开展示。`))
    void action(
      () =>
        http.delete(`/admin/articles/${a.id}`, {
          params: { version: a.version },
        }),
      '文章已下架。',
    )
}
function removeComment(c: AdminComment) {
  if (window.confirm('删除此评论及其全部回复？'))
    void action(() => http.delete(`/comments/${c.id}`), '评论已删除。')
}
function editCategory(c?: Category) {
  editing.value = c?.id || null
  Object.assign(categoryForm, {
    name: c?.name || '',
    description: c?.description || '',
    color: c?.color || '#3b82f6',
    parentId: c?.parentId || null,
  })
}
function saveCategory() {
  void action(async () => {
    if (editing.value)
      await http.put(`/admin/categories/${editing.value}`, categoryForm)
    else await http.post('/admin/categories', categoryForm)
    editCategory()
  }, '分类已保存。')
}
function removeCategory(c: Category) {
  if (window.confirm(`删除分类「${c.name}」？有文章或草稿的分类不能删除。`))
    void action(() => http.delete(`/admin/categories/${c.id}`), '分类已删除。')
}
function changePage(value: number) {
  page.value = value
  void load()
}
function filter() {
  page.value = 0
  void load()
}
</script>
<template>
  <section class="work-page">
    <header class="work-heading">
      <div>
        <p class="eyebrow">COMMUNITY MANAGEMENT</p>
        <h1>管理后台</h1>
        <p>维护社区内容与秩序，让分享持续发生。</p>
      </div>
      <RouterLink to="/review" class="tb-btn tb-btn--primary"
        >内容审核队列 →</RouterLink
      >
    </header>
    <nav class="status-tabs" aria-label="管理模块">
      <RouterLink
        v-for="(label, key) in tabs"
        :key="key"
        :to="{ query: { tab: key } }"
        :class="{ active: tab === key }"
        >{{ label }}管理</RouterLink
      >
    </nav>
    <p v-if="error" class="error-message" role="alert">{{ error }}</p>
    <p v-if="notice" class="success-message" role="status">{{ notice }}</p>
    <form
      v-if="!['categories', 'tags', 'rules'].includes(tab)"
      class="admin-search"
      @submit.prevent="filter"
    >
      <input
        v-model="search"
        maxlength="100"
        :aria-label="`搜索${tabs[tab]}`"
        :placeholder="`搜索${tabs[tab]}…`"
      /><select
        v-if="tab === 'articles'"
        v-model="status"
        aria-label="文章状态"
        @change="filter"
      >
        <option value="">全部公开话题</option>
        <option value="PUBLISHED">已发布</option></select
      ><button class="tb-btn" :disabled="loading || busy">搜索</button
      ><button
        type="button"
        class="tb-btn"
        :disabled="loading || busy"
        @click="load"
      >
        刷新
      </button>
    </form>
    <p v-if="loading" class="empty-state" role="status">正在加载…</p>
    <template v-else>
      <AdminTaxonomy v-if="tab === 'tags' || tab === 'rules'" :kind="tab" />
      <div v-else-if="tab === 'users'" class="table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>用户</th>
              <th>角色</th>
              <th>账号状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in users" :key="u.id">
              <td>
                <RouterLink :to="`/u/${u.id}`"
                  ><strong>{{ u.username }}</strong></RouterLink
                ><small>{{ u.email }}</small>
              </td>
              <td>
                <select
                  v-model="u.role"
                  :aria-label="`${u.username} 的角色`"
                  :disabled="busy || u.id === user?.id"
                >
                  <option value="USER">普通用户</option>
                  <option value="EDITOR">版主</option>
                  <option value="ADMIN">管理员</option>
                </select>
              </td>
              <td>
                <select
                  v-model="u.enabled"
                  :aria-label="`${u.username} 的账号状态`"
                  :disabled="busy || u.id === user?.id"
                >
                  <option :value="true">启用</option>
                  <option :value="false">停用</option>
                </select>
              </td>
              <td>
                <button
                  class="tb-btn"
                  :disabled="busy || u.id === user?.id"
                  @click="saveUser(u)"
                >
                  {{ u.id === user?.id ? '当前账号' : '保存' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <template v-else-if="tab === 'articles'"
        ><article v-for="a in articles" :key="a.id" class="management-row">
          <div>
            <RouterLink :to="`/t/${a.id}`"
              ><h2>{{ a.title }}</h2></RouterLink
            >
            <p>{{ a.author }} · {{ a.category }} · 论坛话题</p>
            <span class="status-badge" :data-status="a.status">{{
              statusLabels[a.status]
            }}</span>
          </div>
          <div class="row-actions">
            <RouterLink :to="`/t/${a.id}`" class="tb-btn">查看</RouterLink
            ><button
              class="tb-btn danger-button"
              :disabled="busy"
              @click="removeArticle(a)"
            >
              下架 / 删除
            </button>
          </div>
        </article></template
      >
      <template v-else-if="tab === 'comments'"
        ><article v-for="c in comments" :key="c.id" class="management-row">
          <div>
            <RouterLink :to="`/article/${c.articleId}#comment-${c.id}`"
              ><h2>{{ c.title }}</h2></RouterLink
            >
            <p>@{{ c.author }}</p>
            <blockquote class="admin-comment">{{ c.content }}</blockquote>
          </div>
          <button
            class="tb-btn danger-button"
            :disabled="busy"
            @click="removeComment(c)"
          >
            删除评论
          </button>
        </article></template
      >
      <div v-else class="category-admin">
        <form class="writing-form tb-card" @submit.prevent="saveCategory">
          <h2>{{ editing ? '编辑分类' : '新增分类' }}</h2>
          <fieldset :disabled="busy">
            <label
              >分类名称<input
                v-model="categoryForm.name"
                maxlength="40"
                required
                :disabled="!!editing" /></label
            ><label
              >介绍<textarea
                v-model="categoryForm.description"
                rows="3"
                maxlength="300"
              ></textarea></label
            ><label
              >父分类<select v-model="categoryForm.parentId">
                <option :value="null">无（顶级分类）</option>
                <option
                  v-for="parent in categories.filter(
                    (c) => !c.parentId && c.id !== editing,
                  )"
                  :key="parent.id"
                  :value="parent.id"
                >
                  {{ parent.name }}
                </option>
              </select></label
            ><label
              >标记颜色<input v-model="categoryForm.color" type="color"
            /></label>
            <div class="row-actions">
              <button type="submit" class="tb-btn tb-btn--primary">
                保存分类</button
              ><button
                v-if="editing"
                type="button"
                class="tb-btn"
                @click="editCategory()"
              >
                取消编辑
              </button>
            </div>
          </fieldset>
        </form>
        <div>
          <article v-for="c in categories" :key="c.id" class="management-row">
            <div>
              <h2>
                <span
                  class="category-square"
                  :style="{ background: c.color }"
                ></span>
                {{ c.name }}
              </h2>
              <p>{{ c.description }}</p>
            </div>
            <div class="row-actions">
              <button class="tb-btn" :disabled="busy" @click="editCategory(c)">
                编辑</button
              ><button
                class="tb-btn danger-button"
                :disabled="busy"
                @click="removeCategory(c)"
              >
                删除
              </button>
            </div>
          </article>
        </div>
      </div>
      <p
        v-if="
          !['categories', 'tags', 'rules'].includes(tab) &&
          total === 0 &&
          !error
        "
        class="empty-state"
      >
        没有符合条件的记录。
      </p>
    </template>
    <PagePagination
      v-if="!['categories', 'tags', 'rules'].includes(tab)"
      :unit="tab === 'users' ? '位' : tab === 'comments' ? '条' : '篇'"
      :page="page"
      :pages="pages"
      :total="total"
      :disabled="loading || busy"
      @change="changePage"
    />
  </section>
</template>
