package com.medical.platform.prescription.application;

import com.medical.platform.prescription.domain.Prescription;
import com.medical.platform.prescription.domain.PrescriptionItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PrescriptionView(
        Long id,
        String rxNo,
        Long consultationId,
        Long patientId,
        Long doctorId,
        String diagnosis,
        String status,
        List<ItemView> items,
        BigDecimal totalAmount,
        int version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static PrescriptionView from(Prescription prescription) {
        List<ItemView> itemViews = prescription.getItems().stream().map(ItemView::from).toList();
        return new PrescriptionView(
                prescription.getId(),
                prescription.getRxNo(),
                prescription.getSessionId(),
                prescription.getPatientId(),
                prescription.getDoctorId(),
                prescription.getDiagnosis(),
                prescription.getStatus().name(),
                itemViews,
                prescription.totalAmount(),
                prescription.getVersion(),
                prescription.getCreatedAt(),
                prescription.getUpdatedAt());
    }

    public record ItemView(
            Long id,
            String drugCode,
            String drugName,
            String specification,
            String dosage,
            String frequency,
            String duration,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal) {

        static ItemView from(PrescriptionItem item) {
            return new ItemView(item.id(), item.drugCode(), item.drugName(), item.specification(),
                    item.dosage(), item.frequency(), item.duration(), item.quantity(),
                    item.unitPrice(), item.subtotal());
        }
    }
}
