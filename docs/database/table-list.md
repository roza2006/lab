# 数据库表清单（24 张）

> 对应报告表 3-6；字段级 DDL 见 [`database/schema.sql`](../../database/schema.sql)。
> 类别说明：**核心**＝承载主要业务不变量；**支撑**＝权限、字典、审计、通知等支撑能力。
> 命名规范见 [`conventions.md`](./conventions.md)。

## 1. 总览

| 序号 | 表名 | 中文名 | 类别 | 主键 | 关键业务唯一键 | 主要关联 |
|---:|---|---|---|---|---|---|
| 1 | `sys_user` | 用户账号表 | 支撑 | `id` | `username`、`phone_hash` | → `patient_profile` / `doctor_profile` / `pharmacist_profile` |
| 2 | `sys_role` | 角色表 | 支撑 | `id` | `role_code` | ← `sys_user_role` |
| 3 | `sys_permission` | 权限点表 | 支撑 | `id` | `perm_code` | ← `sys_role_permission` |
| 4 | `sys_user_role` | 用户角色关联表 | 支撑 | `id` | `(user_id, role_id)` | `sys_user` × `sys_role` |
| 5 | `sys_role_permission` | 角色权限关联表 | 支撑 | `id` | `(role_id, perm_id)` | `sys_role` × `sys_permission` |
| 6 | `patient_profile` | 患者档案表 | 核心 | `id` | `user_id`、`id_card_hash` | → `consultation_session` |
| 7 | `doctor_profile` | 医生档案表 | 核心 | `id` | `user_id`、`license_no` | → `consultation_session`、`prescription` |
| 8 | `pharmacist_profile` | 药师档案表 | 核心 | `id` | `user_id`、`license_no` | → `prescription.reviewer_id` |
| 9 | `department` | 科室表 | 支撑 | `id` | `dept_code` | ← `doctor_profile`、`consultation_session` |
| 10 | `consultation_session` | 问诊会话表 | 核心 | `id` | `session_no` | ← `consultation_message`、`medical_record`、`prescription` |
| 11 | `consultation_message` | 问诊消息表 | 核心 | `id` | `(session_id, client_msg_id)` | → `consultation_session` |
| 12 | `medical_record` | 病历表 | 核心 | `id` | `session_id`（一会话一病历） | → `consultation_session` |
| 13 | `prescription` | 处方主表 | 核心 | `id` | `rx_no` | → `consultation_session`、`patient_profile`、`doctor_profile` |
| 14 | `prescription_item` | 处方明细表 | 核心 | `id` | `(prescription_id, drug_id)` | → `prescription`、`drug` |
| 15 | `drug` | 药品目录表 | 核心 | `id` | `drug_code` | ← `prescription_item` |
| 16 | `drug_category` | 药品分类表 | 支撑 | `id` | `category_code` | ← `drug` |
| 17 | `pharmacy` | 合作药房表 | 核心 | `id` | `pharmacy_code`、`license_no` | ← `circulation_order`、`pharmacist_profile` |
| 18 | `circulation_order` | 处方流转单表 | 核心 | `id` | `rx_id`（一处方一流转单）、`idempotency_key` | → `prescription`、`pharmacy` |
| 19 | `dispense_record` | 发药核销记录表 | 核心 | `id` | `circulation_id`（一次核销一条） | → `circulation_order`、`sys_user` |
| 20 | `audit_log` | 审计日志表 | 支撑 | `id` | —（只增不改不删） | 逻辑关联各业务表，**不建外键** |
| 21 | `consent_record` | 知情同意记录表 | 支撑 | `id` | —（按版本追加） | → `patient_profile`（逻辑） |
| 22 | `notification` | 通知表 | 支撑 | `id` | — | → `sys_user`（逻辑） |
| 23 | `outbox_event` | 事务发件箱表 | 支撑 | `id` | —（按状态+重试时间扫描） | 逻辑关联 `prescription` / `circulation_order` |
| 24 | `idempotency_record` | 幂等记录表 | 支撑 | `id` | `(biz_type, biz_id, operation, idem_key)` | 逻辑关联各写接口 |

## 2. 分组统计

| 分组 | 张数 | 表 |
|---|---:|---|
| 身份与权限 | 5 | `sys_user` `sys_role` `sys_permission` `sys_user_role` `sys_role_permission` |
| 主数据 | 2 | `department` `drug_category` |
| 患者档案 | 1 | `patient_profile` |
| 医生 / 药师 / 药房 | 3 | `doctor_profile` `pharmacist_profile` `pharmacy` |
| 问诊 | 3 | `consultation_session` `consultation_message` `medical_record` |
| 药品目录 | 1 | `drug` |
| 电子处方 | 2 | `prescription` `prescription_item` |
| 流转与核销 | 2 | `circulation_order` `dispense_record` |
| 审计与合规 | 2 | `audit_log` `consent_record` |
| 通知 / 事件 / 幂等 | 3 | `notification` `outbox_event` `idempotency_record` |
| **合计** | **24** | 核心 12 张 + 支撑 12 张 |

## 3. 核心表字段要点（与报告表 3-7 ~ 3-13 对齐）

### 3.1 `prescription` 处方主表

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK | 处方 ID |
| `rx_no` | VARCHAR(32) | UK, NOT NULL | 处方编号，RX+日期+序列 |
| `session_id` | BIGINT | FK, NOT NULL | 绑定问诊会话 |
| `patient_id` | BIGINT | FK, NOT NULL | 患者（冗余便于列表查询） |
| `doctor_id` | BIGINT | FK, NOT NULL | 开方医生 |
| `status` | VARCHAR(32) | NOT NULL, CHECK | 8 状态枚举 |
| `version` | INT | NOT NULL DEFAULT 1 | 乐观锁版本号 |
| `signed_hash` | CHAR(64) | NULL | 审核通过后的签名摘要 |
| `reviewer_id` / `reviewed_at` / `reject_reason` / `expires_at` | — | NULL | R1 补充字段，见 schema 第 12 节 |

索引：`uk_rx_no`、`idx_doctor_status(doctor_id,status,created_at)`、`idx_patient_created`、`idx_rx_expire(status,expires_at)`。

### 3.2 `prescription_item` 处方明细表

`dosage DECIMAL(10,3)`、`dosage_unit`、`frequency`、`days INT`（CHECK 1–30）、`quantity INT`，
唯一键 `(prescription_id, drug_id)` 防止同一处方重复开同一药品。

### 3.3 `consultation_session` 问诊会话表

`status` ∈ `WAITING / IN_PROGRESS / ENDED / CANCELLED`；`version` 用于并发接诊控制；
`doctor_id` 可空（接诊后写入）；`chief_complaint` 必填。

### 3.4 `circulation_order` 处方流转单表

`rx_id` 唯一（一处方一流转单）；`idempotency_key` 唯一（流转/核销去重）；
`status` ∈ `CREATED / ACCEPTED / REDEEMED / REJECTED`。

### 3.5 `dispense_record` 发药核销记录表

`circulation_id` 唯一（一次核销一条）；`batch_no` 用于药品追溯；`evidence` 存取药码/人脸流水号。

### 3.6 `audit_log` 审计日志表

关键索引 `(resource_type, resource_id, created_at)`、`(actor_id, created_at)`；
`hash_chain` 为 SHA-256 哈希链；**不建外键**，避免业务删除与审计留存冲突。

### 3.7 `sys_user` 用户账号表

`username` 唯一；`password_hash` 存 Argon2id/bcrypt；`phone_enc` 为 AES-GCM 密文；
`phone_hash` 为 HMAC 盲索引并唯一，用于手机号等值登录/检索。

## 4. 待评审事项

1. 第 3.1 节的 4 个补充字段是否采纳（理由见 `database/schema.sql` 第 12 节）。
2. `patient_profile` / `doctor_profile` / `pharmacist_profile` 与 `sys_user` 为 1:1，
   是否改为共用一张 `sys_user` 表 + 角色扩展表（当前按报告保留独立档案表）。
3. `audit_log.detail` 用 MySQL `JSON` 类型，需确认部署环境 MySQL ≥ 5.7（当前目标 8.0）。
4. 是否需要在 R2 增加 `risk_event`（风控事件）表——报告中“审计与风控”上下文已提及，
   但 24 张表清单未包含，暂不新增。
