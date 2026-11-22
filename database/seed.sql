-- =============================================================================
-- seed.sql —— 演示数据（全部虚构）
-- -----------------------------------------------------------------------------
-- 用途：本地或演示环境跑通“患者发起问诊 → 医生开方 → 药师审核 → 药房流转”的流程。
-- 前置：先按 V002 ~ V005 建好表结构。
-- 用法：
--   mysql -uroot -p medical_platform < database/seed.sql
--   或在 docker compose 里由 MySQL 初始化目录自动执行（见 deploy/docker-compose.yml）。
--
-- 账号密码统一为 Demo@2026（bcrypt 哈希），仅用于本地演示：
--   admin       管理员
--   doctor01    医生
--   patient01   患者
--   pharmacist01 药师（归属示例药房）
--
-- 数据全部虚构：姓名、手机号、身份证号、药品批号都不是真实信息。
-- ID 固定为 1001 起，只在空库上执行一次；重复执行会主键冲突。
-- =============================================================================

SET NAMES utf8mb4;

-- 1. 角色 ------------------------------------------------------------------
INSERT INTO sys_role (id, role_code, role_name, description, builtin) VALUES
    (1, 'ADMIN',      '管理员', '平台运营与审计', 1),
    (2, 'DOCTOR',     '医生',   '接诊与开方',     1),
    (3, 'PATIENT',    '患者',   '问诊与查看处方', 1),
    (4, 'PHARMACIST', '药师',   '处方审核',       1),
    (5, 'PHARMACY',   '合作药房', '接方与核销',   1);

-- 2. 账号 ------------------------------------------------------------------
-- phone_enc / phone_hash 用 SHA-256 顶替，真实环境应由应用用 AES-GCM 与 HMAC 写入
INSERT INTO sys_user (id, username, password_hash, phone_enc, phone_hash, role_code, status) VALUES
    (1001, 'admin',
     '$2a$10$Dy42sxL3uw3JkGwBaLBKO.DfBtJImUVUP6nQrSAqS6sY69lvacsAO',
     UNHEX(SHA2('13800000001', 256)), SHA2('13800000001', 256), 'ADMIN', 1),
    (1002, 'doctor01',
     '$2a$10$xeSRgkROGHUTXr4hBpXFgONytkBfOb6lTY44X6WAg6PBm8mGT9Pe2',
     UNHEX(SHA2('13800000002', 256)), SHA2('13800000002', 256), 'DOCTOR', 1),
    (1003, 'patient01',
     '$2a$10$19QOg0z0prWvFdk9v6VTnuwkktr0O/58T31vihzM4VfN7oYBhcnc2',
     UNHEX(SHA2('13800000003', 256)), SHA2('13800000003', 256), 'PATIENT', 1),
    (1004, 'pharmacist01',
     '$2a$10$RI1u45WsBNgn93p7Glze3OpAaYf60vCOT3jTc6/54Ecvj0nui8p0u',
     UNHEX(SHA2('13800000004', 256)), SHA2('13800000004', 256), 'PHARMACIST', 1);

-- 3. 科室与药房 --------------------------------------------------------------
INSERT INTO department (id, dept_code, dept_name, sort_no, status) VALUES
    (1, 'NK', '内科', 1, 1),
    (2, 'WK', '外科', 2, 1);

INSERT INTO pharmacy (id, pharmacy_code, pharmacy_name, license_no, license_expire_at,
                      address, service_scope, status) VALUES
    (1, 'PH001', '示例大药房（虚构）', 'JY-2026-0001', '2027-12-31',
     '示例市示例路 1 号', '处方药零售', 1);

-- 4. 档案 ------------------------------------------------------------------
INSERT INTO patient_profile (id, user_id, real_name_enc, id_card_enc, id_card_hash,
                             gender, birth_date, phone_enc, address_enc,
                             allergy_history, consent_status) VALUES
    (1, 1003, UNHEX(SHA2('张演示', 256)), UNHEX(SHA2('110101199001011234', 256)),
     SHA2('110101199001011234', 256), 1, '1990-01-01',
     UNHEX(SHA2('13800000003', 256)), UNHEX(SHA2('示例市示例路 2 号', 256)),
     '青霉素过敏', 1);

INSERT INTO doctor_profile (id, user_id, real_name, title, department_id, license_no,
                            practice_scope, rx_right, status) VALUES
    (1, 1002, '李演示', '主治医师', 1, 'YS-2026-0001', '内科常见病', 1, 1);

INSERT INTO pharmacist_profile (id, user_id, real_name, pharmacy_id, license_no,
                                review_right, status) VALUES
    (1, 1004, '王演示', 1, 'ZY-2026-0001', 1, 1);

-- 5. 药品目录 ---------------------------------------------------------------
INSERT INTO drug_category (id, category_code, category_name, sort_no, status) VALUES
    (1, 'ANTI', '抗感染药', 1, 1),
    (2, 'PAIN', '解热镇痛药', 2, 1);

INSERT INTO drug (id, drug_code, drug_name, category_id, spec, dosage_form, unit,
                  manufacturer, rx_only, controlled_level, max_days, status) VALUES
    (1, 'D001', '阿莫西林胶囊', 1, '0.25g*24 粒', '胶囊', '盒',
     '示例制药（虚构）', 1, 'RX', 7, 1),
    (2, 'D002', '布洛芬缓释胶囊', 2, '0.3g*20 粒', '胶囊', '盒',
     '示例制药（虚构）', 1, 'NONE', 5, 1),
    (3, 'D003', '对乙酰氨基酚片', 2, '0.5g*12 片', '片剂', '盒',
     '示例制药（虚构）', 0, 'NONE', 5, 1),
    (4, 'D004', '头孢克肟分散片', 1, '0.1g*12 片', '片剂', '盒',
     '示例制药（虚构）', 1, 'RX', 7, 1);

-- 6. 一条进行中的问诊 --------------------------------------------------------
INSERT INTO consultation_session (id, patient_id, doctor_id, department, disease_name,
                                  chief_complaint, status, version, started_at) VALUES
    (1, 1003, 1002, '内科', '上呼吸道感染', '咳嗽、咽痛三天，无发热', 'IN_PROGRESS', 1,
     NOW(3) - INTERVAL 10 MINUTE);

INSERT INTO consultation_message (id, session_id, sender_id, sender_role, msg_type,
                                  content, read_flag, sent_at) VALUES
    (1, 1, 1003, 'PATIENT', 'TEXT', '医生您好，咳嗽三天了，需要吃什么药？', 1, NOW(3) - INTERVAL 9 MINUTE),
    (2, 1, 1002, 'DOCTOR', 'TEXT', '有发热或药物过敏吗？', 1, NOW(3) - INTERVAL 7 MINUTE),
    (3, 1, 1003, 'PATIENT', 'TEXT', '没有发热，青霉素过敏。', 0, NOW(3) - INTERVAL 5 MINUTE);

-- 7. 一张待审核的处方 --------------------------------------------------------
INSERT INTO prescription (id, rx_no, session_id, patient_id, doctor_id, diagnosis,
                          status, version, valid_until) VALUES
    (1, 'RX20261122001', 1, 1003, 1002, '急性上呼吸道感染', 'PENDING_REVIEW', 1,
     NOW(3) + INTERVAL 72 HOUR);

INSERT INTO prescription_item (id, prescription_id, drug_code, drug_name, specification,
                               dosage, frequency, duration, quantity, unit_price, subtotal,
                               sort_no) VALUES
    (1, 1, 'D002', '布洛芬缓释胶囊', '0.3g*20 粒', '0.3g', 'bid', '3 天', 1, 18.50, 18.50, 1),
    (2, 1, 'D003', '对乙酰氨基酚片', '0.5g*12 片', '0.5g', 'prn', '3 天', 1, 9.80, 9.80, 2);
