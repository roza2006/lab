package com.medical.platform.prescription.interfaces.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record PrescriptionSaveRequest(
        @NotBlank(message = "诊断不能为空")
        @Size(max = 500, message = "诊断不能超过 500 字")
        String diagnosis,

        @NotEmpty(message = "处方至少需要一条药品明细")
        @Size(max = 5, message = "单张处方最多 5 条明细")
        List<@Valid Item> items) {

    public record Item(
            @NotBlank(message = "药品编码不能为空")
            String drugCode,

            @NotBlank(message = "药品名称不能为空")
            String drugName,

            String specification,

            @NotBlank(message = "单次剂量不能为空")
            String dosage,

            @NotBlank(message = "用药频次不能为空")
            String frequency,

            @NotBlank(message = "疗程不能为空")
            String duration,

            @Min(value = 1, message = "药品数量必须大于 0")
            int quantity,

            @DecimalMin(value = "0", message = "药品单价不能为负数")
            BigDecimal unitPrice) {
    }
}
