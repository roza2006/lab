# 智慧医疗在线问诊与处方流转平台

> 软件工程课程实验小组项目。当前为 `clean-start` 基线，业务代码按 6 轮提交计划逐步加入。

- 课程：软件工程实验
- 题目：智慧医疗在线问诊与处方流转平台
- 团队：成员 A–F（提交前替换为真实姓名与 GitHub 用户名）
- 技术栈：Java 17 + Spring Boot 3 + MySQL 8 + Redis 7；Vue 3 + TypeScript + Vite
- 提交计划：[COMMIT_PLAN.md](COMMIT_PLAN.md)
- 文档索引：[docs/README.md](docs/README.md)（第 1 轮创建）

## 目录结构

```text
docs/        需求、设计、测试、过程、安全、报告
backend/     模块化单体后端（按限界上下文分包）
frontend/    患者端、医生端、药师端、管理端
database/    schema、migration、seed
deploy/      docker-compose、Nginx、部署说明
.github/     Issue/PR 模板与 CI
```

## 协作约定

1. 每个成员使用自己的 GitHub 账号提交，不共用账号、不代 push。
2. 分支：`feat/*`、`fix/*`、`docs/*`；合并前至少 1 人 review。
3. 提交信息遵循 `COMMIT_PLAN.md` 中的 Conventional Commits 格式。
4. AI 辅助开发需在 `docs/ai-usage.md` 记录工具、范围和人工修改；不伪造提交历史。
5. 不在仓库中提交真实患者信息、密钥、`.env`、构建产物。
