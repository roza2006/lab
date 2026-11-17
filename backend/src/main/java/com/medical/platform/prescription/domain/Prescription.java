package com.medical.platform.prescription.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 处方聚合根。状态只能经本类的方法迁移，应用服务和仓储不直接改 status。
 *
 * <p>集中校验两条约束：仅 DRAFT 可编辑明细；每次迁移递增 version，用于数据库
 * {@code WHERE id=? AND version=?} 的条件更新。
 */
public class Prescription {

    private static final int MAX_ITEMS = 5;
    private static final int VALID_HOURS = 72;

    private final Long id;
    private final String rxNo;
    private final Long sessionId;
    private final Long patientId;
    private final Long doctorId;
    private String diagnosis;
    private PrescriptionStatus status;
    private final List<PrescriptionItem> items;
    private String signedHash;
    private Long reviewerId;
    private LocalDateTime reviewedAt;
    private String rejectReason;
    private LocalDateTime validUntil;
    private int version;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Prescription(Long id,
                        String rxNo,
                        Long sessionId,
                        Long patientId,
                        Long doctorId,
                        String diagnosis,
                        PrescriptionStatus status,
                        List<PrescriptionItem> items,
                        String signedHash,
                        Long reviewerId,
                        LocalDateTime reviewedAt,
                        String rejectReason,
                        LocalDateTime validUntil,
                        int version,
                        LocalDateTime createdAt,
                        LocalDateTime updatedAt) {
        this.id = id;
        this.rxNo = Objects.requireNonNull(rxNo, "rxNo");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.patientId = Objects.requireNonNull(patientId, "patientId");
        this.doctorId = Objects.requireNonNull(doctorId, "doctorId");
        this.diagnosis = Objects.requireNonNull(diagnosis, "diagnosis");
        this.status = Objects.requireNonNull(status, "status");
        this.items = new ArrayList<>(items == null ? List.of() : items);
        this.signedHash = signedHash;
        this.reviewerId = reviewerId;
        this.reviewedAt = reviewedAt;
        this.rejectReason = rejectReason;
        this.validUntil = validUntil;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Prescription draft(Long sessionId,
                                     Long patientId,
                                     Long doctorId,
                                     String rxNo,
                                     String diagnosis,
                                     List<PrescriptionItem> items,
                                     LocalDateTime now) {
        if (diagnosis == null || diagnosis.isBlank()) {
            throw new PrescriptionException(PrescriptionError.RX_VALIDATION_FAILED, "诊断不能为空");
        }
        return new Prescription(null, rxNo, sessionId, patientId, doctorId, diagnosis.trim(),
                PrescriptionStatus.DRAFT, normalizeItems(items), null, null, null, null,
                null, 1, now, now);
    }

    /** 修改草稿：整体替换诊断与明细，提交后不可再编辑。 */
    public void replaceItems(String newDiagnosis, List<PrescriptionItem> newItems, LocalDateTime now) {
        requireDraft();
        if (newDiagnosis == null || newDiagnosis.isBlank()) {
            throw new PrescriptionException(PrescriptionError.RX_VALIDATION_FAILED, "诊断不能为空");
        }
        this.diagnosis = newDiagnosis.trim();
        this.items.clear();
        this.items.addAll(normalizeItems(newItems));
        touch(now);
    }

    public void addItem(PrescriptionItem item, LocalDateTime now) {
        requireDraft();
        if (items.size() >= MAX_ITEMS) {
            throw new PrescriptionException(PrescriptionError.RX_ITEM_LIMIT);
        }
        assertDrugNotDuplicated(item.drugCode());
        items.add(item.withSortNo(items.size()));
        touch(now);
    }

    public void removeItem(Long itemId, LocalDateTime now) {
        requireDraft();
        boolean removed = items.removeIf(item -> Objects.equals(item.id(), itemId));
        if (!removed) {
            throw new PrescriptionException(PrescriptionError.RX_VALIDATION_FAILED, "明细不存在");
        }
        renumber();
        touch(now);
    }

    public void submit(LocalDateTime now) {
        ensure(PrescriptionEvent.SUBMIT);
        if (items.isEmpty()) {
            throw new PrescriptionException(PrescriptionError.RX_ITEM_EMPTY);
        }
        status = PrescriptionStatus.PENDING_REVIEW;
        touch(now);
    }

    public void approve(Long pharmacistId, String signature, LocalDateTime now) {
        ensure(PrescriptionEvent.APPROVE);
        if (pharmacistId == null) {
            throw new PrescriptionException(PrescriptionError.RX_SCOPE_DENIED);
        }
        if (signature == null || signature.isBlank()) {
            throw new PrescriptionException(PrescriptionError.RX_SIGNATURE_REQUIRED);
        }
        this.reviewerId = pharmacistId;
        this.reviewedAt = now;
        this.signedHash = signature.trim();
        this.rejectReason = null;
        this.validUntil = now.plusHours(VALID_HOURS);
        this.status = PrescriptionStatus.APPROVED;
        touch(now);
    }

    public void reject(Long pharmacistId, String reason, LocalDateTime now) {
        ensure(PrescriptionEvent.REJECT);
        if (pharmacistId == null) {
            throw new PrescriptionException(PrescriptionError.RX_SCOPE_DENIED);
        }
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < 10) {
            throw new PrescriptionException(PrescriptionError.RX_REJECT_REASON_INVALID);
        }
        this.reviewerId = pharmacistId;
        this.reviewedAt = now;
        this.rejectReason = normalized;
        this.status = PrescriptionStatus.REJECTED;
        touch(now);
    }

    /** 驳回后由原开方医生退回草稿，修改后重新提交。 */
    public void revise(LocalDateTime now) {
        ensure(PrescriptionEvent.REVISE);
        this.signedHash = null;
        this.reviewerId = null;
        this.reviewedAt = null;
        this.rejectReason = null;
        this.validUntil = null;
        this.status = PrescriptionStatus.DRAFT;
        touch(now);
    }

    public void cancel(LocalDateTime now) {
        ensure(PrescriptionEvent.CANCEL);
        this.status = PrescriptionStatus.CANCELLED;
        touch(now);
    }

    public void circulate(LocalDateTime now) {
        ensure(PrescriptionEvent.CIRCULATE);
        this.status = PrescriptionStatus.CIRCULATED;
        touch(now);
    }

    public void redeem(LocalDateTime now) {
        ensure(PrescriptionEvent.REDEEM);
        this.status = PrescriptionStatus.REDEEMED;
        touch(now);
    }

    public void expire(LocalDateTime now) {
        ensure(PrescriptionEvent.EXPIRE);
        if (validUntil == null || !now.isAfter(validUntil)) {
            throw new PrescriptionException(PrescriptionError.RX_STATE_INVALID, "处方尚未到失效时间");
        }
        this.status = PrescriptionStatus.EXPIRED;
        touch(now);
    }

    public boolean isOwnedByDoctor(Long userId) {
        return doctorId.equals(userId);
    }

    public boolean isOwnedByPatient(Long userId) {
        return patientId.equals(userId);
    }

    public BigDecimal totalAmount() {
        return items.stream()
                .map(PrescriptionItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<PrescriptionItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    private void ensure(PrescriptionEvent event) {
        if (!status.canApply(event)) {
            throw new PrescriptionException(PrescriptionError.RX_STATE_INVALID,
                    "处方当前状态为 " + status + "，不能执行 " + event);
        }
    }

    private void requireDraft() {
        if (status != PrescriptionStatus.DRAFT) {
            throw new PrescriptionException(PrescriptionError.RX_STATE_INVALID,
                    "只有草稿状态的处方可以编辑");
        }
    }

    private void assertDrugNotDuplicated(String drugCode) {
        boolean duplicated = items.stream()
                .anyMatch(item -> item.drugCode().equalsIgnoreCase(drugCode));
        if (duplicated) {
            throw new PrescriptionException(PrescriptionError.RX_DUPLICATE_DRUG);
        }
    }

    private void renumber() {
        for (int i = 0; i < items.size(); i++) {
            items.set(i, items.get(i).withSortNo(i));
        }
    }

    private void touch(LocalDateTime now) {
        this.version += 1;
        this.updatedAt = now;
    }

    private static List<PrescriptionItem> normalizeItems(List<PrescriptionItem> rawItems) {
        if (rawItems == null || rawItems.isEmpty()) {
            throw new PrescriptionException(PrescriptionError.RX_ITEM_EMPTY);
        }
        if (rawItems.size() > MAX_ITEMS) {
            throw new PrescriptionException(PrescriptionError.RX_ITEM_LIMIT);
        }
        Set<String> drugCodes = new HashSet<>();
        List<PrescriptionItem> normalized = new ArrayList<>(rawItems.size());
        for (int i = 0; i < rawItems.size(); i++) {
            PrescriptionItem item = Objects.requireNonNull(rawItems.get(i), "item");
            if (!drugCodes.add(item.drugCode().toUpperCase())) {
                throw new PrescriptionException(PrescriptionError.RX_DUPLICATE_DRUG);
            }
            normalized.add(item.withSortNo(i));
        }
        return normalized;
    }

    public Long getId() {
        return id;
    }

    public String getRxNo() {
        return rxNo;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public Long getPatientId() {
        return patientId;
    }

    public Long getDoctorId() {
        return doctorId;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public PrescriptionStatus getStatus() {
        return status;
    }

    public String getSignedHash() {
        return signedHash;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public LocalDateTime getValidUntil() {
        return validUntil;
    }

    public int getVersion() {
        return version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
