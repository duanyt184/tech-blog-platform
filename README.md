# 码上 · 技术博客平台

支持文章创作、投稿审核和公开阅读的前后端分离项目。功能现状与后续建议见 [FEATURES.md](FEATURES.md)。

## 技术栈

| 部分 | 技术 |
| --- | --- |
| 前端 | Vue 3、TypeScript 5.9、Vite 8、Vue Router 4、Axios、Element Plus |
| 正文渲染 | Marked、KaTeX、highlight.js、DOMPurify |
| 后端 | Java 17 编译目标、Spring Boot 3.5.16、Spring Security、Spring Data JPA、Bean Validation |
| 数据库 | MySQL / MariaDB；使用 MariaDB JDBC 驱动，Flyway 管理表结构迁移 |
| 构建与测试 | pnpm 11.25.0、Maven Wrapper 3.9.9、Node.js Test Runner、jsdom、JUnit、Mockito |
| 本地数据库容器 | Docker Compose，仓库配置提供 MySQL 8.0 |

源码位于 `frontend/` 和 `backend/`。数据库迁移位于 `backend/src/main/resources/db/migration/`，启动后端时自动执行，无需手工建表。

## 启动步骤

### 1. 准备环境

- JDK 17 或更高版本，并正确设置 `JAVA_HOME`。
- Node.js 22.13 或更高版本。虽然前端声明最低 20.19，项目使用的 pnpm 11.25 要求至少 22.13。
- Docker 与 Docker Compose；使用已有数据库时可以不安装。

安装项目指定的包管理器：

```bash
npm install -g pnpm@11.25.0
```

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

如果使用已有 MySQL / MariaDB，请先创建 `tech_blog` 数据库和有该库建表权限的账号，并在下一步填写实际连接信息。本机数据库端口通常为 3306。

### 3. 启动后端

另开一个终端。下面的配置与上一步 Compose 示例一致；自定义过数据库配置时，需要同步调整。

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

后端默认运行于 `http://localhost:8080`。根目录 `.env` 供 Compose 使用，Spring Boot 不会自动读取它，因此需要像上面一样设置环境变量。Flyway 自动执行 V1–V3 中尚未执行的迁移；不要修改已执行的迁移文件。

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
```

使用 Compose 时可运行 `docker compose exec mysql mysql -u root -p tech_blog`，输入 `.env` 中的 `DB_ROOT_PASSWORD` 后执行 SQL。

编辑账号退出后重新登录，进入「审核工作台」通过投稿，文章才会出现在公开列表中。新数据库没有演示文章；编辑不能审核自己的投稿。测试两个账号时，请使用不同浏览器或独立隐私会话。

## 常用检查命令

在 `frontend/` 下运行：

```bash
pnpm test
pnpm build
```

构建结果位于 `frontend/dist/`。部署时还需要配置 `/api` 反向代理和前端路由回退；开发服务器配置不会随静态文件部署。

在 `backend/` 下运行 `sh mvnw test`，Windows 使用 `.\mvnw.cmd test`。默认跳过依赖真实数据库的文章集成测试；如需运行，先准备独立测试数据库，再设置 `ARTICLE_DB_TEST=true`、`ARTICLE_TEST_DB_URL`（`jdbc:mariadb://主机:端口/测试库`）、`ARTICLE_TEST_DB_USER` 和 `ARTICLE_TEST_DB_PASSWORD`。测试会执行迁移，应使用测试库。

本地开发完成后停止前后端终端进程，运行 `docker compose stop mysql` 停止数据库并保留数据。
