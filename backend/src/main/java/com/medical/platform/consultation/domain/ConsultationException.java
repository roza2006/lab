package com.medical.platform.consultation.domain;

/**
 * 问诊域业务异常。由接口层统一翻译成 ProblemDetail（code + 中文 message）。
 */
public class ConsultationException extends RuntimeException {

    private final ConsultationError error;

    public ConsultationException(ConsultationError error) {
        this(error, error.defaultMessage());
    }

    public ConsultationException(ConsultationError error, String message) {
        super(message);
        this.error = error;
    }

    public ConsultationError error() {
        return error;
    }
}
