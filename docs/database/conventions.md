# 数据库设计规范

> 适用于 `database/` 下全部脚本与后续 Flyway 迁移。新增表、改字段前先对照本文件；
> 规范变更需在 PR 中说明理由并经跨成员评审。

## 1. 命名

| 对象 | 规则 | 示例 |
|---|---|---|
| 表名 | 小写 + 下划线，单数或业务名词，不加前缀缩写 | `prescription`、`circulation_order` |
| 关联表 | `<主体>_<客体>` | `sys_user_role`、`sys_role_permission` |
| 字段名 | 小写下划线，避免 MySQL 保留字 | `reviewed_at`、`chief_complaint` |
| 主键 | 统一 `id` | `id BIGINT AUTO_INCREMENT` |
| 外键字段 | `<被引用表单数>_id` | `patient_id`、`pharmacy_id` |
| 唯一键 | `uk_<语义>` | `uk_rx_no`、`uk_idem` |
| 普通索引 | `idx_<字段语义>` | `idx_doctor_status` |
| 外键约束 | `fk_<表简称>_<引用对象>` | `fk_rx_session` |
| 检查约束 | `ck_<表简称>_<语义>` | `ck_rx_status` |
| 敏感密文列 | `_enc` 结尾，类型 `VARBINARY` | `phone_enc`、`id_card_enc` |
| 盲索引列 | `_hash` 结尾，类型 `CHAR(64)` | `phone_hash`、`id_card_hash` |

## 2. 类型与默认值

| 场景 | 类型 | 说明 |
|---|---|---|
| 主键 / 外键 | `BIGINT` | 应用层雪花 ID 或自增；不使用 `INT`，避免 10 年后溢出 |
| 业务编号 | `VARCHAR(32)` | 如 `rx_no`、`circulation_no`，另建唯一键 |
| 状态 | `VARCHAR(32)` | 存英文常量（`PENDING_REVIEW`），配合 `CHECK` 约束 |
| 布尔 | `TINYINT` | `1/0` + `CHECK (col IN (0,1))`，不用 `BIT` |
| 金额 | `DECIMAL(10,2)` | 禁止 `FLOAT/DOUBLE` |
| 剂量 | `DECIMAL(10,3)` | 支持 0.125mg 这类精度 |
| 时间 | `DATETIME(3)` | 毫秒精度；`created_at DEFAULT CURRENT_TIMESTAMP(3)`，`updated_at ... ON UPDATE` |
| 长文本 | `VARCHAR(n)` 或 `TEXT` | 超过 4000 字用 `TEXT`，并对查询做限制 |
| 结构化扩展 | `JSON` | 仅用于审计详情、事件载荷、结果快照，**不用于需要索引过滤的业务字段** |
| 敏感信息 | `VARBINARY(255)` | AES-GCM 密文，禁止 `VARCHAR` 存 Base64 明文 |

字符集统一 `utf8mb4` / `utf8mb4_0900_ai_ci`，引擎统一 `InnoDB`，每张表必须写 `COMMENT`。

## 3. 键与索引

1. **每表必须有主键**，且为单列 `id`；不使用联合主键（关联表用唯一键代替）。
2. **业务唯一性用唯一键表达**，不靠应用层 `SELECT ... IF NOT EXISTS`：
   `uk_rx_no`、`uk_rx`（一处方一流转单）、`uk_idem`（幂等）。
3. **索引命名与列顺序**：等值列在前、范围/排序列在后，
   例如 `idx_doctor_status (doctor_id, status, created_at)`。
4. **单表索引不超过 6 个**；新增索引须在 PR 中说明对应查询。
5. 低频、超长文本列不建索引；模糊搜索走全文索引或搜索引擎，不用 `LIKE '%x%'`。

## 4. 外键

- 核心业务表建物理外键，保证引用完整性。
- 审计、事件、通知、幂等表**不建外键**：这些表需要独立归档、批量清理，
  外键会造成级联删除风险与写入锁等待；改由应用层校验 + 索引。
- 不使用 `ON DELETE CASCADE`：医疗数据不允许级联删除，删除一律用状态位或归档表。

## 5. 状态与不变量

- 状态字段一律 `VARCHAR(32)` + `CHECK` 枚举，与应用层枚举值**逐字一致**（全大写）。
- 处方状态只能通过 `Prescription` 聚合方法迁移；数据库层再加 `CHECK` 兜底，
  禁止 `UPDATE prescription SET status=...` 这类脚本改状态。
- 并发写用 `version` 乐观锁：`UPDATE ... SET version = version + 1 WHERE id = ? AND version = ?`，
  受影响行数为 0 返回 409。
- 关键唯一约束在数据库层实现，不能只靠代码判断（重试、并发、多实例都会绕过代码判断）。

## 6. 安全

| 要求 | 做法 |
|---|---|
| 敏感字段加密 | 身份证、手机号、住址用 AES-GCM，密文列 `_enc`；密钥由 KMS/环境变量注入，不入库 |
| 等值检索 | HMAC-SHA256 盲索引 `_hash`，加盐密钥与加密密钥分离 |
| 密码 | Argon2id 或 bcrypt，`password_hash VARCHAR(255)`；禁止 MD5/SHA1 |
| 最小权限 | 应用账号仅有 `SELECT/INSERT/UPDATE/DELETE`，无 `DROP/GRANT/ALTER` |
| 审计 | 审计表只增不改不删；`hash_chain` 串链，保留 ≥ 6 个月 |
| 数据脱敏 | 导出、日志、报错信息均不出现明文手机号/身份证号 |
| 测试数据 | 全部虚构，禁止导入真实患者数据 |

## 7. 变更与迁移

1. 第 2 轮起使用 Flyway：`V002__core.sql`、`V003__auth_consult.sql`、`V004__prescription.sql`……
   文件名版本号连续，已执行的脚本**不得修改**，只能追加新版本。
2. 破坏性变更（删列、改类型、加非空列）必须走“扩展—迁移—收缩”三步：
   先加新列并双写，再回填数据，最后删旧列。
3. 每次迁移需同时更新：`docs/database/table-list.md`、`docs/database/er.md`、
   报告表 3-6 ~ 3-13，以及受影响的测试用例。
4. 迁移脚本必须在空库与已有数据的库上各跑一次（R2 起由 Testcontainers 自动执行）。

## 8. 提交前自检清单

- [ ] `python scripts/check_schema.py database/schema.sql` 通过
- [ ] 表数量与报告表 3-6 一致（24 张）
- [ ] 新增/修改字段已同步 `table-list.md` 与 `er.md`
- [ ] 新增唯一键/索引已在 PR 中说明对应查询与收益
- [ ] 无明文敏感字段、无真实患者数据、无数据库密码
- [ ] 迁移脚本可在空库重放成功
