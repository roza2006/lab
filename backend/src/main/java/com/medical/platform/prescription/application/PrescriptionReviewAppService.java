package com.medical.platform.prescription.application;

import com.medical.platform.identity.application.security.CurrentUser;
import com.medical.platform.prescription.domain.Prescription;
import com.medical.platform.prescription.domain.PrescriptionError;
import com.medical.platform.prescription.domain.PrescriptionException;
import com.medical.platform.prescription.domain.PrescriptionRepository;
import com.medical.platform.prescription.domain.PrescriptionReview;
import com.medical.platform.prescription.domain.PrescriptionStatus;
import com.medical.platform.prescription.domain.ReviewAction;
import com.medical.platform.prescription.domain.ReviewSignature;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 药师审核应用服务：待审队列、通过（电子签名）、驳回（必填意见）、审核记录追溯。
 *
 * <p>职责边界：
 * <ul>
 *   <li>角色与数据范围校验在这一层完成，Controller 只做参数绑定；</li>
 *   <li>状态迁移只调用聚合 {@link Prescription#approve} / {@link Prescription#reject}，
 *       本类不直接改 status；</li>
 *   <li>并发：先取迁移前 version，再用 {@code WHERE id=? AND version=?} 条件更新，
 *       两个药师同时审同一张处方时后到者得到 409 {@code RX_VERSION_CONFLICT}，不会产生两条记录。</li>
 * </ul>
 */
@Service
public class PrescriptionReviewAppService {

    private static final String ROLE_DOCTOR = "DOCTOR";
    private static final String ROLE_PHARMACIST = "PHARMACIST";
    private static final String ROLE_ADMIN = "ADMIN";

    private static final int MAX_PAGE_SIZE = 100;

    private final PrescriptionRepository prescriptionRepository;

    public PrescriptionReviewAppService(PrescriptionRepository prescriptionRepository) {
        this.prescriptionRepository = prescriptionRepository;
    }

    /** 药师待审队列：固定按 PENDING_REVIEW 过滤，按提交时间倒序分页。 */
    public PrescriptionPageView queue(CurrentUser user, int page, int size) {
        requirePharmacist(user);
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        int offset = safePage * safeSize;
        List<Prescription> rows = prescriptionRepository.findAll(
                PrescriptionStatus.PENDING_REVIEW, offset, safeSize);
        long total = prescriptionRepository.countAll(PrescriptionStatus.PENDING_REVIEW);
        return PrescriptionPageView.of(rows.stream().map(PrescriptionView::from).toList(),
                safePage, safeSize, total);
    }

    /** 审核通过：PENDING_REVIEW → APPROVED，写入签名摘要、审核人、审核时间与有效期。 */
    @Transactional
    public ReviewView approve(CurrentUser user, Long id, String comment) {
        requirePharmacist(user);
        Prescription prescription = load(id);
        LocalDateTime now = LocalDateTime.now();

        int expectedVersion = prescription.getVersion();
        String signature = ReviewSignature.of(prescription, user.id(), now);
        prescription.approve(user.id(), signature, now);
        if (!prescriptionRepository.updateHeader(prescription, expectedVersion)) {
            throw new PrescriptionException(PrescriptionError.RX_VERSION_CONFLICT);
        }

        PrescriptionReview review = prescriptionRepository.insertReview(new PrescriptionReview(
                null, id, user.id(), ReviewAction.APPROVED, comment, signature, now));
        return ReviewView.from(review);
    }

    /** 审核驳回：PENDING_REVIEW → REJECTED，理由至少 10 个字（由聚合守卫）。 */
    @Transactional
    public ReviewView reject(CurrentUser user, Long id, String comment) {
        requirePharmacist(user);
        Prescription prescription = load(id);
        LocalDateTime now = LocalDateTime.now();

        int expectedVersion = prescription.getVersion();
        prescription.reject(user.id(), comment, now);
        if (!prescriptionRepository.updateHeader(prescription, expectedVersion)) {
            throw new PrescriptionException(PrescriptionError.RX_VERSION_CONFLICT);
        }

        PrescriptionReview review = prescriptionRepository.insertReview(new PrescriptionReview(
                null, id, user.id(), ReviewAction.REJECTED, comment, null, now));
        return ReviewView.from(review);
    }

    /** 审核记录追溯：开方医生看自己的处方，药师与管理员可查全部（管理员只读监管）。 */
    public List<ReviewView> history(CurrentUser user, Long id) {
        Prescription prescription = load(id);
        String role = user.roleCode();
        boolean allowed = (ROLE_DOCTOR.equals(role) && prescription.isOwnedByDoctor(user.id()))
                || ROLE_PHARMACIST.equals(role)
                || ROLE_ADMIN.equals(role);
        if (!allowed) {
            throw new PrescriptionException(PrescriptionError.RX_SCOPE_DENIED);
        }
        return prescriptionRepository.findReviews(id).stream().map(ReviewView::from).toList();
    }

    private void requirePharmacist(CurrentUser user) {
        if (!ROLE_PHARMACIST.equals(user.roleCode())) {
            throw new PrescriptionException(PrescriptionError.RX_SCOPE_DENIED,
                    "只有药师可以执行处方审核");
        }
    }

    private Prescription load(Long id) {
        if (id == null || id <= 0) {
            throw new PrescriptionException(PrescriptionError.RX_NOT_FOUND);
        }
        return prescriptionRepository.findById(id)
                .orElseThrow(() -> new PrescriptionException(PrescriptionError.RX_NOT_FOUND));
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
