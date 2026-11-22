package com.medical.platform.prescription.application;

import java.math.BigDecimal;
import java.util.List;

public record SavePrescriptionCommand(String diagnosis, List<Item> items) {

    public record Item(String drugCode,
                       String drugName,
                       String specification,
                       String dosage,
                       String frequency,
                       String duration,
                       int quantity,
                       BigDecimal unitPrice) {
    }
}
