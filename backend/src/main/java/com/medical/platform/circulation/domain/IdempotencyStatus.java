package com.medical.platform.circulation.domain;

/**
 * 幂等记录状态。核销采用“事务内一次写入 SUCCESS”策略：
 * 成功记录与业务数据同事务提交，失败则一起回滚，不会留下执行了一半的占位。
 */
public enum IdempotencyStatus {
    PROCESSING,
    SUCCESS,
    FAILED
}
