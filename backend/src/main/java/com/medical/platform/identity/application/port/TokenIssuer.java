package com.medical.platform.identity.application.port;

import com.medical.platform.identity.domain.User;

public interface TokenIssuer {

    IssuedToken issue(User user);

    record IssuedToken(String value, long expiresInSeconds) {
    }
}
