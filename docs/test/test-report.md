# 回归测试报告（R6）

| 项目 | 内容 |
|---|---|
| 报告日期 | 2026-11-22 |
| 回归基线 | `main` @ `eff0655`（R5 收尾）加 R6 缺陷修复 |
| 执行人 | 刘飞燕 |
| 环境 | Windows 11 家庭中文版 10.0.26100；JDK 24.0.2（源码/字节码目标 17）；Maven 3.9.9；Spring Boot 3.2.5；H2 2.2.224（MySQL 兼容模式） |
| 命令 | `cd backend && mvn verify -DskipTests=false` |
| 结果 | **72 例通过，0 失败，0 错误，0 跳过**；打包成功，产物 `backend/target/medical-platform-backend.jar` |
| 结论 | 后端回归通过，可作为课程演示候选版本；发布前还需补种子数据、部署演练与前端手工回归 |

## 1 范围与边界

本轮只覆盖后端自动化回归：认证、问诊、处方状态机、药师审核、流转单、核销幂等与越权。
未执行前端构建、浏览器端到端、真实 MySQL、并发压测与安全扫描，原因见 §5。

测试库为 H2 内存库，建表脚本是 `backend/src/test/resources/schema-test.sql`，与 `database/schema.sql` 的字段保持同一套命名。H2 的行锁与 MySQL 不完全一致，因此并发用例验证的是“唯一键兜底 + 状态码集合”，不等价于 MySQL 压测。

## 2 结果明细

| 测试类 | 覆盖内容 | 用例数 | 结果 |
|---|---|---:|---|
| `AuthTest` | 登录成功/失败、账号禁用、令牌篡改/过期、伪造 ADMIN | 12 | 通过 |
| `ConsultationTest` | 发起问诊、医生接诊、越权读取、结束后写保护 | 10 | 通过 |
| `PrescriptionAggregateTest` | 处方聚合状态迁移、明细校验、非法迁移 | 7 | 通过 |
| `PrescriptionApiTest` | 开方、改草稿、提交、越权读取 | 4 | 通过 |
| `PrescriptionReviewTest` | 待审队列、通过/驳回、审核记录、并发版本冲突 | 5 | 通过 |
| `PrescriptionStateTest` | 8 状态 × 10 条合法迁移、终态、文档一致性 | 17 | 通过 |
| `CirculationOrderApiTest` | 流转单创建、幂等返回、接方、拒接、跨药房越权、患者列表 | 4 | 通过 |
| `CirculationIdempotencyTest` | 核销重放、同键换参数、异键 409、并发、缺键、未接方、越权 | 13 | 通过 |
| **合计** | | **72** | **全部通过** |

关键业务断言：

- 处方状态机只经聚合方法迁移，非法迁移返回 `RX_STATE_INVALID`；`PrescriptionStateTest` 与 `docs/design/domain-model.md` §8.1 的 Mermaid 迁移逐条比对通过。
- 流转单创建后处方进入 `CIRCULATED`，重复创建返回同一张流转单，HTTP 状态从 201 改为 200。
- 核销同键重放返回首次结果且 `dispense_record` 只落一条；异键重复核销返回 `RX_ALREADY_REDEEMED`；缺 `Idempotency-Key` 返回 400。
- 患者、医生、药师、管理员的数据范围在服务层二次校验，跨药房操作返回 `RX_SCOPE_DENIED`。

## 3 缺陷与修复

| 编号 | 现象 | 影响 | 修复 | 回归 |
|---|---|---|---|---|
| D-01 | 同一处方重复创建流转单时，接口仍返回 `201 Created` | 客户端会把幂等重放当成新建资源，可能重复刷新页面状态 | 新增 `CirculationCreateResult`，应用服务区分“新建/命中已有”，Controller 分别返回 201/200；接口测试改为断言第二次 200 | `CirculationOrderApiTest#createIsIdempotentByPrescription` 通过 |

本轮未发现新的业务状态错误。测试过程中出现过的编译/环境问题（JDK 24 下 Lombok 不生成访问器、Maven 未安装）已在 R2/R3 处理，不计入本轮缺陷。

## 4 需求与用例映射

| 需求/用例组 | 覆盖情况 |
|---|---|
| FT-01 登录、ST-01/ST-02 认证与令牌 | `AuthTest` 全覆盖 |
| FT-02 问诊、ST-03 会话越权、SM-02 结束后写保护 | `ConsultationTest` 全覆盖 |
| 处方 8 状态、SM-01～SM-08、ST-07～ST-11 | `PrescriptionStateTest`、`PrescriptionAggregateTest` 覆盖 |
| 开方、审核、驳回、审核记录 | `PrescriptionApiTest`、`PrescriptionReviewTest` 覆盖 |
| 流转创建/接方/拒接、CI-09 流转幂等 | `CirculationOrderApiTest` 覆盖 |
| 核销幂等 CI-01～CI-08、ST-12～ST-16 | `CirculationIdempotencyTest` 覆盖 |

## 5 未覆盖项与风险

| 项 | 原因 | 影响 | 建议 |
|---|---|---|---|
| 前端构建与浏览器回归 | 本轮只跑后端；前端依赖未在本机安装 | 页面与接口字段可能仍有偏差 | R6 由代香晰执行 `pnpm build` 并按演示路径走一遍 |
| 真实 MySQL 与 Flyway | 本机未启动 MySQL/Docker；测试用 H2 | 迁移脚本、索引、CHECK 约束未在 MySQL 实跑 | 用 `deploy/docker-compose.yml` 起 MySQL 后执行 V002～V004 |
| 部署演练 | 本机无 Docker | 现场启动风险未知 | R5-E 补 `seed.sql` 后在干净机器完整 `up` 一次 |
| 并发压测 | 只做两线程接口级并发 | 高并发下的锁等待、连接池未验证 | 非课程演示必需，可在报告中列为限制 |
| 安全扫描 | 未接入依赖漏洞扫描与渗透测试 | 依赖版本与接口暴露面未知 | 至少跑一次 `mvn dependency:tree` 人工核对；答辩时说明未做 |

## 6 退出判断

后端回归达到退出条件：72 例全部执行、零失败、零跳过，`mvn verify` 打包成功。课程演示可以进入发布候选阶段，但“可演示”仍受两个外部条件约束：

1. 需要 `database/seed.sql` 提供患者、医生、药师、药房与 `pharmacist_profile`；
2. 需要在装有 Docker 的机器上完成一次 compose 启动演练。

这两项完成前，不建议在答辩现场直接换机器演示。
