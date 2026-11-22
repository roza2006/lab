package com.medical.platform.circulation.application;

public record CreateCirculationCommand(Long rxId, Long pharmacyId, String idempotencyKey) {
}
