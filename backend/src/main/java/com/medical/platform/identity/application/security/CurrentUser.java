package com.medical.platform.identity.application.security;

/**
 * 通过 JWT 解析出来的当前用户，只放鉴权需要的字段，不携带密码。
 */
public record CurrentUser(Long id, String username, String roleCode) {
}
