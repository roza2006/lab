package com.medical.platform.circulation.interfaces;

import com.medical.platform.circulation.domain.CirculationError;
import com.medical.platform.circulation.domain.CirculationException;
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
 * 流转/核销接口错误映射。错误体保持 {@code code + detail}，与处方、问诊链路一致。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {CirculationController.class, PrescriptionTraceController.class})
public class CirculationExceptionHandler {

    @ExceptionHandler(CirculationException.class)
    public ResponseEntity<ProblemDetail> handleCirculation(CirculationException ex) {
        CirculationError error = ex.error();
        return problem(HttpStatus.valueOf(error.httpStatus()), error.code(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "参数不合法" : error.getDefaultMessage())
                .orElse("参数不合法");
        return problem(HttpStatus.UNPROCESSABLE_ENTITY,
                CirculationError.CIRC_VALIDATION_FAILED.code(), message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> handleMalformed(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, CirculationError.BAD_REQUEST.code(), "请求格式不正确");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setProperty("code", code);
        return ResponseEntity.status(status).body(body);
    }
}
