package com.medical.platform.prescription.interfaces;

import com.medical.platform.prescription.domain.PrescriptionError;
import com.medical.platform.prescription.domain.PrescriptionException;
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

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = PrescriptionController.class)
public class PrescriptionExceptionHandler {

    @ExceptionHandler(PrescriptionException.class)
    public ResponseEntity<ProblemDetail> handlePrescription(PrescriptionException ex) {
        PrescriptionError error = ex.error();
        return problem(HttpStatus.valueOf(error.httpStatus()), error.code(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "参数不合法" : error.getDefaultMessage())
                .orElse("参数不合法");
        return problem(HttpStatus.UNPROCESSABLE_ENTITY,
                PrescriptionError.RX_VALIDATION_FAILED.code(), message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> handleMalformed(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, PrescriptionError.BAD_REQUEST.code(), "请求格式不正确");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setProperty("code", code);
        return ResponseEntity.status(status).body(body);
    }
}
