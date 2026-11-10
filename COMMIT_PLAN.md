# 6 轮提交计划（6 人 × 每轮至少 1 次提交）

> 目标：让 6 个成员都留下可解释、可 review、可回滚的真实提交，而不是伪造时间线。
> 每人用自己的 GitHub 账号；每轮每人至少 1 个 commit，建议开 1 个 PR。
> 成员：刘飞燕、李梓语、代香晰、郝佳鑫、魏宇佳、班国勇。若只有 5 人，把班国勇的任务并入刘飞燕/魏宇佳。

## 成员固定

| 成员 | 主责 | 典型目录 |
|---|---|---|
| 刘飞燕 | 架构、认证、集成 | `backend/.../identity`、`docs/design` |
| 李梓语 | 问诊、处方域 | `backend/.../consultation`、`prescription` |
| 代香晰 | 前端四端 | `frontend/src` |
| 郝佳鑫 | 测试、安全 | `backend/src/test`、`docs/test` |
| 魏宇佳 | 数据库、部署 | `database`、`deploy` |
| 班国勇 | 文档、流程、发布 | `docs/process`、`README.md`、`.github` |

## 分支与提交格式

```text
分支：feat/<模块>-<短描述>、fix/<问题>、docs/<主题>
提交：<type>(<scope>): <what> [R<n>][<member>]
示例：feat(auth): add login and JWT [R3][刘飞燕]
类型：feat / fix / test / docs / refactor / chore / ci / db
```

每次 PR 描述必须写：改了什么、怎么验证、影响哪些表/接口/测试、是否 AI 辅助。

---

## 第 1 轮：仓库治理与需求基线

| 成员 | 提交目录/文件 | 做什么 | commit message |
|---|---|---|---|
| 刘飞燕 | `README.md`、`.gitignore`、`docs/README.md` | 项目说明、目录规范、分支模型 | `chore: bootstrap repository and docs index [R1][刘飞燕]` |
| 李梓语 | `docs/requirements/requirements.md` | 范围、角色、约束、非功能需求 | `docs(req): add scope and actor analysis [R1][李梓语]` |
| 代香晰 | `docs/requirements/use-cases.md` | 29 个用例、优先级、权限边界 | `docs(req): add use case inventory [R1][代香晰]` |
| 郝佳鑫 | `docs/design/domain-model.md` | 聚合根、实体、值对象、关系 | `docs(design): add domain model draft [R1][郝佳鑫]` |
| 魏宇佳 | `database/schema.sql` | 24 张表清单与核心字段草案 | `db: add initial schema draft [R1][魏宇佳]` |
| 班国勇 | `docs/test/test-plan.md`、`.github/ISSUE_TEMPLATE/task.md`、`.github/pull_request_template.md` | 测试策略、Issue/PR 模板 | `test: add test strategy and collaboration templates [R1][班国勇]` |

## 第 2 轮：架构、接口与工程骨架

| 成员 | 提交目录/文件 | 做什么 | commit message |
|---|---|---|---|
| 刘飞燕 | `docs/design/architecture.md`、`docs/design/adr/ADR-001.md` | DDD 分层、模块化单体决策 | `docs(design): add layered architecture and ADR [R2][刘飞燕]` |
| 李梓语 | `docs/design/api-contract.md`、`backend/src/main/resources/openapi.yaml` | 认证/问诊/处方 API 契约 | `api: add v1 contract for auth and consultation [R2][李梓语]` |
| 代香晰 | `frontend/src/router`、`frontend/src/views/*` | 四端路由与页面骨架 | `feat(web): add role-based app skeleton [R2][代香晰]` |
| 郝佳鑫 | `backend/pom.xml`、`backend/src/main/java/.../Application.java` | Spring Boot 工程骨架 | `build: add Spring Boot backend skeleton [R2][郝佳鑫]` |
| 魏宇佳 | `database/schema.sql`、`database/seed.sql`、`database/migrations/V002__core.sql` | 核心表、索引、演示种子 | `db: add core tables and seed data [R2][魏宇佳]` |
| 班国勇 | `deploy/docker-compose.yml`、`.github/workflows/ci.yml` | 本地编排与 CI | `ci: add local compose and build workflow [R2][班国勇]` |

## 第 3 轮：认证与图文问诊

| 成员 | 提交目录/文件 | 做什么 | commit message |
|---|---|---|---|
| 刘飞燕 | `backend/.../identity` | 登录、JWT、角色权限 | `feat(auth): implement login and JWT [R3][刘飞燕]` |
| 李梓语 | `backend/.../consultation` | 会话、消息、接诊 | `feat(consult): add session and message flow [R3][李梓语]` |
| 代香晰 | `frontend/src/views/patient` | 患者登录、发起问诊、会话页 | `feat(web): add patient consultation flow [R3][代香晰]` |
| 郝佳鑫 | `backend/src/test/.../AuthTest.java`、`ConsultationTest.java` | 登录/接诊/越权用例 | `test(auth): add login and consultation cases [R3][郝佳鑫]` |
| 魏宇佳 | `database/migrations/V003__auth_consult.sql`、`docs/design/er.md` | 认证/问诊索引与 ER 更新 | `db: add auth and consultation migrations [R3][魏宇佳]` |
| 班国勇 | `docs/process/sprint-01.md`、`docs/process/review-01.md` | 第 1 次迭代记录与评审 | `docs(process): record sprint 1 review [R3][班国勇]` |

## 第 4 轮：处方、审核与状态机

| 成员 | 提交目录/文件 | 做什么 | commit message |
|---|---|---|---|
| 刘飞燕 | `backend/.../prescription` | 处方聚合、明细、状态迁移 | `feat(rx): add prescription aggregate and state machine [R4][刘飞燕]` |
| 李梓语 | `backend/.../prescription` | 药师审核、签名、驳回 | `feat(review): add pharmacist approve and reject [R4][李梓语]` |
| 代香晰 | `frontend/src/views/doctor`、`frontend/src/views/pharmacist` | 开方页、审核页 | `feat(web): add prescription and review pages [R4][代香晰]` |
| 郝佳鑫 | `backend/src/test/.../PrescriptionStateTest.java` | 8 状态正向/非法迁移 | `test(rx): cover legal and illegal transitions [R4][郝佳鑫]` |
| 魏宇佳 | `database/migrations/V004__prescription.sql`、`docs/design/audit.md` | 处方表、审计表、版本字段 | `db: add prescription and audit tables [R4][魏宇佳]` |
| 班国勇 | `docs/security/threat-model.md`、`docs/security/checklist.md` | STRIDE、隐私检查表 | `docs(security): add STRIDE model and checklist [R4][班国勇]` |

## 第 5 轮：流转、核销与部署

| 成员 | 提交目录/文件 | 做什么 | commit message |
|---|---|---|---|
| 刘飞燕 | `backend/.../circulation` | 流转单、药房接方 | `feat(circ): add circulation order and pharmacy accept [R5][刘飞燕]` |
| 李梓语 | `backend/.../circulation` | 核销、幂等键、发药记录 | `feat(redeem): add idempotent redeem API [R5][李梓语]` |
| 代香晰 | `frontend/src/views/pharmacist`、`frontend/src/views/patient` | 药房核销页、患者追踪 | `feat(web): add redeem and prescription tracking [R5][代香晰]` |
| 郝佳鑫 | `backend/src/test/.../CirculationIdempotencyTest.java` | 重放、并发、越权 | `test(circ): add replay and idempotency cases [R5][郝佳鑫]` |
| 魏宇佳 | `deploy/`、`database/seed.sql` | 演示部署、种子数据 | `deploy: add demo compose and seed data [R5][魏宇佳]` |
| 班国勇 | `docs/process/sprint-03.md`、`docs/process/release-notes.md` | 第 3 次迭代记录与发布说明 | `docs(process): record sprint 3 and release notes [R5][班国勇]` |

## 第 6 轮：测试、修复、报告与发布

| 成员 | 提交目录/文件 | 做什么 | commit message |
|---|---|---|---|
| 刘飞燕 | `docs/test/test-report.md`、`backend/...` 修复 | 回归执行、缺陷修复记录 | `test: execute regression and record results [R6][刘飞燕]` |
| 李梓语 | `backend/.../security`、`backend/.../prescription` | 越权/限流/状态边界修复 | `fix(security): harden auth and rate limit [R6][李梓语]` |
| 代香晰 | `frontend/src` | 演示流程打磨、截图 | `feat(web): polish demo flow and screenshots [R6][代香晰]` |
| 郝佳鑫 | `docs/test/acceptance.md`、`backend/src/test` | 最终验收用例与覆盖统计 | `test: add final acceptance cases [R6][郝佳鑫]` |
| 魏宇佳 | `README.md`、`docs/report/` | 最终文档、部署说明、报告链接 | `docs: finalize README and report links [R6][魏宇佳]` |
| 班国勇 | `docs/demo/demo-script.md`、tag `v1.0.0` | 答辩脚本、版本标签 | `chore(release): v1.0.0 and demo script [R6][班国勇]` |

---

## 每轮检查清单

- [ ] 6 个人都用自己的账号提交（`git log --format='%an <%ae>'` 可核对）
- [ ] 每人至少 1 个 commit，且 commit 能对应到 Issue/PR
- [ ] 至少 1 次跨成员 review
- [ ] 新增/修改的接口有测试或手动验证记录
- [ ] `docs/ai-usage.md` 已记录本轮 AI 使用与人工修改
- [ ] 没有提交 `.env`、密钥、真实患者数据、`target/`、`node_modules/`

## 如果只有 5 人

把 F 的任务按轮次并入 A/E：
- R1：A 兼做测试策略模板
- R2：E 兼做 CI
- R3/R5：A 兼做过程记录
- R4：E 兼做安全文档
- R6：E 兼做发布脚本
