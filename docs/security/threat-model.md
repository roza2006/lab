# STRIDE 威胁模型（R4）

| 版本 | 日期 | 负责人 | 状态 | 关联文档 |
|---|---|---|---|---|
| v1.0 | 2026-11-17 | 班国勇 | 待评审 | `docs/design/api-contract.md` v1.0、`docs/design/architecture.md`、`docs/design/domain-model.md`、`database/schema.sql`、`docs/test/test-plan.md` |

> 编写口径：每一条威胁都要能指到**真实存在的接口或表**，并区分「已实现」「设计已有但未实现」「完全缺失」。无法落到代码的威胁不写进来。

## 1. 范围

覆盖当前已实现的三个上下文：`identity`（登录/JWT/角色）、`consultation`（会话/消息/接诊）、`prescription`（处方 8 状态机、审核签名、驳回理由），以及它们依赖的 MySQL 8 / Redis 7。

不在本期范围：`circulation`（流转核销，R5）、`audit` 查询接口（R6）、合作药房外部系统（R5 才对接）、运维账号体系。

## 2. 依据与证据来源

| 证据 | 位置 |
|---|---|
| 认证与授权实现 | `backend/src/main/java/com/medical/platform/identity/infrastructure/security/`（`SecurityConfig`、`JwtAuthenticationFilter`、`JwtTokenProvider`、`BCryptPasswordHasher`） |
| 令牌配置 | `backend/src/main/resources/application.yml`：`app.jwt.secret`（默认 `dev-only-change-me-32bytes-minimum-secret-key-2026`）、`expiration-minutes`（默认 120，可 `JWT_EXPIRATION_MINUTES` 覆盖） |
| 接口与错误码 | `docs/design/api-contract.md` §3.2 / §3.7 / §4.3；`ConsultationError`、`PrescriptionError` 枚举 |
| 状态机 | `PrescriptionStatus`（8 状态 + `canApply` 白名单） |
| 数据面 | `database/schema.sql`（24 表）、`database/migrations/V002__core.sql`、`V003__auth_consult.sql` |
| 前端令牌处理 | `frontend/src/stores/auth.ts`、`frontend/src/api/http.ts`（`localStorage` 存 `mp.token`） |
| 测试证据 | `AuthTest`（12 例）、`ConsultationTest`（10 例）、处方状态机用例 |

## 3. 资产与分级

| 资产 | 内容 | 分级 | 存放位置 |
|---|---|---|---|
| 账号凭证 | 用户名、BCrypt 密码哈希、账号状态 | 敏感 | `sys_user` |
| 令牌 | JWT（含 `sub`/`role`/`exp`） | 敏感（等同于会话凭证） | 客户端 `localStorage`、服务端无状态 |
| 患者身份 | 姓名、手机号、身份证号、住址（AES-GCM 密文 + HMAC 盲索引） | 极敏感（PII） | `patient_profile`、`sys_user` |
| 健康信息 | 主诉、既往诊断、科室/疾病分类、消息正文（≤4000 字） | 极敏感（健康数据） | `consultation_session`、`consultation_message` |
| 处方 | 诊断、药品明细、剂量疗程、审核签名摘要、驳回理由 | 极敏感（业务凭证） | `prescription`、`prescription_item` |
| 审计 | 操作人、动作、结果、前后摘要、哈希链 | 敏感（抗抵赖） | `audit_log`（本期未写入） |
| 同意记录 | 隐私政策/在线诊疗/数据共享的同意版本与时间 | 敏感（合规） | `consent_record`（本期未写入） |

## 4. 信任边界与数据流

```text
[浏览器/前端 SPA]  ← 不可信网络（本地 HTTP，生产应经 Nginx TLS）→
        │  Authorization: Bearer <JWT>（localStorage 读出后附在头里）
        ▼
[Spring Boot API]  —— 信任边界 B1：所有入参一律不可信
        │  鉴权链：验签+exp → ROLE_* → 资源归属（SCOPE_DENIED）
        ├──► [MySQL 8]      信任边界 B2：内网，账号/权限最小化（当前 compose 用 root）
        ├──► [Redis 7]      信任边界 B3：会话/幂等键（本期未用）
        └──► [合作药房]     信任边界 B4：mTLS + 报文签名（R5 才实现）
[运维/部署] —— 信任边界 B5：跳板机、密钥注入、备份
```

本期的实际边界只有 B1、B2、B3；B4、B5 目前是设计，威胁仍列出来以便 R5 落地时对照。

## 5. STRIDE 威胁清单

> 优先级：P0 本期必须处理；P1 R5 处理；P2 R6 或接受。

| 编号 | 类别 | 威胁场景 | 受影响资产/接口 | 现有缓解（代码证据） | 残余风险 | 优先级 |
|---|---|---|---|---|---|---|
| T-01 | S 仿冒 | 用默认密钥伪造合法 JWT，冒充任意角色 | 全部 `/api/v1/**` | 验签走 `Jwts.parser().verifyWith(key)`，`exp` 自动校验；密钥可 `JWT_SECRET` 注入（`application.yml`） | 未注入环境变量时使用仓库默认密钥，攻击者可自行签发 | **P0** |
| T-02 | S 仿冒 | 撞库/暴力破解登录 | `POST /api/v1/auth/login` | BCrypt 校验，失败统一返回，不区分账号是否存在；R6 增加登录 IP 限流（20 次/分钟） | 无账号锁定、无验证码；换 IP 可绕过 | P1 |
| T-03 | S 仿冒 | 令牌重放：登出后旧令牌在 `exp` 前仍可用 | 全部接口 | 无状态 JWT，无服务端吊销名单（Redis 未接入） | 令牌泄露后最长 120 分钟内无法失效 | P1 |
| T-04 | T 篡改 | 请求体里伪造 `patientId`/`doctorId`/`pharmacyId` 越权操作 | 问诊创建/接诊、处方创建 | 服务端从 JWT 的 `sub` 取当前用户，业务层校验归属（`SCOPE_DENIED`、`RX_SCOPE_DENIED`） | 新增接口若忘记走 `CurrentUser` 会回到信任入参 | P1 |
| T-05 | T 篡改 | 客户端篡改处方明细/金额后提交 | `PUT /api/v1/prescriptions/{id}`、`/submit` | 只允许 `DRAFT`/`REJECTED` 修订；`RX_ITEM_LIMIT`、`RX_DUPLICATE_DRUG`、`RX_DOSAGE_INVALID` 校验；版本号乐观锁 `RX_VERSION_CONFLICT` | 无药品价格快照，篡改“药品名→价格”无法事后发现 | P1 |
| T-06 | T 篡改 | 药师审核通过但不写签名摘要（事后否认可开过方） | `review` 动作（R4 已实现审核字段） | `RX_SIGNATURE_REQUIRED` 强制签名摘要；驳回理由 <10 字报 `RX_REJECT_REASON_INVALID` | 签名只是摘要字符串，未绑定密钥/证书，无法证明“谁签的” | P1 |
| T-07 | R 抵赖 | 操作人否认自己审核/接诊/取消过 | 审核、接诊、结束、取消 | `audit_log` 设计含 `actor_id`/`action`/`result`/`detail`/`trace_id`/`hash_chain` | **审计写入未实现**（`audit/` 目录只有 `.gitkeep`） | **P0** |
| T-08 | R 抵赖 | 审计日志被删改 | `audit_log` | `hash_chain` 字段（SHA256 前链）设计抗篡改 | 有表无写入、无校验任务 | P1 |
| T-09 | I 泄漏 | 患者 A 读取患者 B 的会话/消息/处方 | `GET /api/v1/consultations/{id}`、`/prescriptions/{id}` | 归属校验 + 单测覆盖（`ConsultationTest` 越权用例、`AuthTest` 伪造 ADMIN） | 新接口缺测试时可能漏校验 | P1 |
| T-10 | I 泄漏 | XSS 直接读走 `localStorage` 里的 JWT | 前端全站 | Vue 模板默认转义；无 `v-html` 渲染用户输入 | 令牌放 `localStorage`，任一 XSS 即可窃取；无 CSP | **P0** |
| T-11 | I 泄漏 | 日志/异常打印令牌、密码、健康信息 | 服务端日志 | 过滤器不打印令牌（`JwtAuthenticationFilter` 仅清上下文）；异常统一走错误码 | `logging.level.com.medical.platform: DEBUG`，后续新增日志无脱敏约束 | P1 |
| T-12 | I 泄漏 | 数据库/备份被拖走导致 PII 外泄 | `patient_profile`、`sys_user` | 手机号/身份证/住址用 AES-GCM 密文 + HMAC 盲索引（`schema.sql`） | **`real_name` 等字段仍有明文列**；密钥与库同机；备份未加密 | P1 |
| T-13 | I 泄漏 | 本地/演示环境被公网访问 | `docker-compose.yml` | 文档要求仅本机访问；容器非 root 运行 | compose 直接映射 `3306/6379/8080`，误暴露到 0.0.0.0 即裸奔 | P1 |
| T-14 | D 拒绝服务 | 刷登录/刷消息/刷列表 | `login`、`POST /consultations/{id}/messages`、列表接口 | 消息正文限 4000 字（`@Size`），分页参数有边界；R6 增加登录/消息/全局限流，超限 429 `RATE_LIMITED` | 计数为单机内存、多实例不共享；JSON body 无总量上限 | P1 |
| T-15 | E 提权 | 患者调用医生接口（接诊/开方） | `accept`、`prescriptions` | 业务层校验 `ROLE_DOCTOR`/`ROLE_PHARMACIST`；单测覆盖 | 权限判定散落在应用服务，新增接口易漏 | P1 |
| T-16 | E 提权 | 药师审核/操作其他药房的处方 | R5 流转与核销 | 本期未实现药房域，`pharmacy`/`pharmacist_profile` 表已就绪 | R5 必须补“药房归属”校验，否则跨药房越权 | **P0（R5 前置）** |
| T-17 | E 提权 | 管理员接口（审计查询）无角色限制 | `GET /api/v1/audits`（规划） | 未实现 | 实现时须 `ROLE_ADMIN` + 审计查询本身不可改 | P1 |

## 6. 重点威胁分析

### T-01 默认密钥 + 无密钥轮换（P0）

- **攻击路径**：仓库公开 → 攻击者读到 `application.yml` 默认值 → 本地/演示环境未设置 `JWT_SECRET` → 自行签发 `role=ADMIN` 的令牌 → 直接调用任意接口。
- **为什么现在算 P0**：默认值写进了仓库（可接受，仅演示），但部署脚本没有“未设置就拒绝启动”的保护。
- **缓解**：① 启动时校验 `app.jwt.secret` 不等于默认值且长度 ≥32 字节，否则 `fail fast`；② CI 中增加一步检查（`deploy` 任务里 grep 默认串）；③ 文档明确生产必须注入。
- **验证**：新增单测 `JwtSecretGuardTest`：默认值启动抛异常；自定义值正常启动。

### T-07/T-08 审计缺失（P0）

- **攻击路径**：药师的审核、医生的接诊、患者的取消目前只体现在业务表的 `status` 上，没有独立操作流水。事后无法回答“谁在什么时候把处方从 `PENDING_REVIEW` 改成 `APPROVED`”。
- **现状**：`audit_log` 表结构完整（含 `hash_chain`），但 `audit` 包为空。
- **缓解**：在应用服务的事务里写 `audit_log`（同一事务，失败则回滚）；`hash_chain = SHA256(prev_hash + 本条规范化 JSON)`；提供只读查询接口，禁止更新/删除。
- **验证**：对审核通过、驳回、接诊、结束四个动作各写一条集成测试，断言 `audit_log` 行数与 `hash_chain` 链连续。

### T-09 水平越权（P1，已有防线）

- **攻击路径**：患者 A 拿到自己的令牌，把 URL 里的 `{id}` 换成 B 的会话 ID。
- **现状**：`ConsultationAppService` 按 `CurrentUser.id()` 校验归属，越权返回 `SCOPE_DENIED(403)`；处方侧 `RX_SCOPE_DENIED`；`ConsultationTest` 有对应用例。
- **残余**：新增接口（如 R5 的流转、核销）必须复制同一模式；建议抽一层 `OwnershipGuard` 复用，避免每个 `AppService` 各写一遍。

### T-10 令牌存 localStorage（P0）

- **攻击路径**：任何一处 XSS（第三方组件、富文本、未来图片说明字段）→ `localStorage.getItem('mp.token')` → 令牌外传 → 攻击者以该角色调用 API 至 `exp`。
- **现状**：`frontend/src/stores/auth.ts` 与 `api/http.ts` 都直接读写 `localStorage`。
- **缓解（按成本排序）**：① 后端下发 `HttpOnly; SameSite=Lax; Secure` 的 Cookie 承载令牌（需改 `JwtAuthenticationFilter` 取 Cookie）；② 至少加 CSP（`default-src 'self'`）+ 禁用 `v-html`；③ 缩短 `exp` 到 30 分钟并配合刷新令牌。
- **验证**：CSP 用浏览器控制台确认无 inline script 告警；Cookie 方案用 `AuthTest` 补一条“无 Cookie 即 401”。

### T-16 药房归属（P0，R5 前置）

- **攻击路径**：`pharmacist` 账号属于药房 X，直接调用药房 Y 的接单/核销接口（`circulation_order`、`dispense_record` 的主键可枚举）。
- **现状**：R5 未实现，`pharmacy`、`pharmacist_profile` 表已在 `schema.sql`。
- **缓解**：接方/核销接口一律从 JWT 取 `pharmacistId` → 查 `pharmacist_profile.pharmacy_id` → 与订单 `pharmacy_id` 比对，不一致返回 403；核销必须带 `Idempotency-Key`（`idempotency_record` 唯一键兜底）。
- **验证**：`CirculationIdempotencyTest`（R5 交付）覆盖跨药房访问、重复核销、并发核销。

## 7. 现有控制与缺口对照

| 控制项 | 设计 | 实现 | 证据 |
|---|---|---|---|
| 密码哈希 | 有 | 有 | `BCryptPasswordHasher`、`PasswordEncoder` Bean |
| 令牌验签与过期 | 有 | 有 | `JwtTokenProvider.parse` |
| 令牌密钥外部化 | 有 | 部分 | 支持 `JWT_SECRET` 覆盖，但 `application.yml` 保留了默认值 |
| RBAC（角色） | 有 | 有 | `ROLE_DOCTOR`/`ROLE_PHARMACIST` 判断 |
| 资源归属（防水平越权） | 有 | 有 | 问诊、处方两侧均校验；`SCOPE_DENIED`、`RX_SCOPE_DENIED` + 测试 |
| 参数校验 | 有 | 有 | `@Valid` + `@Size(max = 4000)` 等 |
| 状态机非法迁移 | 有 | 有 | `PrescriptionStatus.canApply` → `RX_STATE_INVALID` |
| 乐观锁（并发修订） | 有 | 有 | `RX_VERSION_CONFLICT` |
| 幂等键 | 有（契约 §3.7） | 无 | `idempotency_record` 表已就绪，代码未接入 |
| 审计写入 | 有（表结构） | 无 | `audit/` 目录仅 `.gitkeep` |
| 同意记录 | 有（表结构） | 无 | `consent_record` 未写入 |
| 限流 | 有（R6 起） | 有（单机固定窗口） | `RateLimitFilter`：登录/消息/全局三类阈值，429 `RATE_LIMITED`；多实例不共享 |
| CSP / HttpOnly 令牌 | 无 | 无 | 前端令牌存 `localStorage` |
| 依赖漏洞扫描 | 无 | 无 | CI 仅 `mvn verify` + compose 校验 |

## 8. 整改计划（按轮次）

| 编号 | 整改项 | 责任人 | 轮次 | 验收 |
|---|---|---|---|---|
| S-01 | 启动校验 JWT 默认密钥，未覆盖即失败 | 刘飞燕 | R5 | 单测 + compose 启动日志 |
| S-02 | 审计写入（审核/接诊/结束/取消）+ hash 链 | 李梓语 / 郝佳鑫 | R5–R6 | 4 条集成测试 + 链校验脚本 |
| S-03 | `Idempotency-Key` 落地（核销优先） | 李梓语 | R5 | 重放/并发用例（`CirculationIdempotencyTest`） |
| S-04 | 药房归属校验（接方/核销） | 刘飞燕 / 李梓语 | R5 | 跨药房 403 用例 |
| S-05 | 令牌改 HttpOnly Cookie 或至少加 CSP | 代香晰 | R6 | 浏览器验证 + 单测 |
| S-06 | 登录/消息接口限流 | 李梓语 | R6 | 已完成登录/消息/全局限流；`RateLimitFilterTest` 覆盖 429，账号锁定与分布式限流留作后续 |
| S-07 | PII 明文列统一加密（`real_name` 等） | 魏宇佳 | R6 | 迁移脚本 + 静态检查 |

## 9. 与测试的映射

| 威胁 | 已覆盖用例 | 缺口 |
|---|---|---|
| T-01 | — | 需新增默认密钥校验用例 |
| T-02 | `AuthTest` 错误密码、账号禁用；`RateLimitFilterTest` 登录 429 | 账号锁定、验证码未实现 |
| T-09 | `ConsultationTest` 越权访问、`AuthTest` 伪造 ADMIN | 处方越权已有 `RX_SCOPE_DENIED` 断言 |
| T-15 | `AuthTest` 角色校验、`ConsultationTest` 非本人接诊 | 新增模块需补 |
| T-05/T-06 | 处方状态机 8 状态正向/非法迁移用例 | 签名摘要强度未验证 |
| T-16 | — | R5 补 |

## 10. 残余风险与接受说明

1. **演示系统定位**：全套虚构数据、无真实患者、无支付与配送，因此部分医疗行业合规项（电子签名法、等保）不在本期范围，报告中明确声明“不可用于真实诊疗”。
2. **单机部署**：MySQL/Redis 单实例、无主从与备份演练，R5 部署文档里注明“仅演示”。
3. **密钥同机**：演示环境 `JWT_SECRET` 与数据库密码在同一 `.env`，R5 改为容器 secret 或 CI 注入。
