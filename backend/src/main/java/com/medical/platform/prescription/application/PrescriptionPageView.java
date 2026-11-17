package com.medical.platform.prescription.application;

import java.util.List;

public record PrescriptionPageView(
        List<PrescriptionView> content,
        int page,
        int size,
        long total) {

    public static PrescriptionPageView of(List<PrescriptionView> content, int page, int size, long total) {
        return new PrescriptionPageView(content, page, size, total);
    }
}
