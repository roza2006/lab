# 第 1 次评审记录（R1–R2 需求与设计评审）

| 版本 | 日期 | 记录人 | 状态 | 评审对象 |
|---|---|---|---|---|
| v1.0 | 2026-11-10 | 班国勇 | 待复核 | R1 需求基线、R2 架构/接口/工程骨架 |

## 1. 评审信息

| 项目 | 内容 |
|---|---|
| 评审对象 | `docs/requirements/*`、`docs/design/domain-model.md`、`docs/design/architecture.md`、`docs/design/api-contract.md`、`database/schema.sql`、`backend/`、`frontend/`、`deploy/`、`.github/workflows/ci.yml` |
| 评审形式 | **GitHub PR 异步评审**：每个 PR 在合并前由非作者成员检查 diff 与 CI 结果；本文汇总 14 个已合并 PR 的评审意见与决议 |
| 参加成员 | 刘飞燕、李梓语、代香晰、郝佳鑫、魏宇佳、班国勇（6 人） |
| 评审依据 | `COMMIT_PLAN.md` 的"每轮检查清单"、`docs/README.md` 的文档规范与追踪关系、`docs/test/test-plan.md` 的测试要求 |
| 评审材料 | 各 PR 描述（改了什么 / 怎么验证 / 影响范围 / AI 辅助 / 回滚方案）、CI 运行结果、`docs/ai-usage.md` |

## 2. 总体结论

**结论：有条件通过。** R1 的需求基线、领域模型与 R2 的架构、接口契约可以作为后续编码依据，但需满足以下三个条件后方可视为"基线冻结"：

1. **数据一致性**：`database/migrations/` 落地 `V003`/`V004`，并开启 Flyway，使表结构与代码同步（责任人：魏宇佳，Sprint 2）；
2. **接口一致性**：前端以 `docs/design/api-contract.md` v1.0 为准，CI 增加前端构建任务（责任人：代香晰 / 班国勇，Sprint 2）；
3. **过程整改**：分支开工前同步 `main`，AI 使用记录与代码同 PR 提交，PR 由非作者合并（责任人：全员，立即执行）。

## 3. 逐项评审意见

| 编号 | 评审对象 | 意见 | 结论 | 责任人 | 期限 |
|---|---|---|---|---|---|
| REV-01 | 需求范围与角色（`requirements.md`） | 角色边界、非功能需求可追踪到用例；"合作药房"与"药师"的职责需在用例中再区分一次 | 通过（补充） | 李梓语 | Sprint 2 |
| REV-02 | 29 个用例（`use-cases.md`） | 覆盖主链路，权限矩阵可执行；8 个待确认问题需给出结论，否则测试无法定稿 | 有条件通过 | 代香晰 | Sprint 2 |
| REV-03 | 领域模型（`domain-model.md`） | 聚合边界与不变量清晰；需明确"病历是否独立聚合"这一开放问题 | 有条件通过 | 郝佳鑫 | Sprint 2 |
| REV-04 | 架构与 ADR（`architecture.md`、`ADR-001.md`） | 模块化单体 + DDD 分层的取舍理由充分，分层依赖规则可检查（`domain` 不依赖 Spring/MyBatis） | 通过 | 刘飞燕 | — |
| REV-05 | API 契约（`api-contract.md` v1.0、`openapi.yaml`） | 认证/问诊/处方接口字段与错误码完整；前端已按契约返工（PR #19），契约作为唯一事实来源 | 通过（冻结 v1.0） | 李梓语 | — |
| REV-06 | 数据库设计（`schema.sql`、ER、字典） | 24 张表、索引与外键齐备，`scripts/check_schema.py` 静态自检通过；迁移脚本缺失，Flyway 仍关闭 | 有条件通过 | 魏宇佳 | Sprint 2 |
| REV-07 | 后端骨架（`backend/`） | 编译通过，包结构按上下文划分；`pom.xml` 引入 Flyway/Redis/Actuator 属"提前引入"，需在 Sprint 2 真正用上 | 通过（备注） | 郝佳鑫 | Sprint 2 |
| REV-08 | 前端骨架（`frontend/`） | 18 个页面、角色路由守卫、请求封装齐备，`pnpm build` 通过；认证仍走 mock，需接真实后端 | 有条件通过 | 代香晰 | Sprint 2 |
| REV-09 | 部署与 CI（`deploy/`、`ci.yml`） | compose 三服务与 healthcheck 顺序合理，CI 两个 job 通过；本机无 Docker，`up` 未实测 | 通过（待实测） | 班国勇 | Sprint 2 |
| REV-10 | 过程与文档（提交规范、AI 记录、索引） | 角色字母与真名混用、AI 记录晚于合并、PR #8 自合并，均属流程问题 | 需整改 | 班国勇 | 立即 |

## 4. 会议/评审决议

| 编号 | 决议 | 影响 |
|---|---|---|
| R-01 | 架构维持**模块化单体 + DDD 分层**，不引入微服务 | 后续功能在 `backend/src/main/java/com/medical/platform/<context>` 内按 interfaces/application/domain/infrastructure 分层实现 |
| R-02 | API 契约 v1.0 **冻结**，变更需在 PR 描述中说明并同步更新 `openapi.yaml` 与前端 | 避免前后端字段漂移（PR #19 已按此执行） |
| R-03 | **CI 必绿才能合并 PR**；CI 为环境差异的唯一判定标准 | 统一 JDK 17 / Node 20，本地环境差异不作为"能跑"的依据 |
| R-04 | 提交信息统一为 `<type>(<scope>): <what> [R<n>][<姓名>]` | 便于按轮次与成员统计（`git log --format='%an %s'`） |
| R-05 | 分支模型：`main` 只经 PR 合并；分支名带类型前缀（`feat/`、`fix/`、`docs/`、`ci/`、`test/`） | 已合并的 15 个分支均符合 |

## 5. 行动项

| 编号 | 行动项 | 责任人 | 期限 | 状态 |
|---|---|---|---|---|
| A-01 | 落 `database/migrations/V003__auth_consult.sql`，开启 Flyway | 魏宇佳 | Sprint 2 | 待开始 |
| A-02 | CI 增加前端 `pnpm install && pnpm build` 任务 | 班国勇 | Sprint 2 | 待开始 |
| A-03 | 前端接入真实后端（登录、问诊列表与会话） | 代香晰 | Sprint 2 | 进行中 |
| A-04 | 补齐 `docs/design/er.md`，明确与 `docs/database/er.md` 的唯一来源 | 魏宇佳 | Sprint 2 | 待开始 |
| A-05 | 关闭 8 个用例待确认问题，更新权限矩阵 | 代香晰 | Sprint 2 | 待开始 |
| A-06 | 统一提交信息里的姓名写法（已完成 11-10，需在后续轮次保持） | 全员 | 立即 | 已完成 |
| A-07 | AI 使用记录与代码同 PR 提交（PR #21 补交属整改） | 全员 | 立即 | 已完成 |
| A-08 | 在装有 Docker 的机器上实测 `docker compose up -d`，记录结果 | 班国勇 | Sprint 2 | 待开始 |

## 6. 遗留问题与风险提示

1. **测试覆盖不均**：当前 22 个用例集中在认证与问诊（R3 前移），处方状态机与核销幂等尚无回归网，R4/R5 必须补齐，否则"状态不能乱"这一核心需求没有证据。
2. **契约冻结后的变更成本**：v1.0 之后再改字段，需要同时改 `openapi.yaml`、前端类型、mock 与测试，成本高，务必在 R3 内确认字段名与错误码。
3. **环境依赖**：Compose、Flyway、Redis 都属于"本地没有就发现不了"的组件，建议 R5 部署演练时在一台干净机器上从零跑一遍。
4. **单人自合并风险**：已发生 1 次（PR #8），后续严格执行"非作者合并"。

## 7. 下一步

- 本记录的整改项进入 `docs/process/sprint-01.md` §6 的偏差表统一跟踪；
- Sprint 2 结束时（R4 收尾）补第 2 次评审记录，重点评审处方状态机与安全设计；
- 评审意见如与实现冲突，以 `main` 上已合并的代码与契约文档为准，并回写本文。
