-- 测试库结构：只建接口测试需要的表，字段与 database/schema.sql 中的 sys_user 保持一致。
-- 说明：H2 以 MySQL 兼容模式运行，类型与自增语义与生产库一致；生产 DDL 仍以 database/schema.sql 为准。

DROP TABLE IF EXISTS idempotency_record;
DROP TABLE IF EXISTS dispense_record;
DROP TABLE IF EXISTS circulation_order;
DROP TABLE IF EXISTS prescription_review;
DROP TABLE IF EXISTS prescription_item;
DROP TABLE IF EXISTS prescription;
DROP TABLE IF EXISTS consultation_message;
DROP TABLE IF EXISTS consultation_session;
DROP TABLE IF EXISTS pharmacist_profile;
DROP TABLE IF EXISTS sys_user;

CREATE TABLE sys_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role_code     VARCHAR(32)  NOT NULL,
    status        INT          NOT NULL DEFAULT 1
);

CREATE UNIQUE INDEX uk_sys_user_username ON sys_user (username);

-- 问诊上下文（R3-B）：字段与 openapi.yaml / docs/design/api-contract.md 的字符串科室保持一致。
-- patient_id / doctor_id 在 v1 直接存 sys_user.id，避免测试再依赖 patient_profile / doctor_profile。
CREATE TABLE consultation_session (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    patient_id      BIGINT        NOT NULL,
    doctor_id       BIGINT        NULL,
    department      VARCHAR(64)   NOT NULL,
    disease_name    VARCHAR(128)  NOT NULL,
    chief_complaint VARCHAR(1000) NOT NULL,
    status          VARCHAR(24)   NOT NULL DEFAULT 'WAITING',
    version         INT           NOT NULL DEFAULT 1,
    started_at      TIMESTAMP(3)  NULL,
    ended_at        TIMESTAMP(3)  NULL,
    created_at      TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);

CREATE INDEX idx_session_patient ON consultation_session (patient_id, status);
CREATE INDEX idx_session_doctor ON consultation_session (doctor_id, status);

CREATE TABLE consultation_message (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id  BIGINT        NOT NULL,
    sender_id   BIGINT        NOT NULL,
    sender_role VARCHAR(32)   NOT NULL,
    msg_type    VARCHAR(16)   NOT NULL DEFAULT 'TEXT',
    content     VARCHAR(4000) NOT NULL,
    read_flag   BOOLEAN       NOT NULL DEFAULT FALSE,
    sent_at     TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at  TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);

CREATE INDEX idx_msg_session ON consultation_message (session_id, id);

-- 处方上下文（R4-A）：字段按 openapi.yaml 的 Prescription 结构落库。
CREATE TABLE prescription (
    id            BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    rx_no         VARCHAR(32)   NOT NULL,
    session_id    BIGINT        NOT NULL,
    patient_id    BIGINT        NOT NULL,
    doctor_id     BIGINT        NOT NULL,
    diagnosis     VARCHAR(500)  NOT NULL,
    status        VARCHAR(32)   NOT NULL,
    signed_hash   VARCHAR(64)   NULL,
    reviewer_id   BIGINT        NULL,
    reviewed_at   TIMESTAMP(3)  NULL,
    reject_reason VARCHAR(500)  NULL,
    valid_until   TIMESTAMP(3)  NULL,
    version       INT           NOT NULL DEFAULT 1,
    created_at    TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);

CREATE UNIQUE INDEX uk_rx_no ON prescription (rx_no);
CREATE INDEX idx_rx_patient ON prescription (patient_id, status);
CREATE INDEX idx_rx_doctor ON prescription (doctor_id, status);

CREATE TABLE prescription_item (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    prescription_id BIGINT        NOT NULL,
    drug_code       VARCHAR(64)   NOT NULL,
    drug_name       VARCHAR(128)  NOT NULL,
    specification   VARCHAR(128)  NULL,
    dosage          VARCHAR(32)   NOT NULL,
    frequency       VARCHAR(32)   NOT NULL,
    duration        VARCHAR(32)   NOT NULL,
    quantity        INT           NOT NULL,
    unit_price      DECIMAL(10,2) NOT NULL DEFAULT 0,
    subtotal        DECIMAL(12,2) NOT NULL DEFAULT 0,
    sort_no         INT           NOT NULL DEFAULT 0,
    created_at      TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);

CREATE INDEX idx_rx_item ON prescription_item (prescription_id, sort_no);

-- 处方审核记录（R4-B）：只追加不改写，列名与 database/schema.sql 的 prescription_review 一致。
CREATE TABLE prescription_review (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    prescription_id BIGINT        NOT NULL,
    pharmacist_id   BIGINT        NOT NULL,
    action          VARCHAR(16)   NOT NULL,
    review_comment  VARCHAR(500)  NOT NULL,
    signature       VARCHAR(128)  NULL,
    signed_at       TIMESTAMP(3)  NOT NULL,
    created_at      TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);

CREATE INDEX idx_review_rx ON prescription_review (prescription_id, id);

-- 药师档案（R5-B）：用于“只能核销本药房流转单”的数据范围校验；测试库不建外键，保持用例独立。
CREATE TABLE pharmacist_profile (
    id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT      NOT NULL,
    real_name    VARCHAR(64) NOT NULL,
    pharmacy_id  BIGINT      NOT NULL,
    license_no   VARCHAR(64) NOT NULL,
    review_right TINYINT     NOT NULL DEFAULT 1,
    status       TINYINT     NOT NULL DEFAULT 1
);

CREATE UNIQUE INDEX uk_pharmacist_user ON pharmacist_profile (user_id);

-- 处方流转单（R5）：字段与 database/schema.sql 的 circulation_order 保持一致。
CREATE TABLE circulation_order (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    circulation_no  VARCHAR(32)  NOT NULL,
    rx_id           BIGINT       NOT NULL,
    pharmacy_id     BIGINT       NOT NULL,
    status          VARCHAR(24)  NOT NULL,
    idempotency_key VARCHAR(64)  NOT NULL,
    circulated_at   TIMESTAMP(3) NULL,
    accepted_at     TIMESTAMP(3) NULL,
    reject_reason   VARCHAR(255) NULL,
    redeemed_at     TIMESTAMP(3) NULL,
    version         INT          NOT NULL DEFAULT 1,
    created_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);

CREATE UNIQUE INDEX uk_circ_no ON circulation_order (circulation_no);
CREATE UNIQUE INDEX uk_circ_rx ON circulation_order (rx_id);
CREATE UNIQUE INDEX uk_circ_idem ON circulation_order (idempotency_key);
CREATE INDEX idx_circ_pharmacy_status ON circulation_order (pharmacy_id, status, created_at);

-- 发药核销记录（R5-B）：circulation_id 唯一，是并发核销“只发一次药”的数据库兜底。
CREATE TABLE dispense_record (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    circulation_id BIGINT       NOT NULL,
    operator_id    BIGINT       NOT NULL,
    batch_no       VARCHAR(64)  NOT NULL,
    evidence       VARCHAR(255) NULL,
    redeemed_at    TIMESTAMP(3) NOT NULL,
    created_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);

CREATE UNIQUE INDEX uk_dispense_circ ON dispense_record (circulation_id);
CREATE INDEX idx_dispense_operator ON dispense_record (operator_id, redeemed_at);

-- 幂等记录（R5-B）：唯一键保证同一业务动作 + 同一幂等键只成功一次。
CREATE TABLE idempotency_record (
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    biz_type          VARCHAR(32)  NOT NULL,
    biz_id            BIGINT       NOT NULL,
    operation         VARCHAR(32)  NOT NULL,
    idem_key          VARCHAR(64)  NOT NULL,
    request_hash      CHAR(64)     NOT NULL,
    response_snapshot CLOB         NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'PROCESSING',
    created_at        TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expired_at        TIMESTAMP(3) NULL
);

CREATE UNIQUE INDEX uk_idem ON idempotency_record (biz_type, biz_id, operation, idem_key);
CREATE INDEX idx_idem_expire ON idempotency_record (expired_at);
