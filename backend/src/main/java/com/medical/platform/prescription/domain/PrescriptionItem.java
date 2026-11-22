package com.medical.platform.prescription.domain;

import java.math.BigDecimal;

/**
 * 处方明细。只随处方聚合读写，没有独立仓储。
 */
public record PrescriptionItem(
        Long id,
        String drugCode,
        String drugName,
        String specification,
        String dosage,
        String frequency,
        String duration,
        int quantity,
        BigDecimal unitPrice,
        int sortNo) {

    public PrescriptionItem {
        drugCode = required(drugCode, "药品编码");
        drugName = required(drugName, "药品名称");
        specification = specification == null ? "" : specification.trim();
        dosage = required(dosage, "单次剂量");
        frequency = required(frequency, "用药频次");
        duration = required(duration, "疗程");
        if (quantity < 1) {
            throw new PrescriptionException(PrescriptionError.RX_DOSAGE_INVALID, "药品数量必须大于 0");
        }
        if (unitPrice == null) {
            unitPrice = BigDecimal.ZERO;
        } else if (unitPrice.signum() < 0) {
            throw new PrescriptionException(PrescriptionError.RX_DOSAGE_INVALID, "药品单价不能为负数");
        }
        if (sortNo < 0) {
            throw new PrescriptionException(PrescriptionError.RX_VALIDATION_FAILED, "明细序号不能为负数");
        }
    }

    public static PrescriptionItem create(String drugCode,
                                          String drugName,
                                          String specification,
                                          String dosage,
                                          String frequency,
                                          String duration,
                                          int quantity,
                                          BigDecimal unitPrice,
                                          int sortNo) {
        return new PrescriptionItem(null, drugCode, drugName, specification, dosage,
                frequency, duration, quantity, unitPrice, sortNo);
    }

    public PrescriptionItem withId(Long newId) {
        return new PrescriptionItem(newId, drugCode, drugName, specification, dosage,
                frequency, duration, quantity, unitPrice, sortNo);
    }

    public PrescriptionItem withSortNo(int newSortNo) {
        return new PrescriptionItem(id, drugCode, drugName, specification, dosage,
                frequency, duration, quantity, unitPrice, newSortNo);
    }

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new PrescriptionException(PrescriptionError.RX_VALIDATION_FAILED, field + "不能为空");
        }
        return value.trim();
    }
}
