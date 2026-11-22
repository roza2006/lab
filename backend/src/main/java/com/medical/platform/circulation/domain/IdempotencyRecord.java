package com.medical.platform.circulation.domain;

import java.time.LocalDateTime;

/**
 * 幂等记录：唯一键 {@code (bizType, bizId, operation, idemKey)}。
 *
 * <p>{@code requestHash} 用于区分“同一 key 重放”和“同一 key 换了参数”：
 * 前者回放首次响应，后者返回 409，避免客户端误用 key 造成语义漂移。
 */
public record IdempotencyRecord(
        Long id,
        String bizType,
        Long bizId,
        String operation,
        String idemKey,
        String requestHash,
        String responseSnapshot,
        IdempotencyStatus status,
        LocalDateTime createdAt,
        LocalDateTime expiredAt) {

    public static final String BIZ_REDEEM = "REDEEM";
    public static final String OP_REDEEM = "REDEEM";

    public IdempotencyRecord {
        if (bizType == null || bizType.isBlank()) {
            throw new IllegalArgumentException("bizType");
        }
        if (bizId == null) {
            throw new IllegalArgumentException("bizId");
        }
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("operation");
        }
        if (idemKey == null || idemKey.isBlank()) {
            throw new IllegalArgumentException("idemKey");
        }
        if (requestHash == null || requestHash.isBlank()) {
            throw new IllegalArgumentException("requestHash");
        }
    }

    public static IdempotencyRecord success(Long bizId,
                                            String idemKey,
                                            String requestHash,
                                            String responseSnapshot,
                                            LocalDateTime now) {
        return new IdempotencyRecord(null, BIZ_REDEEM, bizId, OP_REDEEM, idemKey,
                requestHash, responseSnapshot, IdempotencyStatus.SUCCESS, now, now.plusHours(24));
    }

    public IdempotencyRecord withId(Long newId) {
        return new IdempotencyRecord(newId, bizType, bizId, operation, idemKey, requestHash,
                responseSnapshot, status, createdAt, expiredAt);
    }
}
