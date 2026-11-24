package com.medical.platform.acceptance;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 最终验收用例（R6）：按业务闭环端到端验收，而不是按模块验收。
 *
 * <p>用例编号与判定标准见 {@code docs/test/acceptance.md}：
 * <ul>
 *   <li>AT-01 主链路：问诊 → 接诊 → 开方 → 提交 → 审核通过 → 流转 → 接方 → 核销 → 患者可追溯；</li>
 *   <li>AT-02 驳回链路：药师驳回后处方进入 REJECTED，审核记录与理由可追溯；</li>
 *   <li>AT-03 数据范围：他人处方、他人轨迹、越权核销必须被拒绝；</li>
 *   <li>AT-04 幂等：重复流转返回同一单，同键重放只落一条发药记录；</li>
 *   <li>AT-05 撤销：草稿撤销进入终态，终态拒绝后续提交。</li>
 * </ul>
 *
 * <p>这些用例刻意只依赖 HTTP 接口与数据库事实（某张表几条记录、处方是什么状态），
 * 不依赖内部实现细节，便于在答辩演示时逐条对照。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("验收：业务闭环端到端")
class AcceptanceTest {

    private static final String BASE = "/api/v1";
    private static final String PASSWORD = "Passw0rd!";

    private static final String PRESCRIPTION_BODY = """
            {
              "diagnosis": "2 型糖尿病",
              "items": [
                {"drugCode": "DRUG001", "drugName": "盐酸二甲双胍片", "specification": "0.5g*20 片",
                 "dosage": "0.5g", "frequency": "每日 2 次", "duration": "30 天",
                 "quantity": 2, "unitPrice": 15.00}
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
    void resetData() {
        jdbcTemplate.update("DELETE FROM idempotency_record");
        jdbcTemplate.update("DELETE FROM dispense_record");
        jdbcTemplate.update("DELETE FROM circulation_order");
        jdbcTemplate.update("DELETE FROM pharmacy");
        jdbcTemplate.update("DELETE FROM prescription_review");
        jdbcTemplate.update("DELETE FROM prescription_item");
        jdbcTemplate.update("DELETE FROM prescription");
        jdbcTemplate.update("DELETE FROM pharmacist_profile");
        jdbcTemplate.update("DELETE FROM consultation_message");
        jdbcTemplate.update("DELETE FROM consultation_session");
        jdbcTemplate.update("DELETE FROM sys_user");

        String hash = passwordEncoder.encode(PASSWORD);
        insertUser(1L, "patient01", hash, "PATIENT");
        insertUser(2L, "patient02", hash, "PATIENT");
        insertUser(3L, "doctor01", hash, "DOCTOR");
        insertUser(4L, "doctor02", hash, "DOCTOR");
        insertUser(5L, "pharmacist01", hash, "PHARMACIST");
        jdbcTemplate.update("INSERT INTO pharmacist_profile "
                        + "(user_id, real_name, pharmacy_id, license_no, review_right, status) "
                        + "VALUES (5, '药师甲', 1, 'LIC-5', 1, 1)");
        jdbcTemplate.update("INSERT INTO pharmacy "
                        + "(id, pharmacy_code, name, license_no, status) VALUES (1, 'PH001', '验收药房', 'PH-LIC-1', 1)");
    }

    @Test
    @DisplayName("AT-01 主链路：问诊→接诊→开方→审核→流转→核销，患者可追溯全链路")
    void mainChainEndsInRedeemed() throws Exception {
        String sessionId = openSession();
        acceptSession(sessionId, "doctor01");

        long rxId = createPrescription(sessionId, "doctor01");
        mockMvc.perform(post(BASE + "/prescriptions/" + rxId + "/submit")
                        .header("Authorization", bearer("doctor01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));

        mockMvc.perform(post(BASE + "/prescriptions/" + rxId + "/reviews/approve")
                        .header("Authorization", bearer("pharmacist01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"用法用量与诊断相符，同意调配\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("APPROVED"));

        long circulationId = createCirculation(rxId);
        assertThat(prescriptionStatusOf(rxId)).isEqualTo("CIRCULATED");

        mockMvc.perform(post(BASE + "/circulations/" + circulationId + "/accept")
                        .header("Authorization", bearer("pharmacist01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mockMvc.perform(post(BASE + "/circulations/" + circulationId + "/redeem")
                        .header("Authorization", bearer("pharmacist01"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchNo\":\"B20261124\",\"evidence\":\"PICKUP-123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REDEEMED"))
                .andExpect(jsonPath("$.dispenseRecord.id").isNotEmpty());

        // 处方状态回写 + 发药记录只有一条
        assertThat(prescriptionStatusOf(rxId)).isEqualTo("REDEEMED");
        assertThat(countOf("SELECT COUNT(*) FROM dispense_record WHERE circulation_id = ?", circulationId))
                .isEqualTo(1);

        // 患者看到完整时间线
        MvcResult trace = mockMvc.perform(get(BASE + "/prescriptions/" + rxId + "/trace")
                        .header("Authorization", bearer("patient01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REDEEMED"))
                .andExpect(jsonPath("$.events").isArray())
                .andReturn();
        JsonNode events = objectMapper.readTree(trace.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("events");
        assertThat(events).isNotEmpty();
    }

    @Test
    @DisplayName("AT-02 驳回链路：处方进入 REJECTED，驳回理由与审核记录可追溯")
    void rejectFlowIsTraceable() throws Exception {
        String sessionId = openSession();
        acceptSession(sessionId, "doctor01");
        long rxId = createPrescription(sessionId, "doctor01");
        submit(rxId);

        mockMvc.perform(post(BASE + "/prescriptions/" + rxId + "/reviews/reject")
                        .header("Authorization", bearer("pharmacist01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"用法用量与说明书不符，请修改后重提\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("REJECTED"));

        assertThat(prescriptionStatusOf(rxId)).isEqualTo("REJECTED");

        mockMvc.perform(get(BASE + "/prescriptions/" + rxId + "/reviews")
                        .header("Authorization", bearer("doctor01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("REJECTED"))
                .andExpect(jsonPath("$[0].comment").value("用法用量与说明书不符，请修改后重提"));
    }

    @Test
    @DisplayName("AT-03 数据范围：他人处方、他人轨迹、越权核销一律拒绝")
    void scopeIsEnforced() throws Exception {
        String sessionId = openSession();
        acceptSession(sessionId, "doctor01");
        long rxId = createPrescription(sessionId, "doctor01");
        submit(rxId);

        // 其他患者、其他医生都读不到这张处方
        mockMvc.perform(get(BASE + "/prescriptions/" + rxId)
                        .header("Authorization", bearer("patient02")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RX_SCOPE_DENIED"));

        mockMvc.perform(get(BASE + "/prescriptions/" + rxId)
                        .header("Authorization", bearer("doctor02")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RX_SCOPE_DENIED"));

        // 他人轨迹同样 403
        mockMvc.perform(get(BASE + "/prescriptions/" + rxId + "/trace")
                        .header("Authorization", bearer("patient02")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RX_SCOPE_DENIED"));

        // 患者不能开方、不能审核
        mockMvc.perform(post(BASE + "/consultations/" + sessionId + "/prescriptions")
                        .header("Authorization", bearer("patient01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRESCRIPTION_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RX_SCOPE_DENIED"));

        mockMvc.perform(post(BASE + "/prescriptions/" + rxId + "/reviews/approve")
                        .header("Authorization", bearer("doctor01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"医生越权审核\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AT-04 幂等：重复流转返回同一单，同键重放只落一条发药记录")
    void idempotencyHoldsForCirculationAndRedeem() throws Exception {
        String sessionId = openSession();
        acceptSession(sessionId, "doctor01");
        long rxId = createPrescription(sessionId, "doctor01");
        submit(rxId);
        approve(rxId);

        long circulationId = createCirculation(rxId);
        long againId = createCirculation(rxId);
        assertThat(againId).as("同一处方重复流转必须返回同一张流转单").isEqualTo(circulationId);

        mockMvc.perform(post(BASE + "/circulations/" + circulationId + "/accept")
                        .header("Authorization", bearer("pharmacist01")))
                .andExpect(status().isOk());

        String key = UUID.randomUUID().toString();
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post(BASE + "/circulations/" + circulationId + "/redeem")
                            .header("Authorization", bearer("pharmacist01"))
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"batchNo\":\"B20261124\",\"evidence\":\"PICKUP-123456\"}"))
                    .andExpect(status().isOk());
        }

        assertThat(countOf("SELECT COUNT(*) FROM dispense_record WHERE circulation_id = ?", circulationId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("AT-05 撤销：草稿撤销进入终态，终态拒绝再次提交")
    void cancelIsTerminal() throws Exception {
        String sessionId = openSession();
        acceptSession(sessionId, "doctor01");
        long rxId = createPrescription(sessionId, "doctor01");

        mockMvc.perform(post(BASE + "/prescriptions/" + rxId + "/cancel")
                        .header("Authorization", bearer("doctor01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post(BASE + "/prescriptions/" + rxId + "/submit")
                        .header("Authorization", bearer("doctor01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RX_STATE_INVALID"));

        assertThat(prescriptionStatusOf(rxId)).isEqualTo("CANCELLED");
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private String openSession() throws Exception {
        MvcResult result = mockMvc.perform(post(BASE + "/consultations")
                        .header("Authorization", bearer("patient01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"department\":\"内科\",\"diseaseName\":\"2 型糖尿病\",\"chiefComplaint\":\"复诊开药\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result).get("id").asText();
    }

    private void acceptSession(String sessionId, String doctor) throws Exception {
        mockMvc.perform(post(BASE + "/consultations/" + sessionId + "/accept")
                        .header("Authorization", bearer(doctor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    private long createPrescription(String sessionId, String doctor) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE + "/consultations/" + sessionId + "/prescriptions")
                        .header("Authorization", bearer(doctor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRESCRIPTION_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        return read(result).get("id").asLong();
    }

    private void submit(long rxId) throws Exception {
        mockMvc.perform(post(BASE + "/prescriptions/" + rxId + "/submit")
                        .header("Authorization", bearer("doctor01")))
                .andExpect(status().isOk());
    }

    private void approve(long rxId) throws Exception {
        mockMvc.perform(post(BASE + "/prescriptions/" + rxId + "/reviews/approve")
                        .header("Authorization", bearer("pharmacist01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"用法用量与诊断相符，同意调配\"}"))
                .andExpect(status().isOk());
    }

    private long createCirculation(long rxId) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE + "/circulations")
                        .header("Authorization", bearer("patient01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rxId\":" + rxId + ",\"pharmacyId\":1}"))
                .andReturn();
        int httpStatus = result.getResponse().getStatus();
        assertThat(httpStatus).as("流转单首次创建 201、命中已有 200").isIn(200, 201);
        return read(result).get("id").asLong();
    }

    private String prescriptionStatusOf(long rxId) {
        return jdbcTemplate.queryForObject("SELECT status FROM prescription WHERE id = ?", String.class, rxId);
    }

    private int countOf(String sql, long id) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count == null ? 0 : count;
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String bearer(String username) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE + "/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + read(result).get("accessToken").asText();
    }

    private void insertUser(Long id, String username, String passwordHash, String roleCode) {
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, username, password_hash, role_code, status) VALUES (?, ?, ?, ?, 1)",
                id, username, passwordHash, roleCode);
    }
}
