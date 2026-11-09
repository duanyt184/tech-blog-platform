# 验证与复现

2026-11-09 验收结果：前端 13 项测试、后端 38 项测试（含 19 项真实数据库集成测试）全部通过；前端生产构建通过；真实 Chrome 完成 16 组流程检查且没有运行时异常。后端重启后帖子、回复、点赞和收藏一致，见 [持久化验证](screenshots/local/persistence.json)。所有写入测试均使用独立临时数据库，没有修改已有业务数据库。

## 前端

```bash
cd frontend
pnpm test
pnpm build
```

`test` 包含 13 项正文安全渲染、Markdown 编辑器和点赞交互测试。构建包含 TypeScript 检查。

## 后端

```bash
cd backend
mvn test
```

默认运行 19 项单元／控制器测试，跳过 19 项数据库集成测试。完整测试必须连接**独立测试库**，不能使用生产库：

```bash
ARTICLE_DB_TEST=true \
ARTICLE_TEST_DB_URL=jdbc:mariadb://127.0.0.1:3309/forum_unit_test \
ARTICLE_TEST_DB_USER=forum_test \
ARTICLE_TEST_DB_PASSWORD=local-forum-tests-only \
mvn clean verify
```

集成测试使用事务回滚。覆盖认证／CSRF、草稿隐私、直接发布、编辑归属与过期写入、点赞收藏去重、阅读和收藏不改变帖子活跃时间、资料隐私、角色和停用实时生效、分类层级、标签分组、关键词检测、用户举报与管理员处理、回复搜索和删除后隐藏、活动与主页摘要。

## 真实浏览器

测试脚本为 `frontend/tests/forum.e2e.mjs`，使用 Playwright Core 驱动本机 Chrome，不需要自动下载浏览器。

先准备隔离 MariaDB 实例。下例只在 `/tmp` 创建数据：

```bash
mariadb-install-db --datadir=/tmp/cursor-forum-test-db \
  --auth-root-authentication-method=normal --skip-test-db
mariadbd --datadir=/tmp/cursor-forum-test-db \
  --socket=/tmp/cursor-forum-test.sock --port=3309 \
  --bind-address=127.0.0.1 --pid-file=/tmp/cursor-forum-test.pid
```

另一终端初始化**两个不同测试库**，避免浏览器示例帖子影响集成测试计数：

```bash
mariadb --socket=/tmp/cursor-forum-test.sock -u root <<'SQL'
CREATE DATABASE forum_test CHARACTER SET utf8mb4;
CREATE DATABASE forum_unit_test CHARACTER SET utf8mb4;
CREATE USER 'forum_test'@'127.0.0.1' IDENTIFIED BY 'local-forum-tests-only';
GRANT ALL ON forum_test.* TO 'forum_test'@'127.0.0.1';
GRANT ALL ON forum_unit_test.* TO 'forum_test'@'127.0.0.1';
SQL
```

构建后，启动连接 `forum_test` 的后端：

```bash
DB_HOST=127.0.0.1 DB_PORT=3309 DB_NAME=forum_test \
DB_USER=forum_test DB_PASSWORD=local-forum-tests-only \
SERVER_PORT=8082 APP_CORS_ALLOWED_ORIGIN=http://localhost:5174 \
java -jar backend/target/tech-blog-backend-0.1.0-SNAPSHOT.jar
```

另一终端启动前端：

```bash
cd frontend
API_PROXY_TARGET=http://localhost:8082 pnpm dev --host 127.0.0.1 --port 5174
```

运行浏览器测试：

```bash
cd frontend
FORUM_TEST_URL=http://localhost:5174 \
FORUM_TEST_DB_SOCKET=/tmp/cursor-forum-test.sock \
FORUM_TEST_DB_NAME=forum_test \
CHROME_PATH=/usr/bin/google-chrome-stable \
pnpm test:e2e
```

脚本要求回环 URL、`/tmp/` 数据库 socket 和 `forum_*test*` 格式库名。它会创建临时测试用户、通过数据库将其中一个用户提升为管理员、发布示例话题，并测试注册、草稿刷新、直接发布、修改、回复／引用、点赞／收藏、举报与规则检测、主题保存和手机导航。请勿把测试配置指向真实服务。重复运行会留下新的独立测试数据；可在停止测试实例后清理整个测试库。

截图存入 `docs/screenshots/local/`；最终检查项和测试记录 ID 存入 `result.json`。原站只读截图存于 `docs/screenshots/reference/`。这些截图是测试产物，不是生产种子数据。

浏览器测试与界面编辑应分开执行，避免 Vite HMR 在路由加载时引入测试竞态。结束测试后可停止上述前端、后端和临时数据库。帖子、回复和账号持久化验证通过服务重启前后读取同一记录完成，Session 本身在重启后失效是当前设计。
