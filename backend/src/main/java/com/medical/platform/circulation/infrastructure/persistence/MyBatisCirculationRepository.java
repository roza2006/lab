package com.medical.platform.circulation.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.medical.platform.circulation.domain.CirculationOrder;
import com.medical.platform.circulation.domain.CirculationRepository;
import com.medical.platform.circulation.domain.CirculationStatus;
import com.medical.platform.circulation.domain.DispenseRecord;
import com.medical.platform.circulation.infrastructure.mapper.CirculationOrderMapper;
import com.medical.platform.circulation.infrastructure.mapper.DispenseRecordMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisCirculationRepository implements CirculationRepository {

    private final CirculationOrderMapper orderMapper;
    private final DispenseRecordMapper dispenseMapper;

    public MyBatisCirculationRepository(CirculationOrderMapper orderMapper,
                                        DispenseRecordMapper dispenseMapper) {
        this.orderMapper = orderMapper;
        this.dispenseMapper = dispenseMapper;
    }

    @Override
    public Optional<CirculationOrder> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(orderMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<CirculationOrder> findByIdForUpdate(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        CirculationOrderPO po = orderMapper.selectOne(Wrappers.<CirculationOrderPO>lambdaQuery()
                .eq(CirculationOrderPO::getId, id)
                .last("FOR UPDATE"));
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public Optional<CirculationOrder> findByPrescriptionId(Long prescriptionId) {
        if (prescriptionId == null) {
            return Optional.empty();
        }
        return orderMapper.selectList(Wrappers.<CirculationOrderPO>lambdaQuery()
                        .eq(CirculationOrderPO::getRxId, prescriptionId))
                .stream()
                .findFirst()
                .map(this::toDomain);
    }

    @Override
    public boolean updateRedeemed(CirculationOrder order, int expectedVersion) {
        int rows = orderMapper.update(null, Wrappers.<CirculationOrderPO>lambdaUpdate()
                .eq(CirculationOrderPO::getId, order.getId())
                .eq(CirculationOrderPO::getVersion, expectedVersion)
                .set(CirculationOrderPO::getStatus, order.getStatus().name())
                .set(CirculationOrderPO::getRedeemedAt, order.getRedeemedAt())
                .set(CirculationOrderPO::getVersion, order.getVersion())
                .set(CirculationOrderPO::getUpdatedAt, order.getUpdatedAt()));
        return rows > 0;
    }

    @Override
    public DispenseRecord insertDispense(DispenseRecord record) {
        DispenseRecordPO po = new DispenseRecordPO();
        po.setCirculationId(record.circulationId());
        po.setOperatorId(record.operatorId());
        po.setBatchNo(record.batchNo());
        po.setEvidence(record.evidence());
        po.setRedeemedAt(record.redeemedAt());
        po.setCreatedAt(record.createdAt());
        dispenseMapper.insert(po);
        return record.withId(po.getId());
    }

    @Override
    public Optional<DispenseRecord> findDispense(Long circulationId) {
        if (circulationId == null) {
            return Optional.empty();
        }
        return dispenseMapper.selectList(Wrappers.<DispenseRecordPO>lambdaQuery()
                        .eq(DispenseRecordPO::getCirculationId, circulationId))
                .stream()
                .findFirst()
                .map(this::toDispense);
    }

    @Override
    public Optional<Long> findPharmacistPharmacyId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(orderMapper.selectPharmacistPharmacyId(userId));
    }

    private CirculationOrder toDomain(CirculationOrderPO po) {
        return new CirculationOrder(
                po.getId(),
                po.getCirculationNo(),
                po.getRxId(),
                po.getPharmacyId(),
                CirculationStatus.valueOf(po.getStatus()),
                po.getIdempotencyKey(),
                po.getCirculatedAt(),
                po.getAcceptedAt(),
                po.getRejectReason(),
                po.getRedeemedAt(),
                po.getVersion() == null ? 1 : po.getVersion(),
                po.getCreatedAt(),
                po.getUpdatedAt());
    }

    private DispenseRecord toDispense(DispenseRecordPO po) {
        return new DispenseRecord(
                po.getId(),
                po.getCirculationId(),
                po.getOperatorId(),
                po.getBatchNo(),
                po.getEvidence(),
                po.getRedeemedAt(),
                po.getCreatedAt());
    }
}
