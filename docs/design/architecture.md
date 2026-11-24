# 架构设计（R2-A）

| 版本 | 日期 | 负责人 | 状态 | 关联文档 |
|---|---|---|---|---|
| v1.1 | 2026-11-24 | 刘飞燕 | 已接受（ADR-001），v1 实现边界已回写 | `docs/design/domain-model.md`、`docs/design/adr/ADR-001.md`、`docs/database/er.md`、`docs/demo/local-network-test.md` |

## 1. 这一版要解决什么

项目一共 8 周，真正能写代码的时间只有 4 周左右。需求里已经有问诊、处方、审核、流转、核销五条主链路，还夹着权限、审计和隐私。架构要解决的不是“看起来先进”，而是三件很具体的事：

1. 处方从草稿到核销，状态不能乱，跨表写入要在一个事务里；
2. 六个人能同时改代码，但不能互相踩包；
3. 课程机器上能跑起来，不依赖一整套微服务基础设施。

因此这一版采用 **模块化单体 + DDD 分层**：一个 Spring Boot 进程，按限界上下文分包，领域层不依赖 Spring 和 MyBatis。等以后流量或团队规模真的上来了，再把流转、通知这些边缘上下文拆出去。

## 2. 备选方案与取舍

| 方案 | 适合的场景 | 放到这个项目里的问题 | 结论 |
|---|---|---|---|
| 传统三层单体 | 功能少、团队小、交付快 | Controller 容易直接写业务，处方状态会散落在 Service 里 | 不作为主方案 |
| 微服务 | 多团队、独立扩容、边界稳定 | 本地要起注册中心/网关/消息队列；分布式事务和联调成本高 | 课程周期内不采用 |
| 模块化单体 + DDD 分层 | 业务复杂、团队中等、需要清晰边界 | 需要克制“跨包直接调用”的冲动 | **采用** |

没有选微服务，不是因为它不好，而是这个项目现阶段没有独立部署和独立扩容的需求。把处方、审核、流转拆成三个服务后，最麻烦的“处方审核后流转”会从本地事务变成分布式一致性问题；我们现在的测试和运维能力还接不住。

## 3. 分层与依赖方向

```text
interfaces        Controller、DTO、参数校验、权限注解
    ↓
application       用例编排、事务边界、命令/查询
    ↓
domain            聚合、实体、值对象、领域服务、仓储接口
    ↑
infrastructure    MyBatis、JWT、Redis、外部药房网关
```

几条硬规矩：

- `domain` 不 import Spring、MyBatis、Jackson；
- Controller 不直接改 `status` 字段，状态迁移只能走聚合方法；
- 跨上下文不直接访问对方的表，通过应用服务或领域事件；
- 外部药房、短信等第三方调用放在 `infrastructure`，领域层只依赖端口接口。

这样分的好处很实际：处方状态机可以脱离数据库做单元测试；换 ORM 或换短信服务商时，领域代码不用动。

## 4. 限界上下文

| 上下文 | 类型 | 主要职责 | 包路径 | 主要负责人 | v1 状态 |
|---|---|---|---|---|---|
| Identity | 通用 | 登录、JWT、角色、数据范围 | `identity` | 刘飞燕 | 已实现 |
| Consultation | 核心 | 会话、消息、接诊、病历 | `consultation` | 李梓语 | 会话/消息/接诊已实现；病历未开放接口 |
| Prescription | 核心 | 处方聚合、明细、提交、审核、状态机 | `prescription` | 李梓语、刘飞燕 | 已实现 |
| Circulation | 核心 | 流转单、药房接方、核销 | `circulation` | 刘飞燕 | 已实现 |
| Catalog | 支撑 | 药品目录、处方限制 | `catalog` | 魏宇佳 | 未实现（前端用演示药品） |
| Audit | 支撑 | 审计日志、风控事件 | `audit` | 班国勇 | 未实现（仅有 `audit_log` 表） |
| Notification | 通用 | 站内信、短信、模板 | 暂未单独建包 | 班国勇 | 未实现 |
| Admin | 通用 | 药房、配置、统计 | 暂未单独建包 | 魏宇佳 | 未实现（前端仅页面骨架） |

**口径**：上表是目标设计；v1 真正交付的是 Identity、Consultation、Prescription、Circulation 四个上下文。Catalog / Audit / Notification / Admin 只保留表结构、页面入口或后续扩展位置，不计入“已实现”。`catalog`、`audit` 包当前为空，`notification`、`admin` 没有建包。

## 5. 代码组织

```text
backend/src/main/java/com/medical/platform/
├─ identity/
│  ├─ domain/            User、UserStatus、UserRepository
│  ├─ application/       AuthAppService、LoginCommand、LoginResult
│  ├─ interfaces/        AuthController、LoginRequest/Response
│  └─ infrastructure/    SysUserMapper、JwtTokenProvider、SecurityConfig
├─ consultation/
├─ prescription/
├─ circulation/
├─ catalog/
└─ audit/
```

每个上下文内部按 `domain / application / interfaces / infrastructure` 分。如果某个上下文暂时只有一个类，也保留这四层，避免后面“先放 controller 再说”导致包结构长歪。

## 6. 关键流程

### 6.1 登录

```text
POST /api/v1/auth/login
  → AuthController 校验参数
  → AuthAppService 按用户名取用户
  → BCrypt 校验密码、检查账号状态
  → JwtTokenProvider 签发 access token
  → 返回 token + 用户角色
```

后续请求带 `Authorization: Bearer <token>`，`JwtAuthenticationFilter` 解析出用户 ID、用户名、角色，写入 SecurityContext。接口上用 `@PreAuthorize` 做角色/权限点校验，服务层再校验数据范围（本人、本科室、本药房）。

### 6.2 处方状态

处方聚合是唯一入口：

```text
DRAFT --submit--> PENDING_REVIEW
PENDING_REVIEW --approve--> APPROVED
PENDING_REVIEW --reject--> REJECTED
REJECTED --revise--> DRAFT
APPROVED --circulate--> CIRCULATED
CIRCULATED --redeem--> REDEEMED
```

Controller 只传 `prescriptionId`、`version` 和命令参数；聚合方法负责守卫条件、版本号 +1、登记领域事件。保存时用 `WHERE id = ? AND version = ?` 做乐观锁，更新 0 行就返回 409。

## 7. 数据与一致性

- **MySQL 8**：业务主库，处方、明细、流转单、审计在同一库；
- **本地事务**：处方状态、明细、审计、outbox 在同一个 `@Transactional` 里提交；
- **事务发件箱**：目标设计是审核通过、核销完成这类事件先写 `outbox_event`，后台任务再投递；**v1 未实现**，当前没有通知/外部药房回调；
- **Redis**：compose 和依赖中已预留，**v1 业务代码没有使用**；限流是进程内 `ConcurrentHashMap` 固定窗口，多实例不共享；
- **幂等**：流转、核销用业务唯一键 + 版本号，重复请求返回首次结果。

这一版没有引入 MQ。课程环境里多一个中间件，就多一套“起不来”的原因；目标态的 outbox + 定时轮询足够支撑演示，但当前代码只落地了本地事务和幂等表。

## 8. 安全边界

- 密码用 BCrypt，数据库只存哈希；
- JWT 默认 120 分钟，生产环境通过 `JWT_SECRET` 覆盖密钥；v1 没有刷新令牌与主动吊销；
- 登录、消息与全局接口做进程内固定窗口限流（`RateLimitFilter`）；审核、核销没有单独限流；
- 处方、问诊、流转接口按角色 + 资源归属双重校验；药师在审核阶段看全平台待审队列，流转/核销阶段按本药房隔离；
- `audit_log` 表已建，**v1 没有写入代码**，关键操作以业务表状态和 `prescription_review` 追溯；
- `patient_profile` 等表预留 `_enc` 密文与 `_hash` 盲索引列，v1 没有接入加密服务，演示数据为虚构值，页面不展示证件号。

## 9. 部署

**v1 实际演示环境**是单机 Docker Compose：MySQL 8 + Redis 7 + 后端三个容器，端口默认只绑 `127.0.0.1`；前端用 Vite 开发服务器监听 `0.0.0.0:5173`，浏览器通过 Vite 的 `/api` 代理访问后端。多台电脑接同一局域网即可，不需要云服务器，具体步骤见 `docs/demo/local-network-test.md`。

**目标部署**（未落地）才是 Nginx/WAF、两个后端实例、MySQL 主从、对象存储和 mTLS 药房接入；该图只作规划，不能写成“已部署”。

## 10. 已知取舍

1. **没有服务发现和网关**：单进程部署，Nginx 直接反代，够用；
2. **没有分库分表**：处方量按课程演示规模，单库足够；
3. **没有独立通知上下文**：先用 outbox + 简单发送器，避免过早抽象；
4. **权限缓存未做**：先每次查角色/权限，等 R5 压测后再决定是否加 Redis 缓存；
5. **前端未做微前端**：四端在一个 Vue 应用里按路由拆，课程演示不需要独立部署；
6. **v1 只交付四个核心上下文**：Catalog、Audit、Notification、Admin 未实现，论文/答辩中按“后续扩展”表述；
7. **审核阶段不做药房隔离**：处方在流转前没有绑定药房，v1 审核队列是全平台共享；药房隔离从流转单开始。

## 11. 评审时看什么

- 包结构是否真的按上下文分开，而不是所有 Service 堆在一个包；
- 处方状态是否只由聚合方法修改；
- 登录链路是否把密码校验、令牌签发、权限加载分开；
- 跨上下文调用是否只走应用服务/事件；
- 有没有为了“看起来高级”引入当前用不上的中间件。
