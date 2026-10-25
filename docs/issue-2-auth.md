# Issue #2：注册、登录、退出与真实权限控制

> 本文是「认证成员」这一棒的实施说明：改了什么、为什么这么改、前端要怎么接、怎么自测，以及 RuoYi-Vue 里哪些可以看、哪些不要抄。

## 0. 开工前必须先处理：前端文件缺失（已补齐）

`frontend/src` 原本只有 `App.vue`、`env.d.ts`、`main.ts`，但：

- `App.vue` 引用了 `./components/AppHeader.vue`；
- `main.ts` 引用了 `./router` 和 `./styles/global.css`；
- README 里描述的 `router/`、`views/`、`api/`、`components/`、`styles/` 全部不存在。

也就是说当时 `npm run dev` 一定报错（`Failed to resolve import "./router"`）。这属于拷贝/提交时丢了文件，不是代码写错。

**现在已补齐到「可以正常启动」的状态**：

| 文件 | 性质 |
| --- | --- |
| `router/index.ts` | 全部 7 条路由 + 登录守卫（真实实现） |
| `components/AppHeader.vue` | 导航栏 + 登录态展示 + 退出（真实实现） |
| `styles/global.css` | 设计变量与布局骨架（真实实现） |
| `views/LoginView.vue`、`views/RegisterView.vue` | 登录/注册页（你的交付物） |
| `views/HomeView.vue` | 首页占位版，保留「链路自检」卡片 |
| `views/ArticlesView.vue` 等 4 个页面 | 占位页，只保证路由可用 |

⚠️ 如果初始化同学那边还留着原始版本的 `AppHeader.vue` / `global.css` / `HomeView.vue`，**以他的设计为准，用他的文件覆盖我这份**；`router/index.ts` 则需要把本文 4.1 的守卫和他原来的路由表合并（他的可能带 name 或额外 meta）。占位页在他实现对应 Issue 时直接覆盖即可。

## 1. 你负责什么，不碰什么

| 属于 Issue #2（你） | 不属于你（别改） |
| --- | --- |
| `backend/.../security/**`、`config/SecurityConfig.java` | `entity/Article.java`、`ArticleRepository`（文章成员） |
| `controller/AuthController.java`、`service/AuthService.java` | `ArticlesView.vue`、`ArticleDetailView.vue` |
| `dto/RegisterRequest|LoginRequest|AuthUserResponse` | `WriteView.vue`、`MyArticlesView.vue` |
| `exception/**`（统一错误出口） | `HomeView.vue` 的设计、`global.css` 视觉规范 |
| `frontend/src/api/auth.ts`、`composables/useAuth.ts`、登录/注册页 | `router/index.ts` 里其他人负责的路由条目 |

`router/index.ts` 是共用文件，改动时只加**自己的两行**（`/login`、`/register`）和守卫，别重排别人的路由。

## 2. 接口契约（前后端先对齐，再各写各的）

| 方法 | 路径 | 是否需要登录 | 成功 | 主要失败 |
| --- | --- | --- | --- | --- |
| POST | `/api/auth/register` | 否 | 201 + 用户档案 | 400 参数不合法 / 409 用户名或邮箱已存在 |
| POST | `/api/auth/login` | 否 | 200 + 用户档案，响应带 `Set-Cookie: JSESSIONID` | 400 参数不合法 / 401 账号或密码错误 |
| POST | `/api/auth/logout` | 否（幂等） | 204 | 无 |
| GET | `/api/auth/me` | 是 | 200 + 用户档案 | 401 未登录 |

成功响应示例（任何接口都不返回 `passwordHash`）：

```json
{ "id": 1, "username": "alice", "email": "alice@example.com", "role": "USER" }
```

失败响应统一为同一种结构，前端只读 `message` 即可提示用户，表单页还能用 `fields` 定位到具体输入框：

```json
{ "code": "VALIDATION_ERROR", "message": "请检查填写内容", "fields": { "username": "用户名需为 3-20 位字母、数字或下划线" } }
```

`code` 取值：`VALIDATION_ERROR`、`INVALID_BODY`、`BAD_REQUEST`、`CONFLICT`、`INVALID_CREDENTIALS`、`UNAUTHENTICATED`、`FORBIDDEN`、`NOT_FOUND`、`INTERNAL_ERROR`。

## 3. 后端实现说明

| 文件 | 作用 |
| --- | --- |
| `config/SecurityConfig.java` | 过滤器链、放行规则、BCrypt、401/403 的 JSON 响应、CORS 源 |
| `security/UserPrincipal.java` | `UserDetails` 实现，只保留认证/授权需要的字段 |
| `security/AppUserDetailsService.java` | 用「用户名或邮箱」查用户 |
| `security/AuthSessionManager.java` | 建立登录态（换 SessionId + 写 Session）、退出时让 Session 失效 |
| `service/AuthService.java` | 注册校验与落库、调用 `AuthenticationManager` 校验密码 |
| `controller/AuthController.java` | 4 个接口，只做 HTTP 输入输出 |
| `exception/GlobalExceptionHandler.java` | 统一错误出口，不泄漏 SQL/堆栈 |
| `dto/*` | 请求校验注解 + 响应模型 |

### 3.1 为什么用 Session 而不是 JWT

- 项目是单体前后端分离，Vite 开发时代理同源、生产同域反代，Session Cookie 天然可用；
- 退出必须「真失效」。JWT 天生无状态，想让已发出的 token 立即作废，必须再加 Redis 黑名单——本项目连 Redis 都没有；
- 令牌放 `localStorage` 会被 XSS 直接读走，`HttpOnly` Cookie 拿不到。

代价是水平扩展时需要共享 Session（Spring Session + Redis），MVP 阶段可以接受。

### 3.2 三个容易踩的坑（已处理）

1. **Spring Security 6 不会自动把登录态存进 Session。** 6.x 采用「显式保存」，手工调用 `AuthenticationManager` 后必须 `securityContextRepository.saveContext(...)`，否则登录接口返回 200、下一个请求又是 401。见 `AuthSessionManager#establish`。
2. **CORS 有两套。** `WebConfig.addCorsMappings` 是 Spring MVC 的配置，Spring Security 的过滤器链看不懂，预检请求会被 401 拦掉。所以 `SecurityConfig` 里按同一个配置项 `app.cors.allowed-origin` 又声明了一个 `CorsConfigurationSource`。
3. **BCrypt 只认前 72 个字节。** 超长密码会被静默截断，等于降低了强度，`AuthService` 里直接拒绝。

### 3.3 安全细节

- 密码：`BCryptPasswordEncoder`，同一密码每次哈希结果不同（自带随机盐），库里只存哈希；
- 注册时角色固定 `USER`，**不接受**前端传 `role`，否则可以直接注册成 `ADMIN`；
- 登录失败统一提示「用户名或密码错误」，不区分账号不存在还是密码错，避免被用来枚举账号；
- 唯一约束兜底：两个请求同时通过 `existsByUsername` 检查时，数据库唯一索引会抛 `DataIntegrityViolationException`，全局异常处理器把它转成 409 而不是 500；
- 登录成功后换 SessionId，防会话固定攻击；
- 会话 2 小时过期，Cookie 为 `HttpOnly` + `SameSite=Lax`；
- CSRF 在 MVP 阶段关闭（同源 + Lax Cookie，跨站表单不会带上 Cookie）。若将来前后端跨域部署，需要改用 `CookieCsrfTokenRepository` 并让 axios 携带 `X-XSRF-TOKEN`。

**没有新增 Flyway 迁移**：`users` 表的 `password_hash`、`role`、唯一约束在 V1 里已经够用，不要为了「显得有工作量」去改 V1。

## 4. 前端要怎么接（三处）

### 4.1 路由：加两个页面 + 一个守卫

`frontend/src/router/index.ts` **已经写好**（含全部 7 条路由和守卫），结构如下，合并时按需保留别人的路由条目：

```ts
// frontend/src/router/index.ts
import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '../composables/useAuth'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    guestOnly?: boolean
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: () => import('../views/HomeView.vue') },
    // ↓ 你的两个页面
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { guestOnly: true } },
    { path: '/register', name: 'register', component: () => import('../views/RegisterView.vue'), meta: { guestOnly: true } },
    // ……其他人的路由保持原样
    { path: '/write', name: 'write', component: () => import('../views/WriteView.vue'), meta: { requiresAuth: true } },
    { path: '/my-articles', name: 'my-articles', component: () => import('../views/MyArticlesView.vue'), meta: { requiresAuth: true } },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  const { user, ready, restoreSession } = useAuth()
  // 刷新页面后先用 /api/auth/me 问一次「我是谁」，只问一次
  if (!ready.value) {
    await restoreSession()
  }
  if (to.meta.requiresAuth && !user.value) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guestOnly && user.value) {
    return { path: '/' }
  }
  return true
})

export default router
```

> 以后队友新增「需要登录的页面」，只要在路由上写 `meta: { requiresAuth: true }` 即可，不用改守卫；需要单独限制角色（例如审核页）时，前端用 `isEditor` 隐藏入口，**真正的拦截在服务端** `@PreAuthorize("hasRole('EDITOR')")`。

### 4.2 导航栏：显示登录态

`frontend/src/components/AppHeader.vue` 已实现（含响应式折叠菜单），核心逻辑：

```vue
<script setup lang="ts">
import { useAuth } from '../composables/useAuth'
const { user, isAuthenticated, logoutUser } = useAuth()
async function handleLogout() {
  await logoutUser()
  if (route.meta.requiresAuth) await router.replace('/')
}
</script>

<template>
  <template v-if="isAuthenticated">
    <span>{{ user?.username }}</span>
    <button type="button" @click="handleLogout">退出登录</button>
  </template>
  <RouterLink v-else to="/login">登录</RouterLink>
</template>
```

### 4.3 后续写受保护接口时

统一用 `src/api/http.ts` 的实例，它会自动带 Cookie，并把后端错误转成 `ApiError`：

```ts
import { http } from './http'

export async function createArticle(payload: CreateArticlePayload) {
  const { data } = await http.post('/articles', payload)  // 未登录自动 401 → 跳登录
  return data
}
```

401 的处理不需要每个页面写一遍：`http.ts` 会广播 `auth:unauthorized` 事件，`useAuth` 收到后清空当前用户，守卫下次跳转就会把用户带到登录页。

## 5. 本地联调

```powershell
# 1. 数据库：确保 MySQL80 服务已启动，后端终端里设置连接参数
$env:DB_HOST = "localhost"
$env:DB_PORT = "3306"
$env:DB_NAME = "tech_blog"
$env:DB_USER = "tech_blog"
$env:DB_PASSWORD = "tech_blog_dev_password"
cd backend; mvn spring-boot:run

# 2. 另开终端：注册 → 登录（带 Session）→ 查当前用户 → 退出
Invoke-RestMethod -Uri http://localhost:8080/api/auth/register -Method Post -ContentType 'application/json' `
  -Body '{"username":"alice","email":"alice@example.com","password":"secret123","confirmPassword":"secret123"}'

$me = Invoke-RestMethod -Uri http://localhost:8080/api/auth/login -Method Post -ContentType 'application/json' `
  -Body '{"identifier":"alice","password":"secret123"}' -SessionVariable session

Invoke-RestMethod -Uri http://localhost:8080/api/auth/me -WebSession $session     # 200 alice
Invoke-RestMethod -Uri http://localhost:8080/api/auth/logout -Method Post -WebSession $session
Invoke-RestMethod -Uri http://localhost:8080/api/auth/me -WebSession $session     # 应报 401
```

```bash
# bash / macOS 等价写法
curl -i -X POST http://localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"alice@example.com","password":"secret123","confirmPassword":"secret123"}'
curl -i -c cookie.txt -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"identifier":"alice","password":"secret123"}'
curl -i -b cookie.txt http://localhost:8080/api/auth/me
curl -i -b cookie.txt -c cookie.txt -X POST http://localhost:8080/api/auth/logout
curl -i -b cookie.txt http://localhost:8080/api/auth/me   # 401
```

## 6. 自测清单

| 场景 | 期望 |
| --- | --- |
| 正常注册 | 201；数据库 `users.password_hash` 以 `$2a$10$` 开头，**不是明文** |
| 重复用户名 / 同一邮箱换大小写 | 409，提示已被占用 |
| 两次密码不一致、密码少于 8 位、用户名含中文或只有 2 位 | 400，`fields` 指向具体字段 |
| 密码正确登录 | 200，响应头有 `Set-Cookie: JSESSIONID=...; HttpOnly` |
| 密码错误 / 账号不存在 | 401，两种情况提示文案一样 |
| 未登录访问 `/api/auth/me` | 401 `UNAUTHENTICATED` |
| 退出后再访问 `/api/auth/me` | 401（旧 Cookie 无法复活） |
| 未登录直接打开 `/write` | 跳到 `/login?redirect=/write`，登录后回到 `/write` |
| 已登录再打开 `/login` | 自动跳首页 |
| 刷新页面 | 登录态保持（守卫调用 `/me` 恢复） |
| 其他人新增的接口未登录访问 | 401 JSON，而不是默认的 HTML 登录页 |

## 7. RuoYi-Vue 能参考到什么程度

**结论：思路可看，代码不建议搬。**

可以看的文件（`ruoyi-framework` / `ruoyi-common` / `ruoyi-admin`）：

| RuoYi 文件 | 值得借鉴的点 |
| --- | --- |
| `config/SecurityConfig.java` | 过滤器链写法、放行白名单、`@EnableMethodSecurity` |
| `web/service/SysLoginService.java` | 登录流程编排、失败计数、`AuthenticationManager` 用法 |
| `web/service/SysPasswordService.java` | 登录失败次数限制 + 锁定（我们的加分项） |
| `web/service/UserDetailsServiceImpl.java` | `UserDetailsService` 的标准写法 |
| `security/handle/AuthenticationEntryPointImpl.java` | 未认证返回 401 JSON 而不是重定向 |
| `security/handle/LogoutSuccessHandlerImpl.java` | 退出成功响应 |
| `common/utils/SecurityUtils.java` | 在 Service 里取当前用户 id 的封装 |
| `admin/controller/system/SysLoginController.java` | `/login`、`/getInfo`、`/logout` 三接口的职责划分 |
| 前端 `utils/request.js`、`permission.js`、`store/modules/user.js` | axios 拦截器统一 401、路由守卫、用户状态集中管理 |

我们采用的对应关系：`SysLoginController` → `AuthController`；`/getInfo` → `/api/auth/me`；`TokenService` → 不需要（用 Session）；`JwtAuthenticationTokenFilter` → 不需要（`SecurityContextHolderFilter` 已经做）。

**不要照搬的**：JWT + Redis 双写与续期、Kaptcha 验证码、`@Anonymous` 动态白名单、多模块 Maven 结构（framework/common/system/admin）、动态菜单路由权限、XSS 过滤器与操作日志切面。这些都是「企业后台管理系统」的需求，五人课程小组的博客平台用不上，抄进来只会给自己加维护成本。

**许可证**：RuoYi-Vue 是 MIT 协议。参考思路没问题；如果真复制了成段代码，按 MIT 要求保留版权与许可声明，课程报告里也写清来源，别当成自己原创。

## 8. 提交前

```bash
cd backend  && mvn clean test     # 现有 5 个测试 + 新增 13 个测试应全绿
cd frontend && npm run build      # vue-tsc 类型检查 + 生产构建
```

分支名建议 `feature/issue-2-auth`，PR 描述写明：关联 Issue #2、改动范围、**无新增迁移**、以上验证命令、登录/注册页截图。

## 9. 后续可做（写在 Issue 里当加分项）

1. 登录失败次数限制与账号临时锁定（参考 RuoYi 的 `SysPasswordService`）；
2. 修改密码、忘记密码（邮件重置需要引入邮件服务，工作量偏大）；
3. 记住我（`RememberMeServices`）；
4. 邮箱验证激活；
5. 编辑器/管理员后台的 `@PreAuthorize("hasRole('EDITOR')")` 权限点——`SecurityConfig` 已经开了 `@EnableMethodSecurity`，队友写文章审核接口时直接加注解即可。
