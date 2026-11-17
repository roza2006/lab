package com.medical.platform.prescription.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 药师审核记录（值对象）。只追加、不改写，用于审核追溯与合规审计。
 *
 * <p>通过时携带电子签名摘要（{@link ReviewSignature}），驳回时为 {@code null}，
 * 驳回理由写入 {@link #comment()}，长度校验在聚合 {@link Prescription#reject} 内完成。
 */
public record PrescriptionReview(
        Long id,
        Long prescriptionId,
        Long pharmacistId,
        ReviewAction action,
        String comment,
        String signature,
        LocalDateTime signedAt) {

    public PrescriptionReview {
        Objects.requireNonNull(prescriptionId, "prescriptionId");
        Objects.requireNonNull(pharmacistId, "pharmacistId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(signedAt, "signedAt");
        comment = comment == null ? "" : comment.trim();
        if (action == ReviewAction.APPROVED && (signature == null || signature.isBlank())) {
            throw new PrescriptionException(PrescriptionError.RX_SIGNATURE_REQUIRED);
        }
    }

    public PrescriptionReview withId(Long newId) {
        return new PrescriptionReview(newId, prescriptionId, pharmacistId, action, comment, signature, signedAt);
    }
}
