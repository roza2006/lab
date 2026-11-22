package com.medical.platform.circulation.interfaces;

import com.medical.platform.circulation.application.CirculationRedeemAppService;
import com.medical.platform.circulation.application.CirculationView;
import com.medical.platform.circulation.application.RedeemCommand;
import com.medical.platform.circulation.interfaces.dto.RedeemRequest;
import com.medical.platform.identity.application.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 药房核销接口（R5-B）。
 *
 * <p>{@code POST /api/v1/circulations/{id}/redeem} 强制 {@code Idempotency-Key}：
 * 同一 key 重试返回首次结果，不会重复发药；缺 key 直接 400。
 *
 * <p>Controller 只做参数绑定，角色/药房归属/状态判断都在
 * {@link CirculationRedeemAppService}，避免“接口层放行、服务层假设已鉴权”。
 */
@RestController
@RequestMapping("/api/v1/circulations")
public class CirculationController {

    private final CirculationRedeemAppService redeemAppService;

    public CirculationController(CirculationRedeemAppService redeemAppService) {
        this.redeemAppService = redeemAppService;
    }

    @PostMapping("/{id}/redeem")
    public CirculationView redeem(@AuthenticationPrincipal CurrentUser currentUser,
                                  @PathVariable Long id,
                                  @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                  @RequestBody(required = false) RedeemRequest request) {
        RedeemCommand command = request == null
                ? new RedeemCommand(null, null)
                : new RedeemCommand(request.batchNo(), request.evidence());
        return redeemAppService.redeem(currentUser, id, idempotencyKey, command);
    }
}
