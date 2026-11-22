package com.medical.platform.circulation.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 发药核销记录（只追加，不改写）。
 *
 * <p>{@code circulation_id} 在库中有唯一键，保证一张流转单最多一条发药记录；
 * 这是并发核销时数据库层的最后一道兜底，业务层的行锁与幂等记录是第一道。
 */
public record DispenseRecord(
        Long id,
        Long circulationId,
        Long operatorId,
        String batchNo,
        String evidence,
        LocalDateTime redeemedAt,
        LocalDateTime createdAt) {

    private static final int MAX_BATCH_NO = 64;
    private static final int MAX_EVIDENCE = 255;

    public DispenseRecord {
        Objects.requireNonNull(circulationId, "circulationId");
        Objects.requireNonNull(operatorId, "operatorId");
        Objects.requireNonNull(redeemedAt, "redeemedAt");
        batchNo = batchNo == null ? "" : batchNo.trim();
        if (batchNo.isEmpty() || batchNo.length() > MAX_BATCH_NO) {
            throw new CirculationException(CirculationError.CIRC_BATCH_INVALID);
        }
        evidence = evidence == null ? null : evidence.trim();
        if (evidence != null && evidence.length() > MAX_EVIDENCE) {
            throw new CirculationException(CirculationError.CIRC_EVIDENCE_INVALID);
        }
    }

    /**
     * 创建发药记录。客户端未传批号时，用流转单号生成可追溯的批次号
     * （演示环境没有真实药品包装批号，正式接入药品主数据后由药房扫码传入）。
     */
    public static DispenseRecord create(Long circulationId,
                                        Long operatorId,
                                        String batchNo,
                                        String evidence,
                                        LocalDateTime now,
                                        String circulationNo) {
        String normalizedBatch = batchNo == null || batchNo.isBlank()
                ? "AUTO-" + circulationNo
                : batchNo;
        return new DispenseRecord(null, circulationId, operatorId, normalizedBatch,
                evidence, now, now);
    }

    public DispenseRecord withId(Long newId) {
        return new DispenseRecord(newId, circulationId, operatorId, batchNo, evidence,
                redeemedAt, createdAt);
    }
}
