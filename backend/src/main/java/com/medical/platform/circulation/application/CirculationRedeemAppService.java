package com.medical.platform.circulation.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.platform.circulation.domain.CirculationError;
import com.medical.platform.circulation.domain.CirculationException;
import com.medical.platform.circulation.domain.CirculationOrder;
import com.medical.platform.circulation.domain.CirculationRepository;
import com.medical.platform.circulation.domain.CirculationStatus;
import com.medical.platform.circulation.domain.DispenseRecord;
import com.medical.platform.circulation.domain.IdempotencyRecord;
import com.medical.platform.circulation.domain.IdempotencyRepository;
import com.medical.platform.circulation.domain.IdempotencyStatus;
import com.medical.platform.identity.application.security.CurrentUser;
import com.medical.platform.prescription.domain.Prescription;
import com.medical.platform.prescription.domain.PrescriptionRepository;
import com.medical.platform.prescription.domain.PrescriptionStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * 核销应用服务（R5-B）：核销、幂等重放、发药记录与处方流转轨迹。
 *
 * <p>幂等实现分三层：
 * <ol>
 *   <li><b>快速重放</b>：事务外先查 {@code idempotency_record}，命中 SUCCESS 且请求摘要一致时直接返回首次响应；</li>
 *   <li><b>行锁串行化</b>：事务内 {@code SELECT ... FOR UPDATE} 锁流转单，再复查一次幂等记录。
 *       并发同 key 请求会在锁上排队，后到者拿到锁时首次事务已提交，于是走重放而不是重复发药；</li>
 *   <li><b>数据库兜底</b>：{@code dispense_record.uk_dispense_circ} 与
 *       {@code idempotency_record.uk_idem} 两个唯一键保证任何实现漏洞都不会落两条记录。</li>
 * </ol>
 *
 * <p>核销成功记录与业务数据在同一事务写入，失败一起回滚；不会留下“业务已发药、幂等记录缺失”的状态。
 */
@Service
public class CirculationRedeemAppService {

    private static final String ROLE_PHARMACIST = "PHARMACIST";
    private static final String ROLE_PATIENT = "PATIENT";
    private static final String ROLE_DOCTOR = "DOCTOR";
    private static final String ROLE_ADMIN = "ADMIN";

    private static final int MIN_KEY_LENGTH = 8;
    private static final int MAX_KEY_LENGTH = 64;

    private final CirculationRepository circulationRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public CirculationRedeemAppService(CirculationRepository circulationRepository,
                                       IdempotencyRepository idempotencyRepository,
                                       PrescriptionRepository prescriptionRepository,
                                       ObjectMapper objectMapper,
                                       PlatformTransactionManager transactionManager) {
        this.circulationRepository = circulationRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 药房核销：{@code ACCEPTED -> REDEEMED}，写 {@code dispense_record}，回写处方为 {@code REDEEMED}。
     *
     * @param idemKey 必填的 {@code Idempotency-Key}，同一动作重试必须复用
     */
    public CirculationView redeem(CurrentUser user,
                                  Long circulationId,
                                  String idemKey,
                                  RedeemCommand command) {
        requirePharmacist(user);
        requireCirculationId(circulationId);
        String key = requireIdempotencyKey(idemKey);
        RedeemCommand normalized = normalize(command);
        String requestHash = requestHash(circulationId, normalized);

        Optional<IdempotencyRecord> existing = findRecord(circulationId, key);
        if (existing.isPresent()) {
            return replay(existing.get(), requestHash);
        }

        try {
            return transactionTemplate.execute(status ->
                    redeemInTransaction(user, circulationId, key, requestHash, normalized));
        } catch (DuplicateKeyException ex) {
            // 极窄竞态：两个不同流转单恰好争用同一幂等键（唯一键兜底）。重读后按重放/冲突处理。
            Optional<IdempotencyRecord> winner = findRecord(circulationId, key);
            if (winner.isPresent()) {
                return replay(winner.get(), requestHash);
            }
            throw new CirculationException(CirculationError.IDEMPOTENCY_CONFLICT);
        }
    }

    private CirculationView redeemInTransaction(CurrentUser user,
                                                Long circulationId,
                                                String key,
                                                String requestHash,
                                                RedeemCommand command) {
        CirculationOrder order = circulationRepository.findByIdForUpdate(circulationId)
                .orElseThrow(() -> new CirculationException(CirculationError.CIRC_NOT_FOUND));

        // 加锁后复查：先到的事务可能已经写入幂等记录。
        Optional<IdempotencyRecord> afterLock = findRecord(circulationId, key);
        if (afterLock.isPresent()) {
            return replay(afterLock.get(), requestHash);
        }

        Long pharmacistPharmacyId = circulationRepository.findPharmacistPharmacyId(user.id())
                .orElseThrow(() -> new CirculationException(CirculationError.RX_SCOPE_DENIED,
                        "当前药师未绑定药房档案，不能核销"));
        if (!order.belongsToPharmacy(pharmacistPharmacyId)) {
            throw new CirculationException(CirculationError.RX_SCOPE_DENIED,
                    "只能核销本药房的流转单");
        }

        LocalDateTime now = LocalDateTime.now();
        int expectedOrderVersion = order.getVersion();
        order.redeem(now);

        Prescription prescription = prescriptionRepository.findById(order.getRxId())
                .orElseThrow(() -> new CirculationException(CirculationError.RX_NOT_FOUND));
        if (prescription.getStatus() != PrescriptionStatus.CIRCULATED) {
            throw new CirculationException(CirculationError.CIRC_STATE_INVALID,
                    "处方当前状态为 " + prescription.getStatus() + "，不能核销");
        }
        int expectedRxVersion = prescription.getVersion();
        prescription.redeem(now);

        if (!circulationRepository.updateRedeemed(order, expectedOrderVersion)) {
            throw new CirculationException(CirculationError.CIRC_VERSION_CONFLICT);
        }
        if (!prescriptionRepository.updateHeader(prescription, expectedRxVersion)) {
            throw new CirculationException(CirculationError.RX_VERSION_CONFLICT);
        }

        DispenseRecord dispense = circulationRepository.insertDispense(DispenseRecord.create(
                order.getId(), user.id(), command.batchNo(), command.evidence(),
                now, order.getCirculationNo()));
        CirculationView view = CirculationView.from(order, dispense);

        idempotencyRepository.insert(IdempotencyRecord.success(
                order.getId(), key, requestHash, snapshotOf(view), now));
        return view;
    }

    /**
     * 处方全链路追踪：患者看自己的、医生看自己开的、药师看本药房已流转的、管理员只读。
     */
    public PrescriptionTraceView trace(CurrentUser user, Long prescriptionId) {
        if (prescriptionId == null || prescriptionId <= 0) {
            throw new CirculationException(CirculationError.RX_NOT_FOUND);
        }
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new CirculationException(CirculationError.RX_NOT_FOUND));
        CirculationOrder order = circulationRepository.findByPrescriptionId(prescriptionId)
                .orElse(null);
        authorizeTrace(user, prescription, order);
        return new PrescriptionTraceView(
                prescription.getId(),
                prescription.getStatus().name(),
                eventsOf(prescription, order));
    }

    private void authorizeTrace(CurrentUser user, Prescription prescription, CirculationOrder order) {
        if (user == null) {
            throw new CirculationException(CirculationError.RX_SCOPE_DENIED);
        }
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
                Long pharmacyId = circulationRepository.findPharmacistPharmacyId(user.id())
                        .orElse(null);
                if (order == null || !order.belongsToPharmacy(pharmacyId)) {
                    throw new CirculationException(CirculationError.RX_SCOPE_DENIED);
                }
            }
            case ROLE_ADMIN -> {
                // 管理员只读监管，允许查看全部轨迹，但不参与核销。
            }
            default -> throw new CirculationException(CirculationError.RX_SCOPE_DENIED);
        }
    }

    private List<PrescriptionTraceView.TraceEventView> eventsOf(Prescription prescription,
                                                               CirculationOrder order) {
        List<PrescriptionTraceView.TraceEventView> events = new ArrayList<>();
        events.add(event(PrescriptionStatus.DRAFT.name(), prescription.getCreatedAt(),
                "医生 #" + prescription.getDoctorId(), "开具处方"));
        if (prescription.getReviewedAt() != null) {
            String reviewStatus = prescription.getStatus() == PrescriptionStatus.REJECTED
                    ? PrescriptionStatus.REJECTED.name()
                    : PrescriptionStatus.APPROVED.name();
            events.add(event(reviewStatus, prescription.getReviewedAt(),
                    "药师 #" + prescription.getReviewerId(),
                    prescription.getStatus() == PrescriptionStatus.REJECTED
                            ? "审核驳回：" + prescription.getRejectReason()
                            : "审核通过"));
        } else if (prescription.getStatus() == PrescriptionStatus.PENDING_REVIEW) {
            events.add(event(PrescriptionStatus.PENDING_REVIEW.name(), prescription.getUpdatedAt(),
                    "医生 #" + prescription.getDoctorId(), "提交审核"));
        } else if (prescription.getStatus() == PrescriptionStatus.CANCELLED
                || prescription.getStatus() == PrescriptionStatus.EXPIRED) {
            events.add(event(prescription.getStatus().name(), prescription.getUpdatedAt(),
                    "系统", "处方已" + (prescription.getStatus() == PrescriptionStatus.CANCELLED
                            ? "撤销" : "过期")));
        }

        if (order != null) {
            events.add(event(CirculationStatus.CREATED.name(), order.getCirculatedAt(),
                    "系统", "流转至药房 #" + order.getPharmacyId()
                            + "（流转单 " + order.getCirculationNo() + "）"));
            if (order.getAcceptedAt() != null) {
                events.add(event(CirculationStatus.ACCEPTED.name(), order.getAcceptedAt(),
                        "药房 #" + order.getPharmacyId(), "药房接方"));
            }
            if (order.getStatus() == CirculationStatus.REJECTED) {
                events.add(event(CirculationStatus.REJECTED.name(), order.getUpdatedAt(),
                        "药房 #" + order.getPharmacyId(), "药房拒接：" + order.getRejectReason()));
            }
            if (order.getRedeemedAt() != null) {
                events.add(event(CirculationStatus.REDEEMED.name(), order.getRedeemedAt(),
                        "药师", "药房核销并发药"));
            }
        }
        return events;
    }

    private PrescriptionTraceView.TraceEventView event(String status,
                                                       LocalDateTime at,
                                                       String actor,
                                                       String note) {
        return new PrescriptionTraceView.TraceEventView(status, at, actor, note);
    }

    private CirculationView replay(IdempotencyRecord record, String requestHash) {
        if (!record.requestHash().equals(requestHash)) {
            throw new CirculationException(CirculationError.IDEMPOTENCY_CONFLICT);
        }
        if (record.status() != IdempotencyStatus.SUCCESS) {
            throw new CirculationException(CirculationError.IDEMPOTENCY_IN_PROGRESS);
        }
        String snapshot = record.responseSnapshot();
        if (snapshot != null && !snapshot.isBlank()) {
            try {
                return objectMapper.readValue(snapshot, CirculationView.class);
            } catch (JsonProcessingException ignored) {
                // 快照格式升级或损坏时退回按库重建，保证重放仍然成功。
            }
        }
        return loadView(record.bizId());
    }

    private CirculationView loadView(Long circulationId) {
        CirculationOrder order = circulationRepository.findById(circulationId)
                .orElseThrow(() -> new CirculationException(CirculationError.CIRC_NOT_FOUND));
        DispenseRecord dispense = circulationRepository.findDispense(circulationId).orElse(null);
        return CirculationView.from(order, dispense);
    }

    private Optional<IdempotencyRecord> findRecord(Long circulationId, String key) {
        return idempotencyRepository.find(IdempotencyRecord.BIZ_REDEEM, circulationId,
                IdempotencyRecord.OP_REDEEM, key);
    }

    private String snapshotOf(CirculationView view) {
        try {
            return objectMapper.writeValueAsString(view);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("核销响应快照序列化失败", ex);
        }
    }

    private String requestHash(Long circulationId, RedeemCommand command) {
        String canonical = "circulationId=" + circulationId
                + "|batchNo=" + nullToEmpty(command.batchNo())
                + "|evidence=" + nullToEmpty(command.evidence());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private void requirePharmacist(CurrentUser user) {
        if (user == null || !ROLE_PHARMACIST.equals(user.roleCode())) {
            throw new CirculationException(CirculationError.SCOPE_DENIED);
        }
    }

    private void requireCirculationId(Long circulationId) {
        if (circulationId == null || circulationId <= 0) {
            throw new CirculationException(CirculationError.CIRC_NOT_FOUND);
        }
    }

    private String requireIdempotencyKey(String idemKey) {
        String key = idemKey == null ? "" : idemKey.trim();
        if (key.isEmpty()) {
            throw new CirculationException(CirculationError.IDEMPOTENCY_KEY_REQUIRED);
        }
        if (key.length() < MIN_KEY_LENGTH || key.length() > MAX_KEY_LENGTH) {
            throw new CirculationException(CirculationError.IDEMPOTENCY_KEY_INVALID);
        }
        return key;
    }

    private RedeemCommand normalize(RedeemCommand command) {
        if (command == null) {
            throw new CirculationException(CirculationError.BAD_REQUEST, "请求体不能为空");
        }
        return new RedeemCommand(trimToNull(command.batchNo()), trimToNull(command.evidence()));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
