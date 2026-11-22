package com.medical.platform.circulation.domain;

import java.util.List;
import java.util.Optional;

/**
 * 流转单与发药记录仓储端口。实现放在 infrastructure，领域层不依赖 MyBatis。
 */
public interface CirculationRepository {

    CirculationOrder insert(CirculationOrder order);

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

    /** 接方/拒接的状态与时间字段更新，version 不匹配时返回 false。 */
    boolean updateStatus(CirculationOrder order, int expectedVersion);

    List<CirculationOrder> findForPatient(Long patientId, CirculationStatus status, int offset, int size);

    long countForPatient(Long patientId, CirculationStatus status);

    List<CirculationOrder> findForDoctor(Long doctorId, CirculationStatus status, int offset, int size);

    long countForDoctor(Long doctorId, CirculationStatus status);

    List<CirculationOrder> findForPharmacy(Long pharmacyId, CirculationStatus status, int offset, int size);

    long countForPharmacy(Long pharmacyId, CirculationStatus status);

    List<CirculationOrder> findAll(CirculationStatus status, int offset, int size);

    long countAll(CirculationStatus status);

    DispenseRecord insertDispense(DispenseRecord record);

    Optional<DispenseRecord> findDispense(Long circulationId);

    /**
     * 药师所属药房。来自 {@code pharmacist_profile.pharmacy_id}；
     * 没有档案的账号视为未绑定药房，不能核销。
     */
    Optional<Long> findPharmacistPharmacyId(Long userId);
}
