# 码上 · 技术博客平台

支持文章创作、投稿审核、公开阅读与评论互动的前后端分离项目。功能现状与后续建议见 [FEATURES.md](FEATURES.md)。

## 技术栈

| 部分 | 技术 |
| --- | --- |
| 前端 | Vue 3、TypeScript 5.9、Vite 8、Vue Router 4、Axios、Element Plus |
| 正文渲染 | Marked、KaTeX、highlight.js、DOMPurify |
| 后端 | Java 17 编译目标、Spring Boot 3.5.16、Spring Security、Spring Data JPA、Bean Validation |
| 数据库 | MySQL / MariaDB；使用 MariaDB JDBC 驱动，Flyway 管理表结构迁移 |
| 构建与测试 | pnpm 11.25.0、Maven Wrapper 3.9.9、Node.js Test Runner、jsdom、JUnit、Mockito |
| 本地数据库容器 | Docker Compose，仓库配置提供 MySQL 8.0 |

源码位于 `frontend/` 和 `backend/`。数据库迁移位于 `backend/src/main/resources/db/migration/`（当前 V1–V7），启动后端时自动执行，无需手工建表。

## 功能概览

| 模块 | 当前能力 |
| --- | --- |
| 账号 | 注册、登录、退出、刷新后恢复登录态；Session Cookie 认证 |
| 写作 | 纯文本 / Markdown / LaTeX 正文，编辑、分屏、预览三种视图，草稿与投稿流转 |
| 审核 | 编辑审核队列，通过或填写意见退回，保留审核记录 |
| 阅读 | 公开列表**每页 4 篇**，分类 / 标签 / 关键词筛选，最新与热门排序 |
| 收藏 | 列表行与详情页一键收藏，显示收藏数（超过 1000 显示为 1.2k）；热门按收藏数排序 |
| 评论 | 详情页评论 + 楼中楼回复（标注「回复 @某人」）；顶层超过 5 条、回复超过 2 条自动折叠，可分页展开；「评论」页可查看收到的评论与我的评论，点击直接跳到对应评论 |
| 评论治理 | 作者可删除自己的评论（编辑/管理员可删除任意评论，软删除并连带隐藏整棵回复）；读者可举报他人评论；编辑/管理员在「举报处理」页删除评论或忽略举报 |
| 统计 | 侧边栏「技术分类」显示已发布总数与各分类文章数，路由切换或每 60 秒刷新 |
| 适配 | 宽屏通栏、侧边栏贴左；窄屏（≤760px）改为抽屉式导航 |

## 角色与权限

角色保存在 `users.role`，注册账号默认为 `USER`。

| 角色 | 能力 |
| --- | --- |
| USER | 注册登录、写文章投稿、收藏、评论与回复、举报他人评论、删除自己的评论 |
| EDITOR | USER 的全部能力 + 审核投稿 + 删除任意评论 + 处理举报 |
| ADMIN | EDITOR 的全部能力，保留给系统管理使用 |

升级角色见「启动步骤 5」。评论治理入口「举报处理」位于侧边栏「创作空间」，仅 EDITOR / ADMIN 可见。

## 启动步骤

### 0. 一键启动（本机 MySQL，推荐）

根目录的 `dev.ps1` 会读取 `.env`、检查数据库端口、必要时构建后端，然后分别启动前后端并等待就绪：

```powershell
Copy-Item .env.example .env
# 编辑 .env，填入本机 MySQL 的连接信息（本机服务通常为 3306 端口）
.\dev.ps1          # 启动后端 8080 与前端 5173
.\dev.ps1 -Stop    # 停止（按 .local/dev.pids 记录结束整棵进程树）
```

说明：

- 若数据库用 Compose 启动，把 `.env` 里的 `DB_PORT` 改成 `3307` 即可继续使用同一脚本。
- `dev.ps1` 是 PowerShell 5.1 也能运行的脚本，**必须保存为 UTF-8 with BOM**，否则中文注释会被按 GBK 解析而报语法错误。
- 脚本会先探测 `DB_HOST:DB_PORT`，数据库没起来时会提示；然后依次启动后端与前端，并轮询 `/api/health` 与首页。

### 1. 准备环境

- JDK 17 或更高版本，并正确设置 `JAVA_HOME`。
- Node.js 22.13 或更高版本。虽然前端声明最低 20.19，项目使用的 pnpm 11.25 要求至少 22.13。
- Docker 与 Docker Compose；使用已有数据库时可以不安装。

安装项目指定的包管理器：

```bash
npm install -g pnpm@11.25.0
```

没有全局 pnpm 时，可以用 Node 自带的 corepack：`corepack prepare pnpm@11.25.0 --activate`，并把下面命令中的 `pnpm` 换成 `corepack pnpm`。

以下命令均从项目根目录开始。首次运行需要联网下载依赖；Maven Wrapper 会自动下载 Maven，无需另行安装。

### 2. 启动数据库

复制根目录的环境配置：

```bash
# macOS / Linux
cp .env.example .env
```

Windows PowerShell 使用 `Copy-Item .env.example .env`。已有 `.env` 时直接检查配置，不必覆盖。然后运行：

```bash
docker compose up -d mysql
docker compose ps
```

等待 `mysql` 状态变为 `healthy`。示例配置使用数据库 `tech_blog`、账号 `tech_blog`、密码 `tech_blog_dev_password`，宿主机端口为 **3307**。可在 `.env` 中修改；已有数据库卷的账号密码不会因为修改 `.env` 而自动改变。

如果使用本机 MySQL / MariaDB（如 `dev.ps1` 的默认方式），请先创建 `tech_blog` 数据库和有该库建表权限的账号，并在 `.env` 中填写实际连接信息（本机端口通常为 3306）。

### 3. 启动后端

使用 `dev.ps1` 时会自动完成这一步；手动启动时另开一个终端。下面的配置与 Compose 示例一致（3307），使用本机数据库时把 `DB_PORT` 改为 3306。

macOS / Linux：

```bash
cd backend
DB_HOST=localhost DB_PORT=3307 DB_NAME=tech_blog \
DB_USER=tech_blog DB_PASSWORD=tech_blog_dev_password \
sh mvnw spring-boot:run
```

Windows PowerShell：

```powershell
cd backend
$env:DB_HOST = "localhost"
$env:DB_PORT = "3307"
$env:DB_NAME = "tech_blog"
$env:DB_USER = "tech_blog"
$env:DB_PASSWORD = "tech_blog_dev_password"
.\mvnw.cmd spring-boot:run
```

后端默认运行于 `http://localhost:8080`。根目录 `.env` 供 Compose 与 `dev.ps1` 使用，Spring Boot 不会自动读取它，因此手动启动时需要像上面一样设置环境变量。Flyway 自动执行尚未执行的迁移；不要修改已执行的迁移文件。

打开 `http://localhost:8080/api/health`，数据库正常时返回 HTTP 200 和 `{"status":"UP","database":"UP"}`；连接失败时返回 503。

### 4. 启动前端

另开一个终端：

```bash
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

访问 **http://localhost:5173**。开发服务器将 `/api` 请求代理到 `http://localhost:8080`。如修改后端端口，需要同步修改 `frontend/vite.config.ts` 中的代理目标；如修改前端来源，需要同步设置后端的 `APP_CORS_ALLOWED_ORIGIN`。

### 5. 注册与发布第一篇文章

1. 在网页注册作者账号，进入「写文章」，保存正文并投稿。
2. 再注册一个独立的编辑账号。
3. 由数据库管理员连接 `tech_blog`，为该编辑账号执行下列 SQL，用户名必须替换为实际值：

```sql
UPDATE users SET role = 'EDITOR' WHERE username = '实际编辑用户名';
-- 需要管理员（ADMIN）时改成 'ADMIN'
```

使用 Compose 时可运行 `docker compose exec mysql mysql -u root -p tech_blog`，输入 `.env` 中的 `DB_ROOT_PASSWORD` 后执行 SQL。

编辑账号退出后重新登录，进入「审核工作台」通过投稿，文章才会出现在公开列表中。新数据库没有演示文章；编辑不能审核自己的投稿。测试两个账号时，请使用不同浏览器或独立隐私会话。

## 项目结构

```text
tech-blog-platform-main/
├─ backend/                          Spring Boot 服务
│  ├─ src/main/java/com/techblog/platform/
│  │  ├─ controller/                 REST 接口
│  │  ├─ service/                    业务规则（投稿流转、评论、举报）
│  │  ├─ repository/                 Spring Data JPA
│  │  ├─ entity/                     JPA 实体
│  │  ├─ dto/                        请求 / 响应结构
│  │  └─ security/                   会话认证与 CSRF
│  └─ src/main/resources/db/migration/   Flyway 迁移 V1–V7
├─ frontend/                         Vue 3 + Vite 前端
│  ├─ src/views/                     页面级组件
│  ├─ src/components/                文章列表、评论、编辑器等
│  ├─ src/api/                       Axios 封装与接口
│  └─ src/styles/                    全局与页面样式
├─ dev.ps1                           一键启动 / 停止（Windows）
├─ docker-compose.yml                MySQL 8.0（可选）
└─ .env.example                      本地配置模板
```

## 常用检查命令

在 `frontend/` 下运行：

```bash
pnpm test     # 正文渲染与编辑器组件的 Node 测试（依赖 @vue/compiler-sfc 与 jsdom）
pnpm build    # 类型检查 + 生产构建
```

构建结果位于 `frontend/dist/`。部署时还需要配置 `/api` 反向代理和前端路由回退；开发服务器配置不会随静态文件部署。

在 `backend/` 下运行 `sh mvnw test`，Windows 使用 `.\mvnw.cmd test`。默认跳过依赖真实数据库的文章集成测试；如需运行，先准备独立测试数据库，再设置 `ARTICLE_DB_TEST=true`、`ARTICLE_TEST_DB_URL`（`jdbc:mariadb://主机:端口/测试库`）、`ARTICLE_TEST_DB_USER` 和 `ARTICLE_TEST_DB_PASSWORD`。测试会执行迁移，应使用测试库。

本地开发完成后停止前后端终端进程，运行 `docker compose stop mysql` 停止数据库并保留数据；用 `dev.ps1` 启动时直接运行 `.\dev.ps1 -Stop` 即可。
