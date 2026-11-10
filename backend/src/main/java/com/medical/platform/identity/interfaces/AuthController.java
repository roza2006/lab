package com.medical.platform.identity.interfaces;

import com.medical.platform.identity.application.AuthAppService;
import com.medical.platform.identity.application.LoginCommand;
import com.medical.platform.identity.application.UserView;
import com.medical.platform.identity.application.security.CurrentUser;
import com.medical.platform.identity.interfaces.dto.LoginRequest;
import com.medical.platform.identity.interfaces.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthAppService authAppService;

    public AuthController(AuthAppService authAppService) {
        this.authAppService = authAppService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return LoginResponse.from(authAppService.login(
                new LoginCommand(request.username(), request.password())));
    }

    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal CurrentUser currentUser) {
        return new UserView(currentUser.id(), currentUser.username(), currentUser.roleCode());
    }
}
