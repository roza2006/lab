-- 测试库结构：只建接口测试需要的表，字段与 database/schema.sql 中的 sys_user 保持一致。
-- 说明：H2 以 MySQL 兼容模式运行，类型与自增语义与生产库一致；生产 DDL 仍以 database/schema.sql 为准。

DROP TABLE IF EXISTS consultation_message;
DROP TABLE IF EXISTS consultation_session;
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
