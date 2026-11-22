-- =============================================================================
-- V005__circulation.sql —— 处方流转与核销的索引（第 5 轮）
-- -----------------------------------------------------------------------------
-- 第 5 轮的 CirculationOrderMapper 里，药房端和管理端都是
--   WHERE pharmacy_id = ? [AND status = ?] ORDER BY id DESC
-- 原来的 (pharmacy_id, status, created_at) 对 id 排序帮不上忙，换成 (pharmacy_id, id)
-- 和 (status, id)。流转单的 rx_id 唯一键、幂等键唯一键上一轮已经有了。
-- =============================================================================

SET NAMES utf8mb4;

ALTER TABLE circulation_order
    ADD KEY idx_circ_pharmacy (pharmacy_id, id),
    ADD KEY idx_circ_status (status, id);

ALTER TABLE circulation_order
    DROP KEY idx_pharmacy_status;
