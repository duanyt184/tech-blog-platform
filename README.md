# 码上 · 西农CS论坛

基于现有 Vue 3 + TypeScript + Spring Boot + MariaDB/MySQL 项目开发的社区论坛。唯一界面参考为 [Cursor Community Forum](https://forum.cursor.com/)；沿用其暖白／暖黑主题、Cursor Gothic 字体、固定顶栏、侧栏、分类与最新话题双栏、话题表格及讨论流。

## 功能与业务边界

- 注册、登录、退出、个人昵称／简介、公开用户主页、活动记录；USER / EDITOR（版主）/ ADMIN 权限。停用和角色修改立即作用于后续请求。
- Markdown 话题编辑与预览，数据库草稿、直接发布、已发布话题直接编辑、软删除；并发修改校验。
- 回复、引用、Markdown 回复预览、回复编辑／删除、话题与回复点赞、话题收藏。互动关系保存在数据库，重复点赞／收藏不会累计。
- 分类与两级子分类、标签和标签分组、最新／热门列表、同分类相关话题。
- 搜索话题标题／正文／回复／作者／标签；作者、分类、标签、日期及有无回复筛选；公开用户、分类及标签搜索。
- 普通帖子直接公开；关键词规则和多链接检测将可疑内容送入治理队列，用户也可举报。版主可忽略举报或移除内容，处理结果和说明持久化。
- 管理用户、帖子、回复、分类、标签及社区规则；管理员可编辑检测短语、开关规则和维护公开社区准则。
- 个人资料支持 PNG/JPEG 头像上传、预览与恢复默认，最大 2 MB／4096×4096 像素；服务端居中裁剪为 256×256 PNG 并存入数据库，页头、帖子、回复和主页统一显示。
- 登录后悬停侧栏 Categories / Tags 标题并点击铅笔，可搜索、筛选全部／已选／未选、取消全选、恢复默认并保存个人导航；各账号配置独立，清空选择与恢复默认是不同操作。
- 手机布局、导航抽屉、浅色／深色／系统主题；主题偏好存于当前浏览器，社区内容和账号数据存于数据库。

不提供独立博客、投稿审批、申请精选、Boards、多人协作编辑或稿件版本管理。旧 `/blog/:id` 地址兼容跳到同一用户主页；旧 `/submit` API 兼容直接发布。数据库 `version` 仅用于防止过期写入，不保存稿件历史快照。

自动检测是可配置的短语匹配及多链接规则，并非能识别所有违规的 AI 审核。可疑内容在人工处理前保持公开；不会把所有正常发帖变成投稿审核。热门按收藏数、阅读数和发布时间排序；相关推荐按同分类最新话题选取，统计均来自实际数据。

## 本地启动

需要 JDK 17+、Node.js 22.13+、pnpm 11.25.0、Maven（或仓库 Wrapper）和 MySQL 8 / MariaDB。已有 `.env` 请勿覆盖。

```bash
cp .env.example .env  # 仅首次配置
# 修改 .env 中数据库连接、账号及密码
# 可选：使用项目的 MySQL 容器
docker compose up -d mysql

cd frontend
pnpm install --frozen-lockfile
cd ..
./dev.sh
```

默认访问 **http://localhost:5173**，健康检查 **http://localhost:8080/api/health**。脚本读取 `.env`，构建后端、等待数据库、启动 Vite。按 Ctrl+C 停止本脚本启动的服务；日志在 `.local/`。

手动运行后端：

```bash
set -a
source .env
set +a
cd backend
mvn spring-boot:run
# 没有系统 Maven 时使用 sh mvnw spring-boot:run
```

另一终端：

```bash
cd frontend
pnpm dev
```

Vite 将 `/api` 代理到后端，默认 `http://localhost:8080`。更换端口时设置 `API_PROXY_TARGET`；前端实际访问域名和端口须与 `APP_CORS_ALLOWED_ORIGIN` 一致，例如 `localhost` 与 `127.0.0.1` 是不同 Origin。

## 数据迁移和管理员

Flyway 自动运行 V1–V15。旧账号、文章、评论和分类保留：V9 将旧待投稿／退回文章转为私人草稿，将旧公开内容纳入论坛；V11 将旧举报（含处理历史）迁入新队列。V12 支持标签分组；V13 更新为 13 个数学与计算机学科分类，并提供 10 个相关标签。已有帖子重归类，正在使用的历史标签保留；V14 为 13 个学科设置独立分类颜色；V15 为个人导航配置和头像添加数据库表。发布前备份已有数据库，不要修改已应用的迁移文件。

新库只创建分类和社区规则，没有默认管理员密码，也没有生产演示帖子。先注册账号，再由数据库管理员初始化首位管理员：

```sql
UPDATE users SET role = 'ADMIN' WHERE username = '你的管理员用户名';
```

刷新页面后进入 `/admin`，此后可在管理后台调整其他账号。注册接口只能创建普通用户，无法通过提交角色字段提权。管理员不能停用自己或降级自己，也不能移除最后一位有效管理员。

## 页面

| 页面 | 地址 |
| --- | --- |
| 首页 / 分类 | `/`、`/categories` |
| 最新 / 热门话题 | `/latest`、`/top` |
| 分类／标签过滤 | `/articles?category=软件工程`、`/articles?tag=testing` |
| 话题 / 编辑器 | `/t/:id`、`/write/:id?` |
| 搜索 / 标签 | `/search`、`/tags` |
| 个人发帖 / 草稿 / 收藏 | `/my-articles`、`/drafts`、`/bookmarks` |
| 用户主页 / 活动 | `/u/:id`、`/u/:id?tab=activity` |
| 个人资料 / 回复 | `/settings`、`/comments` |
| 内容治理 / 管理后台 | `/review`、`/admin` |
| 社区准则 | `/guidelines` |

## 验证

```bash
cd frontend
pnpm test
pnpm build
cd ../backend
mvn test
```

常规后端测试会跳过数据库集成测试。完整数据库测试和真实浏览器测试的环境要求、命令及截图见 [docs/TESTING.md](docs/TESTING.md)。测试账号与示例话题只由测试脚本写入指定的隔离测试实例，不在生产启动时自动生成。

界面对比记录见 [docs/VISUAL_REVIEW.md](docs/VISUAL_REVIEW.md)。原站帖子、用户头像和当前数据会变化，本项目不复制用户账号或伪造原站统计。原站登录采用其自身账户体系；本项目使用独立账号与 Session。因此不能将截图相似等同于像素差异为零或原站全部服务能力完全一致。

## 部署

```bash
cd frontend
pnpm build
cd ../backend
mvn -DskipTests package
java -jar target/tech-blog-backend-0.1.0-SNAPSHOT.jar
```

生产将 `frontend/dist/` 放入静态服务器，配置 Vue 路由回退到 `index.html`，同域代理 `/api` 到后端。使用真实数据库配置、HTTPS，并设置 `SERVER_SERVLET_SESSION_COOKIE_SECURE=true`。当前 Session 适用于单实例，服务重启需要重新登录，帖子和互动数据不受影响。多实例部署须共享会话或采用粘性会话。备份、邮件服务、对象存储、外部 SSO 不包含在此本地实现中。
