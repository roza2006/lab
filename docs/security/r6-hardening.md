# R6 安全加固与处方状态边界修复

| 项目 | 内容 |
|---|---|
| 版本 | v1.0 |
| 日期 | 2026-11-24 |
| 负责人 | 李梓语（问诊域 / 处方域） |
| 关联提交 | `fix(security): harden auth and rate limit [R6][李梓语]` |
| 关联文档 | `docs/security/threat-model.md`、`docs/security/checklist.md`、`docs/test/acceptance.md` §5-1、`docs/design/api-contract.md` §3.7 |

## 1 背景与范围

R6 中李梓语负责的任务为“越权 / 限流 / 状态边界修复”。本轮只改后端认证与处方两个上下文，不新增表、不改变已有接口的成功语义：

1. **限流缺失**：`threat-model.md` 的 T-02（撞库）与 T-14（刷接口）均标注“无限流”，验收报告也把“安全扫描 / 限流”列为未覆盖项。
2. **驳回态无法修改**：`acceptance.md` §5-1 判定“药师驳回后医生无法修改也无法重新提交”为阻断项。聚合已有 `Prescription.revise()`，但没有接口调用它，`PUT /prescriptions/{id}` 仍只接受 `DRAFT`，与 OpenAPI 中“修改草稿/驳回态处方”的描述不一致。
3. **越权防线复核**：问诊、处方、流转的应用服务已按 `CurrentUser.id()` 校验归属。本轮不重写这套逻辑，而是补“驳回态修改”的越权用例，防止新放行的状态绕过归属校验。

## 2 变更内容

### 2.1 限流（新增 `RateLimitFilter`）

新增 `com.medical.platform.security.RateLimitFilter`，注册在 JWT 过滤器之后，按“自然分钟”固定窗口计数。选择固定窗口而不是令牌桶，是为了在不引入 Redis 的前提下用最小代码满足课程演示；阈值全部可配。

| 入口 | 计数维度 | 默认阈值 | 配置项 / 环境变量 | 对应威胁 |
|---|---|---:|---|---|
| `POST /api/v1/auth/login` | 客户端 IP | 20 次/分钟 | `app.rate-limit.login-per-minute` / `RATE_LIMIT_LOGIN_PER_MINUTE` | T-02 撞库、暴力破解 |
| `POST /api/v1/consultations/{id}/messages` | 登录用户 ID（无认证时退回 IP） | 60 次/分钟 | `app.rate-limit.message-per-minute` / `RATE_LIMIT_MESSAGE_PER_MINUTE` | T-14 刷消息 |
| 其余 `/api/v1/**` | 客户端 IP | 300 次/分钟 | `app.rate-limit.global-per-minute` / `RATE_LIMIT_GLOBAL_PER_MINUTE` | T-14 刷列表/查询 |
| 总开关 | — | 开启 | `app.rate-limit.enabled` / `RATE_LIMIT_ENABLED` | — |

超限响应与契约中的错误结构一致，并带 `Retry-After`、`X-RateLimit-Limit`、`X-RateLimit-Remaining` 头：

```json
{
  "type": "about:blank",
  "title": "Too Many Requests",
  "status": 429,
  "detail": "请求过于频繁，请稍后重试",
  "code": "RATE_LIMITED"
}
```

测试环境默认关闭限流（`src/test/resources/application-test.yml`），否则大量登录/建单用例会互相计数；`RateLimitFilterTest` 用独立 `properties` 打开开关并把登录阈值压到 2 次/分钟，验证“前两次 401、第三次 429”。

### 2.2 认证与越权边界

- 登录仍统一返回“用户名或密码错误”，不区分账号是否存在；限流把“无限次尝试”收紧为“每 IP 每分钟 20 次”，降低撞库成功率，但不替代账号锁定与验证码。
- 消息限流在 JWT 解析之后执行，计数键优先取 `CurrentUser.id()`，避免同一 NAT 出口下的患者互相影响。
- 驳回态修改继续走 `requireDoctorOwner`：只有原开方医生可以调用，非本人返回 403 `RX_SCOPE_DENIED`；患者、药师调用同样被角色校验拦截。
- 未改动既有数据范围规则（患者只看本人、医生只看本人、药师/管理员按契约读取），避免在本轮引入新的可见性变化。

### 2.3 处方状态边界（`REJECTED → DRAFT`）

`PrescriptionAppService.update` 现在按状态分两种路径，且都在同一个事务内完成：

```text
DRAFT      --PUT--> 替换诊断/明细，version + 1
REJECTED   --PUT--> revise() 退回 DRAFT（清空签名、审核人、审核时间、驳回理由、有效期）
                     → 替换诊断/明细
                     → 一次 WHERE id=? AND version=? 条件更新落库，version + 2
其他状态   --PUT--> 409 RX_STATE_INVALID（状态不变）
```

设计取舍：不新增 `POST /prescriptions/{id}/revise`，而是放行已有 `PUT`。原因是 OpenAPI 与前端编辑页都把“保存修改”建模为 PUT，新增端点会让契约与页面同时分叉；聚合 `revise()` 仍是唯一状态迁移入口，应用服务只负责编排。

## 3 接口与数据影响

| 项 | 影响 |
|---|---|
| `PUT /api/v1/prescriptions/{id}` | 新增接受 `REJECTED`；成功返回 `DRAFT`，`rejectReason` 为空，`version` 连续递增。`DRAFT` 行为不变 |
| `POST /api/v1/auth/login` | 超限新增 429 `RATE_LIMITED`，成功/失败语义不变 |
| `POST /api/v1/consultations/{id}/messages` | 超限新增 429 `RATE_LIMITED` |
| 数据库 | 无迁移、无字段变更；只使用已有 `prescription.version` |
| 配置 | `application.yml` 新增 `app.rate-limit.*`，均可用环境变量覆盖 |

## 4 验证

执行环境：Windows 11，JDK 17，Maven 3.9，Spring Boot 3.2.5，H2（MySQL 兼容模式）。

```bash
cd backend
mvn -B -ntp test
```

| 用例 | 断言 |
|---|---|
| `RateLimitFilterTest#loginAttemptsAreLimitedPerIp` | 同一 IP 前两次登录失败 401，第三次 429，`code=RATE_LIMITED`，`Retry-After` 存在 |
| `PrescriptionApiTest#rejectedPrescriptionCanBeRevisedAndResubmitted` | 提交 → 驳回 → PUT 改明细后状态 `DRAFT`、`version=5`、明细替换为 1 条、驳回理由清空 → 再提交成功 `PENDING_REVIEW` |
| `PrescriptionApiTest#otherDoctorCannotReviseRejectedPrescription` | 非开方医生 PUT 驳回态处方返回 403 `RX_SCOPE_DENIED` |
| 既有 77 例 | 认证、问诊、处方状态机、审核、流转、核销、验收用例保持通过 |

> 具体通过数在与 R6-A/R6-D 的测试合并后由 `docs/test/test-report.md`、`docs/test/acceptance.md` 统一记录；本文件只声明本线新增用例的断言。

## 5 残余风险

| 风险 | 说明 | 后续建议 |
|---|---|---|
| 单机计数 | 计数在进程内，多实例部署不共享，重启清零 | 生产改用 Redis + Lua 或 API 网关限流 |
| IP 维度可绕过 | 攻击者换出口 IP 可绕过登录限流；反向代理后 `getRemoteAddr()` 可能拿到代理地址 | 网关层取可信 `X-Forwarded-For` 并做全局限流 |
| 无账号锁定/验证码 | 限流只降低频率，不能阻止慢速撞库 | 连续失败 N 次锁定账号或加验证码 |
| 审计仍未落地 | S-02 的 `audit_log` 写入不在本轮范围，驳回/修改仍只有业务状态 | 由 R6 测试线与后续迭代补审计 |
| 429 未纳入压测 | 只做接口级顺序断言，未做并发压测 | 答辩前用 100 并发确认计数器无丢失 |

## 6 责任与协作

| 成员 | 本轮相关职责 |
|---|---|
| 刘飞燕 | 回归执行与 `docs/test/test-report.md`；认证上下文接口约定 |
| 李梓语 | 限流过滤器、处方状态边界修复、`docs/security/r6-hardening.md` |
| 代香晰 | 前端对 429 的提示与驳回后编辑入口 |
| 郝佳鑫 | 验收用例与覆盖统计；复测驳回重提链路 |
| 魏宇佳 | 配置项进入部署环境变量、README 部署说明 |
| 班国勇 | 发布说明与答辩材料同步本轮修复 |

## 7 AI 使用与人工确认

本文与代码由 AI 助手起草，人工完成：阈值取值与单机实现取舍、`PUT` 复用而非新增端点的决定、越权用例边界、残余风险表述。记录已写入 `docs/ai-usage.md`。所有测试数据均为虚构，不含真实患者信息。
