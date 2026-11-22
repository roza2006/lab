package com.medical.platform.consultation.interfaces;

import com.medical.platform.consultation.application.ConsultationAppService;
import com.medical.platform.consultation.application.ConsultationView;
import com.medical.platform.consultation.application.CreateConsultationCommand;
import com.medical.platform.consultation.application.MessageView;
import com.medical.platform.consultation.application.PageView;
import com.medical.platform.consultation.application.SendMessageCommand;
import com.medical.platform.consultation.interfaces.dto.ConsultationCreateRequest;
import com.medical.platform.consultation.interfaces.dto.MessageSendRequest;
import com.medical.platform.identity.application.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图文问诊接口：发起、列表、详情、接诊、消息、结束。
 *
 * <p>Controller 只做参数绑定与校验，归属判断与状态迁移都下沉到应用服务/聚合，
 * 避免“接口层改状态”绕过不变量。
 */
@RestController
@RequestMapping("/api/v1/consultations")
public class ConsultationController {

    private final ConsultationAppService consultationAppService;

    public ConsultationController(ConsultationAppService consultationAppService) {
        this.consultationAppService = consultationAppService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConsultationView create(@AuthenticationPrincipal CurrentUser currentUser,
                                   @Valid @RequestBody ConsultationCreateRequest request) {
        return consultationAppService.create(currentUser, new CreateConsultationCommand(
                request.department(), request.diseaseName(), request.chiefComplaint()));
    }

    @GetMapping
    public PageView<ConsultationView> list(@AuthenticationPrincipal CurrentUser currentUser,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return consultationAppService.list(currentUser, status, page, size);
    }

    @GetMapping("/{id}")
    public ConsultationView detail(@AuthenticationPrincipal CurrentUser currentUser,
                                   @PathVariable Long id) {
        return consultationAppService.detail(currentUser, id);
    }

    @PostMapping("/{id}/accept")
    public ConsultationView accept(@AuthenticationPrincipal CurrentUser currentUser,
                                   @PathVariable Long id) {
        return consultationAppService.accept(currentUser, id);
    }

    @GetMapping("/{id}/messages")
    public PageView<MessageView> messages(@AuthenticationPrincipal CurrentUser currentUser,
                                          @PathVariable Long id,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return consultationAppService.messages(currentUser, id, page, size);
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageView send(@AuthenticationPrincipal CurrentUser currentUser,
                            @PathVariable Long id,
                            @Valid @RequestBody MessageSendRequest request) {
        return consultationAppService.send(currentUser, id,
                new SendMessageCommand(request.contentType(), request.content()));
    }

    @PostMapping("/{id}/end")
    public ConsultationView end(@AuthenticationPrincipal CurrentUser currentUser,
                                @PathVariable Long id) {
        return consultationAppService.end(currentUser, id);
    }
}
