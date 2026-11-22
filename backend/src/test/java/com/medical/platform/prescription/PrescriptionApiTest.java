package com.medical.platform.prescription;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 处方开方链路：开方、改草稿、提交、越权读取。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("处方：开方、提交与数据范围")
class PrescriptionApiTest {

    private static final String BASE = "/api/v1";
    private static final String PASSWORD = "Passw0rd!";
    private static final String RX_BODY = """
            {
              "diagnosis": "2 型糖尿病",
              "items": [
                {"drugCode": "DRUG001", "drugName": "盐酸二甲双胍片", "specification": "0.5g*20 片",
                 "dosage": "0.5g", "frequency": "每日 2 次", "duration": "30 天",
                 "quantity": 2, "unitPrice": 15.00},
                {"drugCode": "DRUG002", "drugName": "阿卡波糖片", "specification": "50mg*30 片",
                 "dosage": "50mg", "frequency": "每日 3 次", "duration": "30 天",
                 "quantity": 1, "unitPrice": 28.00}
              ]
            }
            """;
    private static final String RX_BODY_ONE_ITEM = """
            {
              "diagnosis": "2 型糖尿病（调整）",
              "items": [
                {"drugCode": "DRUG002", "drugName": "阿卡波糖片", "specification": "50mg*30 片",
                 "dosage": "50mg", "frequency": "每日 3 次", "duration": "30 天",
                 "quantity": 1, "unitPrice": 28.00}
              ]
            }
            """;

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
        jdbcTemplate.update("DELETE FROM prescription_item");
        jdbcTemplate.update("DELETE FROM prescription");
        jdbcTemplate.update("DELETE FROM consultation_message");
        jdbcTemplate.update("DELETE FROM consultation_session");
        jdbcTemplate.update("DELETE FROM sys_user");

        String hash = passwordEncoder.encode(PASSWORD);
        insertUser(1L, "patient01", hash, "PATIENT");
        insertUser(2L, "doctor01", hash, "DOCTOR");
        insertUser(3L, "doctor02", hash, "DOCTOR");
    }

    @Test
    @DisplayName("医生开方后提交，患者可读，其他医生不可读")
    void createSubmitAndReadScope() throws Exception {
        String sessionId = acceptedSession();
        String rxId = createPrescription(sessionId, "doctor01");

        mockMvc.perform(post(BASE + "/prescriptions/" + rxId + "/submit")
                        .header("Authorization", "Bearer " + login("doctor01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(get(BASE + "/prescriptions/" + rxId)
                        .header("Authorization", "Bearer " + login("patient01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));

        mockMvc.perform(get(BASE + "/prescriptions/" + rxId)
                        .header("Authorization", "Bearer " + login("doctor02")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RX_SCOPE_DENIED"));
    }

    @Test
    @DisplayName("草稿可整体替换明细，版本递增")
    void updateDraftReplacesItems() throws Exception {
        String rxId = createPrescription(acceptedSession(), "doctor01");

        mockMvc.perform(put(BASE + "/prescriptions/" + rxId)
                        .header("Authorization", "Bearer " + login("doctor01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RX_BODY_ONE_ITEM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].drugCode").value("DRUG002"));
    }

    @Test
    @DisplayName("患者不能开方")
    void patientCannotCreatePrescription() throws Exception {
        String sessionId = acceptedSession();

        mockMvc.perform(post(BASE + "/consultations/" + sessionId + "/prescriptions")
                        .header("Authorization", "Bearer " + login("patient01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RX_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RX_SCOPE_DENIED"));
    }

    @Test
    @DisplayName("重复提交返回 RX_STATE_INVALID")
    void resubmitIsRejected() throws Exception {
        String rxId = createPrescription(acceptedSession(), "doctor01");
        mockMvc.perform(post(BASE + "/prescriptions/" + rxId + "/submit")
                        .header("Authorization", "Bearer " + login("doctor01")))
                .andExpect(status().isOk());

        mockMvc.perform(post(BASE + "/prescriptions/" + rxId + "/submit")
                        .header("Authorization", "Bearer " + login("doctor01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RX_STATE_INVALID"));
    }

    private String acceptedSession() throws Exception {
        MvcResult created = mockMvc.perform(post(BASE + "/consultations")
                        .header("Authorization", "Bearer " + login("patient01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"department\":\"内科\",\"diseaseName\":\"2 型糖尿病\",\"chiefComplaint\":\"复诊开药\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String sessionId = objectMapper.readTree(created.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("id").asText();

        mockMvc.perform(post(BASE + "/consultations/" + sessionId + "/accept")
                        .header("Authorization", "Bearer " + login("doctor01")))
                .andExpect(status().isOk());
        return sessionId;
    }

    private String createPrescription(String sessionId, String doctorUsername) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE + "/consultations/" + sessionId + "/prescriptions")
                        .header("Authorization", "Bearer " + login(doctorUsername))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RX_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("id").asText();
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE + "/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("accessToken").asText();
    }

    private void insertUser(Long id, String username, String passwordHash, String roleCode) {
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, username, password_hash, role_code, status) VALUES (?, ?, ?, ?, 1)",
                id, username, passwordHash, roleCode);
    }
}
