package com.medical.platform.circulation.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 处方流转单聚合根（R5-A 创建/接方，R5-B 核销）。
 *
 * <p>本类只负责状态迁移与不变量，仓储不直接改 {@code status}：
 * <ul>
 *   <li>只有 {@code ACCEPTED} 可核销；</li>
 *   <li>核销是终态，重复核销抛 {@link CirculationError#RX_ALREADY_REDEEMED}；</li>
 *   <li>每次迁移递增 {@code version}，配合 {@code WHERE id=? AND version=?} 条件更新。</li>
 * </ul>
 */
public class CirculationOrder {

    private final Long id;
    private final String circulationNo;
    private final Long rxId;
    private final Long pharmacyId;
    private CirculationStatus status;
    private final String idempotencyKey;
    private LocalDateTime circulatedAt;
    private LocalDateTime acceptedAt;
    private String rejectReason;
    private LocalDateTime redeemedAt;
    private int version;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public CirculationOrder(Long id,
                            String circulationNo,
                            Long rxId,
                            Long pharmacyId,
                            CirculationStatus status,
                            String idempotencyKey,
                            LocalDateTime circulatedAt,
                            LocalDateTime acceptedAt,
                            String rejectReason,
                            LocalDateTime redeemedAt,
                            int version,
                            LocalDateTime createdAt,
                            LocalDateTime updatedAt) {
        this.id = id;
        this.circulationNo = Objects.requireNonNull(circulationNo, "circulationNo");
        this.rxId = Objects.requireNonNull(rxId, "rxId");
        this.pharmacyId = Objects.requireNonNull(pharmacyId, "pharmacyId");
        this.status = Objects.requireNonNull(status, "status");
        this.idempotencyKey = idempotencyKey;
        this.circulatedAt = circulatedAt;
        this.acceptedAt = acceptedAt;
        this.rejectReason = rejectReason;
        this.redeemedAt = redeemedAt;
        this.version = version <= 0 ? 1 : version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 创建流转单：处方审核通过后由药房或管理员发起，初始状态 {@code CREATED}。
     */
    public static CirculationOrder create(Long rxId,
                                          Long pharmacyId,
                                          String circulationNo,
                                          String idempotencyKey,
                                          LocalDateTime now) {
        return new CirculationOrder(null, circulationNo, rxId, pharmacyId,
                CirculationStatus.CREATED, idempotencyKey, now, null, null,
                null, 1, now, now);
    }

    /**
     * 药房接方：{@code CREATED -> ACCEPTED}，重复接方返回 409。
     */
    public void accept(LocalDateTime now) {
        if (!status.canAccept()) {
            throw new CirculationException(CirculationError.CIRC_STATE_INVALID,
                    "流转单当前状态为 " + status + "，不能接方");
        }
        this.status = CirculationStatus.ACCEPTED;
        this.acceptedAt = now;
        touch(now);
    }

    /**
     * 药房拒接：{@code CREATED -> REJECTED}，必须写明原因。
     */
    public void reject(String reason, LocalDateTime now) {
        if (!status.canReject()) {
            throw new CirculationException(CirculationError.CIRC_STATE_INVALID,
                    "流转单当前状态为 " + status + "，不能拒接");
        }
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < 2 || normalized.length() > 255) {
            throw new CirculationException(CirculationError.CIRC_REJECT_REASON_INVALID);
        }
        this.status = CirculationStatus.REJECTED;
        this.rejectReason = normalized;
        touch(now);
    }

    /**
     * 核销：{@code ACCEPTED -> REDEEMED}，写核销时间并递增版本。
     * 已核销的单子给出可区分的 {@code RX_ALREADY_REDEEMED}，便于客户端停止重试。
     */
    public void redeem(LocalDateTime now) {
        if (status == CirculationStatus.REDEEMED) {
            throw new CirculationException(CirculationError.RX_ALREADY_REDEEMED);
        }
        if (!status.canRedeem()) {
            throw new CirculationException(CirculationError.CIRC_STATE_INVALID,
                    "流转单当前状态为 " + status + "，未接方不能核销");
        }
        this.status = CirculationStatus.REDEEMED;
        this.redeemedAt = now;
        touch(now);
    }

    public boolean belongsToPharmacy(Long pharmacistPharmacyId) {
        return pharmacistPharmacyId != null && pharmacistPharmacyId.equals(pharmacyId);
    }

    private void touch(LocalDateTime now) {
        this.version += 1;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getCirculationNo() {
        return circulationNo;
    }

    public Long getRxId() {
        return rxId;
    }

    public Long getPharmacyId() {
        return pharmacyId;
    }

    public CirculationStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public LocalDateTime getCirculatedAt() {
        return circulatedAt;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public LocalDateTime getRedeemedAt() {
        return redeemedAt;
    }

    public int getVersion() {
        return version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
