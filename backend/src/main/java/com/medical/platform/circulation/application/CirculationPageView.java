package com.medical.platform.circulation.application;

import java.util.List;

public record CirculationPageView(
        List<CirculationView> content,
        int page,
        int size,
        long total) {

    public static CirculationPageView of(List<CirculationView> content, int page, int size, long total) {
        return new CirculationPageView(content, page, size, total);
    }
}
