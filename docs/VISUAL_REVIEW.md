# 界面复核记录

最新界面修订：分类使用 13 种独立颜色，移除侧栏底部品牌文字；高级搜索、标签及列表图标采用原站公开 SVG 图形。高级搜索直接打开已展开的筛选项。New Topic 改为底部编辑面板，提供实时预览、收起／恢复、全屏及拖动调高，手机端可切换正文与预览。见 [新版编辑器](screenshots/refinements/editor.png)、[深色](screenshots/refinements/editor-dark.png)、[手机版](screenshots/refinements/editor-mobile.png)、[交互检查](screenshots/refinements/ui-checks.json) 和 [17 组浏览器流程回归](screenshots/refinements/result.json)。原站需要登录的发帖编辑器未获得完整访问；该布局是按论坛编辑器模式实现的近似，不能视为逐像素核验结果。

2026-11-09 后续定制：按用户要求改为“码上”及黑白代码括号标志，移除页头 Guides / Help，分类更新为 13 个数学与计算机学科，新增 10 个相关标签。最新截图见 [浅色分类页](screenshots/mashang/categories-light.png)、[深色分类页](screenshots/mashang/categories-dark.png)、[手机页面](screenshots/mashang/mobile.png) 和 [检查结果](screenshots/mashang/result.json)。以下原站对比保留为早期布局参考。

唯一界面参考：[Cursor Community Forum](https://forum.cursor.com/)。原站公开页面截图于 2026-11-08，本地最终浏览器截图于 2026-11-09。原站只读访问，没有创建账号、发帖或修改数据。本地使用隔离 MariaDB 测试数据。

打开 [并排截图查看器](screenshots/compare.html)，选择页面即可查看原站和本地截图。截图不是整站复制或零像素差异证明：两侧帖子、用户、时间和统计不同，无法用全图像素差计算有意义的相似率。

## 已逐页检查的公开页面

桌面截图宽度 1440px，手机宽度 390px。复核重点为顶栏、侧栏、内容起点、字体、背景、边框、间距、表格、搜索布局及手机溢出。

| 页面 | 原站 | 本地 | 调整与核验 |
| --- | --- | --- | --- |
| 首页 | [截图](screenshots/reference/home.png) | [截图](screenshots/local/home.png) | 分类与最新话题双栏、热门卡片、分类颜色、侧栏分组 |
| 分类 | [截图](screenshots/reference/categories.png) | [截图](screenshots/local/categories.png) | 分类行、子分类、话题计数和最新话题 |
| 最新 | [截图](screenshots/reference/latest.png) | [截图](screenshots/local/latest.png) | 标题与标签层级、回复／浏览／活跃列、行高 |
| 话题 | [截图](screenshots/reference/topic.png) | [截图](screenshots/local/topic.png) | 正文宽度、用户信息、Markdown、互动操作、回复流 |
| 搜索 | [截图](screenshots/reference/search.png) | [截图](screenshots/local/search.png) | 搜索内容区宽度与位置、结果页签、筛选和结果摘要 |
| 标签 | [截图](screenshots/reference/tags.png) | [截图](screenshots/local/tags.png) | 标签分组和文本列表布局 |
| 用户主页 | [截图](screenshots/reference/profile.png) | [截图](screenshots/local/profile.png) | 个人简介、统计、热门话题与回复双栏、活动切换 |
| 深色首页 | [截图](screenshots/reference/home-dark.png) | [截图](screenshots/local/home-dark.png) | 暖黑背景、文字和边框层级、主题刷新后保留 |
| 手机首页 | [截图](screenshots/reference/home-mobile.png) | [截图](screenshots/local/home-mobile.png) | 顶栏压缩、导航抽屉、内容单栏 |

原站公开样式用于校准：50px 顶栏、桌面侧栏约 299px、内容左起约 355px；正文约 15px、话题标题行约 16px；浅色背景 `#f7f7f4`、主文字 `#26251e`、深色背景 `#14120b`；字体使用原站公开加载的 Cursor Gothic 文件。搜索和话题正文分别按约 848px 和 756px 内容宽度校准。

## 扩展页面与交互

本地另外保存了编辑器、草稿、我的帖子、收藏、个人设置、活动、治理队列及六个管理模块截图。原站登录后的页面没有可用账号，无法逐页获取参考，因此这些页面沿用公开设计语言，不能宣称其与原站后台像素一致。原站登录入口截图保留在 [login.png](screenshots/reference/login.png)；本地注册登录使用独立账号体系，没有仿造原站 SSO。

真实浏览器覆盖注册、数据库草稿刷新、直接发布、编辑、回复／引用、点赞／收藏、举报、规则检测、管理员处理、主题偏好和导航。手机端额外检查最新列表、话题、搜索、标签、主页、登录与注册，没有横向溢出。见 [16 组浏览器检查结果](screenshots/local/result.json)、[重启持久化验证](screenshots/local/persistence.json) 和 [测试复现说明](TESTING.md)。

## 已知差异与边界

- 头像使用昵称首字母，图标为本地实现；真实帖子、账号、头像、统计和动态推荐没有复制。测试截图中的重复话题来自多轮测试，不会在生产初始化时生成。
- 公开页面主要使用英文，部分账号／编辑／管理操作使用中文；原站可能随访问时间、登录状态和设备改变布局。
- 个人中心和管理后台以真实功能为先，使用公开页面的字体、配色和组件样式。没有以不可访问的原站后台作为已验证依据。
- 自动检测是管理员配置的短语和多链接规则，不是外部 AI 内容审查服务。热门与相关推荐来自本地数据库查询。
- 本次交付为可运行的本地项目；未配置生产域名、邮件投递、对象存储或外部账户服务。

这些限制不影响已测试的本地论坛流程，但“视觉高度接近”不应被解读为原站全部能力、数据与界面完全一致。
