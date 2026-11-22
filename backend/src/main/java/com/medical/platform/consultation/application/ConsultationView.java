package com.medical.platform.consultation.application;

import com.medical.platform.consultation.domain.ConsultationSession;

import java.time.LocalDateTime;

/**
 * 会话对外视图，字段与 openapi.yaml 的 Consultation schema 对齐。
 */
public record ConsultationView(Long id,
                               Long patientId,
                               Long doctorId,
                               String department,
                               String diseaseName,
                               String chiefComplaint,
                               String status,
                               LocalDateTime createdAt,
                               LocalDateTime endedAt) {

    public static ConsultationView from(ConsultationSession session) {
        return new ConsultationView(
                session.getId(),
                session.getPatientId(),
                session.getDoctorId(),
                session.getDepartment(),
                session.getDiseaseName(),
                session.getChiefComplaint(),
                session.getStatus().name(),
                session.getCreatedAt(),
                session.getEndedAt());
    }
}
