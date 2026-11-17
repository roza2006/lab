package com.medical.platform.prescription.domain;

/**
 * 药师审核动作，取值与 {@code openapi.yaml} 的 {@code ReviewAction} 枚举一致。
 *
 * <p>审核记录只追加不修改：一次通过或驳回就是一条不可变的历史，驳回后医生修改再提交会产生新记录。
 */
public enum ReviewAction {

    APPROVED,
    REJECTED;

    public static ReviewAction fromStatus(PrescriptionStatus status) {
        if (status == PrescriptionStatus.APPROVED) {
            return APPROVED;
        }
        if (status == PrescriptionStatus.REJECTED) {
            return REJECTED;
        }
        throw new PrescriptionException(PrescriptionError.RX_STATE_INVALID,
                "审核动作只能对应 APPROVED 或 REJECTED，当前为 " + status);
    }
}
