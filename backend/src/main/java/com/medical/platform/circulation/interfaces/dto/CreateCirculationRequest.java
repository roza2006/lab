package com.medical.platform.circulation.interfaces.dto;

import jakarta.validation.constraints.NotNull;

public record CreateCirculationRequest(
        @NotNull(message = "处方 ID 不能为空")
        Long rxId,
        Long pharmacyId) {
}
