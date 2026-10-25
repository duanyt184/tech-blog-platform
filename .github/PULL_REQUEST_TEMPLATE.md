## 关联 Issue

Closes #

## 改动范围

<!-- 简单说明这个 PR 做了什么，涉及哪些模块 -->

- [ ] 后端（Controller / Service / Repository / 迁移）
- [ ] 前端（页面 / 组件 / API / 类型）
- [ ] 文档

## 数据库迁移

<!-- 有则写明迁移文件名，例如 V2__add_xxx.sql；没有则写「无」 -->

无

## 验证方式

<!-- 贴出实际执行过的命令与结果，示例：

mvnw.cmd test        → Tests run: 19, Failures: 0
npm run build        → ✓ built in 566ms
curl /api/auth/me    → 200 alice

-->

## 自查

- [ ] 已从最新 `main` 拉分支，且只包含本 Issue 的改动
- [ ] 未提交 `target`、`dist`、`node_modules`、`.env`、IDE 配置
- [ ] 未修改已执行过的 Flyway 迁移文件
- [ ] 接口遵循现有 DTO 与错误响应约定
- [ ] 新增的接口已处理未登录（401）与无权限（403）两种情况
