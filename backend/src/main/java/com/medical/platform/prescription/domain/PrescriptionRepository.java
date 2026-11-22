package com.medical.platform.prescription.domain;

import java.util.List;
import java.util.Optional;

/**
 * 处方聚合仓储端口。实现放在 infrastructure，领域层不依赖 MyBatis。
 */
public interface PrescriptionRepository {

    Prescription insert(Prescription prescription);

    Optional<Prescription> findById(Long id);

    Optional<Prescription> findDraftBySession(Long sessionId);

    /**
     * 按 version 条件更新处方头。明细只在草稿阶段通过 {@link #replaceItems} 整体替换。
     *
     * @return false 表示版本已变，调用方返回 409
     */
    boolean updateHeader(Prescription prescription, int expectedVersion);

    void replaceItems(Long prescriptionId, List<PrescriptionItem> items);

    List<Prescription> findForPatient(Long patientId, PrescriptionStatus status, int offset, int size);

    long countForPatient(Long patientId, PrescriptionStatus status);

    List<Prescription> findForDoctor(Long doctorId, PrescriptionStatus status, int offset, int size);

    long countForDoctor(Long doctorId, PrescriptionStatus status);

    List<Prescription> findAll(PrescriptionStatus status, int offset, int size);

    long countAll(PrescriptionStatus status);

    /**
     * 追加一条审核记录（只增不改），返回带主键的记录。
     */
    PrescriptionReview insertReview(PrescriptionReview review);

    /**
     * 按时间顺序读取某张处方的全部审核记录，供医生与药师追溯“驳回 → 修改 → 通过”的完整链路。
     */
    List<PrescriptionReview> findReviews(Long prescriptionId);
}
