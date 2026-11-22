package com.medical.platform.consultation.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发起问诊请求，字段与 openapi.yaml 的 ConsultationCreateRequest 对齐。
 */
public record ConsultationCreateRequest(
        @NotBlank(message = "科室不能为空")
        @Size(max = 64, message = "科室不能超过 64 字")
        String department,

        @NotBlank(message = "复诊疾病不能为空")
        @Size(max = 128, message = "复诊疾病不能超过 128 字")
        String diseaseName,

        @NotBlank(message = "主诉不能为空")
        @Size(max = 1000, message = "主诉不能超过 1000 字")
        String chiefComplaint) {
}
