package com.medical.platform.consultation.application;

import java.util.List;

/**
 * 分页响应，字段与 openapi.yaml 的 PageResponse schema 对齐。
 */
public record PageView<T>(List<T> content,
                          int page,
                          int size,
                          long totalElements,
                          int totalPages,
                          boolean last) {

    public static <T> PageView<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageView<>(content, page, size, totalElements, totalPages, page + 1 >= totalPages);
    }
}
