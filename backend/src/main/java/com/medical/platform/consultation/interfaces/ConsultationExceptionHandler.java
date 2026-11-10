package com.medical.platform.consultation.interfaces;

import com.medical.platform.consultation.domain.ConsultationError;
import com.medical.platform.consultation.domain.ConsultationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 问诊接口的错误响应。所有错误都带业务错误码 {@code code}，与 openapi.yaml 的 ErrorResponse 对齐。
 *
 * <p>只作用于 {@link ConsultationController}；用最高优先级保证 422 / 400 的语义不被全局
 * 认证异常处理器抢先处理。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ConsultationController.class)
public class ConsultationExceptionHandler {

    @ExceptionHandler(ConsultationException.class)
    public ResponseEntity<ProblemDetail> handleConsultation(ConsultationException ex) {
        ConsultationError error = ex.error();
        return problem(HttpStatus.valueOf(error.httpStatus()), error.code(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "参数不合法" : error.getDefaultMessage())
                .orElse("参数不合法");
        return problem(HttpStatus.UNPROCESSABLE_ENTITY,
                ConsultationError.VALIDATION_FAILED.code(), message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> handleMalformed(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, ConsultationError.BAD_REQUEST.code(),
                "请求格式不正确");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setProperty("code", code);
        return ResponseEntity.status(status).body(body);
    }
}
