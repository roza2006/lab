package com.medical.platform.circulation.application;

import com.medical.platform.circulation.domain.DispenseRecord;

import java.time.LocalDateTime;

/**
 * 发药记录视图，字段与前端 {@code frontend/src/api/circulation.ts} 的
 * {@code DispenseRecord} 对齐。
 */
public record DispenseView(
        Long id,
        String batchNo,
        String evidence,
        Long operatorId,
        LocalDateTime redeemedAt) {

    public static DispenseView from(DispenseRecord record) {
        return new DispenseView(
                record.id(),
                record.batchNo(),
                record.evidence(),
                record.operatorId(),
                record.redeemedAt());
    }
}
