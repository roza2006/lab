package com.medical.platform.circulation.domain;

/**
 * 流转单状态。与 {@code circulation_order.status} 的 CHECK 约束一致。
 *
 * <p>流转链路：{@code CREATED -> ACCEPTED -> REDEEMED}，药房也可在接方前 {@code REJECTED}。
 * 核销只允许从 {@code ACCEPTED} 进入，保证“未接方不能发药”。
 */
public enum CirculationStatus {

    CREATED,
    ACCEPTED,
    REDEEMED,
    REJECTED;

    public boolean isTerminal() {
        return this == REDEEMED || this == REJECTED;
    }

    public boolean canRedeem() {
        return this == ACCEPTED;
    }

    public boolean canAccept() {
        return this == CREATED;
    }

    public boolean canReject() {
        return this == CREATED;
    }
}
