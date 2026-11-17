package com.medical.platform.prescription.domain;

/**
 * 处方域错误码，与 docs/design/domain-model.md §11 和 openapi.yaml 的 ErrorResponse 对齐。
 */
public enum PrescriptionError {

    RX_NOT_FOUND("RX_NOT_FOUND", 404, "处方不存在"),
    RX_SCOPE_DENIED("RX_SCOPE_DENIED", 403, "无权访问该处方"),
    RX_SESSION_INVALID("RX_SESSION_INVALID", 400, "处方必须绑定进行中的问诊会话"),
    RX_STATE_INVALID("RX_STATE_INVALID", 409, "当前状态不允许该操作"),
    RX_VERSION_CONFLICT("RX_VERSION_CONFLICT", 409, "处方已被修改，请刷新后重试"),
    RX_ITEM_EMPTY("RX_ITEM_EMPTY", 400, "处方至少需要一条药品明细"),
    RX_ITEM_LIMIT("RX_ITEM_LIMIT", 400, "单张处方最多 5 条明细"),
    RX_DUPLICATE_DRUG("RX_DUPLICATE_DRUG", 400, "同一药品不能重复添加"),
    RX_DOSAGE_INVALID("RX_DOSAGE_INVALID", 400, "剂量、疗程或数量不合法"),
    RX_SIGNATURE_REQUIRED("RX_SIGNATURE_REQUIRED", 422, "审核通过必须写入签名摘要"),
    RX_REJECT_REASON_INVALID("RX_REJECT_REASON_INVALID", 422, "驳回理由不能少于 10 个字"),
    RX_EXPIRED("RX_EXPIRED", 409, "处方已超过有效期"),
    RX_VALIDATION_FAILED("RX_VALIDATION_FAILED", 422, "处方字段校验失败"),
    BAD_REQUEST("BAD_REQUEST", 400, "请求参数不合法");

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;

    PrescriptionError(String code, int httpStatus, String defaultMessage) {
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
