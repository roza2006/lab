package com.medical.platform.circulation.domain;

import java.util.Optional;

/**
 * 幂等记录仓储端口。唯一键冲突由数据库保证，调用方捕获并转为重放或 409。
 */
public interface IdempotencyRepository {

    Optional<IdempotencyRecord> find(String bizType, Long bizId, String operation, String idemKey);

    IdempotencyRecord insert(IdempotencyRecord record);
}
