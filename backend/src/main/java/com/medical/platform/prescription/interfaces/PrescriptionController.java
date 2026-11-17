package com.medical.platform.prescription.interfaces;

import com.medical.platform.identity.application.security.CurrentUser;
import com.medical.platform.prescription.application.PrescriptionAppService;
import com.medical.platform.prescription.application.PrescriptionPageView;
import com.medical.platform.prescription.application.PrescriptionView;
import com.medical.platform.prescription.application.SavePrescriptionCommand;
import com.medical.platform.prescription.interfaces.dto.PrescriptionSaveRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 处方接口：开方、改草稿、提交、撤销与查询。审核接口由 R4-B 接入。
 */
@RestController
@RequestMapping("/api/v1")
public class PrescriptionController {

    private final PrescriptionAppService prescriptionAppService;

    public PrescriptionController(PrescriptionAppService prescriptionAppService) {
        this.prescriptionAppService = prescriptionAppService;
    }

    @PostMapping("/consultations/{id}/prescriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public PrescriptionView create(@AuthenticationPrincipal CurrentUser currentUser,
                                   @PathVariable Long id,
                                   @Valid @RequestBody PrescriptionSaveRequest request) {
        return prescriptionAppService.create(currentUser, id, toCommand(request));
    }

    @PutMapping("/prescriptions/{id}")
    public PrescriptionView update(@AuthenticationPrincipal CurrentUser currentUser,
                                   @PathVariable Long id,
                                   @Valid @RequestBody PrescriptionSaveRequest request) {
        return prescriptionAppService.update(currentUser, id, toCommand(request));
    }

    @PostMapping("/prescriptions/{id}/submit")
    public PrescriptionView submit(@AuthenticationPrincipal CurrentUser currentUser,
                                   @PathVariable Long id) {
        return prescriptionAppService.submit(currentUser, id);
    }

    @PostMapping("/prescriptions/{id}/cancel")
    public PrescriptionView cancel(@AuthenticationPrincipal CurrentUser currentUser,
                                   @PathVariable Long id) {
        return prescriptionAppService.cancel(currentUser, id);
    }

    @GetMapping("/prescriptions")
    public PrescriptionPageView list(@AuthenticationPrincipal CurrentUser currentUser,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return prescriptionAppService.list(currentUser, status, page, size);
    }

    @GetMapping("/prescriptions/{id}")
    public PrescriptionView detail(@AuthenticationPrincipal CurrentUser currentUser,
                                   @PathVariable Long id) {
        return prescriptionAppService.detail(currentUser, id);
    }

    private SavePrescriptionCommand toCommand(PrescriptionSaveRequest request) {
        return new SavePrescriptionCommand(
                request.diagnosis(),
                request.items().stream()
                        .map(item -> new SavePrescriptionCommand.Item(
                                item.drugCode(),
                                item.drugName(),
                                item.specification(),
                                item.dosage(),
                                item.frequency(),
                                item.duration(),
                                item.quantity(),
                                item.unitPrice()))
                        .toList());
    }
}
