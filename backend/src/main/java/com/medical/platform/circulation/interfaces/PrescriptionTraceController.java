package com.medical.platform.circulation.interfaces;

import com.medical.platform.circulation.application.CirculationRedeemAppService;
import com.medical.platform.circulation.application.PrescriptionTraceView;
import com.medical.platform.identity.application.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 处方全链路追踪（R5-B）：展示“开方 → 审核 → 流转 → 接方 → 核销”的时间线。
 *
 * <p>路径供患者端处方详情与药师端核销页共用，权限在应用层按角色与数据范围收敛。
 */
@RestController
@RequestMapping("/api/v1/prescriptions")
public class PrescriptionTraceController {

    private final CirculationRedeemAppService redeemAppService;

    public PrescriptionTraceController(CirculationRedeemAppService redeemAppService) {
        this.redeemAppService = redeemAppService;
    }

    @GetMapping("/{id}/trace")
    public PrescriptionTraceView trace(@AuthenticationPrincipal CurrentUser currentUser,
                                       @PathVariable Long id) {
        return redeemAppService.trace(currentUser, id);
    }
}
