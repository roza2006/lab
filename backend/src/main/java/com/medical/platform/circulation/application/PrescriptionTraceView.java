package com.medical.platform.circulation.application;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 处方全链路追踪视图，字段与前端 {@code PrescriptionTrace} 对齐。
 */
public record PrescriptionTraceView(
        Long prescriptionId,
        String status,
        List<TraceEventView> events) {

    public record TraceEventView(
            String status,
            LocalDateTime at,
            String actor,
            String note) {
    }
}
