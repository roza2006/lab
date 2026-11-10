package com.medical.platform.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证接口测试：登录成功/失败、账号状态、令牌校验与越权尝试。
 *
 * <p>用 H2 内存库（MySQL 兼容模式）跑真实链路：Controller → AuthAppService → MyBatis 仓储 →
 * BCrypt 校验 → JWT 签发 → Security 过滤器鉴权，因此既覆盖业务分支，也覆盖安全边界。
 *
 * <p>对应用例编号见 docs/design/domain-model.md §12.1：FT-01（登录）、ST-01（越权）、ST-02（令牌）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("认证：登录、账号状态、令牌与越权")
class AuthTest {

    private static final String LOGIN = "/api/v1/auth/login";
    private static final String ME = "/api/v1/auth/me";
    private static final String PASSWORD = "Passw0rd!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private String patientHash;

    @BeforeEach
    void seedUsers() {
        patientHash = passwordEncoder.encode(PASSWORD);
        jdbcTemplate.update("DELETE FROM sys_user");
        insertUser(1L, "patient01", patientHash, "PATIENT", 1);
        insertUser(2L, "doctor01", passwordEncoder.encode(PASSWORD), "DOCTOR", 1);
        insertUser(3L, "disabled01", patientHash, "PATIENT", 0);
    }

    @Test
    @DisplayName("登录成功：返回 Bearer 令牌、有效期与角色")
    void loginReturnsBearerToken() throws Exception {
        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("patient01", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.user.username").value("patient01"))
                .andExpect(jsonPath("$.user.roleCode").value("PATIENT"));
    }

    @Test
    @DisplayName("密码错误：401 AUTH_FAILED")
    void loginRejectsWrongPassword() throws Exception {
        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("patient01", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_FAILED"));
    }

    @Test
    @DisplayName("账号不存在：与密码错误返回同一错误码，避免账号枚举")
    void loginDoesNotRevealUnknownAccount() throws Exception {
        MvcResult unknown = mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("nobody", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult wrongPassword = mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("patient01", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String unknownBody = unknown.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String wrongBody = wrongPassword.getResponse().getContentAsString(StandardCharsets.UTF_8);
        // 两条失败路径的响应必须完全一致，否则可被用来探测账号是否存在
        assertThat(unknownBody).isEqualTo(wrongBody);
    }

    @Test
    @DisplayName("账号被禁用：403 ACCOUNT_DISABLED")
    void loginRejectsDisabledAccount() throws Exception {
        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("disabled01", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    @DisplayName("缺少密码：400 VALIDATION_FAILED")
    void loginRejectsBlankPassword() throws Exception {
        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"patient01\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("携带令牌访问 /me：返回本人身份与角色")
    void meReturnsAuthenticatedUser() throws Exception {
        String token = login("patient01");

        mockMvc.perform(get(ME).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("patient01"))
                .andExpect(jsonPath("$.roleCode").value("PATIENT"));
    }

    @Test
    @DisplayName("未携带令牌访问受保护接口：401")
    void meRequiresToken() throws Exception {
        mockMvc.perform(get(ME))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Authorization 头格式错误（缺少 Bearer 前缀）：401")
    void meRejectsMalformedAuthorizationHeader() throws Exception {
        String token = login("patient01");

        mockMvc.perform(get(ME).header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("令牌被篡改：401，不产生任何身份")
    void meRejectsTamperedToken() throws Exception {
        String token = login("patient01");
        String tampered = token.substring(0, token.length() - 3) + "abc";

        mockMvc.perform(get(ME).header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("越权尝试：用自签密钥伪造 ADMIN 令牌被拒绝")
    void forgedAdminTokenIsRejected() throws Exception {
        SecretKey attackerKey = Keys.hmacShaKeyFor(
                "attacker-secret-key-32-bytes-minimum-000000".getBytes(StandardCharsets.UTF_8));
        String forged = Jwts.builder()
                .subject("1")
                .claim("username", "patient01")
                .claim("role", "ADMIN")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(attackerKey)
                .compact();

        mockMvc.perform(get(ME).header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("过期令牌：401 TOKEN_INVALID")
    void meRejectsExpiredToken() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        String expired = Jwts.builder()
                .subject("1")
                .claim("username", "patient01")
                .claim("role", "PATIENT")
                .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(key)
                .compact();

        mockMvc.perform(get(ME).header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("登录响应不含密码哈希等敏感字段")
    void loginResponseHasNoPasswordHash() throws Exception {
        MvcResult result = mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("patient01", PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("passwordHash");
        assertThat(body).doesNotContain(patientHash);
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("accessToken")
                .asText();
    }

    private String loginBody(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("username", username, "password", password));
    }

    private void insertUser(Long id, String username, String passwordHash, String roleCode, int status) {
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, username, password_hash, role_code, status) VALUES (?, ?, ?, ?, ?)",
                id, username, passwordHash, roleCode, status);
    }
}
