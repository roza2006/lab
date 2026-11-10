package com.medical.platform.identity.application;

import com.medical.platform.identity.application.port.PasswordHasher;
import com.medical.platform.identity.application.port.TokenIssuer;
import com.medical.platform.identity.domain.User;
import com.medical.platform.identity.domain.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;

@Service
public class AuthAppService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;

    public AuthAppService(UserRepository userRepository,
                          PasswordHasher passwordHasher,
                          TokenIssuer tokenIssuer) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
    }

    public LoginResult login(LoginCommand command) {
        User user = userRepository.findByUsername(command.username())
                .orElseThrow(() -> new BadCredentialsException("用户名或密码错误"));

        if (!user.canLogin()) {
            throw new DisabledException("账号已被禁用");
        }
        if (!passwordHasher.matches(command.password(), user.passwordHash())) {
            // 不区分“用户不存在”和“密码错误”，避免账号枚举
            throw new BadCredentialsException("用户名或密码错误");
        }

        TokenIssuer.IssuedToken token = tokenIssuer.issue(user);
        UserView view = new UserView(user.id(), user.username(), user.roleCode());
        return new LoginResult(token.value(), "Bearer", token.expiresInSeconds(), view);
    }
}
