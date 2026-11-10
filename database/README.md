# 数据库设计（魏宇佳）

本目录放数据库设计与变更脚本。第 1 轮交付 24 张表的清单和核心字段草案，供评审表结构、
主外键、索引与安全列；草案不是最终脚本，第 2 轮起改用 Flyway 迁移管理。

## 1. 文件说明

| 文件 | 作用 | 状态 |
|---|---|---|
| `schema.sql` | 24 张表 DDL（表注释、字段注释、索引、CHECK 约束） | R1 草案 v0.1，待评审 |
| `../docs/database/table-list.md` | 表清单：中文名、分类、主键、关键字段、关联 | R1 |
| `../docs/database/dictionary.xlsx` | 数据字典：表清单、235 个字段、索引、外键 | R1，由 DDL 解析生成 |
| `../docs/database/er.md` | 表关系说明与 ER 图 | R1 |
| `../docs/database/diagrams/` | ER 图、表总览图、部署图（png + svg） | R1 |
| `../docs/database/conventions.md` | 命名、类型、索引、外键、安全与迁移规范 | R1 |
| `../scripts/check_schema.py` | 无数据库时的静态结构自检 | R1 |
| `../scripts/build_dictionary.py` | 解析 DDL 生成数据字典 | R1 |
| `../scripts/render_er_diagram.py` | 生成 ER 图 | R1 |
| `../scripts/render_table_overview.py` | 生成表总览图 | R1 |

第 2 轮会增加 `seed.sql` 和 `migrations/V002__core.sql`，那时 `schema.sql` 只作基线参考，
不再直接对生产库执行。

## 2. 表的分组

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
| 合计 | 24 | 核心 12 张 + 支撑 12 张 |

## 3. 本地验证

```bash
# 0. 需要 MySQL 8.0.16+（CHECK 约束从 8.0.16 起生效）
mysql --version

# 1. 建库（本地用，生产由 Flyway 管理）
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS medical_platform \
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

# 2. 导入
mysql -uroot -p medical_platform < database/schema.sql

# 3. 核对表数量，应输出 24
mysql -uroot -p -N -e "SELECT COUNT(*) FROM information_schema.tables \
  WHERE table_schema='medical_platform';"

# 4. 没有 MySQL 时先做静态检查
python scripts/check_schema.py database/schema.sql
```

PowerShell 下第 2 步：

```powershell
Get-Content -Raw database/schema.sql | mysql -uroot -p medical_platform
```

## 4. 关键约束

1. 一处方一流转单：`circulation_order.rx_id` 建唯一键，重复流转在库层就被拒绝。
2. 核销幂等：`idempotency_record` 唯一键 `(biz_type, biz_id, operation, idem_key)`，
   重复请求返回首次结果，不重复发药。
3. 乐观锁：`prescription.version`、`consultation_session.version` 由
   `UPDATE ... WHERE id=? AND version=?` 控制，影响行数为 0 返回 409。
4. 状态受控：状态列统一 `VARCHAR(32)` + `CHECK` 枚举，与应用层枚举值一致；
   处方状态只能通过聚合方法迁移，不允许 SQL 直接改。
5. 敏感字段：身份证、手机号、住址存 `_enc` 密文列（AES-GCM），
   检索走 `_hash` 盲索引（HMAC-SHA256），不存明文，也不做明文模糊查询。
6. 审计：`audit_log` 只增不改不删，`hash_chain` 串成哈希链，保留不少于 6 个月；
   审计、事件、日志、幂等表不建外键，避免业务数据删除时被级联或阻塞。

## 5. 与报告的差异

`schema.sql` 第 12 节列了补充字段和理由，摘要如下：

| 表 | 补充字段 | 原因 |
|---|---|---|
| `prescription` | `reviewer_id`、`reviewed_at`、`reject_reason`、`expires_at` | 报告要求记录审核人、驳回理由 ≥10 字、72 小时过期，表 3-7 没有对应列 |
| `circulation_order` | `accepted_at`、`reject_reason`、`version` | 状态机有“已接方/已拒绝”两个分支 |
| `audit_log` | `trace_id`、`ip` | traceId 贯穿日志，越权要能追到来源 |
| `sys_user` | `phone_hash` | 报告要求 HMAC 盲索引，只有 `phone_enc` 无法查询 |
| `consultation_session` | `session_no` | 业务编号，客服和药房对单用 |
| `prescription_item` | `usage_note`、`sort_no` | 用法说明与开方顺序 |

这些都是增量补充，不改变报告已有字段的类型和含义。评审通过后在第 2 轮随
`V002__core.sql` 固化，并回写报告表 3-7 ~ 3-13。

## 6. 六轮计划

| 轮次 | 交付物 | 主要工作 |
|---|---|---|
| R1（本轮） | `schema.sql`、数据字典、ER 图、部署图 | 表清单、核心字段、索引与安全列草案 |
| R2 | `schema.sql`、`seed.sql`、`V002__core.sql` | 核心表定稿、演示种子、接入 Flyway |
| R3 | `V003__auth_consult.sql`、`er.md` | 认证/问诊索引、ER 更新 |
| R4 | `V004__prescription.sql`、`audit.md` | 处方/审计表、版本字段、哈希链 |
| R5 | `deploy/`、`seed.sql` | 演示部署、种子数据、一键启动 |
| R6 | `README.md`、报告部署章节 | 部署说明、最终文档 |

## 7. 数据与合规

- 仓库不包含真实患者数据；`seed.sql`（R2 起）里的姓名、手机号、身份证号、药品批号全部虚构。
- 不提交 `.env`、数据库密码、证书私钥，连接信息通过环境变量注入。
- DDL 初稿由 AI 辅助生成，字段含义、约束与索引已对照报告表 3-7 ~ 3-13 逐条核对，
  补充字段和差异在第 5 节写明，供人工评审。
