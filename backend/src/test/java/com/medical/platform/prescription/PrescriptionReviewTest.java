package com.medical.platform.prescription;

import com.fasterxml.jackson.databind.JsonNode;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 处方审核接口测试（R4-B / 李梓语）：通过、驳回、越权与审核记录追溯。
 *
 * <p>覆盖 {@code docs/design/api-contract.md} §5.4 的四个端点，以及
 * {@code docs/design/domain-model.md} §11 的错误码：
 * 通过写签名摘要、驳回理由不足 10 字返回 422、非药师操作返回 403、审核后状态落库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("处方审核：通过、驳回与越权")
class PrescriptionReviewTest {

    private static final String PASSWORD = "Passw0rd!";
    private static final String REVIEW_QUEUE = "/api/v1/prescriptions/review-queue";

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
        jdbcTemplate.update("DELETE FROM prescription_review");
        jdbcTemplate.update("DELETE FROM prescription_item");
        jdbcTemplate.update("DELETE FROM prescription");
        jdbcTemplate.update("DELETE FROM consultation_message");
        jdbcTemplate.update("DELETE FROM consultation_session");
        jdbcTemplate.update("DELETE FROM sys_user");
        insertUser(1L, "patient01", hash, "PATIENT");
        insertUser(2L, "doctor01", hash, "DOCTOR");
        insertUser(3L, "doctor02", hash, "DOCTOR");
        insertUser(4L, "pharmacist01", hash, "PHARMACIST");
        insertUser(5L, "pharmacist02", hash, "PHARMACIST");
    }

    @Test
    @DisplayName("药师通过：写签名摘要、状态变 APPROVED、审核记录可追溯")
    void pharmacistApprovesWithSignature() throws Exception {
        String patient = login("patient01");
        String doctor = login("doctor01");
        String pharmacist = login("pharmacist01");
        long prescriptionId = submittedPrescription(patient, doctor);

        mockMvc.perform(get(REVIEW_QUEUE).header("Authorization", bearer(pharmacist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(prescriptionId))
                .andExpect(jsonPath("$.content[0].status").value("PENDING_REVIEW"));

        mockMvc.perform(post("/api/v1/prescriptions/{id}/reviews/approve", prescriptionId)
                        .header("Authorization", bearer(pharmacist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"用法用量适宜，审核通过\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("APPROVED"))
                .andExpect(jsonPath("$.pharmacistId").value(4))
                .andExpect(jsonPath("$.signature").value(org.hamcrest.Matchers.startsWith("SIG-")));

        assertThat(statusOf(prescriptionId)).isEqualTo("APPROVED");
        assertThat(signedHashOf(prescriptionId)).startsWith("SIG-");

        mockMvc.perform(get("/api/v1/prescriptions/{id}/reviews", prescriptionId)
                        .header("Authorization", bearer(doctor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("APPROVED"))
                .andExpect(jsonPath("$[0].comment").value("用法用量适宜，审核通过"));

        // 通过后不再出现在待审队列
        mockMvc.perform(get(REVIEW_QUEUE).header("Authorization", bearer(pharmacist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("驳回理由不足 10 字：422 RX_REJECT_REASON_INVALID，状态不变")
    void rejectWithShortReasonIsRejected() throws Exception {
        String patient = login("patient01");
        String doctor = login("doctor01");
        String pharmacist = login("pharmacist01");
        long prescriptionId = submittedPrescription(patient, doctor);

        mockMvc.perform(post("/api/v1/prescriptions/{id}/reviews/reject", prescriptionId)
                        .header("Authorization", bearer(pharmacist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"不合适\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RX_REJECT_REASON_INVALID"));

        assertThat(statusOf(prescriptionId)).isEqualTo("PENDING_REVIEW");
    }

    @Test
    @DisplayName("药师驳回：状态 REJECTED 并落驳回理由与审核记录")
    void pharmacistRejectsWithReason() throws Exception {
        String patient = login("patient01");
        String doctor = login("doctor01");
        String pharmacist = login("pharmacist02");
        long prescriptionId = submittedPrescription(patient, doctor);

        String reason = "药品剂量超过说明书上限，请下调后重新提交";
        mockMvc.perform(post("/api/v1/prescriptions/{id}/reviews/reject", prescriptionId)
                        .header("Authorization", bearer(pharmacist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("comment", reason))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("REJECTED"))
                .andExpect(jsonPath("$.signature").doesNotExist());

        assertThat(statusOf(prescriptionId)).isEqualTo("REJECTED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT reject_reason FROM prescription WHERE id = ?", String.class, prescriptionId))
                .isEqualTo(reason);

        mockMvc.perform(get("/api/v1/prescriptions/{id}/reviews", prescriptionId)
                        .header("Authorization", bearer(doctor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("REJECTED"))
                .andExpect(jsonPath("$[0].comment").value(reason));
    }

    @Test
    @DisplayName("越权：医生不能审核，患者不能看待审队列")
    void nonPharmacistCannotReview() throws Exception {
        String patient = login("patient01");
        String doctor = login("doctor01");
        long prescriptionId = submittedPrescription(patient, doctor);

        mockMvc.perform(post("/api/v1/prescriptions/{id}/reviews/approve", prescriptionId)
                        .header("Authorization", bearer(doctor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"自己审自己开的处方\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RX_SCOPE_DENIED"));

        mockMvc.perform(get(REVIEW_QUEUE).header("Authorization", bearer(patient)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RX_SCOPE_DENIED"));
    }

    @Test
    @DisplayName("未认证访问审核端点：401，不读取业务数据")
    void unauthenticatedReviewIsRejected() throws Exception {
        mockMvc.perform(get(REVIEW_QUEUE)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/prescriptions/1/reviews/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"未登录\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private long submittedPrescription(String patientToken, String doctorToken) throws Exception {
        MvcResult sessionResult = mockMvc.perform(post("/api/v1/consultations")
                        .header("Authorization", bearer(patientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"department\":\"内分泌科\",\"diseaseName\":\"2 型糖尿病\","
                                + "\"chiefComplaint\":\"血糖控制不佳\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long sessionId = body(sessionResult).get("id").asLong();

        mockMvc.perform(post("/api/v1/consultations/{id}/accept", sessionId)
                        .header("Authorization", bearer(doctorToken)))
                .andExpect(status().isOk());

        String items = "[{\"drugCode\":\"D001\",\"drugName\":\"二甲双胍片\",\"specification\":\"0.5g*30\","
                + "\"dosage\":\"0.5g\",\"frequency\":\"bid\",\"duration\":\"14 天\","
                + "\"quantity\":2,\"unitPrice\":12.50}]";
        MvcResult prescriptionResult = mockMvc.perform(
                        post("/api/v1/consultations/{id}/prescriptions", sessionId)
                                .header("Authorization", bearer(doctorToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"diagnosis\":\"2 型糖尿病\",\"items\":" + items + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        long prescriptionId = body(prescriptionResult).get("id").asLong();

        mockMvc.perform(post("/api/v1/prescriptions/{id}/submit", prescriptionId)
                        .header("Authorization", bearer(doctorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        return prescriptionId;
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return body(result).get("accessToken").asText();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String statusOf(long prescriptionId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM prescription WHERE id = ?", String.class, prescriptionId);
    }

    private String signedHashOf(long prescriptionId) {
        return jdbcTemplate.queryForObject(
                "SELECT signed_hash FROM prescription WHERE id = ?", String.class, prescriptionId);
    }

    private void insertUser(Long id, String username, String hash, String roleCode) {
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password_hash, role_code, status) "
                + "VALUES (?, ?, ?, ?, ?)", id, username, hash, roleCode, 1);
    }
}
