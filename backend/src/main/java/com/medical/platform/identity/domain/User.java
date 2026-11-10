package com.medical.platform.identity.domain;

/**
 * 登录用户的最小领域模型。
 *
 * <p>这里只放认证需要的字段；患者/医生/药师档案在各自上下文里，避免把
 * 身份上下文做成一张大宽表。
 */
public record User(
        Long id,
        String username,
        String passwordHash,
        String roleCode,
        UserStatus status
) {

    public boolean canLogin() {
        return status == UserStatus.ACTIVE;
    }
}
