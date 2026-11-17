package com.medical.platform.prescription.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.medical.platform.prescription.domain.Prescription;
import com.medical.platform.prescription.domain.PrescriptionItem;
import com.medical.platform.prescription.domain.PrescriptionRepository;
import com.medical.platform.prescription.domain.PrescriptionReview;
import com.medical.platform.prescription.domain.PrescriptionStatus;
import com.medical.platform.prescription.domain.ReviewAction;
import com.medical.platform.prescription.infrastructure.mapper.PrescriptionItemMapper;
import com.medical.platform.prescription.infrastructure.mapper.PrescriptionMapper;
import com.medical.platform.prescription.infrastructure.mapper.PrescriptionReviewMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class MyBatisPrescriptionRepository implements PrescriptionRepository {

    private final PrescriptionMapper prescriptionMapper;
    private final PrescriptionItemMapper itemMapper;
    private final PrescriptionReviewMapper reviewMapper;

    public MyBatisPrescriptionRepository(PrescriptionMapper prescriptionMapper,
                                         PrescriptionItemMapper itemMapper,
                                         PrescriptionReviewMapper reviewMapper) {
        this.prescriptionMapper = prescriptionMapper;
        this.itemMapper = itemMapper;
        this.reviewMapper = reviewMapper;
    }

    @Override
    public Prescription insert(Prescription prescription) {
        PrescriptionPO po = toPO(prescription);
        prescriptionMapper.insert(po);
        insertItems(po.getId(), prescription.getItems());
        return findById(po.getId()).orElseThrow();
    }

    @Override
    public Optional<Prescription> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        PrescriptionPO po = prescriptionMapper.selectById(id);
        if (po == null) {
            return Optional.empty();
        }
        List<PrescriptionItem> items = itemMapper.selectList(
                        Wrappers.<PrescriptionItemPO>lambdaQuery()
                                .eq(PrescriptionItemPO::getPrescriptionId, id)
                                .orderByAsc(PrescriptionItemPO::getSortNo))
                .stream()
                .map(this::toItem)
                .toList();
        return Optional.of(toDomain(po, items));
    }

    @Override
    public Optional<Prescription> findDraftBySession(Long sessionId) {
        return prescriptionMapper.selectList(Wrappers.<PrescriptionPO>lambdaQuery()
                        .eq(PrescriptionPO::getSessionId, sessionId)
                        .eq(PrescriptionPO::getStatus, PrescriptionStatus.DRAFT.name()))
                .stream()
                .findFirst()
                .map(po -> findById(po.getId()).orElseThrow());
    }

    @Override
    public boolean updateHeader(Prescription prescription, int expectedVersion) {
        int rows = prescriptionMapper.update(null, Wrappers.<PrescriptionPO>lambdaUpdate()
                .eq(PrescriptionPO::getId, prescription.getId())
                .eq(PrescriptionPO::getVersion, expectedVersion)
                .set(PrescriptionPO::getDiagnosis, prescription.getDiagnosis())
                .set(PrescriptionPO::getStatus, prescription.getStatus().name())
                .set(PrescriptionPO::getSignedHash, prescription.getSignedHash())
                .set(PrescriptionPO::getReviewerId, prescription.getReviewerId())
                .set(PrescriptionPO::getReviewedAt, prescription.getReviewedAt())
                .set(PrescriptionPO::getRejectReason, prescription.getRejectReason())
                .set(PrescriptionPO::getValidUntil, prescription.getValidUntil())
                .set(PrescriptionPO::getVersion, prescription.getVersion())
                .set(PrescriptionPO::getUpdatedAt, prescription.getUpdatedAt()));
        return rows > 0;
    }

    @Override
    public void replaceItems(Long prescriptionId, List<PrescriptionItem> items) {
        itemMapper.delete(Wrappers.<PrescriptionItemPO>lambdaQuery()
                .eq(PrescriptionItemPO::getPrescriptionId, prescriptionId));
        insertItems(prescriptionId, items);
    }

    @Override
    public List<Prescription> findForPatient(Long patientId, PrescriptionStatus status, int offset, int size) {
        return mapRows(prescriptionMapper.selectPatientPage(patientId, statusName(status), offset, size));
    }

    @Override
    public long countForPatient(Long patientId, PrescriptionStatus status) {
        return prescriptionMapper.countPatient(patientId, statusName(status));
    }

    @Override
    public List<Prescription> findForDoctor(Long doctorId, PrescriptionStatus status, int offset, int size) {
        return mapRows(prescriptionMapper.selectDoctorPage(doctorId, statusName(status), offset, size));
    }

    @Override
    public long countForDoctor(Long doctorId, PrescriptionStatus status) {
        return prescriptionMapper.countDoctor(doctorId, statusName(status));
    }

    @Override
    public List<Prescription> findAll(PrescriptionStatus status, int offset, int size) {
        return mapRows(prescriptionMapper.selectAllPage(statusName(status), offset, size));
    }

    @Override
    public long countAll(PrescriptionStatus status) {
        return prescriptionMapper.countAll(statusName(status));
    }

    @Override
    public PrescriptionReview insertReview(PrescriptionReview review) {
        PrescriptionReviewPO po = new PrescriptionReviewPO();
        po.setPrescriptionId(review.prescriptionId());
        po.setPharmacistId(review.pharmacistId());
        po.setAction(review.action().name());
        po.setReviewComment(review.comment());
        po.setSignature(review.signature());
        po.setSignedAt(review.signedAt());
        po.setCreatedAt(LocalDateTime.now());
        reviewMapper.insert(po);
        return review.withId(po.getId());
    }

    @Override
    public List<PrescriptionReview> findReviews(Long prescriptionId) {
        return reviewMapper.selectList(Wrappers.<PrescriptionReviewPO>lambdaQuery()
                        .eq(PrescriptionReviewPO::getPrescriptionId, prescriptionId)
                        .orderByAsc(PrescriptionReviewPO::getId))
                .stream()
                .map(this::toReview)
                .toList();
    }

    private PrescriptionReview toReview(PrescriptionReviewPO po) {
        return new PrescriptionReview(
                po.getId(),
                po.getPrescriptionId(),
                po.getPharmacistId(),
                ReviewAction.valueOf(po.getAction()),
                po.getReviewComment(),
                po.getSignature(),
                po.getSignedAt());
    }

    private List<Prescription> mapRows(List<PrescriptionPO> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(PrescriptionPO::getId).toList();
        Map<Long, List<PrescriptionItem>> itemsByRx = itemMapper.selectList(
                        Wrappers.<PrescriptionItemPO>lambdaQuery()
                                .in(PrescriptionItemPO::getPrescriptionId, ids)
                                .orderByAsc(PrescriptionItemPO::getSortNo))
                .stream()
                .collect(Collectors.groupingBy(PrescriptionItemPO::getPrescriptionId,
                        Collectors.mapping(this::toItem, Collectors.toList())));
        return rows.stream()
                .map(po -> toDomain(po, itemsByRx.getOrDefault(po.getId(), List.of())))
                .toList();
    }

    private void insertItems(Long prescriptionId, List<PrescriptionItem> items) {
        LocalDateTime now = LocalDateTime.now();
        for (PrescriptionItem item : items) {
            PrescriptionItemPO po = new PrescriptionItemPO();
            po.setPrescriptionId(prescriptionId);
            po.setDrugCode(item.drugCode());
            po.setDrugName(item.drugName());
            po.setSpecification(item.specification());
            po.setDosage(item.dosage());
            po.setFrequency(item.frequency());
            po.setDuration(item.duration());
            po.setQuantity(item.quantity());
            po.setUnitPrice(item.unitPrice());
            po.setSubtotal(item.subtotal());
            po.setSortNo(item.sortNo());
            po.setCreatedAt(now);
            itemMapper.insert(po);
        }
    }

    private PrescriptionPO toPO(Prescription prescription) {
        PrescriptionPO po = new PrescriptionPO();
        po.setId(prescription.getId());
        po.setRxNo(prescription.getRxNo());
        po.setSessionId(prescription.getSessionId());
        po.setPatientId(prescription.getPatientId());
        po.setDoctorId(prescription.getDoctorId());
        po.setDiagnosis(prescription.getDiagnosis());
        po.setStatus(prescription.getStatus().name());
        po.setSignedHash(prescription.getSignedHash());
        po.setReviewerId(prescription.getReviewerId());
        po.setReviewedAt(prescription.getReviewedAt());
        po.setRejectReason(prescription.getRejectReason());
        po.setValidUntil(prescription.getValidUntil());
        po.setVersion(prescription.getVersion());
        po.setCreatedAt(prescription.getCreatedAt());
        po.setUpdatedAt(prescription.getUpdatedAt());
        return po;
    }

    private Prescription toDomain(PrescriptionPO po, List<PrescriptionItem> items) {
        return new Prescription(
                po.getId(),
                po.getRxNo(),
                po.getSessionId(),
                po.getPatientId(),
                po.getDoctorId(),
                po.getDiagnosis(),
                PrescriptionStatus.valueOf(po.getStatus()),
                items,
                po.getSignedHash(),
                po.getReviewerId(),
                po.getReviewedAt(),
                po.getRejectReason(),
                po.getValidUntil(),
                po.getVersion() == null ? 1 : po.getVersion(),
                po.getCreatedAt(),
                po.getUpdatedAt());
    }

    private PrescriptionItem toItem(PrescriptionItemPO po) {
        return new PrescriptionItem(
                po.getId(),
                po.getDrugCode(),
                po.getDrugName(),
                po.getSpecification(),
                po.getDosage(),
                po.getFrequency(),
                po.getDuration(),
                po.getQuantity() == null ? 0 : po.getQuantity(),
                po.getUnitPrice(),
                po.getSortNo() == null ? 0 : po.getSortNo());
    }

    private String statusName(PrescriptionStatus status) {
        return status == null ? null : status.name();
    }
}
