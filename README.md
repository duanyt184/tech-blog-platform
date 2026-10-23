# 码上：面向技术分享的博客与投稿协作平台

“码上”是一个面向高校学生、技术爱好者与内容编辑的技术博客和投稿协作平台。本仓库采用前后端分离架构，目前处于 **MVP v0.1 / Issue #1（初始化项目与数据库）** 阶段，目标是给五人团队提供一套可启动、可验证、方便继续开发的基础工程。

> 当前首页中的文章、分类和统计数字均标注为 **Demo 演示数据**，没有伪装成数据库查询结果。

## 当前范围

### 已完成

- Vue 3 + TypeScript + Vite 前端工程与响应式技术社区首页。
- Vue Router 基础路由：`/`、`/login`、`/articles`、`/article/:id`、`/write`、`/my-articles`。
- Axios 公共请求实例，开发环境通过 Vite 将 `/api` 代理到 Spring Boot。
- Spring Boot 基础分层、MySQL 数据源、JPA 实体和仓库。
- Flyway 版本化迁移，首次启动创建 `users`、`articles` 两张核心表。
- `GET /api/health`：真实执行 `SELECT 1`；正常返回 200，异常返回不泄漏连接信息的 503。
- MySQL 8 Docker Compose 配置、持久化数据卷、健康检查和环境变量示例。
- 后端健康检查单元测试与 Web 层测试。

### 暂未实现

- Issue #2：注册、登录、退出和真实权限控制。
- Issue #3：从数据库读取文章列表与详情。
- Issue #4：纯文本文章发布。
- Issue #5：用户自己的文章管理。
- 后续迭代：投稿审核、版本历史、Markdown、评论、点赞、收藏、通知和后台统计。

占位页面只负责确认路由与布局有效，没有提前实现其他成员负责的业务。

## 技术栈

| 模块 | 技术 | 项目版本/要求 |
| --- | --- | --- |
| 前端 | Vue、TypeScript、Vite | Vue 3.5+、TypeScript 5.9+、Vite 8 |
| 前端路由与请求 | Vue Router、Axios | Vue Router 4.6+、Axios 1.20+ |
| UI | Element Plus（手动按需导入） | 2.14+ |
| 后端 | Java、Spring Boot、Maven | Java 17、Spring Boot 3.5.16、Maven 3.6.3+ |
| 数据访问 | Spring Data JPA、Flyway | 由 Spring Boot 统一管理兼容版本 |
| 数据库 | MySQL | 8.0 |
| 本地数据库编排 | Docker Compose | Docker Desktop 或 Docker Engine + Compose v2 |

Spring Boot 代码以 Java 17 为编译目标，也可使用项目兼容范围内的较新 JDK 运行。前端要求 Node.js `20.19+`（也支持满足 Vite 要求的更新 LTS/Current 版本）。

## 目录结构

```text
.
├─ backend/
│  ├─ pom.xml                         # Maven 依赖与构建配置
│  └─ src/
│     ├─ main/
│     │  ├─ java/com/techblog/platform/
│     │  │  ├─ config/                # Web/CORS 等横切配置
│     │  │  ├─ controller/            # REST 接口层
│     │  │  ├─ dto/                   # 请求/响应对象
│     │  │  ├─ entity/                # JPA 实体与枚举
│     │  │  ├─ repository/            # Spring Data 数据访问层
│     │  │  ├─ service/               # 业务服务层
│     │  │  └─ TechBlogApplication.java
│     │  └─ resources/
│     │     ├─ application.yml         # 数据源、JPA、Flyway 等配置
│     │     └─ db/migration/           # Flyway 版本化 SQL
│     └─ test/                         # 后端自动化测试
├─ frontend/
│  ├─ src/
│  │  ├─ api/                          # Axios 实例与各领域 API
│  │  ├─ components/                   # 公共组件
│  │  ├─ router/                       # 路由定义
│  │  ├─ styles/                       # 全局与响应式样式
│  │  ├─ types/                        # TypeScript 类型
│  │  └─ views/                        # 路由页面
│  ├─ package.json
│  └─ vite.config.ts                   # 开发服务器与 /api 代理
├─ .env.example                        # 本地配置示例，不含生产凭据
├─ .gitignore
├─ docker-compose.yml
└─ README.md
```

## 环境准备

推荐安装：

- JDK 17（可用 `java -version` 检查）。
- Maven 3.6.3+（可用 `mvn -version` 检查）。
- Node.js 20.19+ 与 npm（可用 `node -v`、`npm -v` 检查）。
- Docker Desktop（Windows/macOS）或 Docker Engine + Compose v2（Linux）。

如果不使用 Docker，也可以使用本机 MySQL 8.0；请参见“使用本机 MySQL”。

## 快速启动（推荐：Docker MySQL）

以下命令均从仓库根目录执行。数据库、后端、前端需要分别保持运行。

### 1. 准备本地配置

Windows PowerShell：

```powershell
Copy-Item .env.example .env
```

macOS / Linux：

```bash
cp .env.example .env
```

`.env` 已被 Git 忽略。示例中的密码只用于本地开发；共享或部署环境必须替换。若不复制 `.env`，Compose 和 Spring Boot 也有相互匹配的本地默认值。

### 2. 启动 MySQL 8

```bash
docker compose up -d mysql
docker compose ps
```

默认连接参数：

| 配置 | 默认值 |
| --- | --- |
| 地址 | `localhost` |
| 宿主机端口 | `3307` |
| 数据库 | `tech_blog` |
| 用户 | `tech_blog` |
| 密码 | `tech_blog_dev_password` |

容器内 MySQL 仍监听 3306，宿主机映射到 3307，避免与已安装的 MySQL 冲突。数据保存在命名卷 `tech-blog-mysql-data` 中，普通的 `docker compose down` 或容器重启不会删除数据。

> `docker compose down -v` 会删除数据卷和全部数据库数据，请勿在有用数据时执行。

### 3. 启动后端

Windows PowerShell：

```powershell
Set-Location backend
mvn spring-boot:run
```

macOS / Linux：

```bash
cd backend
mvn spring-boot:run
```

后端默认地址为 `http://localhost:8080`。启动期间 Flyway 自动执行尚未运行的迁移，JPA 只校验映射（`ddl-auto=validate`），不会删除或重建表。

如需修改连接参数，必须在启动后端的同一个终端设置环境变量。例如：

Windows PowerShell：

```powershell
$env:DB_HOST = "localhost"
$env:DB_PORT = "3307"
$env:DB_NAME = "tech_blog"
$env:DB_USER = "tech_blog"
$env:DB_PASSWORD = "your_local_password"
mvn spring-boot:run
```

macOS / Linux：

```bash
export DB_HOST=localhost
export DB_PORT=3307
export DB_NAME=tech_blog
export DB_USER=tech_blog
export DB_PASSWORD=your_local_password
mvn spring-boot:run
```

可选变量还有 `SERVER_PORT`（默认 8080）和 `APP_CORS_ALLOWED_ORIGIN`（默认 `http://localhost:5173`）。注意：Docker Compose 会读取根目录 `.env`，但它不会自动把变量导入另一个终端中的 Spring Boot 进程。

### 4. 启动前端

打开新终端：

```bash
cd frontend
npm install
npm run dev
```

也可以使用 pnpm：

```bash
cd frontend
pnpm install
pnpm dev
```

浏览器访问 `http://localhost:5173`。开发服务器会把 `/api/*` 代理到 `http://localhost:8080`，浏览器端无需硬编码后端完整地址。

## 使用本机 MySQL 8

如果已安装 MySQL 8，可在有建库和授权权限的账号下执行：

```sql
CREATE DATABASE IF NOT EXISTS tech_blog
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'tech_blog'@'localhost'
  IDENTIFIED BY 'tech_blog_dev_password';

GRANT ALL PRIVILEGES ON tech_blog.* TO 'tech_blog'@'localhost';
FLUSH PRIVILEGES;
```

本机 MySQL 通常监听 3306。启动后端前将 `DB_PORT` 设置为 `3306`；若使用其他用户名或密码，也一并设置 `DB_USER`、`DB_PASSWORD`。不要手工执行 `V1__create_core_tables.sql` 后再启动一个全新的 Flyway 环境，否则 Flyway 无法知道该脚本已执行。

## 数据库初始化与表结构

数据库结构由 [V1__create_core_tables.sql](backend/src/main/resources/db/migration/V1__create_core_tables.sql) 管理。首次启动后端时，Flyway 创建 `flyway_schema_history` 并按版本执行迁移；后续启动只做校验，不清空数据。

### `users`

- 主键：`id`（自增 `BIGINT`）。
- 唯一字段：`username`、`email`。
- 密码字段：`password_hash`，只允许保存安全哈希，禁止保存明文。
- 角色：`USER`、`EDITOR`、`ADMIN`。
- 时间：`created_at`、`updated_at`。

### `articles`

- 主键：`id`；`author_id` 外键指向 `users.id`。
- 内容：`title`、`summary`、`content`（`LONGTEXT`，MVP v0.1 先存纯文本）。
- 状态：`DRAFT`、`PENDING`、`REJECTED`、`PUBLISHED`。
- 统计与时间：`view_count`、`created_at`、`updated_at`、`published_at`。
- 常用索引：作者、状态与创建时间、发布时间、浏览量。

以后修改表结构时，**不要编辑已经共享或执行过的 V1 文件**。请新增如 `V2__add_xxx.sql` 的迁移，避免团队成员数据库出现版本分叉。

## 验证完整调用链路

### 后端接口

数据库和后端启动后：

Windows PowerShell：

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

macOS / Linux：

```bash
curl -i http://localhost:8080/api/health
```

数据库正常时：

```json
{"status":"UP","database":"UP"}
```

HTTP 状态为 200。接口并非固定返回成功，而是通过 `JdbcTemplate` 实际执行 `SELECT 1`。数据库不可用时返回 HTTP 503 和：

```json
{"status":"DOWN","database":"DOWN"}
```

响应不会包含数据库密码、JDBC URL 或底层异常详情。

### 前端链路

同时启动数据库、后端、前端，打开首页。右侧“系统状态”应显示“完整链路运行正常”。这说明：

```text
浏览器 → Vite /api 代理 → Spring Boot → MySQL
```

四个环节均已打通。关闭 MySQL 后点击“重新检查”，状态应变为不可用。

### 自动化检查

```bash
# 后端编译与测试
cd backend
mvn clean test

# 前端类型检查与生产构建
cd frontend
npm run build
```

## API 约定

- 所有业务接口统一使用 `/api` 前缀。
- Controller 只处理 HTTP 输入输出，业务逻辑进入 Service，数据访问进入 Repository。
- 接口不得直接返回 JPA Entity；后续功能使用 `dto` 下的请求/响应对象，避免泄漏密码哈希和懒加载关系。
- 前端统一从 `src/api/http.ts` 使用 Axios 实例，按领域增加文件（例如 `auth.ts`、`articles.ts`），不要在组件内散落完整后端 URL。
- Java 包根路径统一为 `com.techblog.platform`；数据库使用 `snake_case`，Java/TypeScript 使用 `camelCase`。

## 五人协作建议

1. 从主分支拉出短生命周期功能分支，例如 `feature/issue-2-auth`，一个分支聚焦一个 Issue。
2. 提交前至少运行 `mvn test` 和 `npm run build`，不要提交 `target`、`dist`、`node_modules`、`.env` 或 IDE 文件。
3. 数据库变更只通过新增 Flyway 迁移提交；迁移文件一旦进入共享分支就不要改名或重写。
4. 业务接口先约定 DTO 与 HTTP 状态码，再分别推进前后端，减少互相等待。
5. Controller、Service、Repository 保持职责分离；页面、公共组件、API 和类型分别放入现有目录。
6. 合并请求中说明：关联 Issue、改动范围、数据库迁移、验证命令和页面截图（如有）。

## 后续成员入口

- 认证成员：从 `backend/.../entity/User.java`、`repository/UserRepository.java` 和 `frontend/src/views/LoginView.vue` 开始，新增认证 DTO/Service/Controller；密码必须使用可靠的单向哈希。
- 文章查询成员：从 `Article.java`、`ArticleRepository.java`、`ArticlesView.vue`、`ArticleDetailView.vue` 开始，替换 Demo/占位内容。
- 文章发布成员：从 `WriteView.vue` 开始，新增文章请求 DTO 与文章 Service/Controller。
- 个人文章成员：从 `MyArticlesView.vue` 和按作者查询的 Repository 方法开始，并在服务端校验数据归属。
- 公共前端请求：复用 `frontend/src/api/http.ts`；路由统一维护在 `frontend/src/router/index.ts`。

## 常见问题

### 3307 端口被占用

修改根目录 `.env` 中的 `DB_PORT`，例如改为 `3308`，重新运行 `docker compose up -d mysql`；启动后端的终端也要设置相同的 `DB_PORT`。

### 8080 或 5173 被占用

- 后端：设置 `SERVER_PORT`，同时修改 `frontend/vite.config.ts` 中代理的目标端口。
- 前端：Vite 已启用 `strictPort`；请先结束占用 5173 的进程，或同步修改 Vite 端口和 `APP_CORS_ALLOWED_ORIGIN`。

Windows 可用以下命令查找端口占用：

```powershell
Get-NetTCPConnection -LocalPort 3307,8080,5173 -ErrorAction SilentlyContinue
```

macOS / Linux：

```bash
lsof -i :3307 -i :8080 -i :5173
```

### 后端提示数据库连接失败

1. 运行 `docker compose ps`，确认 MySQL 状态为 healthy。
2. 核对 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USER`、`DB_PASSWORD`。
3. 确认后端使用宿主机映射端口 3307，而不是容器内部端口 3306。
4. 查看 `docker compose logs mysql`，排除初始化或磁盘问题。
5. 如果修改过已有数据卷的密码，仅改 `.env` 不会重设旧账号；应使用原密码登录后修改，或在确认没有重要数据后重建数据卷。

### Flyway 校验失败

通常表示已执行的迁移被修改。恢复已共享迁移文件，改为新增下一个版本。不要随意执行 `repair` 或删除 `flyway_schema_history`。

### 前端状态一直显示不可用

先直接访问 `http://localhost:8080/api/health`。若直接访问正常，检查 Vite 是否运行在 5173、`vite.config.ts` 代理是否指向正确的后端端口，然后重启 Vite。

### Maven 或前端依赖下载失败

- 确认 Maven 可访问 Maven Central，Node 包管理器可访问 npm registry。
- 在校园网络或代理环境下，按本机网络要求配置 Maven `settings.xml` 和 npm/pnpm registry。
- 不要把个人镜像地址、代理凭据或访问令牌提交到仓库。

## 安全说明

- `.env.example` 只提供本地开发示例，生产环境不得沿用示例密码。
- 当前尚未实现认证与授权，除健康检查外不要把此版本直接暴露到公网。
- `password_hash` 只是安全存储的字段准备；后续认证实现必须在服务端使用成熟密码哈希算法，并进行 DTO 校验与角色校验。

