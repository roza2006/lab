package com.medical.platform.prescription.interfaces;

import com.medical.platform.identity.application.security.CurrentUser;
import com.medical.platform.prescription.application.PrescriptionPageView;
import com.medical.platform.prescription.application.PrescriptionReviewAppService;
import com.medical.platform.prescription.application.ReviewView;
import com.medical.platform.prescription.interfaces.dto.ReviewRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 药师审核接口：待审队列、审核通过（电子签名）、驳回（必填意见）、审核记录。
 *
 * <p>路径与 {@code docs/design/api-contract.md} §5.4 一致：
 * {@code GET /prescriptions/review-queue}、{@code POST /prescriptions/{id}/reviews/approve}、
 * {@code POST /prescriptions/{id}/reviews/reject}、{@code GET /prescriptions/{id}/reviews}。
 *
 * <p>Controller 不做权限判断，角色与归属校验统一在 {@link PrescriptionReviewAppService}，
 * 避免“接口层放行、服务层假设已鉴权”的漏洞。
 */
@RestController
@RequestMapping("/api/v1/prescriptions")
public class PrescriptionReviewController {

    private final PrescriptionReviewAppService reviewAppService;

    public PrescriptionReviewController(PrescriptionReviewAppService reviewAppService) {
        this.reviewAppService = reviewAppService;
    }

    /** 待审核队列。注意：该字面路径优先于 {@code /prescriptions/{id}} 匹配，不会被当成处方 ID。 */
    @GetMapping("/review-queue")
    public PrescriptionPageView queue(@AuthenticationPrincipal CurrentUser currentUser,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return reviewAppService.queue(currentUser, page, size);
    }

    @PostMapping("/{id}/reviews/approve")
    public ReviewView approve(@AuthenticationPrincipal CurrentUser currentUser,
                              @PathVariable Long id,
                              @Valid @RequestBody ReviewRequest request) {
        return reviewAppService.approve(currentUser, id, request.comment());
    }

    @PostMapping("/{id}/reviews/reject")
    public ReviewView reject(@AuthenticationPrincipal CurrentUser currentUser,
                             @PathVariable Long id,
                             @Valid @RequestBody ReviewRequest request) {
        return reviewAppService.reject(currentUser, id, request.comment());
    }

    @GetMapping("/{id}/reviews")
    public List<ReviewView> history(@AuthenticationPrincipal CurrentUser currentUser,
                                    @PathVariable Long id) {
        return reviewAppService.history(currentUser, id);
    }
}
