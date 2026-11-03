# 智慧医疗在线问诊与处方流转平台

> 软件工程课程实验小组项目：连接患者、医生、药师与合作药房，完成“图文问诊—病历—电子处方—药师审核—处方流转—药房核销”的业务闭环。

## 1. 项目简介

本项目面向常见病复诊与轻症健康咨询，提供在线图文问诊、电子处方开具与审核、处方流转至合作药房、身份核验与核销、用药指导等功能。系统不替代实体医疗机构的首次诊断，不承担急诊救治职能；首版不接入医保结算、在线支付与检验检查预约。

## 2. 核心业务闭环

```text
患者发起图文问诊
      ↓
医生接诊 / 书写病历
      ↓
医生创建处方草稿 → 提交
      ↓
药师审核 → 通过 / 驳回
      ↓
处方流转至合作药房
      ↓
药房核验身份 → 调剂发药 → 核销
      ↓
患者查看处方与用药指导
```

处方状态按 `DRAFT → PENDING_REVIEW → APPROVED → CIRCULATED → REDEEMED` 管理，并处理驳回、取消、过期等异常分支。

## 3. 系统角色

| 角色 | 主要职责 |
|---|---|
| 患者 | 实名认证、发起问诊、查看处方与用药指导 |
| 医生 | 接诊、书写病历、创建/提交处方 |
| 药师 | 处方审核、通过/驳回、药品目录维护 |
| 管理员 | 用户、角色、权限、药品目录、审计与统计 |
| 合作药房 | 接方、身份核验、发药、核销回传 |

## 4. 技术栈

- 后端：Java 17、Spring Boot 3、Spring Security 6、MyBatis-Plus、MySQL 8、Redis 7
- 前端：Vue 3、TypeScript、Vite、Element Plus、Pinia
- 文档：Markdown、OpenAPI 3.0、Mermaid / draw.io
- 部署：Docker Compose、Nginx

## 5. 目录结构

```text
docs/        需求、设计、测试、过程、安全、报告、演示
backend/     模块化单体后端（按限界上下文分包）
frontend/    患者端、医生端、药师端、管理端
database/    schema、migration、seed
deploy/      docker-compose、Nginx、部署说明
.github/     Issue/PR 模板与 CI
```

## 6. 项目文档

- [提交计划](COMMIT_PLAN.md)：6 轮 × 6 人，每轮的任务、目录与 commit message
- [文档索引](docs/README.md)：需求、设计、测试、过程、安全、报告
- [AI 使用记录](docs/ai-usage.md)：工具、范围、人工修改与验证
- [远程仓库重置说明](RESET_GUIDE.md)：已完成记录与回滚参考

## 7. 当前进度

- [x] 仓库重置与目录骨架
- [x] R1-A：README、目录规范、文档索引
- [ ] R1-B：需求范围与角色
- [ ] R1-C：用例清单
- [ ] R1-D：领域模型
- [ ] R1-E：数据库表清单
- [ ] R1-F：测试计划与协作模板
- [ ] R2–R6：架构、编码、测试、发布

## 8. 本地开发

> 工程骨架将在第 2 轮加入；当前仓库以文档和目录为主。

后续计划：

```bash
# 后端（第 2 轮加入 pom.xml 后）
cd backend
mvn spring-boot:run

# 前端（第 2 轮加入 package.json 后）
cd frontend
pnpm install
pnpm dev

# 数据库
mysql -u root -p < database/schema.sql
```
