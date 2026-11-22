-- =============================================================================
-- V003__auth_consult.sql —— 认证与问诊的字段、索引调整（第 3 轮）
-- -----------------------------------------------------------------------------
-- 依据第 3 轮代码：
--   identity/     登录、JWT、账号状态
--   consultation/ 会话与消息（MyBatis-Plus + 注解 SQL，见 Consultation*Mapper）
--
-- 问诊模块 v1 直接用登录账号 ID 标识患者和医生，科室存名称而不是部门外键；
-- 会话多一个“疾病分类”字段，消息多一个“已读”标记。这里按代码实际字段调整结构，
-- 再按 Mapper 里的 SQL 补索引。
--
-- 注意：从 v0.1 的库升级时，若已有会话数据，patient_id/doctor_id 存的是档案表 ID，
-- 换外键前需要先做一次 ID 映射；课程演示库为空，可以直接执行。
-- =============================================================================

SET NAMES utf8mb4;

-- 1. 会话表：科室改为名称，新增疾病分类 -------------------------------------
ALTER TABLE consultation_session
    ADD COLUMN department   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '科室名称，与接口字段一致' AFTER doctor_id,
    ADD COLUMN disease_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT '就诊疾病或问题分类' AFTER department;

-- 旧数据按 department_id 回填科室名称
UPDATE consultation_session s
    JOIN department d ON s.department_id = d.id
SET s.department = d.dept_name
WHERE s.department = '';

-- v1 不再依赖 department 表，去掉外键、索引和列
ALTER TABLE consultation_session
    DROP FOREIGN KEY fk_session_dept,
    DROP KEY idx_queue,
    DROP COLUMN department_id;

-- 患者、医生改挂账号表（v1 用登录账号 ID）
ALTER TABLE consultation_session
    DROP FOREIGN KEY fk_session_patient,
    DROP FOREIGN KEY fk_session_doctor;

ALTER TABLE consultation_session
    ADD CONSTRAINT fk_session_patient FOREIGN KEY (patient_id) REFERENCES sys_user (id),
    ADD CONSTRAINT fk_session_doctor  FOREIGN KEY (doctor_id)  REFERENCES sys_user (id);

-- 2. 消息表：已读标记 -------------------------------------------------------
ALTER TABLE consultation_message
    ADD COLUMN read_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已读：1 是 0 否' AFTER content;

-- 3. 按 Mapper 的查询补索引 --------------------------------------------------
-- 患者端：WHERE patient_id = ? [AND status = ?] ORDER BY id DESC
ALTER TABLE consultation_session
    ADD KEY idx_session_patient (patient_id, id),
    ADD KEY idx_session_status  (status, id),
    ADD KEY idx_session_doctor  (doctor_id, id);

-- 上面三个按 id 排序的索引已经覆盖旧索引，删掉避免重复维护
ALTER TABLE consultation_session
    DROP KEY idx_patient_created,
    DROP KEY idx_doctor_status;

-- 消息分页：WHERE session_id = ? ORDER BY id ASC
ALTER TABLE consultation_message
    ADD KEY idx_msg_session (session_id, id);

-- 未读统计：WHERE session_id = ? AND read_flag = 0
ALTER TABLE consultation_message
    ADD KEY idx_msg_unread (session_id, read_flag);

-- 按 id 分页后，原来按 sent_at 的索引用不上了
ALTER TABLE consultation_message
    DROP KEY idx_session_sent;

-- 4. 认证侧 ----------------------------------------------------------------
-- 账号列表与清理：WHERE status = ? ORDER BY last_login_at
ALTER TABLE sys_user
    ADD KEY idx_user_status_login (status, last_login_at);
