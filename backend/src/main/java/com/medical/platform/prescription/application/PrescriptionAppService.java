package com.medical.platform.prescription.application;

import com.medical.platform.consultation.domain.ConsultationRepository;
import com.medical.platform.consultation.domain.ConsultationSession;
import com.medical.platform.consultation.domain.ConsultationStatus;
import com.medical.platform.identity.application.security.CurrentUser;
import com.medical.platform.prescription.domain.Prescription;
import com.medical.platform.prescription.domain.PrescriptionError;
import com.medical.platform.prescription.domain.PrescriptionException;
import com.medical.platform.prescription.domain.PrescriptionItem;
import com.medical.platform.prescription.domain.PrescriptionRepository;
import com.medical.platform.prescription.domain.PrescriptionStatus;
import com.medical.platform.prescription.domain.RxNoGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 处方应用服务：开方、改草稿、提交、撤销，以及按角色的读取范围。
 *
 * <p>药师审核（approve/reject）在 R4-B 接入，聚合方法已经预留，避免审核逻辑反向侵入开方链路。
 */
@Service
public class PrescriptionAppService {

    private static final String ROLE_PATIENT = "PATIENT";
    private static final String ROLE_DOCTOR = "DOCTOR";
    private static final String ROLE_PHARMACIST = "PHARMACIST";
    private static final String ROLE_ADMIN = "ADMIN";

    private static final int MAX_PAGE_SIZE = 100;

    private final PrescriptionRepository prescriptionRepository;
    private final ConsultationRepository consultationRepository;
    private final RxNoGenerator rxNoGenerator;

    public PrescriptionAppService(PrescriptionRepository prescriptionRepository,
                                  ConsultationRepository consultationRepository,
                                  RxNoGenerator rxNoGenerator) {
        this.prescriptionRepository = prescriptionRepository;
        this.consultationRepository = consultationRepository;
        this.rxNoGenerator = rxNoGenerator;
    }

    /** 医生在会话内开方，落库为 DRAFT。 */
    @Transactional
    public PrescriptionView create(CurrentUser user, Long sessionId, SavePrescriptionCommand command) {
        requireRole(user, ROLE_DOCTOR, "只有医生可以开具处方");
        ConsultationSession session = loadSession(sessionId);
        if (session.getStatus() != ConsultationStatus.IN_PROGRESS || !session.isDoctor(user.id())) {
            throw new PrescriptionException(PrescriptionError.RX_SESSION_INVALID);
        }
        if (prescriptionRepository.findDraftBySession(sessionId).isPresent()) {
            throw new PrescriptionException(PrescriptionError.RX_STATE_INVALID,
                    "该会话已有未提交的处方草稿");
        }

        LocalDateTime now = LocalDateTime.now();
        Prescription prescription = Prescription.draft(
                sessionId,
                session.getPatientId(),
                user.id(),
                rxNoGenerator.next(),
                command.diagnosis(),
                toItems(command),
                now);
        return PrescriptionView.from(prescriptionRepository.insert(prescription));
    }

    /**
     * 修改草稿：整体替换诊断和明细，仅开方医生可操作。
     *
     * <p>R6-B 起放行驳回态：{@code REJECTED} 先经聚合的 {@code revise()} 退回 {@code DRAFT}，
     * 再替换明细，最后用一次版本条件更新落库。这样医生在驳回后可以直接点“保存并重新提交”，
     * 与 {@code openapi.yaml} 中 PUT“修改草稿/驳回态处方”的契约一致，也不会留下绕过状态机的入口。
     */
    @Transactional
    public PrescriptionView update(CurrentUser user, Long id, SavePrescriptionCommand command) {
        Prescription prescription = load(id);
        requireDoctorOwner(user, prescription);

        LocalDateTime now = LocalDateTime.now();
        int expectedVersion = prescription.getVersion();
        if (prescription.getStatus() == PrescriptionStatus.REJECTED) {
            prescription.revise(now);
        }
        prescription.replaceItems(command.diagnosis(), toItems(command), now);
        prescriptionRepository.replaceItems(id, prescription.getItems());
        if (!prescriptionRepository.updateHeader(prescription, expectedVersion)) {
            throw new PrescriptionException(PrescriptionError.RX_VERSION_CONFLICT);
        }
        return PrescriptionView.from(load(id));
    }

    /** 提交审核：DRAFT → PENDING_REVIEW。 */
    @Transactional
    public PrescriptionView submit(CurrentUser user, Long id) {
        Prescription prescription = load(id);
        requireDoctorOwner(user, prescription);

        int expectedVersion = prescription.getVersion();
        prescription.submit(LocalDateTime.now());
        if (!prescriptionRepository.updateHeader(prescription, expectedVersion)) {
            throw new PrescriptionException(PrescriptionError.RX_VERSION_CONFLICT);
        }
        return PrescriptionView.from(load(id));
    }

    /** 撤销：医生本人或患者本人在审核前可撤销。 */
    @Transactional
    public PrescriptionView cancel(CurrentUser user, Long id) {
        Prescription prescription = load(id);
        boolean allowed = (ROLE_DOCTOR.equals(user.roleCode()) && prescription.isOwnedByDoctor(user.id()))
                || (ROLE_PATIENT.equals(user.roleCode()) && prescription.isOwnedByPatient(user.id()));
        if (!allowed) {
            throw new PrescriptionException(PrescriptionError.RX_SCOPE_DENIED);
        }

        int expectedVersion = prescription.getVersion();
        prescription.cancel(LocalDateTime.now());
        if (!prescriptionRepository.updateHeader(prescription, expectedVersion)) {
            throw new PrescriptionException(PrescriptionError.RX_VERSION_CONFLICT);
        }
        return PrescriptionView.from(load(id));
    }

    public PrescriptionView detail(CurrentUser user, Long id) {
        Prescription prescription = load(id);
        assertCanRead(user, prescription);
        return PrescriptionView.from(prescription);
    }

    public PrescriptionPageView list(CurrentUser user, String statusParam, int page, int size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        PrescriptionStatus status = parseStatus(statusParam);
        int offset = safePage * safeSize;

        String role = user.roleCode();
        List<Prescription> rows;
        long total;
        if (ROLE_PATIENT.equals(role)) {
            rows = prescriptionRepository.findForPatient(user.id(), status, offset, safeSize);
            total = prescriptionRepository.countForPatient(user.id(), status);
        } else if (ROLE_DOCTOR.equals(role)) {
            rows = prescriptionRepository.findForDoctor(user.id(), status, offset, safeSize);
            total = prescriptionRepository.countForDoctor(user.id(), status);
        } else if (ROLE_PHARMACIST.equals(role)) {
            PrescriptionStatus reviewStatus = status == null ? PrescriptionStatus.PENDING_REVIEW : status;
            rows = prescriptionRepository.findAll(reviewStatus, offset, safeSize);
            total = prescriptionRepository.countAll(reviewStatus);
        } else if (ROLE_ADMIN.equals(role)) {
            rows = prescriptionRepository.findAll(status, offset, safeSize);
            total = prescriptionRepository.countAll(status);
        } else {
            throw new PrescriptionException(PrescriptionError.RX_SCOPE_DENIED);
        }
        return PrescriptionPageView.of(rows.stream().map(PrescriptionView::from).toList(),
                safePage, safeSize, total);
    }

    private List<PrescriptionItem> toItems(SavePrescriptionCommand command) {
        if (command.items() == null || command.items().isEmpty()) {
            throw new PrescriptionException(PrescriptionError.RX_ITEM_EMPTY);
        }
        return command.items().stream()
                .map(item -> PrescriptionItem.create(
                        item.drugCode(),
                        item.drugName(),
                        item.specification(),
                        item.dosage(),
                        item.frequency(),
                        item.duration(),
                        item.quantity(),
                        item.unitPrice(),
                        0))
                .toList();
    }

    private void assertCanRead(CurrentUser user, Prescription prescription) {
        String role = user.roleCode();
        if (ROLE_PATIENT.equals(role)) {
            if (!prescription.isOwnedByPatient(user.id())) {
                throw new PrescriptionException(PrescriptionError.RX_SCOPE_DENIED);
            }
            return;
        }
        if (ROLE_DOCTOR.equals(role)) {
            if (!prescription.isOwnedByDoctor(user.id())) {
                throw new PrescriptionException(PrescriptionError.RX_SCOPE_DENIED);
            }
            return;
        }
        if (ROLE_PHARMACIST.equals(role) || ROLE_ADMIN.equals(role)) {
            return;
        }
        throw new PrescriptionException(PrescriptionError.RX_SCOPE_DENIED);
    }

    private void requireDoctorOwner(CurrentUser user, Prescription prescription) {
        requireRole(user, ROLE_DOCTOR, "只有医生可以修改处方");
        if (!prescription.isOwnedByDoctor(user.id())) {
            throw new PrescriptionException(PrescriptionError.RX_SCOPE_DENIED);
        }
    }

    private void requireRole(CurrentUser user, String expected, String message) {
        if (!expected.equals(user.roleCode())) {
            throw new PrescriptionException(PrescriptionError.RX_SCOPE_DENIED, message);
        }
    }

    private Prescription load(Long id) {
        if (id == null || id <= 0) {
            throw new PrescriptionException(PrescriptionError.RX_NOT_FOUND);
        }
        return prescriptionRepository.findById(id)
                .orElseThrow(() -> new PrescriptionException(PrescriptionError.RX_NOT_FOUND));
    }

    private ConsultationSession loadSession(Long sessionId) {
        if (sessionId == null || sessionId <= 0) {
            throw new PrescriptionException(PrescriptionError.RX_SESSION_INVALID);
        }
        return consultationRepository.findById(sessionId)
                .orElseThrow(() -> new PrescriptionException(
                        PrescriptionError.RX_SESSION_INVALID, "问诊会话不存在"));
    }

    private PrescriptionStatus parseStatus(String statusParam) {
        if (statusParam == null || statusParam.isBlank()) {
            return null;
        }
        try {
            return PrescriptionStatus.valueOf(statusParam.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new PrescriptionException(PrescriptionError.BAD_REQUEST,
                    "不支持的处方状态：" + statusParam);
        }
    }

    private int normalizePage(int page) {
        if (page < 0) {
            throw new PrescriptionException(PrescriptionError.BAD_REQUEST, "page 不能小于 0");
        }
        return page;
    }

    private int normalizeSize(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new PrescriptionException(PrescriptionError.BAD_REQUEST,
                    "size 必须在 1 到 " + MAX_PAGE_SIZE + " 之间");
        }
        return size;
    }
}
