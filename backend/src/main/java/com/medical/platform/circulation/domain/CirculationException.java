package com.medical.platform.circulation.domain;

/**
 * 流转上下文异常。接口层只读 {@link #error()} 映射 HTTP 状态，不解析异常文本。
 */
public class CirculationException extends RuntimeException {

    private final CirculationError error;

    public CirculationException(CirculationError error) {
        this(error, error.defaultMessage());
    }

    public CirculationException(CirculationError error, String message) {
        super(message);
        this.error = error;
    }

    public CirculationError error() {
        return error;
    }
}
