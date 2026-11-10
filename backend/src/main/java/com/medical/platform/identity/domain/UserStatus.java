package com.medical.platform.identity.domain;

/**
 * 账号状态。数据库里用 1/0 存，领域里用枚举，避免在业务代码里散落魔法值。
 */
public enum UserStatus {
    ACTIVE,
    DISABLED
}
