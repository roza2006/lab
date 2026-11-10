# 第 1 次迭代记录（Sprint 1：R1 需求基线 + R2 架构与骨架）

| 版本 | 日期 | 负责人 | 状态 | 关联文档 |
|---|---|---|---|---|
| v1.0 | 2026-11-10 | 班国勇 | 待评审 | `docs/test/test-plan.md`、`docs/design/architecture.md`、`docs/design/api-contract.md`、`docs/ai-usage.md` |

> 数据口径：全部取自 `main` 分支的实际提交与已合并 PR（`git log`、`git shortlog`、Merge PR 记录），不写计划外的东西。

## 1. 迭代范围与时长

Sprint 1 覆盖提交计划的前两轮：

- **R1 仓库治理与需求基线**：目录规范、分支模型、需求范围与角色、29 个用例、领域模型、24 张表草案、测试策略与协作模板；
- **R2 架构、接口与工程骨架**：分层架构与 ADR、API v1.0 契约、Spring Boot 骨架、前端四端骨架、数据库交付物、本地编排与 CI。

时间：**2026-11-03 11:21 ~ 2026-11-10 11:01**（8 个自然日，含周末与两次远程仓库重置）。

## 2. 迭代目标与达成情况

| 编号 | 目标（验收口径） | 达成 | 说明 |
|---|---|---|---|
| G1 | 仓库可协作：目录骨架、分支命名、Issue/PR 模板、文档索引可用 | ✅ | `README.md`、`docs/README.md`、`.github/*` 模板、`.gitignore` 就位 |
| G2 | 需求基线可评审：范围、角色、用例、领域模型、表清单齐全且互相可追踪 | ✅ | 需求/用例/领域模型/24 表 DDL 均已提交，追踪关系见 `docs/README.md` §2 |
| G3 | 架构与接口冻结：分层规则 + ADR + API v1.0 | ✅ | `architecture.md`、`ADR-001.md`、`api-contract.md` v1.0；前端已按契约返工对齐（PR #19） |
| G4 | 工程骨架可运行：后端编译通过、前端可构建、本地可编排、提交有自动化检查 | ✅（有保留） | `mvn -B -DskipTests package` 成功；`pnpm build` 通过；compose 仅经 CI 静态校验，本机未实测 |

## 3. 计划 vs 实际

| 轮次 | 计划产出 | 负责人 | 实际结果 | 证据 |
|---|---|---|---|---|
| R1 | `README.md`、`.gitignore`、`docs/README.md` | 刘飞燕 | 完成 | PR #1/#2 之后的重置提交 `chore: bootstrap repository` |
| R1 | `docs/requirements/requirements.md` | 李梓语 | 完成 | PR #…/`docs/r1b-requirements`，11-06 合并 |
| R1 | `docs/requirements/use-cases.md`（29 用例） | 代香晰 | 完成 | PR #5 |
| R1 | `docs/design/domain-model.md` | 郝佳鑫 | 完成（v0.1） | PR #3 |
| R1 | `database/schema.sql` + ER/表清单/数据字典 | 魏宇佳 | 完成 | PR #6、#7、#15 |
| R1 | `docs/test/test-plan.md` + Issue/PR 模板 | 班国勇 | 完成 | PR #8 |
| R2 | `docs/design/architecture.md` + `ADR-001.md` | 刘飞燕 | 完成（评审中） | 11-10 提交 |
| R2 | `docs/design/api-contract.md` + `openapi.yaml` | 李梓语 | 完成 v1.0 | `docs/design/api-contract.md`、`backend/src/main/resources/openapi.yaml` |
| R2 | 后端骨架（`pom.xml`、启动类、`application.yml`） | 郝佳鑫 | 完成 | PR #10 |
| R2 | 前端四端骨架（路由、页面、请求封装） | 代香晰 | 完成 | PR #12 |
| R2 | 本地编排 + CI（compose、Dockerfile、workflow） | 班国勇 | 完成 | PR #14 |
| R2 | 数据库交付物（部署图、表总览、数据字典） | 魏宇佳 | 完成 | PR #15 |

## 4. 交付物清单

| 目录 | 内容 |
|---|---|
| `docs/requirements/` | `requirements.md`（范围/角色/约束/非功能）、`use-cases.md`（29 用例、权限矩阵） |
| `docs/design/` | `domain-model.md`、`architecture.md`、`api-contract.md`、`adr/ADR-001.md` |
| `docs/database/` | 表清单、数据字典、ER/总览/部署图、命名规范 |
| `docs/test/` | `test-plan.md`（策略、层级、环境、越权与安全用例要求） |
| `docs/process/` | `sprint-01.md`（本文）、`review-01.md` |
| `backend/` | `pom.xml`、`PlatformApplication`、`application.yml`、`openapi.yaml`、按上下文分包的包骨架 |
| `frontend/` | Vite + Vue 3 + TS + Element Plus + Pinia 骨架、18 个页面、角色路由守卫、mock 服务 |
| `database/` | `schema.sql`（24 表）、`README.md`、`migrations/.gitkeep`、`conventions.md` |
| `deploy/` | `docker-compose.yml`（MySQL 8 + Redis 7 + 后端）、`README.md`（部署结构 + 本地编排） |
| `.github/` | Issue 模板、PR 模板、`workflows/ci.yml` |
| `scripts/` | `check_schema.py`、ER/总览/部署图渲染脚本、数据字典生成脚本 |

## 5. 过程与质量数据

| 指标 | 数值 | 说明 |
|---|---|---|
| `main` 提交数 | 53（含 14 个合并提交） | 2026-11-03 ~ 2026-11-10 |
| 已合并 PR | 14 | #3–#8、#10、#12、#14–#16、#18–#20 |
| 待合并 PR | 1 | #21（`docs(ai)` 记录，#14 合并后才推送） |
| 参与成员 | 6 人 + 仓库负责人 | 刘飞燕、李梓语、代香晰、郝佳鑫、魏宇佳、班国勇 |
| 已合并分支 | 15 个远程分支 | 均有对应 PR，无直接推 `main` |
| 自动化 | CI 2 个 job | `backend (mvn verify)`、`deploy (compose config)`，11-10 首次接入 |
| 合并冲突 | 1 次 | `deploy/README.md` add/add（详见 §6） |
| 提前完成 | 认证与问诊测试 22 例 | AuthTest 12 例 + ConsultationTest 10 例（R3 任务前移） |

## 6. 偏差、问题与处理

| 编号 | 现象 | 原因 | 处理 | 状态 |
|---|---|---|---|---|
| D-01 | 提交信息里混用角色字母（`[R1][F]`）与真实姓名 | R1 时对 `COMMIT_PLAN.md` 的"占位符"理解不一致 | 11-10 统一改为真实姓名，并保留轮次标记 | 已处理 |
| D-02 | `deploy/README.md` 合并冲突（add/add） | 魏宇佳 R1 已写部署说明，班国勇 R2 又新建同名文件 | 合并为一份：前 6 节保留部署结构与分区，新增第 7 节"本地编排（R2-F）" | 已处理 |
| D-03 | 分支落后 `main` 就开始开发，合并时才暴露冲突 | 缺少"开工先同步"的动作 | 规定每轮第一步 `git switch main && git pull`，小 PR 勤合并 | 已整改 |
| D-04 | `docs/ai-usage.md` 的记录在 PR #14 合并后才推送，未随 PR 进入 `main` | 记录与代码分成两次提交，且晚于合并 | 补 PR #21；后续要求 AI 记录与代码放在同一 PR | 处理中 |
| D-05 | PR #8 由作者本人合并 | 当时无"他人合并"约定 | 此后 PR #14 等均由非作者成员合并 | 已处理 |
| D-06 | `docs/design/er.md` 未生成，ER 文档仍在 `docs/database/er.md` | R2 时间被骨架与前端占用 | 记入 Sprint 2 待办（魏宇佳） | 待处理 |
| D-07 | CI 无前端任务 | 前端骨架 11-10 才落地，CI 在同时期编写 | Sprint 2 增加 `pnpm install && pnpm build` | 待处理 |
| D-08 | `docker-compose.yml` 未在本地实测 | 提交者机器未安装 Docker | 由 CI 的 `docker compose config` 兜底语法与变量校验；答辩前在一台装有 Docker 的机器跑一次 `up -d` | 待验证 |

## 7. 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| 迁移脚本仍为空、Flyway 关闭（`FLYWAY_ENABLED=false`） | `schema.sql` 与代码可能脱节，建表靠手工导入 | Sprint 2 由 E 落 `V003`（认证/问诊），Sprint 3 落 `V004`（处方/审计），并开启 Flyway |
| 测试只覆盖认证与问诊 | 处方状态机、核销幂等没有回归网 | R4 补 `PrescriptionStateTest`（8 状态正向/非法迁移），R5 补重放与并发用例 |
| 六个 PR 同时改动 `docs/README.md` 等索引文件 | 易产生冲突、状态失真 | 索引只由文档负责人定期汇总；成员只改自己负责的行 |
| 前端用 mock，未接真实后端 | 演示时联调风险 | Sprint 2 起用 compose 起后端，前后端联调一次 |
| 本机无 Docker、无统一 JDK/Node 版本 | 环境差异导致"我这能跑" | 统一 JDK 17 / Node 20，CI 为唯一判定标准 |

## 8. Sprint 2 目标（R3–R4，下一迭代）

1. 认证闭环：登录、JWT、角色权限，PR 已由 A 落地，需补越权回归；
2. 图文问诊闭环：会话、消息、接诊，患者端页面接通真实接口；
3. 处方与审核：处方聚合、状态机、药师审核/驳回，配 8 状态迁移测试；
4. 数据库迁移落地（`V003`/`V004`）并开启 Flyway；
5. 安全文档：STRIDE 威胁模型 + 隐私检查表；
6. 过程改进项：D-04、D-06、D-07 关闭，CI 增加前端任务。

## 9. 追溯清单

| PR | 分支 | 交付物 |
|---|---|---|
| #3 | `docs/design-domain-model` | 领域模型 v0.1 |
| #4 | `docs/r1d-index-sync` | 文档索引同步（R1-D） |
| #5 | `docs/r1c-use-cases` | 29 个用例与权限矩阵 |
| #6 | `feat/db-schema-draft` | 24 张表 DDL 草案 |
| #7 | `docs/db-er-diagram` | ER 图（png/svg）与渲染脚本 |
| #8 | `docs/test-collab-templates` | 测试策略、Issue/PR 模板 |
| #10 | `feat/backend-skeleton` | Spring Boot 骨架 |
| #12 | `feat/web-app-skeleton` | 前端四端骨架 |
| #14 | `ci/local-compose-and-workflow` | compose、Dockerfile、CI |
| #15 | `feat/db-artifacts-v2` | 部署图、表总览、数据字典 |
| #16 | `test/r3-auth-consultation` | 认证/问诊测试 22 例（R3 前移） |
| #18 | `feat/web-patient-flow` | 患者端问诊流程 |
| #19 | `fix/web-contract-align` | 前端按 API 契约对齐 |
| #20 | `docs/er-wording` | ER 文档措辞修订 |
| #21 | `docs/ai-usage-r2-f` | R2 AI 使用记录（待合并） |
