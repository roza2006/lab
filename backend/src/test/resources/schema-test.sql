-- 测试库结构：只建接口测试需要的表，字段与 database/schema.sql 中的 sys_user 保持一致。
-- 说明：H2 以 MySQL 兼容模式运行，类型与自增语义与生产库一致；生产 DDL 仍以 database/schema.sql 为准。

DROP TABLE IF EXISTS sys_user;

CREATE TABLE sys_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role_code     VARCHAR(32)  NOT NULL,
    status        INT          NOT NULL DEFAULT 1
);

CREATE UNIQUE INDEX uk_sys_user_username ON sys_user (username);
