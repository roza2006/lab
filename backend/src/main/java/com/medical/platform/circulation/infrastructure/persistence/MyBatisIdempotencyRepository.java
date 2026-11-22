package com.medical.platform.circulation.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.medical.platform.circulation.domain.IdempotencyRecord;
import com.medical.platform.circulation.domain.IdempotencyRepository;
import com.medical.platform.circulation.domain.IdempotencyStatus;
import com.medical.platform.circulation.infrastructure.mapper.IdempotencyRecordMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisIdempotencyRepository implements IdempotencyRepository {

    private final IdempotencyRecordMapper mapper;

    public MyBatisIdempotencyRepository(IdempotencyRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<IdempotencyRecord> find(String bizType, Long bizId, String operation, String idemKey) {
        return mapper.selectList(Wrappers.<IdempotencyRecordPO>lambdaQuery()
                        .eq(IdempotencyRecordPO::getBizType, bizType)
                        .eq(IdempotencyRecordPO::getBizId, bizId)
                        .eq(IdempotencyRecordPO::getOperation, operation)
                        .eq(IdempotencyRecordPO::getIdemKey, idemKey))
                .stream()
                .findFirst()
                .map(this::toDomain);
    }

    @Override
    public IdempotencyRecord insert(IdempotencyRecord record) {
        IdempotencyRecordPO po = new IdempotencyRecordPO();
        po.setBizType(record.bizType());
        po.setBizId(record.bizId());
        po.setOperation(record.operation());
        po.setIdemKey(record.idemKey());
        po.setRequestHash(record.requestHash());
        po.setResponseSnapshot(record.responseSnapshot());
        po.setStatus(record.status().name());
        po.setCreatedAt(record.createdAt());
        po.setExpiredAt(record.expiredAt());
        mapper.insert(po);
        return record.withId(po.getId());
    }

    private IdempotencyRecord toDomain(IdempotencyRecordPO po) {
        return new IdempotencyRecord(
                po.getId(),
                po.getBizType(),
                po.getBizId(),
                po.getOperation(),
                po.getIdemKey(),
                po.getRequestHash(),
                po.getResponseSnapshot(),
                IdempotencyStatus.valueOf(po.getStatus()),
                po.getCreatedAt(),
                po.getExpiredAt());
    }
}
