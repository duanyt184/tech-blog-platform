import { createRouter, createWebHistory } from 'vue-router'
import { AUTH_UNAUTHORIZED_EVENT } from '../api/http'
import { useAuth } from '../composables/useAuth'

declare module 'vue-router' {
  interface RouteMeta {
    /** 需要登录才能访问，未登录会被送去 /login?redirect=当前路径 */
    requiresAuth?: boolean
    /** 只有未登录用户能看的页面（登录页、注册页），已登录会被送回首页 */
    guestOnly?: boolean
    requiresEditor?: boolean
    requiresAdmin?: boolean
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('../views/CategoriesView.vue'),
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../views/RegisterView.vue'),
      meta: { guestOnly: true },
    },
    { path: '/latest', component: () => import('../views/ArticlesView.vue') },
    { path: '/top', component: () => import('../views/ArticlesView.vue') },
    { path: '/search', component: () => import('../views/ArticlesView.vue') },
    { path: '/tags', component: () => import('../views/DirectoryView.vue') },
    {
      path: '/guidelines',
      component: () => import('../views/DirectoryView.vue'),
    },
    {
      path: '/drafts',
      component: () => import('../views/MyArticlesView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/articles',
      name: 'articles',
      component: () => import('../views/ArticlesView.vue'),
    },
    {
      path: '/article/:id',
      alias: '/t/:id',
      name: 'article-detail',
      component: () => import('../views/ArticleDetailView.vue'),
    },
    {
      path: '/write/:id?',
      name: 'write',
      component: () => import('../views/WriteView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/my-articles',
      name: 'my-articles',
      component: () => import('../views/MyArticlesView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/bookmarks',
      component: () => import('../views/ArticlesView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/comments',
      component: () => import('../views/CommentsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/review',
      alias: ['/editor', '/reports'],
      component: () => import('../views/ModerationView.vue'),
      meta: { requiresAuth: true, requiresEditor: true },
    },
    {
      path: '/categories',
      component: () => import('../views/CategoriesView.vue'),
    },
    {
      path: '/u/:id',
      alias: '/blog/:id',
      component: () => import('../views/ProfileView.vue'),
    },
    {
      path: '/settings',
      component: () => import('../views/SettingsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/admin',
      component: () => import('../views/AdminView.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
    },
    // 未知路径回首页，避免出现空白页
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: (to, _from, savedPosition) => {
    if (to.name === 'write' || _from.name === 'write') return false
    // 评论定位：/article/3#comment-12 优先滚动到对应评论
    if (to.hash) {
      return { el: to.hash, top: 96, behavior: 'smooth' }
    }
    return savedPosition || { top: 0 }
  },
})

/**
 * 全局守卫：认证模块的核心前端逻辑。
 *
 * 1. 首次进入任何页面先用 GET /api/auth/me 恢复登录态（刷新页面后 Session 仍有效）；
 *    restoreSession 内部做了并发去重，连续跳转不会重复请求。
 * 2. 需要登录的页面：未登录 → /login?redirect=原路径。
 * 3. 登录页/注册页：已登录用户不需要再看 → 回首页。
 */
router.beforeEach(async (to) => {
  const { user, ready, restoreSession } = useAuth()

  if (!ready.value) {
    await restoreSession()
  }

  if (to.meta.requiresAuth && !user.value) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (
    to.meta.requiresEditor &&
    user.value?.role !== 'EDITOR' &&
    user.value?.role !== 'ADMIN'
  ) {
    return { path: '/my-articles' }
  }

  if (to.meta.requiresAdmin && user.value?.role !== 'ADMIN')
    return { path: '/' }

  if (to.meta.guestOnly && user.value) {
    return { path: '/' }
  }

  return true
})

/**
 * 会话在页面停留期间过期时（例如超过 2 小时、或后端重启），
 * 只要当前页需要登录态，就立刻把用户送回登录页，而不是让页面停在假登录状态。
 */
window.addEventListener(AUTH_UNAUTHORIZED_EVENT, () => {
  const current = router.currentRoute.value
  if (current.meta.requiresAuth) {
    void router.replace({
      path: '/login',
      query: { redirect: current.fullPath },
    })
  }
})

export default router
