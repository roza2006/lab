package com.medical.platform.identity.application;

public record LoginResult(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UserView user
) {
}
