package com.medical.platform.identity.interfaces.dto;

import com.medical.platform.identity.application.LoginResult;
import com.medical.platform.identity.application.UserView;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserView user
) {

    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(result.accessToken(), result.tokenType(),
                result.expiresInSeconds(), result.user());
    }
}
