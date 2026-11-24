package com.medical.platform.circulation.domain;

/**
 * 流转与核销域错误码，与 {@code docs/design/api-contract.md} §4.3、
 * {@code docs/test/circulation-idempotency-cases.md} 以及前端适配层对齐。
 *
 * <p>注意：患者调用核销返回 {@code SCOPE_DENIED}，其他药房操作他人流转单返回
 * {@code RX_SCOPE_DENIED}，两者语义不同，测试用例 ST-12 / ST-13 分别断言。
 */
public enum CirculationError {

    CIRC_NOT_FOUND("CIRC_NOT_FOUND", 404, "流转单不存在"),
    RX_NOT_FOUND("RX_NOT_FOUND", 404, "处方不存在"),
    CIRC_STATE_INVALID("CIRC_STATE_INVALID", 409, "当前流转状态不允许该操作"),
    RX_ALREADY_REDEEMED("RX_ALREADY_REDEEMED", 409, "该流转单已核销，不能重复发药"),
    CIRC_VERSION_CONFLICT("CIRC_VERSION_CONFLICT", 409, "流转单已被修改，请刷新后重试"),
    RX_VERSION_CONFLICT("RX_VERSION_CONFLICT", 409, "处方已被修改，请刷新后重试"),
    SCOPE_DENIED("SCOPE_DENIED", 403, "只有药师可以执行核销"),
    RX_SCOPE_DENIED("RX_SCOPE_DENIED", 403, "无权访问该流转单或处方"),
    IDEMPOTENCY_KEY_REQUIRED("IDEMPOTENCY_KEY_REQUIRED", 400, "核销必须携带 Idempotency-Key"),
    IDEMPOTENCY_KEY_INVALID("IDEMPOTENCY_KEY_INVALID", 400, "Idempotency-Key 长度必须为 8~64"),
    IDEMPOTENCY_CONFLICT("IDEMPOTENCY_CONFLICT", 409, "同一幂等键对应了不同的核销参数"),
    IDEMPOTENCY_IN_PROGRESS("IDEMPOTENCY_IN_PROGRESS", 409, "相同幂等键的核销正在处理中，请稍后重试"),
    CIRC_BATCH_INVALID("CIRC_BATCH_INVALID", 422, "药品批号不能为空且不能超过 64 个字符"),
    CIRC_EVIDENCE_INVALID("CIRC_EVIDENCE_INVALID", 422, "核验凭证不能超过 255 个字符"),
    CIRC_PHARMACY_INVALID("CIRC_PHARMACY_INVALID", 400, "药房不存在、已停用或账号未绑定有效药房"),
    CIRC_REJECT_REASON_INVALID("CIRC_REJECT_REASON_INVALID", 422, "拒接原因不能少于 2 个字"),
    CIRC_VALIDATION_FAILED("CIRC_VALIDATION_FAILED", 422, "流转参数校验失败"),
    BAD_REQUEST("BAD_REQUEST", 400, "请求参数不合法");

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;

    CirculationError(String code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
