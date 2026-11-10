# database —— 数据库设计（成员 魏宇佳 主责）

本目录存放智慧医疗在线问诊与处方流转平台的数据库设计与变更脚本。
第 1 轮的交付物是 **24 张表的清单与核心字段草案**，用于评审表结构、主外键、
索引与安全列设计，**不是可直接上线的最终脚本**。

## 1. 本目录文件

| 文件 | 作用 | 当前状态 |
|---|---|---|
| `schema.sql` | 24 张表的 DDL 草案（含表注释、字段注释、索引、CHECK 约束） | R1 草案 v0.1，待评审 |
| `../docs/database/table-list.md` | 24 张表的中文名、类别、主键、关键字段、关联关系 | R1 |
| `../docs/database/er.md` | 表关系说明与核心 ER 图（Mermaid，对应报告图 3-5） | R1 |
| `../docs/database/diagrams/er-core.png` | ER 图成品位图（2600×1480），可直接插入报告 | R1 |
| `../docs/database/diagrams/er-core.svg` | ER 图矢量源文件，可用 draw.io / Visio 修改 | R1 |
| `../docs/database/conventions.md` | 命名、类型、索引、外键、安全与迁移规范 | R1 |
| `../scripts/check_schema.py` | 无数据库环境下的静态结构自检脚本 | R1 |
| `../scripts/render_er_diagram.py` | ER 图生成脚本，表结构变更后重跑即可更新图 | R1 |

> 第 2 轮起新增 `seed.sql`（演示种子数据）与 `migrations/V002__core.sql` 等
> Flyway 版本化脚本；届时 `schema.sql` 转为 baseline，不再直接对生产库执行。

## 2. 24 张表是怎么分的

| 分组 | 张数 | 表 |
|---|---|---|
| 身份与权限 | 5 | `sys_user`、`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission` |
| 主数据 | 2 | `department`、`drug_category` |
| 患者 | 1 | `patient_profile` |
| 医生 / 药师 / 药房 | 3 | `doctor_profile`、`pharmacist_profile`、`pharmacy` |
| 问诊 | 3 | `consultation_session`、`consultation_message`、`medical_record` |
| 药品目录 | 1 | `drug` |
| 电子处方 | 2 | `prescription`、`prescription_item` |
| 流转与核销 | 2 | `circulation_order`、`dispense_record` |
| 审计与合规 | 2 | `audit_log`、`consent_record` |
| 通知 / 事件 / 幂等 | 3 | `notification`、`outbox_event`、`idempotency_record` |
| **合计** | **24** | 业务核心表 12 张 + 支撑表 12 张 |

## 3. 怎么用（本地验证）

```bash
# 0. 前置：MySQL 8.0.16+（CHECK 约束从 8.0.16 起真正生效）
mysql --version

# 1. 建库（本地开发专用，生产由 Flyway 管理）
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS medical_platform \
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

# 2. 导入草案
mysql -uroot -p medical_platform < database/schema.sql

# 3. 核对表数量，应输出 24
mysql -uroot -p -N -e "SELECT COUNT(*) FROM information_schema.tables \
  WHERE table_schema='medical_platform';"

# 4. 没有 MySQL 时，先做静态结构自检（表数量 / 外键目标 / 括号配平）
python scripts/check_schema.py database/schema.sql
```

Windows PowerShell 下第 2 步可用：

```powershell
Get-Content -Raw database/schema.sql | mysql -uroot -p medical_platform
```

## 4. 设计要点（评审时优先看这几条）

1. **一处方一流转单**：`circulation_order.rx_id` 建唯一键，从库层面杜绝重复流转。
2. **核销幂等**：`idempotency_record` 唯一键为 `(biz_type, biz_id, operation, idem_key)`，
   重复请求直接返回首次结果快照，不重复发药。
3. **乐观锁**：`prescription.version`、`consultation_session.version` 由应用层
   `UPDATE ... WHERE id=? AND version=?` 保证，受影响行数 0 即 409。
4. **状态受控**：所有状态列用 `VARCHAR(32)` + `CHECK` 枚举，与应用层枚举一致；
   处方状态只能经聚合方法迁移，禁止 SQL 直接改 `status`。
5. **敏感字段**：身份证、手机号、住址用 `_enc` 密文列（AES-GCM），
   检索用 `_hash` 盲索引（HMAC-SHA256），不存明文、不做明文模糊查询。
6. **审计留痕**：`audit_log` 只增不改不删，`hash_chain` 串成哈希链，保留 ≥ 6 个月；
   审计/事件/日志/幂等表**不建外键**，避免业务数据删除时被级联或阻塞。

## 5. 与报告的差异（需评审确认）

`schema.sql` 第 12 节列了全部补充字段及理由，摘要如下：

| 表 | 补充字段 | 原因 |
|---|---|---|
| `prescription` | `reviewer_id`、`reviewed_at`、`reject_reason`、`expires_at` | 报告要求记录审核人、驳回理由 ≥10 字、72 小时过期，但表 3-7 无承载列 |
| `circulation_order` | `accepted_at`、`reject_reason`、`version` | 状态机含“已接方/已拒绝”分支 |
| `audit_log` | `trace_id`、`ip` | traceId 贯穿日志、越权可追到来源 |
| `sys_user` | `phone_hash` | 报告要求 HMAC 盲索引检索，仅 `phone_enc` 无法查询 |
| `consultation_session` | `session_no` | 业务编号，便于客服与药房对单 |
| `prescription_item` | `usage_note`、`sort_no` | 用法说明与开方顺序 |

以上字段均为**增量补充**，不改变报告已有字段的类型与含义；
评审通过后在第 2 轮随 `V002__core.sql` 固化，并回写报告表 3-7 ~ 3-13。

## 6. 成员 魏宇佳 的六轮计划

| 轮次 | 交付物 | 关键动作 |
|---|---|---|
| R1（本轮） | `database/schema.sql` | 24 表清单、核心字段、索引与安全列草案 |
| R2 | `schema.sql`、`seed.sql`、`migrations/V002__core.sql` | 核心表定稿、演示种子、Flyway 接入 |
| R3 | `migrations/V003__auth_consult.sql`、`docs/database/er.md` | 认证/问诊索引、ER 更新 |
| R4 | `migrations/V004__prescription.sql`、`docs/database/audit.md` | 处方/审计表、版本字段、哈希链 |
| R5 | `deploy/`、`seed.sql` | 演示部署、种子数据、一键启动 |
| R6 | `README.md`、报告部署章节 | 部署说明、最终文档 |

## 7. 数据与合规声明

- 本仓库**不包含**任何真实患者数据；`seed.sql`（R2 起）中的姓名、手机号、
  身份证号、药品批号全部为虚构数据。
- 不提交 `.env`、数据库密码、证书私钥；连接信息通过环境变量注入。
- 本轮 DDL 由 AI 辅助生成初稿，字段含义、约束与索引均与报告表 3-7 ~ 3-13
  逐条核对，补充字段与差异已在第 5 节显式说明，供人工评审。
