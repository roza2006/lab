-- =============================================================================
-- V002__core.sql —— 核心表结构（第 2 轮）
-- -----------------------------------------------------------------------------
-- 24 张表的建表脚本：主键、唯一键、外键、CHECK 约束与命名索引齐全。
-- 第 3 轮按问诊代码里真实的 SQL 再加索引，见 V003__auth_consult.sql。
--
-- 与 database/schema.sql 的关系：
--   schema.sql 是给人看的完整视图，本文件是 Flyway 执行用的版本；
--   先把结构建起来，后续轮次用 ALTER 增量演进而不是改这份脚本。
-- =============================================================================

SET NAMES utf8mb4;

CREATE TABLE sys_role (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_code   VARCHAR(32)  NOT NULL                COMMENT '角色编码：PATIENT/DOCTOR/PHARMACIST/PHARMACY/ADMIN',
    role_name   VARCHAR(64)  NOT NULL                COMMENT '角色名称',
    description VARCHAR(255) NULL                    COMMENT '角色说明',
    builtin     TINYINT      NOT NULL DEFAULT 0      COMMENT '是否内置角色：1 是 0 否（内置不可删除）',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code),
    CONSTRAINT ck_role_builtin CHECK (builtin IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '角色表：RBAC 的角色定义';

CREATE TABLE sys_permission (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    perm_code   VARCHAR(64)  NOT NULL                COMMENT '权限点编码，如 rx:approve',
    perm_name   VARCHAR(64)  NOT NULL                COMMENT '权限点名称',
    resource    VARCHAR(64)  NOT NULL                COMMENT '资源标识，如 prescription',
    action      VARCHAR(32)  NOT NULL                COMMENT '动作：read/create/update/approve/redeem',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_perm_code (perm_code),
    KEY idx_perm_resource (resource, action)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '权限点表：接口级权限的最小单位';

CREATE TABLE sys_user (
    id            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '用户 ID',
    username      VARCHAR(64)    NOT NULL                COMMENT '登录名（UK）',
    password_hash VARCHAR(255)   NOT NULL                COMMENT '密码哈希：Argon2id/bcrypt，禁止明文或 MD5',
    phone_enc     VARBINARY(255) NOT NULL                COMMENT '手机号密文（AES-GCM）',
    phone_hash    CHAR(64)       NOT NULL                COMMENT '手机号盲索引（HMAC-SHA256），仅用于等值检索',
    role_code     VARCHAR(32)    NOT NULL                COMMENT '主角色编码，用于快速鉴权；多角色以 sys_user_role 为准',
    status        TINYINT        NOT NULL DEFAULT 1      COMMENT '状态：1 正常 0 禁用',
    last_login_at DATETIME(3)    NULL                    COMMENT '最后登录时间',
    created_at    DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at    DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_phone_hash (phone_hash),
    KEY idx_role_status (role_code, status),
    CONSTRAINT ck_user_status CHECK (status IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '用户账号表：登录凭证与账号状态';

CREATE TABLE sys_user_role (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT      NOT NULL                COMMENT '用户 ID',
    role_id    BIGINT      NOT NULL                COMMENT '角色 ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_role_user (role_id, user_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES sys_role (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '用户角色关联表：支持一人多角色';

CREATE TABLE sys_role_permission (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_id    BIGINT      NOT NULL                COMMENT '角色 ID',
    perm_id    BIGINT      NOT NULL                COMMENT '权限点 ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_perm (role_id, perm_id),
    KEY idx_perm_role (perm_id, role_id),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES sys_role (id),
    CONSTRAINT fk_rp_perm FOREIGN KEY (perm_id) REFERENCES sys_permission (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '角色权限关联表：权限配置变更需二次确认';

CREATE TABLE department (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    dept_code  VARCHAR(32)  NOT NULL                COMMENT '科室编码（UK）',
    dept_name  VARCHAR(64)  NOT NULL                COMMENT '科室名称',
    parent_id  BIGINT       NULL                    COMMENT '上级科室，NULL 表示一级科室',
    sort_no    INT          NOT NULL DEFAULT 0      COMMENT '排序号',
    status     TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 启用 0 停用',
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dept_code (dept_code),
    KEY idx_dept_parent (parent_id, sort_no),
    CONSTRAINT fk_dept_parent FOREIGN KEY (parent_id) REFERENCES department (id),
    CONSTRAINT ck_dept_status CHECK (status IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '科室表：医生执业科室与处方权范围';

CREATE TABLE drug_category (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    category_code VARCHAR(32)  NOT NULL                COMMENT '分类编码（UK）',
    category_name VARCHAR(64)  NOT NULL                COMMENT '分类名称',
    parent_id     BIGINT       NULL                    COMMENT '上级分类，NULL 表示一级分类',
    sort_no       INT          NOT NULL DEFAULT 0      COMMENT '排序号',
    status        TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 启用 0 停用',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_code (category_code),
    KEY idx_category_parent (parent_id, sort_no),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES drug_category (id),
    CONSTRAINT ck_category_status CHECK (status IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '药品分类表：目录两级分类';

CREATE TABLE patient_profile (
    id             BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id        BIGINT         NOT NULL                COMMENT '关联账号（UK，一人一档）',
    real_name_enc  VARBINARY(255) NOT NULL                COMMENT '姓名密文（AES-GCM）',
    id_card_enc    VARBINARY(255) NULL                    COMMENT '身份证号密文（AES-GCM）',
    id_card_hash   CHAR(64)       NULL                    COMMENT '身份证号盲索引（HMAC-SHA256）',
    gender         TINYINT        NOT NULL DEFAULT 0      COMMENT '性别：0 未知 1 男 2 女',
    birth_date     DATE           NULL                    COMMENT '出生日期（由身份证解析，仅存日期）',
    phone_enc      VARBINARY(255) NOT NULL                COMMENT '手机号密文',
    address_enc    VARBINARY(255) NULL                    COMMENT '住址密文',
    allergy_history VARCHAR(1000) NULL                    COMMENT '过敏史（供处方过敏校验，页面脱敏展示）',
    consent_status TINYINT        NOT NULL DEFAULT 0      COMMENT '知情同意状态：1 已同意 0 未同意',
    created_at     DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at     DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_patient_user (user_id),
    UNIQUE KEY uk_patient_id_card (id_card_hash),
    KEY idx_patient_consent (consent_status),
    CONSTRAINT fk_patient_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT ck_patient_gender CHECK (gender IN (0, 1, 2)),
    CONSTRAINT ck_patient_consent CHECK (consent_status IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '患者档案表：实名信息密文存储，禁止明文检索';

CREATE TABLE doctor_profile (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id        BIGINT       NOT NULL                COMMENT '关联账号（UK）',
    real_name      VARCHAR(64)  NOT NULL                COMMENT '姓名',
    title          VARCHAR(32)  NOT NULL                COMMENT '职称：住院医师/主治医师/副主任医师/主任医师',
    department_id  BIGINT       NOT NULL                COMMENT '所属科室',
    license_no     VARCHAR(64)  NOT NULL                COMMENT '执业证书编号（UK）',
    practice_scope VARCHAR(255) NOT NULL                COMMENT '执业范围（决定可开具的药品/科室）',
    rx_right       TINYINT      NOT NULL DEFAULT 0      COMMENT '是否有处方权：1 有 0 无',
    status         TINYINT      NOT NULL DEFAULT 1      COMMENT '在岗状态：1 在岗 0 停诊',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doctor_user (user_id),
    UNIQUE KEY uk_doctor_license (license_no),
    KEY idx_doctor_dept (department_id, status),
    CONSTRAINT fk_doctor_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_doctor_dept FOREIGN KEY (department_id) REFERENCES department (id),
    CONSTRAINT ck_doctor_rx_right CHECK (rx_right IN (0, 1)),
    CONSTRAINT ck_doctor_status CHECK (status IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '医生档案表：执业资质与处方权';

CREATE TABLE pharmacy (
    id               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    pharmacy_code    VARCHAR(32)    NOT NULL                COMMENT '药房编码（UK）',
    pharmacy_name    VARCHAR(128)   NOT NULL                COMMENT '药房名称',
    license_no       VARCHAR(64)    NOT NULL                COMMENT '药品经营许可证号（UK）',
    license_expire_at DATE          NOT NULL                COMMENT '许可证到期日，过期不得接方',
    contact_phone_enc VARBINARY(255) NULL                   COMMENT '联系电话密文',
    address          VARCHAR(255)   NOT NULL                COMMENT '经营地址',
    service_scope    VARCHAR(255)   NULL                    COMMENT '服务范围说明',
    status           TINYINT        NOT NULL DEFAULT 1      COMMENT '资质状态：1 有效 0 无效/停用',
    created_at       DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at       DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pharmacy_code (pharmacy_code),
    UNIQUE KEY uk_pharmacy_license (license_no),
    KEY idx_pharmacy_status (status, license_expire_at),
    CONSTRAINT ck_pharmacy_status CHECK (status IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '合作药房表：资质有效性是流转/接方的前置条件';

CREATE TABLE pharmacist_profile (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT       NOT NULL                COMMENT '关联账号（UK）',
    real_name     VARCHAR(64)  NOT NULL                COMMENT '姓名',
    pharmacy_id   BIGINT       NOT NULL                COMMENT '所属药房（决定审核数据范围）',
    license_no    VARCHAR(64)  NOT NULL                COMMENT '执业药师证书编号（UK）',
    review_right  TINYINT      NOT NULL DEFAULT 0      COMMENT '是否有审核权：1 有 0 无',
    status        TINYINT      NOT NULL DEFAULT 1      COMMENT '在岗状态：1 在岗 0 离岗',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pharmacist_user (user_id),
    UNIQUE KEY uk_pharmacist_license (license_no),
    KEY idx_pharmacist_pharmacy (pharmacy_id, status),
    CONSTRAINT fk_pharmacist_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_pharmacist_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacy (id),
    CONSTRAINT ck_pharmacist_review CHECK (review_right IN (0, 1)),
    CONSTRAINT ck_pharmacist_status CHECK (status IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '药师档案表：审核权与药房归属';

CREATE TABLE consultation_session (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '会话 ID',
    session_no      VARCHAR(32)   NULL                    COMMENT '会话编号（业务编号，UK）',
    patient_id      BIGINT        NOT NULL                COMMENT '发起人（患者）',
    doctor_id       BIGINT        NULL                    COMMENT '接诊医生，接诊后写入',
    department_id   BIGINT        NULL                    COMMENT '就诊科室（候诊队列分组）',
    status          VARCHAR(24)   NOT NULL                COMMENT '状态：WAITING 待接诊 / IN_PROGRESS 进行中 / ENDED 已结束 / CANCELLED 已取消',
    chief_complaint VARCHAR(1000) NOT NULL                COMMENT '主诉，患者填写',
    started_at      DATETIME(3)   NULL                    COMMENT '接诊时间',
    ended_at        DATETIME(3)   NULL                    COMMENT '结束时间',
    version         INT           NOT NULL DEFAULT 1      COMMENT '版本号：并发接诊乐观锁',
    created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_no (session_no),
    KEY idx_patient_created (patient_id, created_at),
    KEY idx_doctor_status (doctor_id, status, created_at),
    KEY idx_queue (department_id, status, created_at),
    CONSTRAINT fk_session_patient FOREIGN KEY (patient_id) REFERENCES patient_profile (id),
    CONSTRAINT fk_session_doctor FOREIGN KEY (doctor_id) REFERENCES doctor_profile (id),
    CONSTRAINT fk_session_dept FOREIGN KEY (department_id) REFERENCES department (id),
    CONSTRAINT ck_session_status CHECK (status IN ('WAITING', 'IN_PROGRESS', 'ENDED', 'CANCELLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '问诊会话表：接诊用 version 乐观锁，禁止重复接诊';

CREATE TABLE consultation_message (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '消息 ID',
    session_id    BIGINT       NOT NULL                COMMENT '所属会话',
    sender_id     BIGINT       NOT NULL                COMMENT '发送者用户 ID',
    sender_role   VARCHAR(32)  NOT NULL                COMMENT '发送者角色：PATIENT/DOCTOR/SYSTEM',
    msg_type      VARCHAR(16)  NOT NULL DEFAULT 'TEXT' COMMENT '消息类型：TEXT/IMAGE/FILE/SYSTEM',
    content       VARCHAR(4000) NOT NULL               COMMENT '消息正文（图片/文件存对象存储键，正文不落二进制）',
    client_msg_id VARCHAR(64)  NULL                    COMMENT '客户端消息 ID，用于重发去重',
    sent_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发送时间',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_msg_client (session_id, client_msg_id),
    KEY idx_session_sent (session_id, sent_at),
    CONSTRAINT fk_msg_session FOREIGN KEY (session_id) REFERENCES consultation_session (id),
    CONSTRAINT ck_msg_type CHECK (msg_type IN ('TEXT', 'IMAGE', 'FILE', 'SYSTEM')),
    CONSTRAINT ck_msg_role CHECK (sender_role IN ('PATIENT', 'DOCTOR', 'SYSTEM'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '问诊消息表：会话内按时间序展示，会话结束后禁止新增';

CREATE TABLE medical_record (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '病历 ID',
    session_id   BIGINT       NOT NULL                COMMENT '所属会话（UK，一会话一病历）',
    patient_id   BIGINT       NOT NULL                COMMENT '患者',
    doctor_id    BIGINT       NOT NULL                COMMENT '书写医生',
    diagnosis    VARCHAR(1000) NOT NULL               COMMENT '诊断结论',
    advice       VARCHAR(1000) NULL                   COMMENT '处置建议/用药指导',
    signed_at    DATETIME(3)  NULL                    COMMENT '签名时间，签名后不可修改',
    signed_hash  CHAR(64)     NULL                    COMMENT '病历内容摘要（SHA-256），用于防篡改校验',
    version      INT          NOT NULL DEFAULT 1      COMMENT '版本号',
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_record_session (session_id),
    KEY idx_record_patient (patient_id, created_at),
    CONSTRAINT fk_record_session FOREIGN KEY (session_id) REFERENCES consultation_session (id),
    CONSTRAINT fk_record_patient FOREIGN KEY (patient_id) REFERENCES patient_profile (id),
    CONSTRAINT fk_record_doctor FOREIGN KEY (doctor_id) REFERENCES doctor_profile (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '病历表：签名后内容锁定，修改须留痕';

CREATE TABLE drug (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '药品 ID',
    drug_code        VARCHAR(64)   NOT NULL                COMMENT '药品编码（国家/院内统一编码，UK）',
    drug_name        VARCHAR(128)  NOT NULL                COMMENT '药品通用名',
    trade_name       VARCHAR(128)  NULL                    COMMENT '商品名',
    category_id      BIGINT        NOT NULL                COMMENT '药品分类',
    spec             VARCHAR(128)  NOT NULL                COMMENT '规格，如 0.25g*24 片',
    dosage_form      VARCHAR(32)   NOT NULL                COMMENT '剂型：片剂/胶囊/注射液/颗粒',
    unit             VARCHAR(16)   NOT NULL                COMMENT '最小销售单位：盒/瓶/支',
    manufacturer     VARCHAR(128)  NULL                    COMMENT '生产厂家',
    rx_only          TINYINT       NOT NULL DEFAULT 1      COMMENT '是否处方药：1 是 0 否（OTC）',
    controlled_level VARCHAR(16)   NOT NULL DEFAULT 'NONE' COMMENT '特殊管理等级：NONE/RX/CONTROLLED（麻醉/精神/毒性）',
    max_days         INT           NULL                    COMMENT '单次开方最长疗程天数，NULL 表示不限',
    status           TINYINT       NOT NULL DEFAULT 1      COMMENT '目录状态：1 可开方 0 停用',
    created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_drug_code (drug_code),
    KEY idx_drug_category (category_id, status),
    KEY idx_drug_name (drug_name),
    CONSTRAINT fk_drug_category FOREIGN KEY (category_id) REFERENCES drug_category (id),
    CONSTRAINT ck_drug_rx_only CHECK (rx_only IN (0, 1)),
    CONSTRAINT ck_drug_status CHECK (status IN (0, 1)),
    CONSTRAINT ck_drug_controlled CHECK (controlled_level IN ('NONE', 'RX', 'CONTROLLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '药品目录表：停用或特殊管理药品不得进入互联网处方';

CREATE TABLE prescription (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '处方 ID',
    rx_no         VARCHAR(32)  NOT NULL                COMMENT '处方编号：RX+日期+序列（UK）',
    session_id    BIGINT       NOT NULL                COMMENT '绑定问诊会话',
    patient_id    BIGINT       NOT NULL                COMMENT '患者（冗余，便于列表查询）',
    doctor_id     BIGINT       NOT NULL                COMMENT '开方医生',
    status        VARCHAR(32)  NOT NULL                COMMENT '状态：DRAFT/PENDING_REVIEW/REJECTED/APPROVED/CIRCULATED/REDEEMED/CANCELLED/EXPIRED',
    version       INT          NOT NULL DEFAULT 1      COMMENT '版本号（乐观锁，If-Match 比对）',
    signed_hash   CHAR(64)     NULL                    COMMENT '审核通过后的签名摘要，APPROVED 后写入',
    reviewer_id   BIGINT       NULL                    COMMENT '审核药师（补充字段，见第 12 节）',
    reviewed_at   DATETIME(3)  NULL                    COMMENT '审核时间（补充字段）',
    reject_reason VARCHAR(500) NULL                    COMMENT '驳回理由，长度 ≥ 10（补充字段）',
    expires_at    DATETIME(3)  NULL                    COMMENT '处方失效时间，默认签发后 72 小时（补充字段）',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rx_no (rx_no),
    KEY idx_doctor_status (doctor_id, status, created_at),
    KEY idx_patient_created (patient_id, created_at),
    KEY idx_rx_expire (status, expires_at),
    CONSTRAINT fk_rx_session FOREIGN KEY (session_id) REFERENCES consultation_session (id),
    CONSTRAINT fk_rx_patient FOREIGN KEY (patient_id) REFERENCES patient_profile (id),
    CONSTRAINT fk_rx_doctor FOREIGN KEY (doctor_id) REFERENCES doctor_profile (id),
    CONSTRAINT fk_rx_reviewer FOREIGN KEY (reviewer_id) REFERENCES pharmacist_profile (id),
    CONSTRAINT ck_rx_status CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'REJECTED', 'APPROVED',
                                              'CIRCULATED', 'REDEEMED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_rx_version CHECK (version >= 1),
    CONSTRAINT ck_rx_reject_reason CHECK (reject_reason IS NULL OR CHAR_LENGTH(reject_reason) >= 10)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '处方主表：状态只能经聚合方法迁移，禁止 UPDATE 直接改 status';

CREATE TABLE prescription_item (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '明细 ID',
    prescription_id BIGINT        NOT NULL                COMMENT '所属处方',
    drug_id         BIGINT        NOT NULL                COMMENT '目录药品',
    dosage          DECIMAL(10,3) NOT NULL                COMMENT '单次剂量，如 0.5',
    dosage_unit     VARCHAR(16)   NOT NULL                COMMENT '剂量单位：mg/g/ml/片',
    frequency       VARCHAR(32)   NOT NULL                COMMENT '频次，如 qd/bid/tid',
    days            INT           NOT NULL                COMMENT '疗程天数：1—30',
    quantity        INT           NOT NULL                COMMENT '数量，需与剂量联动校验',
    usage_note      VARCHAR(255)  NULL                    COMMENT '用法备注，如“饭后服用”',
    sort_no         INT           NOT NULL DEFAULT 0      COMMENT '明细序号，保持开方顺序',
    created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_item_rx (prescription_id, sort_no),
    UNIQUE KEY uk_item_rx_drug (prescription_id, drug_id),
    CONSTRAINT fk_item_rx FOREIGN KEY (prescription_id) REFERENCES prescription (id),
    CONSTRAINT fk_item_drug FOREIGN KEY (drug_id) REFERENCES drug (id),
    CONSTRAINT ck_item_days CHECK (days BETWEEN 1 AND 30),
    CONSTRAINT ck_item_dosage CHECK (dosage > 0),
    CONSTRAINT ck_item_qty CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '处方明细表：剂量/疗程/数量三项由药品规则校验';

CREATE TABLE circulation_order (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '流转 ID',
    circulation_no  VARCHAR(32)  NOT NULL                COMMENT '流转单号（UK）',
    rx_id           BIGINT       NOT NULL                COMMENT '处方 ID（UK：一张处方一张流转单）',
    pharmacy_id     BIGINT       NOT NULL                COMMENT '接收药房',
    status          VARCHAR(24)  NOT NULL                COMMENT '状态：CREATED 已创建 / ACCEPTED 已接方 / REDEEMED 已核销 / REJECTED 已拒绝',
    idempotency_key VARCHAR(64)  NOT NULL                COMMENT '流转/核销幂等键（UK）',
    circulated_at   DATETIME(3)  NULL                    COMMENT '流转时间',
    accepted_at     DATETIME(3)  NULL                    COMMENT '药房接方时间（补充字段）',
    reject_reason   VARCHAR(255) NULL                    COMMENT '药房拒接原因（补充字段）',
    redeemed_at     DATETIME(3)  NULL                    COMMENT '核销时间',
    version         INT          NOT NULL DEFAULT 1      COMMENT '版本号',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_circ_no (circulation_no),
    UNIQUE KEY uk_rx (rx_id),
    UNIQUE KEY uk_idem (idempotency_key),
    KEY idx_pharmacy_status (pharmacy_id, status, created_at),
    CONSTRAINT fk_circ_rx FOREIGN KEY (rx_id) REFERENCES prescription (id),
    CONSTRAINT fk_circ_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacy (id),
    CONSTRAINT ck_circ_status CHECK (status IN ('CREATED', 'ACCEPTED', 'REDEEMED', 'REJECTED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '处方流转单表：rx_id 唯一约束保证一处方一流转单';

CREATE TABLE dispense_record (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '发药 ID',
    circulation_id BIGINT      NOT NULL                COMMENT '流转单 ID（UK：一次核销一条）',
    operator_id   BIGINT       NOT NULL                COMMENT '操作人（药师账号）',
    batch_no      VARCHAR(64)  NOT NULL                COMMENT '药品批号，用于追溯',
    evidence      VARCHAR(255) NULL                    COMMENT '核验凭证：取药码/人脸流水号',
    redeemed_at   DATETIME(3)  NOT NULL                COMMENT '核销时间',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dispense_circ (circulation_id),
    KEY idx_dispense_operator (operator_id, redeemed_at),
    CONSTRAINT fk_dispense_circ FOREIGN KEY (circulation_id) REFERENCES circulation_order (id),
    CONSTRAINT fk_dispense_operator FOREIGN KEY (operator_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '发药核销记录表：核销幂等由流转单 + 幂等键保证';

CREATE TABLE audit_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志 ID',
    actor_id      BIGINT       NOT NULL                COMMENT '操作者用户 ID（不建外键，账号删除后仍保留）',
    actor_role    VARCHAR(32)  NULL                    COMMENT '操作者角色快照',
    action        VARCHAR(64)  NOT NULL                COMMENT '动作，如 RX_APPROVE / RX_REDEEM / LOGIN_FAIL',
    resource_type VARCHAR(32)  NOT NULL                COMMENT '资源类型，如 RX / SESSION / USER',
    resource_id   BIGINT       NOT NULL                COMMENT '业务主键',
    result        VARCHAR(16)  NOT NULL                COMMENT '结果：SUCCESS / FAIL',
    detail        JSON         NULL                    COMMENT '变更前后摘要（已脱敏），不记录明文敏感字段',
    trace_id      VARCHAR(64)  NULL                    COMMENT '链路追踪 ID（补充字段）',
    ip            VARCHAR(64)  NULL                    COMMENT '来源 IP（补充字段）',
    hash_chain    CHAR(64)     NOT NULL                COMMENT '哈希链：SHA256(前条 hash_chain + 本条内容)',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发生时间',
    PRIMARY KEY (id),
    KEY idx_audit_resource (resource_type, resource_id, created_at),
    KEY idx_audit_actor (actor_id, created_at),
    KEY idx_audit_action (action, created_at),
    CONSTRAINT ck_audit_result CHECK (result IN ('SUCCESS', 'FAIL'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '审计日志表：只增不改不删，哈希链可校验，保留 ≥ 6 个月';

CREATE TABLE consent_record (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    patient_id   BIGINT       NOT NULL                COMMENT '患者',
    consent_type VARCHAR(32)  NOT NULL                COMMENT '同意类型：PRIVACY 隐私政策 / TELEMED 在线诊疗 / DATA_SHARE 数据共享',
    consent_version VARCHAR(32) NOT NULL              COMMENT '协议版本号，如 v1.2',
    content_hash CHAR(64)     NOT NULL                COMMENT '协议正文摘要，证明同意的具体版本',
    agreed_at    DATETIME(3)  NOT NULL                COMMENT '同意时间',
    revoked_at   DATETIME(3)  NULL                    COMMENT '撤回时间，NULL 表示仍有效',
    ip           VARCHAR(64)  NULL                    COMMENT '同意时的来源 IP',
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_consent_patient (patient_id, consent_type, agreed_at),
    CONSTRAINT ck_consent_type CHECK (consent_type IN ('PRIVACY', 'TELEMED', 'DATA_SHARE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '知情同意记录表：不可覆盖，只能新增或撤回';

CREATE TABLE notification (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    receiver_id BIGINT       NOT NULL                COMMENT '接收人用户 ID',
    biz_type    VARCHAR(32)  NOT NULL                COMMENT '业务类型：RX_REVIEW / RX_REJECT / CIRCULATION / REDEEM',
    biz_id      BIGINT       NOT NULL                COMMENT '业务主键（处方/流转单 ID）',
    title       VARCHAR(128) NOT NULL                COMMENT '通知标题',
    content     VARCHAR(1000) NOT NULL               COMMENT '通知正文（不含敏感明文）',
    channel     VARCHAR(16)  NOT NULL DEFAULT 'INBOX' COMMENT '渠道：INBOX 站内信 / SMS 短信',
    status      VARCHAR(16)  NOT NULL DEFAULT 'NEW'  COMMENT '状态：NEW 待发 / SENT 已发 / READ 已读 / FAILED 失败',
    sent_at     DATETIME(3)  NULL                    COMMENT '发送时间',
    read_at     DATETIME(3)  NULL                    COMMENT '阅读时间',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_notify_receiver (receiver_id, status, created_at),
    KEY idx_notify_biz (biz_type, biz_id),
    CONSTRAINT ck_notify_channel CHECK (channel IN ('INBOX', 'SMS')),
    CONSTRAINT ck_notify_status CHECK (status IN ('NEW', 'SENT', 'READ', 'FAILED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '通知表：由事务发件箱异步投递，失败可重试';

CREATE TABLE outbox_event (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    aggregate_type VARCHAR(64)  NOT NULL                COMMENT '聚合类型：Prescription / CirculationOrder',
    aggregate_id   BIGINT       NOT NULL                COMMENT '聚合 ID',
    event_type     VARCHAR(64)  NOT NULL                COMMENT '事件类型：PrescriptionSubmitted / PrescriptionRedeemed',
    payload        JSON         NOT NULL                COMMENT '事件载荷（与业务事务同库同事务写入）',
    status         VARCHAR(16)  NOT NULL DEFAULT 'NEW'  COMMENT '状态：NEW 待投递 / SENT 已投递 / FAILED 失败',
    retry_count    INT          NOT NULL DEFAULT 0      COMMENT '重试次数，指数退避',
    next_retry_at  DATETIME(3)  NULL                    COMMENT '下次重试时间',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    sent_at        DATETIME(3)  NULL                    COMMENT '投递成功时间',
    PRIMARY KEY (id),
    KEY idx_outbox_status (status, next_retry_at, id),
    KEY idx_outbox_agg (aggregate_type, aggregate_id),
    CONSTRAINT ck_outbox_status CHECK (status IN ('NEW', 'SENT', 'FAILED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '事务发件箱表：解决“业务成功、消息丢失”，由定时任务轮询投递';

CREATE TABLE idempotency_record (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    biz_type          VARCHAR(32)  NOT NULL                COMMENT '业务类型：SUBMIT / CIRCULATE / REDEEM',
    biz_id            BIGINT       NOT NULL                COMMENT '业务主键',
    operation         VARCHAR(32)  NOT NULL                COMMENT '操作：SUBMIT/APPROVE/REJECT/CIRCULATE/REDEEM',
    idem_key          VARCHAR(64)  NOT NULL                COMMENT '幂等键（客户端或药房提供）',
    request_hash      CHAR(64)     NOT NULL                COMMENT '请求摘要，同键不同参数判为冲突',
    response_snapshot JSON         NULL                    COMMENT '首次执行结果快照，重复请求直接返回',
    status            VARCHAR(16)  NOT NULL DEFAULT 'PROCESSING' COMMENT '状态：PROCESSING 处理中 / SUCCESS 成功 / FAILED 失败',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    expired_at        DATETIME(3)  NULL                    COMMENT '过期时间，默认 24 小时后可清理',
    PRIMARY KEY (id),
    UNIQUE KEY uk_idem (biz_type, biz_id, operation, idem_key),
    KEY idx_idem_expire (expired_at),
    CONSTRAINT ck_idem_status CHECK (status IN ('PROCESSING', 'SUCCESS', 'FAILED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '幂等记录表：唯一键 (biz_type, biz_id, operation, idem_key) 保证重放不重复发药';
