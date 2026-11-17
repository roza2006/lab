package com.medical.platform.prescription.domain;

/**
 * 处方 8 状态。终态不再接受任何事件，非法迁移统一抛 RX_STATE_INVALID。
 */
public enum PrescriptionStatus {

    DRAFT,
    PENDING_REVIEW,
    REJECTED,
    APPROVED,
    CIRCULATED,
    REDEEMED,
    CANCELLED,
    EXPIRED;

    public boolean isTerminal() {
        return this == REDEEMED || this == CANCELLED || this == EXPIRED;
    }

    public boolean canApply(PrescriptionEvent event) {
        return switch (event) {
            case SUBMIT -> this == DRAFT;
            case APPROVE, REJECT -> this == PENDING_REVIEW;
            case REVISE -> this == REJECTED;
            case CANCEL -> this == DRAFT || this == PENDING_REVIEW;
            case CIRCULATE -> this == APPROVED;
            case REDEEM -> this == CIRCULATED;
            case EXPIRE -> this == APPROVED || this == CIRCULATED;
        };
    }
}
