package com.medical.platform.prescription.application;

import com.medical.platform.prescription.domain.PrescriptionReview;

import java.time.LocalDateTime;

/**
 * 审核记录视图，字段与 {@code openapi.yaml} 的 {@code Review} schema 对齐：
 * {@code id / prescriptionId / pharmacistId / action / comment / signature / signedAt}。
 */
public record ReviewView(
        Long id,
        Long prescriptionId,
        Long pharmacistId,
        String action,
        String comment,
        String signature,
        LocalDateTime signedAt) {

    public static ReviewView from(PrescriptionReview review) {
        return new ReviewView(
                review.id(),
                review.prescriptionId(),
                review.pharmacistId(),
                review.action().name(),
                review.comment(),
                review.signature(),
                review.signedAt());
    }
}
