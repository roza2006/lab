package com.medical.platform.circulation.interfaces;

import com.medical.platform.circulation.application.CirculationAppService;
import com.medical.platform.circulation.application.CirculationCreateResult;
import com.medical.platform.circulation.application.CirculationPageView;
import com.medical.platform.circulation.application.CirculationRedeemAppService;
import com.medical.platform.circulation.application.CirculationView;
import com.medical.platform.circulation.application.CreateCirculationCommand;
import com.medical.platform.circulation.application.RedeemCommand;
import com.medical.platform.circulation.interfaces.dto.CreateCirculationRequest;
import com.medical.platform.circulation.interfaces.dto.RedeemRequest;
import com.medical.platform.circulation.interfaces.dto.RejectCirculationRequest;
import com.medical.platform.identity.application.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流转接口：创建、列表、详情、接方、拒接（R5-A）与核销（R5-B）。
 */
@RestController
@RequestMapping("/api/v1/circulations")
public class CirculationController {

    private final CirculationAppService circulationAppService;
    private final CirculationRedeemAppService redeemAppService;

    public CirculationController(CirculationAppService circulationAppService,
                                 CirculationRedeemAppService redeemAppService) {
        this.circulationAppService = circulationAppService;
        this.redeemAppService = redeemAppService;
    }

    @PostMapping
    public ResponseEntity<CirculationView> create(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateCirculationRequest request) {
        CirculationCreateResult result = circulationAppService.create(currentUser,
                new CreateCirculationCommand(request.rxId(), request.pharmacyId(), idempotencyKey));
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.view());
    }

    @GetMapping
    public CirculationPageView list(@AuthenticationPrincipal CurrentUser currentUser,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return circulationAppService.list(currentUser, status, page, size);
    }

    @GetMapping("/{id}")
    public CirculationView detail(@AuthenticationPrincipal CurrentUser currentUser,
                                  @PathVariable Long id) {
        return circulationAppService.detail(currentUser, id);
    }

    @PostMapping("/{id}/accept")
    public CirculationView accept(@AuthenticationPrincipal CurrentUser currentUser,
                                  @PathVariable Long id) {
        return circulationAppService.accept(currentUser, id);
    }

    @PostMapping("/{id}/reject")
    public CirculationView reject(@AuthenticationPrincipal CurrentUser currentUser,
                                  @PathVariable Long id,
                                  @RequestBody(required = false) RejectCirculationRequest request) {
        return circulationAppService.reject(currentUser, id, request == null ? null : request.reason());
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
