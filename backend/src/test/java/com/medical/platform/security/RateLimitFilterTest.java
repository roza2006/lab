package com.medical.platform.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 限流过滤器测试（R6-B / 李梓语）：登录按 IP 的固定窗口计数，超限返回 429。
 *
 * <p>默认测试配置关闭限流，避免其他用例的大量登录被计数；本类用独立 properties 打开，
 * 并把阈值压到 2 次/分钟，属于新 Spring 上下文，不影响其他测试类。
 */
@SpringBootTest(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.login-per-minute=2",
        "app.rate-limit.message-per-minute=100",
        "app.rate-limit.global-per-minute=1000"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("限流：登录失败次数超限返回 429")
class RateLimitFilterTest {

    private static final String LOGIN = "/api/v1/auth/login";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("同一 IP 前两次失败返回 401，第三次返回 429 RATE_LIMITED")
    void loginAttemptsAreLimitedPerIp() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post(LOGIN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"nobody\",\"password\":\"wrong-password\"}"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.status").value(429));
    }
}
