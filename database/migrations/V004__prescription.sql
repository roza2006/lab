-- =============================================================================
-- V004__prescription.sql —— 处方、明细与审核记录（第 4 轮）
-- -----------------------------------------------------------------------------
-- 依据第 4 轮代码：
--   prescription/domain/Prescription.java     处方聚合：诊断、72 小时有效期、版本号
--   prescription/domain/PrescriptionReview    审核结论只追加，不覆盖
--   prescription/infrastructure/persistence/* 三个 PO 对应的字段
--
-- 和第 3 轮一样，v1 的 patient_id / doctor_id / reviewer_id 都指向 sys_user；
-- 处方明细改成直接存药品编码与名称，不再挂药品目录外键。
-- =============================================================================

SET NAMES utf8mb4;

-- 1. 处方主表 ---------------------------------------------------------------
-- 开方时必须写诊断，代码里做了非空校验
ALTER TABLE prescription
    ADD COLUMN diagnosis VARCHAR(500) NOT NULL DEFAULT '' COMMENT '门诊诊断，开方时填写' AFTER doctor_id;

-- 代码里叫 validUntil，值取“提交时间 + 72 小时”
ALTER TABLE prescription
    CHANGE COLUMN expires_at valid_until DATETIME(3) NULL COMMENT '处方有效期，提交后 72 小时';

-- 患者、医生、审核药师都改挂账号表
ALTER TABLE prescription
    DROP FOREIGN KEY fk_rx_patient,
    DROP FOREIGN KEY fk_rx_doctor,
    DROP FOREIGN KEY fk_rx_reviewer;

ALTER TABLE prescription
    ADD CONSTRAINT fk_rx_patient FOREIGN KEY (patient_id) REFERENCES sys_user (id),
    ADD CONSTRAINT fk_rx_doctor  FOREIGN KEY (doctor_id)  REFERENCES sys_user (id),
    ADD CONSTRAINT fk_rx_reviewer FOREIGN KEY (reviewer_id) REFERENCES sys_user (id);

-- 列表查询统一按 id 倒序分页；草稿查看按 (session_id, status) 找
ALTER TABLE prescription
    ADD KEY idx_rx_patient (patient_id, id),
    ADD KEY idx_rx_doctor  (doctor_id, id),
    ADD KEY idx_rx_status  (status, id),
    ADD KEY idx_rx_session (session_id, status);

-- 新的按 id 索引取代了旧的按创建时间索引
ALTER TABLE prescription
    DROP KEY idx_patient_created,
    DROP KEY idx_doctor_status;

-- 2. 处方明细 ---------------------------------------------------------------
-- 明细直接带药品编码、名称和规格，改价改目录不影响已开处方
ALTER TABLE prescription_item
    ADD COLUMN drug_code     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '药品编码' AFTER prescription_id,
    ADD COLUMN drug_name     VARCHAR(128) NOT NULL DEFAULT '' COMMENT '药品名称' AFTER drug_code,
    ADD COLUMN specification VARCHAR(128) NULL COMMENT '规格，如 0.25g*24 片' AFTER drug_name,
    ADD COLUMN duration      VARCHAR(32)  NULL COMMENT '疗程，如 7 天' AFTER frequency,
    ADD COLUMN unit_price    DECIMAL(10,2) NULL COMMENT '单价' AFTER quantity,
    ADD COLUMN subtotal      DECIMAL(10,2) NULL COMMENT '小计' AFTER unit_price;

-- 剂量改成带单位的字符串（代码里是 “0.5g” 这种写法）
ALTER TABLE prescription_item
    MODIFY COLUMN dosage VARCHAR(64) NOT NULL COMMENT '单次剂量，含单位，如 0.5g';

-- 剂量已经是字符串，原来的数值比较不能再留
ALTER TABLE prescription_item
    DROP CHECK ck_item_dosage;

ALTER TABLE prescription_item
    DROP FOREIGN KEY fk_item_drug;

ALTER TABLE prescription_item
    DROP INDEX uk_item_rx_drug;

ALTER TABLE prescription_item
    DROP COLUMN drug_id,
    DROP COLUMN dosage_unit,
    DROP COLUMN days,
    DROP COLUMN usage_note;

-- 3. 审核记录表 -------------------------------------------------------------
-- 驳回 → 修改 → 再提交 → 通过，每次审核追加一条，只增不改
CREATE TABLE prescription_review (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '审核记录 ID',
    prescription_id BIGINT       NOT NULL                COMMENT '所属处方',
    pharmacist_id   BIGINT       NOT NULL                COMMENT '审核药师账号 ID',
    action          VARCHAR(16)  NOT NULL                COMMENT '审核动作：APPROVED / REJECTED',
    review_comment  VARCHAR(500) NOT NULL DEFAULT ''     COMMENT '审核意见，驳回时不少于 10 字',
    signature       VARCHAR(128) NULL                    COMMENT '通过时的签名摘要，SIG- 开头',
    signed_at       DATETIME(3)  NOT NULL                COMMENT '审核时间',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '入库时间',
    PRIMARY KEY (id),
    KEY idx_review_rx (prescription_id, id),
    KEY idx_review_pharmacist (pharmacist_id, signed_at),
    CONSTRAINT fk_review_rx FOREIGN KEY (prescription_id) REFERENCES prescription (id),
    CONSTRAINT fk_review_pharmacist FOREIGN KEY (pharmacist_id) REFERENCES sys_user (id),
    CONSTRAINT ck_review_action CHECK (action IN ('APPROVED', 'REJECTED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '处方审核记录表：审核结论追加存储，可按处方追溯历史';
