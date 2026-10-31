import { computed, ref } from 'vue'
import {
  fetchCurrentUser,
  login as loginRequest,
  logout as logoutRequest,
  register as registerRequest,
} from '../api/auth'
import { ApiError, AUTH_UNAUTHORIZED_EVENT } from '../api/http'
import type { AuthUser, LoginPayload, RegisterPayload } from '../types/auth'

/**
 * 全局登录态。
 *
 * 项目目前没有引入 Pinia，这里用「模块级 ref」实现单例状态：
 * 任何组件调用 useAuth() 拿到的都是同一份数据，刷新页面后由 restoreSession() 向
 * GET /api/auth/me 询问一次「我是谁」，Session 有效就自动恢复登录态。
 */
const currentUser = ref<AuthUser | null>(null)
const ready = ref(false)
const pending = ref(false)
let restoreTask: Promise<AuthUser | null> | null = null

const isAuthenticated = computed(() => currentUser.value !== null)
const isEditor = computed(
  () => currentUser.value?.role === 'EDITOR' || currentUser.value?.role === 'ADMIN',
)

/** 会话过期被后端拒绝时，前端同步清空状态，避免页面还显示着「已登录」 */
window.addEventListener(AUTH_UNAUTHORIZED_EVENT, () => {
  if (currentUser.value) {
    currentUser.value = null
  }
})

/**
 * 恢复登录态。路由守卫会在首次进入受保护页面时调用；
 * 内部做了去重，并发调用只会真正请求一次 /auth/me。
 */
async function restoreSession(force = false): Promise<AuthUser | null> {
  if (ready.value && !force) {
    return currentUser.value
  }
  if (restoreTask) {
    return restoreTask
  }

  restoreTask = (async () => {
    try {
      currentUser.value = await fetchCurrentUser()
    } catch (error) {
      // 401 表示「当前没有登录」，属于正常分支，不需要报错
      if (!(error instanceof ApiError && error.status === 401)) {
        console.warn('[auth] 恢复登录态失败：', error)
      }
      currentUser.value = null
    } finally {
      ready.value = true
      restoreTask = null
    }
    return currentUser.value
  })()

  return restoreTask
}

async function loginWithPassword(payload: LoginPayload): Promise<AuthUser> {
  pending.value = true
  try {
    currentUser.value = await loginRequest(payload)
    ready.value = true
    return currentUser.value
  } finally {
    pending.value = false
  }
}

/**
 * 注册后自动登录：注册接口只负责建账号（201），
 * 这里紧接着用同一份账号密码调一次登录，让用户不用再手填一遍。
 */
async function registerAccount(payload: RegisterPayload): Promise<AuthUser> {
  await registerRequest(payload)
  return loginWithPassword({ identifier: payload.username, password: payload.password })
}

async function logoutUser(): Promise<void> {
  await logoutRequest()
  currentUser.value = null
  ready.value = true
}

export function useAuth() {
  return {
    user: currentUser,
    ready,
    pending,
    isAuthenticated,
    isEditor,
    restoreSession,
    loginWithPassword,
    registerAccount,
    logoutUser,
  }
}
