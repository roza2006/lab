package com.medical.platform.prescription.domain;

/**
 * 处方域异常。接口层只读 {@link #error()}，不解析异常文本做分支。
 */
public class PrescriptionException extends RuntimeException {

    private final PrescriptionError error;

    public PrescriptionException(PrescriptionError error) {
        this(error, error.defaultMessage());
    }

    public PrescriptionException(PrescriptionError error, String message) {
        super(message);
        this.error = error;
    }

    public PrescriptionError error() {
        return error;
    }
}
