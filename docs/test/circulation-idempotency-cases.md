# 处方流转与核销用例（重放 / 并发 / 越权）

> 第 5 轮测试设计（R5）。依据：`docs/design/domain-model.md` §5.3 与 §11、《实验报告》表 2-10 与 §5 接口清单、
> `docs/design/api-contract.md` §4（`Idempotency-Key` 约定）。可执行用例在
> `backend/src/test/java/com/medical/platform/circulation/CirculationIdempotencyTest.java`。

| 项目 | 内容 |
|---|---|
| 用例前缀 | `CI`（幂等与并发）、`ST`（安全/越权） |
| 覆盖范围 | 流转单接方、核销、重放、并发核销、幂等键缺失、越权与轨迹查询 |
| 前置依赖 | 刘飞燕的流转单与接方、李梓语的核销与幂等记录（`circulation_order` / `dispense_record` / `idempotency_record`） |
| 当前状态 | 李梓语核销与幂等实现已合入，13 例全部启用并通过（`mvn -B -ntp test`：68 例、0 失败、0 跳过） |

---

## 1 幂等语义（判定标准）

核销是**外部副作用**（发药、扣减库存、回传药房），必须满足四条：

| # | 规则 | 判定方式 |
|---|---|---|
| R1 | 同一 `Idempotency-Key` 重复调用，**返回首次结果**，不新建记录 | 两次响应体的记录 ID 相同；`dispense_record` 计数不变 |
| R2 | 不同 `Idempotency-Key` 命中已核销流转单，返回 `409 RX_ALREADY_REDEEMED` | 状态码 + 错误码；发药记录仍为 1 |
| R3 | 并发（2 线程）核销，数据库层只能有一条发药记录 | 唯一索引 `dispense_record.uk_circulation` 兜底；另一个请求 409 或回放首次结果 |
| R4 | 缺少 `Idempotency-Key` 直接拒绝（400），不进入业务逻辑 | 无任何写库副作用 |

幂等键的落库形式：`idempotency_record` 唯一键 `(biz_type, biz_id, operation, idem_key)`。
李梓语 R5 实现采用“事务内先锁流转单 → 复查幂等 → 业务与 SUCCESS 记录同事务提交”，
冲突时回放首次结果（见 `docs/design/circulation-redeem.md` §5）。

---

## 2 用例清单

### 2.1 幂等与并发（CI）

| 编号 | 场景 | 前置 | 操作 | 期望 |
|---|---|---|---|---|
| CI-01 | 正向核销 | 流转单已接方 | 带新 key 核销 | 200，状态 `REDEEMED`，发药记录 +1 |
| CI-02 | **重放**（同 key） | 已核销 | 用同一 key 重放 | 200，返回首次记录，发药记录不变 |
| CI-03 | **重放**（异 key） | 已核销 | 换 key 再核销 | 409 `RX_ALREADY_REDEEMED`，发药记录不变 |
| CI-04 | **并发**（同 key） | 已接方 | 2 线程同时核销 | 两个响应都成功或其一被回放；发药记录 = 1 |
| CI-05 | **并发**（异 key） | 已接方 | 2 线程不同 key | 一个 200、一个 409；发药记录 = 1 |
| CI-06 | 缺幂等键 | 已接方 | 不带 `Idempotency-Key` | 400 `IDEMPOTENCY_KEY_REQUIRED`，零副作用 |
| CI-07 | 未接方直接核销 | 仅 `CREATED` | 直接核销 | 409 `CIRC_STATE_INVALID` |
| CI-08 | 重复接方 | 已 `ACCEPTED` | 再次接方 | 200 幂等返回，或 409 `CIRC_STATE_INVALID`（以实现为准，需与契约统一） |
| CI-09 | 流转幂等 | 处方 `APPROVED` | 同一处方重复流转 | 返回既有流转单，不新建（`uk_rx` 唯一） |
| CI-10 | 核销后状态回写 | 核销成功 | 查处方详情 | 处方状态 `REDEEMED`，含 `redeemed_at` |
| CI-11 | 同 key 换参数 | 已核销 | 同一 key 传不同 `evidence` | 409 `IDEMPOTENCY_CONFLICT`，发药记录不变 |

### 2.2 越权（ST）

| 编号 | 场景 | 期望 |
|---|---|---|
| ST-12 | 患者调用核销 | 403 `SCOPE_DENIED` |
| ST-13 | 其他药房核销他人流转单 | 403 `RX_SCOPE_DENIED` |
| ST-14 | 患者查看他人处方流转轨迹 | 403 `RX_SCOPE_DENIED` |
| ST-15 | 未认证调用接方/核销/轨迹 | 401（**已生效**） |
| ST-16 | 无效令牌调用核销 | 401，不得返回 400/409（**已生效**） |

---

## 3 并发测试的实现方式

```java
// 两个线程在同一 CountDownLatch 上同时起跑，避免“单线程顺序执行”掩盖竞态
ExecutorService pool = Executors.newFixedThreadPool(2);
CountDownLatch start = new CountDownLatch(1);
// 各自 perform 核销请求，收集 HTTP 状态码
```

判定不看“谁先成功”，只看三条不变量：
1. `dispense_record` 计数 = 1；
2. 状态码集合 ⊆ {200, 409}，且至少一个 200；
3. 若两个都 200（回放场景），返回的记录 ID 必须相同。

> 说明：H2 内存库的并发语义与 MySQL 有差异（行锁粒度），因此 CI-04/CI-05 在 `mvn test` 下
> 通过唯一索引兜底验证；真实 MySQL 的并发验证放在部署后的手工/压测环节（见 `docs/deploy` 说明）。

---

## 4 前置数据与启用方式

1. `schema-test.sql` 已增加三张表：`circulation_order`、`dispense_record`、`idempotency_record`
   （字段以 `database/schema.sql` 为准，`V005__circulation.sql` 由魏宇佳在 R5 固化），并给
   `dispense_record.circulation_id` 建唯一索引；另加 `pharmacist_profile` 支撑药房范围校验。
2. `CirculationIdempotencyTest#givenCirculatedOrder()` 已实现：直接写入 `CIRCULATED` 处方与
   `ACCEPTED` 流转单，避免测试重复覆盖 R3/R4/刘飞燕 R5 的接口链路；`givenOrder("CREATED")` 用于未接方用例。
3. 13 个业务用例已去掉 `@Disabled`，执行 `mvn -B -ntp test -Dtest=CirculationIdempotencyTest` 全绿。

## 5 与契约的待确认项

| # | 问题 | 结论 |
|---|---|---|
| Q4 | 契约未定义流转/核销端点 | 李梓语 R5 已在 `docs/design/api-contract.md` §5.5 与 `openapi.yaml` 补 `POST /api/v1/circulations/{id}/redeem`、`GET /api/v1/prescriptions/{id}/trace`；接方端点由刘飞燕在 R5 补 |
| Q5 | 重复接方是“幂等 200”还是“409” | 接方属刘飞燕 R5，本文件不覆盖 |
| Q6 | 缺少幂等键返回 400 还是 422 | 已定为 400 `IDEMPOTENCY_KEY_REQUIRED`（未进入 Bean Validation） |

## 6 覆盖统计

| 维度 | 数量 | 当前可执行 |
|---|---|---|
| 幂等与并发 | 11（CI-01 ~ CI-11） | 9（CI-01~CI-07、CI-10、CI-11；CI-08/CI-09 属刘飞燕 R5 接方与流转幂等） |
| 越权与认证 | 5（ST-12 ~ ST-16） | 5（全部） |
| 合计 | 16 | 13 个测试方法（部分方法覆盖多条用例） |
