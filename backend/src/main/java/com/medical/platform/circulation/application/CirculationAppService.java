package com.medical.platform.circulation.application;

import com.medical.platform.circulation.domain.CirculationError;
import com.medical.platform.circulation.domain.CirculationException;
import com.medical.platform.circulation.domain.CirculationNoGenerator;
import com.medical.platform.circulation.domain.CirculationOrder;
import com.medical.platform.circulation.domain.CirculationRepository;
import com.medical.platform.circulation.domain.CirculationStatus;
import com.medical.platform.circulation.domain.DispenseRecord;
import com.medical.platform.identity.application.security.CurrentUser;
import com.medical.platform.prescription.application.PrescriptionView;
import com.medical.platform.prescription.domain.Prescription;
import com.medical.platform.prescription.domain.PrescriptionRepository;
import com.medical.platform.prescription.domain.PrescriptionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流转单应用服务（R5-A）：创建、列表、详情、药房接方与拒接。
 *
 * <p>创建流转单时同步把处方从 {@code APPROVED} 推到 {@code CIRCULATED}；
 * 两张表在同一个事务里更新，避免出现“处方已流转、但查不到流转单”的中间态。
 * 核销仍由 {@link CirculationRedeemAppService} 负责。
 */
@Service
public class CirculationAppService {

    private static final String ROLE_PATIENT = "PATIENT";
    private static final String ROLE_DOCTOR = "DOCTOR";
    private static final String ROLE_PHARMACIST = "PHARMACIST";
    private static final String ROLE_ADMIN = "ADMIN";

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MIN_KEY_LENGTH = 8;
    private static final int MAX_KEY_LENGTH = 64;

    private final CirculationRepository circulationRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final CirculationNoGenerator circulationNoGenerator;

    public CirculationAppService(CirculationRepository circulationRepository,
                                 PrescriptionRepository prescriptionRepository,
                                 CirculationNoGenerator circulationNoGenerator) {
        this.circulationRepository = circulationRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.circulationNoGenerator = circulationNoGenerator;
    }

    /**
     * 创建流转单。一张处方只有一张流转单，重复调用返回已存在的那张。
     */
    @Transactional
    public CirculationCreateResult create(CurrentUser user, CreateCirculationCommand command) {
        Prescription prescription = loadPrescription(command.rxId());
        Long pharmacyId = resolvePharmacy(user, command.pharmacyId(), prescription);

        CirculationOrder existing = circulationRepository
                .findByPrescriptionId(prescription.getId())
                .orElse(null);
        if (existing != null) {
            authorizeExisting(user, prescription, existing, pharmacyId);
            return new CirculationCreateResult(viewOf(existing), false);
        }
        if (prescription.getStatus() != PrescriptionStatus.APPROVED) {
            throw new CirculationException(CirculationError.CIRC_STATE_INVALID,
                    "处方当前状态为 " + prescription.getStatus() + "，不能流转");
        }

        LocalDateTime now = LocalDateTime.now();
        int expectedVersion = prescription.getVersion();
        prescription.circulate(now);
        if (!prescriptionRepository.updateHeader(prescription, expectedVersion)) {
            throw new CirculationException(CirculationError.RX_VERSION_CONFLICT);
        }

        CirculationOrder order = CirculationOrder.create(
                prescription.getId(),
                pharmacyId,
                circulationNoGenerator.next(),
                idempotencyKey(command.idempotencyKey(), prescription.getId()),
                now);
        return new CirculationCreateResult(
                viewOf(circulationRepository.insert(order)), true);
    }

    public CirculationPageView list(CurrentUser user, String statusParam, int page, int size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        CirculationStatus status = parseStatus(statusParam);
        int offset = safePage * safeSize;

        String role = user.roleCode();
        List<CirculationOrder> rows;
        long total;
        if (ROLE_PATIENT.equals(role)) {
            rows = circulationRepository.findForPatient(user.id(), status, offset, safeSize);
            total = circulationRepository.countForPatient(user.id(), status);
        } else if (ROLE_DOCTOR.equals(role)) {
            rows = circulationRepository.findForDoctor(user.id(), status, offset, safeSize);
            total = circulationRepository.countForDoctor(user.id(), status);
        } else if (ROLE_PHARMACIST.equals(role)) {
            Long pharmacyId = requirePharmacy(user);
            rows = circulationRepository.findForPharmacy(pharmacyId, status, offset, safeSize);
            total = circulationRepository.countForPharmacy(pharmacyId, status);
        } else if (ROLE_ADMIN.equals(role)) {
            rows = circulationRepository.findAll(status, offset, safeSize);
            total = circulationRepository.countAll(status);
        } else {
            throw new CirculationException(CirculationError.SCOPE_DENIED);
        }
        return CirculationPageView.of(rows.stream().map(this::viewOf).toList(),
                safePage, safeSize, total);
    }

    public CirculationView detail(CurrentUser user, Long id) {
        CirculationOrder order = loadOrder(id);
        Prescription prescription = loadPrescription(order.getRxId());
        authorizeRead(user, prescription, order);
        DispenseRecord dispense = circulationRepository.findDispense(id).orElse(null);
        return CirculationView.from(order, dispense, PrescriptionView.from(prescription));
    }

    /**
     * 药房接方：只能操作本药房的流转单，重复接方返回 409。
     */
    @Transactional
    public CirculationView accept(CurrentUser user, Long id) {
        requirePharmacist(user);
        Long pharmacyId = requirePharmacy(user);
        CirculationOrder order = loadOrder(id);
        if (!order.belongsToPharmacy(pharmacyId)) {
            throw new CirculationException(CirculationError.RX_SCOPE_DENIED,
                    "只能接本药房的流转单");
        }

        int expectedVersion = order.getVersion();
        order.accept(LocalDateTime.now());
        if (!circulationRepository.updateStatus(order, expectedVersion)) {
            throw new CirculationException(CirculationError.CIRC_VERSION_CONFLICT);
        }
        return viewOf(loadOrder(id));
    }

    /**
     * 药房拒接：{@code CREATED -> REJECTED}，原因至少 2 个字。
     */
    @Transactional
    public CirculationView reject(CurrentUser user, Long id, String reason) {
        requirePharmacist(user);
        Long pharmacyId = requirePharmacy(user);
        CirculationOrder order = loadOrder(id);
        if (!order.belongsToPharmacy(pharmacyId)) {
            throw new CirculationException(CirculationError.RX_SCOPE_DENIED,
                    "只能拒接本药房的流转单");
        }

        int expectedVersion = order.getVersion();
        order.reject(reason, LocalDateTime.now());
        if (!circulationRepository.updateStatus(order, expectedVersion)) {
            throw new CirculationException(CirculationError.CIRC_VERSION_CONFLICT);
        }
        return viewOf(loadOrder(id));
    }

    private CirculationView viewOf(CirculationOrder order) {
        Prescription prescription = loadPrescription(order.getRxId());
        DispenseRecord dispense = circulationRepository.findDispense(order.getId()).orElse(null);
        return CirculationView.from(order, dispense, PrescriptionView.from(prescription));
    }

    /**
     * 解析流转目标药房。
     *
     * <p>v1 口径：患者可为本人已通过处方选择取药药房；药师只能流转到本人所属药房；
     * 管理员可指定药房。三条路径都要求药房处于启用状态。
     */
    private Long resolvePharmacy(CurrentUser user, Long requestedPharmacyId,
                                 Prescription prescription) {
        if (user == null) {
            throw new CirculationException(CirculationError.SCOPE_DENIED);
        }
        String role = user.roleCode();
        if (ROLE_ADMIN.equals(role)) {
            requireActivePharmacy(requestedPharmacyId);
            return requestedPharmacyId;
        }
        if (ROLE_PHARMACIST.equals(role)) {
            Long pharmacyId = requirePharmacy(user);
            if (!circulationRepository.isActivePharmacy(pharmacyId)) {
                throw new CirculationException(CirculationError.CIRC_PHARMACY_INVALID,
                        "当前药师所属药房已停用，不能流转");
            }
            return pharmacyId;
        }
        if (ROLE_PATIENT.equals(role)) {
            if (!prescription.isOwnedByPatient(user.id())) {
                throw new CirculationException(CirculationError.RX_SCOPE_DENIED);
            }
            requireActivePharmacy(requestedPharmacyId);
            return requestedPharmacyId;
        }
        throw new CirculationException(CirculationError.SCOPE_DENIED);
    }

    private void requireActivePharmacy(Long pharmacyId) {
        if (!circulationRepository.isActivePharmacy(pharmacyId)) {
            throw new CirculationException(CirculationError.CIRC_PHARMACY_INVALID,
                    "药房不存在或已停用，请选择其他药房");
        }
    }

    /**
     * 重复创建命中已有流转单时，仍要按角色校验可见性，避免患者或别家药房
     * 通过“重复建单”拿到不属于自己的流转单。
     */
    private void authorizeExisting(CurrentUser user,
                                   Prescription prescription,
                                   CirculationOrder existing,
                                   Long pharmacyId) {
        String role = user.roleCode();
        if (ROLE_PATIENT.equals(role)) {
            if (!prescription.isOwnedByPatient(user.id())) {
                throw new CirculationException(CirculationError.RX_SCOPE_DENIED);
            }
            return;
        }
        if (ROLE_PHARMACIST.equals(role)) {
            if (!existing.belongsToPharmacy(pharmacyId)) {
                throw new CirculationException(CirculationError.RX_SCOPE_DENIED);
            }
            return;
        }
        if (!ROLE_ADMIN.equals(role)) {
            throw new CirculationException(CirculationError.SCOPE_DENIED);
        }
    }

    private Long requirePharmacy(CurrentUser user) {
        return circulationRepository.findPharmacistPharmacyId(user.id())
                .orElseThrow(() -> new CirculationException(
                        CirculationError.CIRC_PHARMACY_INVALID,
                        "当前药师未绑定药房档案，不能处理流转单"));
    }

    private void requirePharmacist(CurrentUser user) {
        if (user == null || !ROLE_PHARMACIST.equals(user.roleCode())) {
            throw new CirculationException(CirculationError.SCOPE_DENIED);
        }
    }

    private void authorizeRead(CurrentUser user,
                               Prescription prescription,
                               CirculationOrder order) {
        switch (user.roleCode()) {
            case ROLE_PATIENT -> {
                if (!prescription.isOwnedByPatient(user.id())) {
                    throw new CirculationException(CirculationError.RX_SCOPE_DENIED);
                }
            }
            case ROLE_DOCTOR -> {
                if (!prescription.isOwnedByDoctor(user.id())) {
                    throw new CirculationException(CirculationError.RX_SCOPE_DENIED);
                }
            }
            case ROLE_PHARMACIST -> {
                Long pharmacyId = requirePharmacy(user);
                if (!order.belongsToPharmacy(pharmacyId)) {
                    throw new CirculationException(CirculationError.RX_SCOPE_DENIED);
                }
            }
            case ROLE_ADMIN -> {
                // 管理员只读监管
            }
            default -> throw new CirculationException(CirculationError.RX_SCOPE_DENIED);
        }
    }

    private Prescription loadPrescription(Long rxId) {
        if (rxId == null || rxId <= 0) {
            throw new CirculationException(CirculationError.RX_NOT_FOUND);
        }
        return prescriptionRepository.findById(rxId)
                .orElseThrow(() -> new CirculationException(CirculationError.RX_NOT_FOUND));
    }

    private CirculationOrder loadOrder(Long id) {
        if (id == null || id <= 0) {
            throw new CirculationException(CirculationError.CIRC_NOT_FOUND);
        }
        return circulationRepository.findById(id)
                .orElseThrow(() -> new CirculationException(CirculationError.CIRC_NOT_FOUND));
    }

    private String idempotencyKey(String raw, Long rxId) {
        String key = raw == null ? "" : raw.trim();
        if (key.isEmpty()) {
            key = "CIRC-RX-" + rxId;
        }
        if (key.length() < MIN_KEY_LENGTH || key.length() > MAX_KEY_LENGTH) {
            throw new CirculationException(CirculationError.IDEMPOTENCY_KEY_INVALID);
        }
        return key;
    }

    private CirculationStatus parseStatus(String statusParam) {
        if (statusParam == null || statusParam.isBlank()) {
            return null;
        }
        try {
            return CirculationStatus.valueOf(statusParam.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new CirculationException(CirculationError.BAD_REQUEST,
                    "不支持的流转状态：" + statusParam);
        }
    }

    private int normalizePage(int page) {
        if (page < 0) {
            throw new CirculationException(CirculationError.BAD_REQUEST, "page 不能小于 0");
        }
        return page;
    }

    private int normalizeSize(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new CirculationException(CirculationError.BAD_REQUEST,
                    "size 必须在 1 到 " + MAX_PAGE_SIZE + " 之间");
        }
        return size;
    }
}
