# 架构设计（R2-A）

| 版本 | 日期 | 负责人 | 状态 | 关联文档 |
|---|---|---|---|---|
| v1.0 | 2026-11-10 | 刘飞燕 | 评审中 | `docs/design/domain-model.md`、`docs/design/adr/ADR-001.md`、`docs/database/er.md` |

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

| 上下文 | 类型 | 主要职责 | 包路径 | 主要负责人 |
|---|---|---|---|---|
| Identity | 通用 | 登录、JWT、角色、数据范围 | `identity` | 刘飞燕 |
| Consultation | 核心 | 会话、消息、接诊、病历 | `consultation` | 李梓语 |
| Prescription | 核心 | 处方聚合、明细、提交、审核、状态机 | `prescription` | 李梓语、刘飞燕 |
| Circulation | 核心 | 流转单、药房接方、核销 | `circulation` | 刘飞燕 |
| Catalog | 支撑 | 药品目录、处方限制 | `catalog` | 魏宇佳 |
| Audit | 支撑 | 审计日志、风控事件 | `audit` | 班国勇 |
| Notification | 通用 | 站内信、短信、模板 | 暂未单独建包 | 班国勇 |
| Admin | 通用 | 药房、配置、统计 | 暂未单独建包 | 魏宇佳 |

Notification 和 Admin 现在不单独建包，是为了避免第 2 轮就铺太多空目录。等 R5 需要发通知、R6 需要统计时再落，不影响当前主链路。

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
- **事务发件箱**：审核通过、核销完成这类需要通知药房/患者的事件，先写 `outbox_event`，后台任务再投递，避免“业务成功、消息丢了”；
- **Redis**：只放令牌黑名单、幂等键、热点目录，不作为业务事实来源；
- **幂等**：流转、核销用业务唯一键 + 版本号，重复请求返回首次结果。

这一版没有引入 MQ。课程环境里多一个中间件，就多一套“起不来”的原因；outbox + 定时轮询足够支撑演示。

## 8. 安全边界

- 密码用 BCrypt，数据库只存哈希；
- JWT 默认 120 分钟，生产环境通过 `JWT_SECRET` 覆盖密钥；
- 登录、审核、核销接口做限流；
- 处方、病历、审计接口按角色 + 数据范围双重校验；
- 审计日志只追加，记录操作者、资源、动作、结果、时间；
- 患者身份证、手机号等敏感字段在数据库加密，页面脱敏。

## 9. 部署

本地用 Docker Compose 起 MySQL、Redis、后端；前端 Vite 开发服务器直连后端。演示环境用 Nginx 反代，后端无状态，可以起两个实例。具体编排在 R2-F 的 `deploy/docker-compose.yml` 里补。

## 10. 已知取舍

1. **没有服务发现和网关**：单进程部署，Nginx 直接反代，够用；
2. **没有分库分表**：处方量按课程演示规模，单库足够；
3. **没有独立通知上下文**：先用 outbox + 简单发送器，避免过早抽象；
4. **权限缓存未做**：先每次查角色/权限，等 R5 压测后再决定是否加 Redis 缓存；
5. **前端未做微前端**：四端在一个 Vue 应用里按路由拆，课程演示不需要独立部署。

## 11. 评审时看什么

- 包结构是否真的按上下文分开，而不是所有 Service 堆在一个包；
- 处方状态是否只由聚合方法修改；
- 登录链路是否把密码校验、令牌签发、权限加载分开；
- 跨上下文调用是否只走应用服务/事件；
- 有没有为了“看起来高级”引入当前用不上的中间件。
