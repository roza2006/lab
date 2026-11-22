# 处方状态机用例（8 状态 × 8 事件）

> 第 4 轮测试设计（R4）。依据：`docs/design/domain-model.md` §8.1、《实验报告》表 2-9 / 表 2-10、
> `docs/design/api-contract.md` §5.3–5.4。可执行用例在
> `backend/src/test/java/com/medical/platform/prescription/PrescriptionStateTest.java`。

| 项目 | 内容 |
|---|---|
| 用例前缀 | `SM`（状态机）、`ST`（安全/越权） |
| 覆盖范围 | 处方 8 个状态的全部合法迁移与非法迁移、状态变更端点的认证边界 |
| 前置依赖 | R4-A 处方聚合与状态迁移、R4-B 药师审核；R5 流转与核销（`circulate` / `redeem` / `expire`） |
| 当前状态 | R4-A / R4-B 已合入，迁移用例全部启用（17 例全绿） |

---

## 1 状态与事件

**状态（8）**：`DRAFT` 待开具、`PENDING_REVIEW` 待审核、`REJECTED` 审核驳回、`APPROVED` 待流转、
`CIRCULATED` 已流转、`REDEEMED` 已核销、`CANCELLED` 已取消、`EXPIRED` 已过期。

**终态（3）**：`CANCELLED`、`REDEEMED`、`EXPIRED`——不再接受任何事件。

**事件（8）**：`submit` 提交审核、`approve` 审核通过、`reject` 审核驳回、`revise` 修改重提、
`cancel` 撤销、`circulate` 流转、`redeem` 核销、`expire` 过期。

| 事件 | 触发者 | 接口（契约 v1） | 生效轮次 |
|---|---|---|---|
| `submit` | 医生 | `POST /api/v1/prescriptions/{id}/submit` | R4 |
| `approve` | 药师 | `POST /api/v1/prescriptions/{id}/reviews/approve` | R4 |
| `reject` | 药师 | `POST /api/v1/prescriptions/{id}/reviews/reject` | R4 |
| `revise` | 医生 | `PUT /api/v1/prescriptions/{id}`（修改后回到 `DRAFT`） | R4 |
| `cancel` | 医生 | `POST /api/v1/prescriptions/{id}/cancel` | R4 |
| `circulate` | 系统/药师 | 流转单接口 | R5 |
| `redeem` | 药房 | 核销接口 | R5 |
| `expire` | 定时任务 | 无对外接口（`validUntil` 判定） | R5 |

---

## 2 迁移矩阵

行 = 当前状态，列 = 事件；单元格为「目标状态」或「✘」。
`✘` 的期望响应统一为 **409 `ILLEGAL_STATE`**，且数据库状态、版本号不变。

| 当前 \ 事件 | submit | approve | reject | revise | cancel | circulate | redeem | expire |
|---|---|---|---|---|---|---|---|---|
| `DRAFT` | `PENDING_REVIEW` | ✘ | ✘ | ✘ | `CANCELLED` | ✘ | ✘ | ✘ |
| `PENDING_REVIEW` | ✘ | `APPROVED` | `REJECTED` | ✘ | `CANCELLED` | ✘ | ✘ | ✘ |
| `REJECTED` | ✘ (见 Q1) | ✘ | ✘ | `DRAFT` | ✘ | ✘ | ✘ | ✘ |
| `APPROVED` | ✘ | ✘ | ✘ | ✘ | ✘ | `CIRCULATED` | ✘ | `EXPIRED` |
| `CIRCULATED` | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | `REDEEMED` | `EXPIRED` |
| `REDEEMED` | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ |
| `CANCELLED` | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ |
| `EXPIRED` | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ |

合法迁移 10 条，非法组合 54 条（8×8 = 64，去掉 10 条合法与 0 条空）。

---

## 3 正向用例（10 条）

| 编号 | 从 | 事件 | 角色 | 期望 HTTP | 期望状态 | 关键断言 |
|---|---|---|---|---|---|---|
| SM-01 | `DRAFT` | submit | 开方医生 | 200 | `PENDING_REVIEW` | 版本号 +1；内容锁定（再 PUT 返回 409） |
| SM-02 | `PENDING_REVIEW` | approve | 药师 | 200 | `APPROVED` | `signedHash` 非空；审核人写入 |
| SM-03 | `PENDING_REVIEW` | reject | 药师 | 200 | `REJECTED` | 驳回意见落库；医生待办生成 |
| SM-04 | `REJECTED` | revise（PUT） | 原开方医生 | 200 | `DRAFT` | 版本号 +1；审核意见被清空 |
| SM-05 | `DRAFT` | cancel | 开方医生 | 200 | `CANCELLED` | 终态，后续任何事件 409 |
| SM-06 | `PENDING_REVIEW` | cancel | 开方医生 | 200 | `CANCELLED` | 终态；待办释放 |
| SM-07 | `APPROVED` | circulate | 药师/系统 | 200 | `CIRCULATED` | 生成流转单（R5 启用） |
| SM-08 | `CIRCULATED` | redeem | 药房 | 200 | `REDEEMED` | 发药记录 + 幂等键（R5 启用） |
| SM-09 | `APPROVED` | expire | 定时任务 | 200 | `EXPIRED` | 超过 `validUntil` 才过期（R5 启用） |
| SM-10 | `CIRCULATED` | expire | 定时任务 | 200 | `EXPIRED` | 同上（R5 启用） |

## 4 非法迁移用例（每条都断言 409 + `ILLEGAL_STATE` + 状态未变）

| 编号 | 从 | 事件 | 说明 |
|---|---|---|---|
| SM-11 | `DRAFT` | approve | 未经提交不能审核通过 |
| SM-12 | `DRAFT` | reject | 同上 |
| SM-13 | `PENDING_REVIEW` | submit | 重复提交（幂等由 `If-Match` 版本控制，状态非法优先 409） |
| SM-14 | `PENDING_REVIEW` | revise | 内容已锁定，必须驳回后才能改 |
| SM-15 | `APPROVED` | cancel | 已通过只能作废重开 |
| SM-16 | `APPROVED` | submit | 已审核不能再提交 |
| SM-17 | `CIRCULATED` | cancel | 已流转不可撤销 |
| SM-18 | `CANCELLED` | submit | 终态拒绝 |
| SM-19 | `REDEEMED` | submit | 终态拒绝 |
| SM-20 | `EXPIRED` | cancel | 终态拒绝 |
| SM-21 | `REJECTED` | cancel | 驳回后只能修改重提或等待过期 |
| SM-22 | `DRAFT` | redeem | 未流转不可核销 |

> 参数化实现：`terminalStatesRejectSubmit` 对 3 个终态各跑一次；其余非法用例按上表逐条断言。

## 5 认证与越权用例（ST）

| 编号 | 场景 | 期望 | 现状 |
|---|---|---|---|
| ST-07 | 未带令牌调用 10 个处方/审核端点 | 401 | **已生效**（`prescriptionEndpointsRequireAuthentication`） |
| ST-08 | 令牌签名无效时提交处方 | 401（不是 409/403） | **已生效** |
| ST-09 | 非开方医生提交他人处方 | 403 `SCOPE_DENIED` | 待 R4-A 启用 |
| ST-10 | 药师调用开方/修改接口 | 403 `FORBIDDEN` | 待 R4-A 启用 |
| ST-11 | 医生调用审核接口 | 403 `FORBIDDEN` | 待 R4-B 启用 |

---

## 6 前置数据与启用方式

1. `backend/src/test/resources/schema-test.sql` 增加 `prescription`、`prescription_item` 两张表
   （字段以 `database/schema.sql` 为准，含 `status`、`version`、`signed_hash`）。
2. 实现 `PrescriptionStateTest#givenPrescriptionIn(String state)`：
   - `DRAFT`：直接插入一条草稿（含 1 条明细）；
   - `PENDING_REVIEW`：草稿 + submit；
   - `APPROVED`：submit + approve；
   - `REJECTED`：submit + reject；
   - `CANCELLED` / `EXPIRED`：SQL 直接落终态（终态由定时任务/用户行为产生，不走正向链路）。
3. 逐个去掉迁移用例上的 `@Disabled`，跑 `mvn -B test -Dtest=PrescriptionStateTest`。

## 7 与接口契约的差异（评审确认）

| 编号 | 差异 | 影响 | 建议 |
|---|---|---|---|
| Q1 | 契约写 `submit` 允许 `DRAFT/REJECTED → PENDING_REVIEW`；domain-model §8.1 要求先 `revise` 回 `DRAFT` | 驳回后医生能否“直接重提” | **已按实现确定**：`PrescriptionStatus.canApply` 中 `SUBMIT` 只允许 `DRAFT`，`REJECTED` 需先 `PUT`（等同 revise）再 submit；契约措辞待同步 |
| Q2 | 契约错误码是 `ILLEGAL_STATE`，domain-model §11 写 `RX_STATE_INVALID` | 前后端错误码不一致 | **已按实现确定**：`PrescriptionError` 使用 `RX_STATE_INVALID`，契约 §4 的 `ILLEGAL_STATE` 需替换 |
| Q3 | `expire` 没有对外接口，靠 `validUntil` 定时扫描 | 无法用 HTTP 用例覆盖 | R5 用领域单元测试或定时任务测试覆盖 |

## 8 覆盖统计

| 维度 | 数量 | 说明 |
|---|---|---|
| 状态 | 8 | 全部出现在矩阵与用例中 |
| 合法迁移 | 10 | SM-01 ~ SM-10 |
| 非法迁移 | 12（矩阵 54） | SM-11 ~ SM-22 覆盖关键分支，矩阵给出全量 |
| 安全边界 | 3（ST-07 ~ ST-09） | ST-07/ST-08 已生效 |
| 当前可执行 | 17 | 状态机自检 4 + 认证边界 2 + 正向迁移 4 + 非法迁移 7（含 3 个终态） |
