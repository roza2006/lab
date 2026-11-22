package com.medical.platform.circulation.application;

/**
 * 创建流转单的结果。{@code created=false} 表示命中已有流转单，用于把 HTTP 201 与 200 区分开。
 */
public record CirculationCreateResult(CirculationView view, boolean created) {
}
