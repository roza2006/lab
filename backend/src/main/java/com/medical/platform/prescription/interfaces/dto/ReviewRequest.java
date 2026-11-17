package com.medical.platform.prescription.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 审核请求体，对应 {@code openapi.yaml} 的 {@code ReviewRequest}。
 *
 * <p>契约要求 {@code comment} 必填且至少 2 个字符；驳回场景聚合还会要求至少 10 个字，
 * 不足时返回 422 {@code RX_REJECT_REASON_INVALID}，以便前端提示“请写清楚驳回理由”。
 */
public record ReviewRequest(
        @NotBlank(message = "审核意见不能为空")
        @Size(min = 2, max = 500, message = "审核意见需 2 到 500 个字符")
        String comment) {
}
