package com.medical.platform.identity.application.port;

/**
 * 密码校验端口。应用层不直接依赖 BCrypt，方便测试时替换成固定盐值的实现。
 */
public interface PasswordHasher {

    boolean matches(String rawPassword, String encodedPassword);
}
