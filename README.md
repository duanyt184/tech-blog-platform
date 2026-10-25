# 码上 · 面向技术分享的博客与投稿协作平台

面向高校学生、技术爱好者与内容编辑的技术博客与投稿协作平台。用户注册登录后可以撰写文章、投稿审核、发布分享；编辑负责审核与退回。

前后端分离架构：**Vue 3 + TypeScript + Vite** 前端，**Spring Boot 3 + MySQL 8** 后端。当前已完成项目基础工程（Issue #1）与注册 / 登录 / 退出（Issue #2）。

## 当前进度

| Issue | 内容 | 状态 |
| --- | --- | --- |
| #1 | 初始化项目与数据库 | ✅ 已完成 |
| #2 | 注册、登录、退出与真实权限控制 | ✅ 已完成 |
| #3 | 从数据库读取文章列表与详情 | ⏳ 待开发 |
| #4 | 纯文本文章发布 | ⏳ 待开发 |
| #5 | 用户自己的文章管理 | ⏳ 待开发 |
| 后续 | 投稿审核、版本历史、Markdown、评论、点赞、收藏、通知、后台统计 | 📋 规划中 |

> 首页的文章列表与分类仍是 **Demo 演示数据**，接口尚未接入数据库；`/articles`、`/article/:id`、`/write`、`/my-articles` 为占位页，只保证路由与布局可用，未提前实现其他成员负责的业务。
>
> 认证部分（注册 / 登录 / 退出 / 会话恢复）是**真实可用**的：数据落库、密码 BCrypt 哈希、Session 认证、路由守卫与退出失效均已通过接口与页面验证。

## 技术栈

| 模块 | 技术 | 版本 / 要求 |
| --- | --- | --- |
| 前端框架 | Vue、TypeScript、Vite | Vue 3.5+、TypeScript 5.9+、Vite 8 |
| 前端路由与请求 | Vue Router、Axios | Vue Router 4.6+、Axios 1.20+ |
| UI 组件 | Element Plus（手动按需引入） | 2.14+ |
| 后端框架 | Java、Spring Boot、Maven | Java 17 编译目标、Spring Boot 3.5.16、Maven 3.9.9（Wrapper） |
| 安全 | Spring Security | 随 Spring Boot 版本管理，会话式认证 |
| 数据访问 | Spring Data JPA、Flyway | 随 Spring Boot 版本管理 |
| 数据库 | MySQL | 8.0 |
| 本地数据库编排 | Docker Compose（可选） | Docker Desktop 或 Docker Engine + Compose v2 |

后端以 Java 17 为编译目标，在 JDK 17/21/25 上均可运行。前端要求 Node.js `20.19+`。

## 目录结构

```text
.
├─ backend/
│  ├─ mvnw / mvnw.cmd                # Maven Wrapper：未安装 Maven 也能构建
│  ├─ pom.xml
│  └─ src/
│     ├─ main/java/com/techblog/platform/
│     │  ├─ config/                  # SecurityConfig（安全）、WebConfig（CORS）
│     │  ├─ controller/              # AuthController、HealthController
│     │  ├─ dto/                     # 请求/响应对象（不含实体）
│     │  ├─ entity/                  # JPA 实体与枚举：User、Article
│     │  ├─ exception/               # 统一异常出口
│     │  ├─ repository/              # Spring Data 仓库
│     │  ├─ security/                # UserPrincipal、UserDetailsService、会话管理
│     │  ├─ service/                 # 业务逻辑：AuthService、HealthService
│     │  └─ TechBlogApplication.java
│     ├─ main/resources/
│     │  ├─ application.yml          # 数据源、JPA、Flyway、Session Cookie
│     │  └─ db/migration/            # Flyway 版本化 SQL（V1__create_core_tables.sql）
│     └─ test/java/…                 # 19 个自动化测试
├─ frontend/
│  ├─ src/
│  │  ├─ api/                        # http.ts（Axios 实例）、auth.ts
│  │  ├─ components/                 # AppHeader（导航与登录态）
│  │  ├─ composables/                 # useAuth（全局登录态）
│  │  ├─ router/                     # 路由表 + 登录守卫
│  │  ├─ styles/                     # global.css（设计变量）、auth.css
│  │  ├─ types/                      # TypeScript 类型
│  │  └─ views/                      # 7 个路由页面
│  ├─ package.json / pnpm-lock.yaml
│  └─ vite.config.ts                 # 开发服务器与 /api 代理
├─ docs/
│  ├─ issue-2-auth.md                # 认证模块实施说明（接口契约、决策、联调）
│  └─ environment-setup-windows.docx # Windows 环境安装图文指南
├─ .env.example                      # 本地配置示例（不含生产凭据）
├─ .gitignore
├─ docker-compose.yml                # MySQL 8（可选，替代本机 MySQL）
└─ README.md
```

## 快速开始

### 0. 环境要求

| 依赖 | 说明 |
| --- | --- |
| JDK 17+ | `java -version` 能输出即可（JDK 17/21/25 均可） |
| Maven | **可以不装**：仓库自带 `mvnw` Wrapper，首次运行会自动下载 Maven |
| Node.js 20.19+ | 自带 npm |
| MySQL 8.0 | 本机安装，或用下面的 Docker Compose |

### 1. 获取代码

```bash
git clone <仓库地址> tech-blog-platform
cd tech-blog-platform
```

### 2. 准备数据库

**方式 A：本机 MySQL 8（推荐，端口 3306）**

用有建库权限的账号（通常是 root）执行：

```sql
CREATE DATABASE IF NOT EXISTS tech_blog
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'tech_blog'@'localhost' IDENTIFIED BY 'tech_blog_dev_password';
ALTER USER 'tech_blog'@'localhost' IDENTIFIED BY 'tech_blog_dev_password';
GRANT ALL PRIVILEGES ON tech_blog.* TO 'tech_blog'@'localhost';
FLUSH PRIVILEGES;
```

表结构不用手工建，后端启动时由 Flyway 自动创建。

**方式 B：Docker Compose（端口 3307，避免与本机 MySQL 冲突）**

```bash
cp .env.example .env
docker compose up -d mysql
docker compose ps          # 等到 healthy
```

数据保存在命名卷 `tech-blog-mysql-data`；`docker compose down -v` 会连同数据一起删除，谨慎使用。

### 3. 启动后端

Windows PowerShell（**环境变量只在当前窗口有效，每次都要设**）：

```powershell
cd backend
$env:DB_HOST = "localhost"
$env:DB_PORT = "3306"            # 用 Docker 的话改成 3307
$env:DB_NAME = "tech_blog"
$env:DB_USER = "tech_blog"
$env:DB_PASSWORD = "tech_blog_dev_password"
.\mvnw.cmd spring-boot:run
```

macOS / Linux：

```bash
cd backend
DB_HOST=localhost DB_PORT=3306 DB_NAME=tech_blog \
DB_USER=tech_blog DB_PASSWORD=tech_blog_dev_password ./mvnw spring-boot:run
```

看到下面两行说明启动成功（窗口保持运行）：

```text
Successfully applied 1 migration   (首次启动)
Tomcat started on port 8080 (http)
```

### 4. 启动前端（另开一个终端）

```bash
cd frontend
npm install            # 若提示 peer 依赖冲突，改用 npm install --legacy-peer-deps
npm run dev
```

浏览器访问 `http://localhost:5173`。开发服务器把 `/api/*` 代理到 `http://localhost:8080`，浏览器端不需要硬编码后端地址。

> Windows PowerShell 若报 `npm.ps1 无法加载，因为在此系统上禁止运行脚本`，执行一次
> `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`（不需要管理员），或直接使用 `npm.cmd run dev`。

### 5. 验证

```bash
curl -i http://localhost:8080/api/health
# 数据库正常 → HTTP 200 {"status":"UP","database":"UP"}
# 数据库不可用 → HTTP 503 {"status":"DOWN","database":"DOWN"}
```

首页右侧「系统状态」显示两个「正常」，说明 `浏览器 → Vite 代理 → Spring Boot → MySQL` 全链路打通。

## 接口文档

### 通用约定

- 所有业务接口统一前缀 `/api`。
- 请求与响应均为 JSON（`Content-Type: application/json`）。
- 认证基于 **HttpOnly Cookie + 服务端 Session**，浏览器登录后自动携带，前端无需手动加 Token 头。
- 接口不返回 JPA 实体，统一使用 `dto` 下的对象，任何响应都不包含 `password_hash`。
- 失败响应统一结构：

```json
{
  "code": "VALIDATION_ERROR",
  "message": "请检查填写内容",
  "fields": { "username": "用户名需为 3-20 位字母、数字或下划线" }
}
```

`fields` 仅在字段级校验失败时出现。`code` 取值：

| code | HTTP | 含义 |
| --- | --- | --- |
| `VALIDATION_ERROR` | 400 | 请求体字段校验失败（见 `fields`） |
| `INVALID_BODY` | 400 | 请求体不是合法 JSON |
| `BAD_REQUEST` | 400 | 业务校验不通过（如两次密码不一致） |
| `INVALID_CREDENTIALS` | 401 | 账号或密码错误 |
| `UNAUTHENTICATED` | 401 | 未登录或会话已过期 |
| `FORBIDDEN` | 403 | 已登录但无权限 |
| `NOT_FOUND` | 404 | 接口不存在 |
| `METHOD_NOT_ALLOWED` | 405 | 请求方法不支持 |
| `CONFLICT` | 409 | 用户名或邮箱已被占用 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | Content-Type 不受支持 |
| `INTERNAL_ERROR` | 500 | 服务端异常（不返回堆栈与 SQL） |

### 健康检查

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/health` | 公开 | 真实执行 `SELECT 1`，验证数据库连通性 |

### 认证接口

| 方法 | 路径 | 鉴权 | 成功 | 主要失败 |
| --- | --- | --- | --- | --- |
| POST | `/api/auth/register` | 公开 | 201 + 用户档案 | 400 参数不合法 / 409 用户名或邮箱已存在 |
| POST | `/api/auth/login` | 公开 | 200 + 用户档案，响应带 `Set-Cookie: JSESSIONID` | 400 参数不合法 / 401 账号或密码错误 |
| POST | `/api/auth/logout` | 公开（幂等） | 204，服务端 Session 失效并下发过期 Cookie | — |
| GET | `/api/auth/me` | 需登录 | 200 + 用户档案 | 401 未登录 |

**用户档案**（任何接口都不返回密码字段）：

```json
{ "id": 1, "username": "alice", "email": "alice@example.com", "role": "USER" }
```

**注册**

```bash
curl -i -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"alice@example.com","password":"secret123","confirmPassword":"secret123"}'
```

字段规则：`username` 3-20 位字母/数字/下划线；`email` 合法邮箱（服务端统一转小写存储）；`password` 8-64 个字符且不超过 72 字节（BCrypt 上限）。

**登录**（`identifier` 可以是用户名，也可以是邮箱；不区分大小写）

```bash
curl -i -c cookie.txt -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"identifier":"alice","password":"secret123"}'
```

**查询当前用户 / 退出**

```bash
curl -i -b cookie.txt http://localhost:8080/api/auth/me
curl -i -b cookie.txt -c cookie.txt -X POST http://localhost:8080/api/auth/logout
curl -i -b cookie.txt http://localhost:8080/api/auth/me     # 退出后应为 401
```

> 注册成功后接口只返回 201，**不会**自动建立登录态；前端在注册成功后接着调用一次登录接口，实现「注册即登录」的体验。

## 认证与权限设计

### 为什么用 Session 而不是 JWT

- 项目是同域前后端分离（开发经 Vite 代理，生产同域反向代理），Cookie 天然可用；
- 「退出」必须真实生效。JWT 想让已签发的令牌立即失效，需要额外的 Redis 黑名单，而本项目暂无 Redis；
- 令牌放 `localStorage` 会被 XSS 直接读取，`HttpOnly` Cookie 拿不到。

代价是水平扩展需要共享会话（可后续替换为 Spring Session + Redis）。

### 安全措施

| 项目 | 实现 |
| --- | --- |
| 密码存储 | BCrypt（自带随机盐），只存哈希；拒绝超过 72 字节的密码 |
| 会话 | `JSESSIONID`，`HttpOnly` + `SameSite=Lax`，2 小时超时 |
| 会话固定攻击 | 登录成功后更换 SessionId |
| 越权注册 | 注册时角色固定为 `USER`，不接受前端传入 `role` |
| 账号枚举 | 登录失败统一提示「用户名或密码错误」 |
| 并发注册 | 数据库唯一索引兜底，冲突转为 409 而非 500 |
| 退出 | 服务端 Session 立即失效（旧 Cookie 重放验证为 401） |
| 错误信息 | 不返回堆栈、SQL、数据库连接信息 |
| 路由保护 | 前端守卫生效；**真正的权限校验在服务端** |

### 已知边界（后续迭代处理）

1. 未做登录失败次数限制与账号锁定；
2. CSRF 当前关闭（同源 + SameSite=Lax 下风险可控），跨域部署前必须开启；
3. Cookie 未加 `Secure`（本地 http 环境），上 https 后应开启；
4. Session 存于内存，重启后端会导致所有用户重新登录；
5. 未实现修改密码 / 找回密码 / 邮箱验证。

角色目前分为 `USER` / `EDITOR` / `ADMIN`。`SecurityConfig` 已开启 `@EnableMethodSecurity`，后续文章审核接口可直接用 `@PreAuthorize("hasRole('EDITOR')")` 做方法级鉴权。

## 数据库

结构由 Flyway 版本化迁移管理，脚本位于 `backend/src/main/resources/db/migration/`。首次启动后端时创建 `flyway_schema_history` 并执行未运行的迁移；之后启动只做校验。

### `users`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键，自增 |
| `username` | VARCHAR(50) | 唯一 |
| `email` | VARCHAR(255) | 唯一，小写存储 |
| `password_hash` | VARCHAR(255) | BCrypt 哈希，禁止明文 |
| `role` | VARCHAR(20) | `USER` / `EDITOR` / `ADMIN`，默认 `USER` |
| `created_at` / `updated_at` | DATETIME(6) | 由数据库自动维护 |

### `articles`

`id`、`author_id`（外键 → `users.id`）、`title`、`summary`、`content`（LONGTEXT）、`status`（`DRAFT` / `PENDING` / `REJECTED` / `PUBLISHED`）、`view_count`、`created_at`、`updated_at`、`published_at`。

### 修改表结构的规则

**不要编辑已经执行过的迁移文件**（例如 V1）。需要变更时新增 `V2__add_xxx.sql`，否则团队成员本地会出现 Flyway 校验失败、数据库版本分叉。

## 测试

```bash
# 后端：单元 + Web 层测试（19 个）
cd backend
.\mvnw.cmd test        # macOS / Linux: ./mvnw test

# 前端：类型检查 + 生产构建
cd frontend
npm run build          # vue-tsc --noEmit && vite build
```

后端测试覆盖：健康检查（正常 / 数据库不可用）、注册（哈希入库、邮箱归一化、密码不一致、用户名重复、邮箱重复、超长密码）、登录（委派认证、凭证错误）、接口层（201 / 400 字段错误 / 200 会话建立 / 401 / 204 退出 / me 返回当前用户）。

## 前端说明

### 登录态管理

`src/composables/useAuth.ts` 用模块级 `ref` 维护全局登录态（未引入 Pinia），任何组件调用 `useAuth()` 拿到同一份数据：

```ts
const { user, isAuthenticated, isEditor, restoreSession, loginWithPassword, registerAccount, logoutUser } = useAuth()
```

刷新页面时，路由守卫会先调用 `GET /api/auth/me` 恢复登录态，失败则视为未登录。

### 新增受保护页面

在路由上加一行 `meta` 即可，无需改守卫：

```ts
{
  path: '/editor',
  component: () => import('../views/EditorView.vue'),
  meta: { requiresAuth: true },   // 未登录 → /login?redirect=/editor
}
```

角色限制在前端只用于隐藏入口，**服务端必须再校验一次**。

### 请求规范

统一使用 `src/api/http.ts` 的 Axios 实例（自动携带 Cookie、统一错误对象、401 广播），按领域新增文件如 `src/api/articles.ts`，不要在组件里写死后端地址。

## 团队协作规范

1. 从 `main` 拉短生命周期分支，一个分支对应一个 Issue，如 `feature/issue-3-article-list`。
2. 提交前至少跑通 `.\mvnw.cmd test` 和 `npm run build`。
3. 不要提交 `target`、`dist`、`node_modules`、`.env` 或 IDE 配置（已在 `.gitignore` 中）。
4. 数据库变更只通过新增 Flyway 迁移提交，迁移文件进入共享分支后不要改名或重写。
5. 接口先约定 DTO 与 HTTP 状态码，前后端并行开发。
6. Controller / Service / Repository 职责分离；页面、组件、API、类型分别放入既有目录。
7. PR 说明写清：关联 Issue、改动范围、数据库迁移、验证命令、页面截图（如有）。

## 常见问题

**3306 端口被占用或 MySQL 没启动**
`Get-Service MySQL80` 查看状态；服务未启动时用管理员 PowerShell 执行 `net start MySQL80`。若改用 Docker，端口映射在 3307，启动后端时 `DB_PORT` 要同步改成 3307。

**`mvn` 不是内部或外部命令**
本项目不需要安装 Maven，直接用 `.\mvnw.cmd`（macOS / Linux 用 `./mvnw`）即可。

**Flyway 校验失败**
通常表示已执行过的迁移文件被修改。恢复原文件，改为新增下一个版本；不要随意 `repair` 或删除 `flyway_schema_history`。

**后端启动报 `Access denied for user 'tech_blog'`**
数据库账号与密码不一致，或库没建。按「快速开始 → 准备数据库」重新执行一次。

**8080 / 5173 被占用**
后端可通过 `SERVER_PORT` 改端口（同时要改 `frontend/vite.config.ts` 的代理目标）；前端端口在 `vite.config.ts`（已开启 `strictPort`）。

**前端提示接口 404 或 401**
先直接访问 `http://localhost:8080/api/health` 判断后端是否存活。注意未登录状态下访问任何 `/api/**` 都会返回 401（鉴权先于路由匹配），接口路径写错时也一样。

**接口跨域被拦截**
后端 CORS 白名单来自 `APP_CORS_ALLOWED_ORIGIN`（默认 `http://localhost:5173`）。改前端端口后要同步修改。

## 安全说明

- `.env.example` 只提供本地开发示例，生产环境必须替换数据库密码。
- 示例账号密码仅用于本地开发，不要提交任何真实凭据。
- 当前版本已具备基础认证能力，但未做限流、审计与密码找回，**不建议直接暴露到公网**。
