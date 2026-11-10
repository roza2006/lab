package com.medical.platform.consultation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 问诊会话接口测试：发起、接诊、越权与结束后的写保护。
 *
 * <p><b>当前状态</b>：问诊上下文（R3-B）的实现尚未合入 main，因此业务用例暂时
 * {@link Disabled}，只保留已经生效的安全边界用例；实现合入后逐个去掉 {@code @Disabled}
 * 并补齐会话种子数据即可启用，断言按《实验报告》§5 的接口清单与
 * {@code docs/design/domain-model.md} §11 的错误码编写。
 *
 * <p>用例编号：FT-02（发起/接诊）、ST-03（越权）、SM-02（结束后不可写）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("问诊：发起、接诊与越权")
class ConsultationTest {

    private static final String SESSIONS = "/api/v1/consultations";
    private static final String PASSWORD = "Passw0rd!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedUsers() {
        String hash = passwordEncoder.encode(PASSWORD);
        jdbcTemplate.update("DELETE FROM sys_user");
        insertUser(1L, "patient01", hash, "PATIENT", 1);
        insertUser(2L, "patient02", hash, "PATIENT", 1);
        insertUser(3L, "doctor01", hash, "DOCTOR", 1);
        insertUser(4L, "doctor02", hash, "DOCTOR", 1);
    }

    // ------------------------------------------------------------------
    // 已生效的安全边界：任何未认证请求都不应进入业务逻辑
    // ------------------------------------------------------------------

    @Test
    @DisplayName("未携带令牌发起问诊：401，不创建任何会话")
    void unauthenticatedCreateIsRejected() throws Exception {
        mockMvc.perform(post(SESSIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":1,\"chiefComplaint\":\"咳嗽三天\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("令牌签名无效：401，按未认证处理")
    void invalidSignatureIsRejected() throws Exception {
        mockMvc.perform(post(SESSIONS)
                        .header("Authorization", "Bearer not-a-real-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("未携带令牌接诊：401")
    void unauthenticatedAcceptIsRejected() throws Exception {
        mockMvc.perform(post(SESSIONS + "/1/accept"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("未携带令牌查看会话消息：401")
    void unauthenticatedMessageReadIsRejected() throws Exception {
        mockMvc.perform(get(SESSIONS + "/1/messages"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // 待问诊实现合入后启用
    // ------------------------------------------------------------------

    @Test
    @Disabled("等待 R3-B 问诊实现合入 main 后启用")
    @DisplayName("患者发起问诊：返回 WAITING 状态会话与候诊号")
    void patientCreatesSession() throws Exception {
        mockMvc.perform(post(SESSIONS)
                        .header("Authorization", "Bearer " + login("patient01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":1,\"doctorId\":3,\"chiefComplaint\":\"咳嗽三天，夜间加重\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.patientId").value(1));
    }

    @Test
    @Disabled("等待 R3-B 问诊实现合入 main 后启用")
    @DisplayName("医生接诊：会话进入 IN_PROGRESS 并写入接诊医生")
    void doctorAcceptsWaitingSession() throws Exception {
        String sessionId = createSessionFor("patient01");

        mockMvc.perform(post(SESSIONS + "/" + sessionId + "/accept")
                        .header("Authorization", "Bearer " + login("doctor01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.doctorId").value(3));
    }

    @Test
    @Disabled("等待 R3-B 问诊实现合入 main 后启用")
    @DisplayName("越权：患者调用接诊接口返回 403")
    void patientCannotAcceptSession() throws Exception {
        String sessionId = createSessionFor("patient01");

        mockMvc.perform(post(SESSIONS + "/" + sessionId + "/accept")
                        .header("Authorization", "Bearer " + login("patient01")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCOPE_DENIED"));
    }

    @Test
    @Disabled("等待 R3-B 问诊实现合入 main 后启用")
    @DisplayName("越权：医生接诊他人已接诊的会话返回 409 而不是覆盖")
    void doctorCannotTakeOverAcceptedSession() throws Exception {
        String sessionId = createSessionFor("patient01");
        accept(sessionId, "doctor01");

        mockMvc.perform(post(SESSIONS + "/" + sessionId + "/accept")
                        .header("Authorization", "Bearer " + login("doctor02")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_ALREADY_ACCEPTED"));
    }

    @Test
    @Disabled("等待 R3-B 问诊实现合入 main 后启用")
    @DisplayName("越权：患者读取他人会话返回 403")
    void patientCannotReadAnotherPatientsSession() throws Exception {
        String sessionId = createSessionFor("patient02");

        mockMvc.perform(get(SESSIONS + "/" + sessionId)
                        .header("Authorization", "Bearer " + login("patient01")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCOPE_DENIED"));
    }

    @Test
    @Disabled("等待 R3-B 问诊实现合入 main 后启用")
    @DisplayName("结束后的会话不可再发消息：409 SESSION_CLOSED")
    void endedSessionRejectsNewMessage() throws Exception {
        String sessionId = createSessionFor("patient01");
        accept(sessionId, "doctor01");
        mockMvc.perform(post(SESSIONS + "/" + sessionId + "/end")
                        .header("Authorization", "Bearer " + login("doctor01")))
                .andExpect(status().isOk());

        mockMvc.perform(post(SESSIONS + "/" + sessionId + "/messages")
                        .header("Authorization", "Bearer " + login("patient01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"TEXT\",\"content\":\"还在吗\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_CLOSED"));
    }

    private String createSessionFor(String patientUsername) throws Exception {
        MvcResult result = mockMvc.perform(post(SESSIONS)
                        .header("Authorization", "Bearer " + login(patientUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":1,\"chiefComplaint\":\"复诊开药\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("id")
                .asText();
    }

    private void accept(String sessionId, String doctorUsername) throws Exception {
        mockMvc.perform(post(SESSIONS + "/" + sessionId + "/accept")
                        .header("Authorization", "Bearer " + login(doctorUsername)))
                .andExpect(status().isOk());
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("accessToken")
                .asText();
    }

    private void insertUser(Long id, String username, String passwordHash, String roleCode, int status) {
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, username, password_hash, role_code, status) VALUES (?, ?, ?, ?, ?)",
                id, username, passwordHash, roleCode, status);
    }
}
