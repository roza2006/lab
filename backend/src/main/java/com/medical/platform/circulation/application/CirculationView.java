package com.medical.platform.circulation.application;

import com.medical.platform.circulation.domain.CirculationOrder;
import com.medical.platform.circulation.domain.DispenseRecord;

import java.time.LocalDateTime;

/**
 * 流转单视图，字段与前端 {@code frontend/src/api/circulation.ts} 的
 * {@code CirculationOrder} 对齐；核销成功后附带 {@code dispenseRecord}。
 *
 * <p>{@code id} 是流转单主键，重放时保持不变；发药记录主键在
 * {@code dispenseRecord.id} 中单独返回。
 */
public record CirculationView(
        Long id,
        String circulationNo,
        Long rxId,
        Long pharmacyId,
        String status,
        String idempotencyKey,
        LocalDateTime circulatedAt,
        LocalDateTime acceptedAt,
        LocalDateTime redeemedAt,
        String rejectReason,
        int version,
        DispenseView dispenseRecord) {

    public static CirculationView from(CirculationOrder order, DispenseRecord dispense) {
        return new CirculationView(
                order.getId(),
                order.getCirculationNo(),
                order.getRxId(),
                order.getPharmacyId(),
                order.getStatus().name(),
                order.getIdempotencyKey(),
                order.getCirculatedAt(),
                order.getAcceptedAt(),
                order.getRedeemedAt(),
                order.getRejectReason(),
                order.getVersion(),
                dispense == null ? null : DispenseView.from(dispense));
    }
}
