package com.medical.platform.circulation.domain;

import java.util.Optional;

/**
 * 流转单与发药记录仓储端口。实现放在 infrastructure，领域层不依赖 MyBatis。
 */
public interface CirculationRepository {

    Optional<CirculationOrder> findById(Long id);

    /**
     * 事务内加行锁读取流转单，串行化同一张单的并发核销。
     *
     * <p>SQL 形如 {@code SELECT ... FOR UPDATE}；MySQL 与 H2 均支持。
     */
    Optional<CirculationOrder> findByIdForUpdate(Long id);

    Optional<CirculationOrder> findByPrescriptionId(Long prescriptionId);

    /** 按 version 条件更新核销状态；返回 false 表示被其他事务抢先修改。 */
    boolean updateRedeemed(CirculationOrder order, int expectedVersion);

    DispenseRecord insertDispense(DispenseRecord record);

    Optional<DispenseRecord> findDispense(Long circulationId);

    /**
     * 药师所属药房。来自 {@code pharmacist_profile.pharmacy_id}；
     * 没有档案的账号视为未绑定药房，不能核销。
     */
    Optional<Long> findPharmacistPharmacyId(Long userId);
}
