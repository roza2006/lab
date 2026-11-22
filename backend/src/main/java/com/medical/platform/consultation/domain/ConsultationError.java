package com.medical.platform.consultation.domain;

/**
 * 问诊域错误码（对齐 docs/design/domain-model.md §11 与 openapi.yaml 的 ErrorResponse.code）。
 * HTTP 状态码写在枚举里，避免接口层再拼一次字符串。
 */
public enum ConsultationError {

    /** 会话不存在或已归档。 */
    CONSULTATION_NOT_FOUND("CONSULTATION_NOT_FOUND", 404, "问诊会话不存在"),
    /** 已认证但操作的不是本人会话。 */
    SCOPE_DENIED("SCOPE_DENIED", 403, "无权访问该问诊会话"),
    /** 重复接诊：会话已被接诊或状态不允许接诊。 */
    SESSION_ALREADY_ACCEPTED("SESSION_ALREADY_ACCEPTED", 409, "会话已被接诊"),
    /** 会话已结束，禁止继续写入。 */
    SESSION_CLOSED("SESSION_CLOSED", 409, "会话已结束，不能继续操作"),
    /** 当前状态不允许该操作。 */
    ILLEGAL_STATE("ILLEGAL_STATE", 409, "会话状态不允许该操作"),
    /** 请求参数不合法。 */
    BAD_REQUEST("BAD_REQUEST", 400, "请求参数不合法"),
    /** 字段校验失败。 */
    VALIDATION_FAILED("VALIDATION_FAILED", 422, "字段校验失败");

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;

    ConsultationError(String code, int httpStatus, String defaultMessage) {
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
