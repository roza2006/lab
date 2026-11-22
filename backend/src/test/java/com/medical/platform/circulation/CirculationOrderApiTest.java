package com.medical.platform.circulation;

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
import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 流转单创建、药房接方与拒接的接口测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("处方流转：创建、接方与拒接")
class CirculationOrderApiTest {

    private static final String CIRC = "/api/v1/circulations";
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
    void resetData() {
        jdbcTemplate.update("DELETE FROM idempotency_record");
        jdbcTemplate.update("DELETE FROM dispense_record");
        jdbcTemplate.update("DELETE FROM circulation_order");
        jdbcTemplate.update("DELETE FROM prescription_review");
        jdbcTemplate.update("DELETE FROM prescription_item");
        jdbcTemplate.update("DELETE FROM prescription");
        jdbcTemplate.update("DELETE FROM pharmacist_profile");
        jdbcTemplate.update("DELETE FROM consultation_message");
        jdbcTemplate.update("DELETE FROM consultation_session");
        jdbcTemplate.update("DELETE FROM sys_user");

        String hash = passwordEncoder.encode(PASSWORD);
        insertUser(1L, "patient01", hash, "PATIENT");
        insertUser(2L, "doctor01", hash, "DOCTOR");
        insertUser(3L, "pharmacist01", hash, "PHARMACIST");
        insertUser(4L, "pharmacy02", hash, "PHARMACIST");
        insertUser(5L, "admin", hash, "ADMIN");
        insertPharmacist(3L, "药师甲", 1L);
        insertPharmacist(4L, "药师乙", 2L);
    }

    @Test
    @DisplayName("药师创建流转单：处方 APPROVED -> CIRCULATED，重复创建返回同一张")
    void createIsIdempotentByPrescription() throws Exception {
        long rxId = approvedPrescription();

        MvcResult first = mockMvc.perform(post(CIRC)
                        .header("Authorization", "Bearer " + login("pharmacist01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rxId\":" + rxId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.pharmacyId").value(1))
                .andExpect(jsonPath("$.prescription.status").value("CIRCULATED"))
                .andReturn();
        String firstId = objectMapper.readTree(first.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("id").asText();

        String secondId = objectMapper.readTree(mockMvc.perform(post(CIRC)
                        .header("Authorization", "Bearer " + login("pharmacist01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rxId\":" + rxId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("id").asText();

        org.assertj.core.api.Assertions.assertThat(secondId).isEqualTo(firstId);
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM prescription WHERE id = ?", String.class, rxId))
                .isEqualTo("CIRCULATED");
    }

    @Test
    @DisplayName("接方：本药房成功，其他药房 403，重复接方 409")
    void onlyOwningPharmacyCanAccept() throws Exception {
        long rxId = approvedPrescription();
        long circulationId = createAsPharmacist(rxId, "pharmacist01");

        mockMvc.perform(post(CIRC + "/" + circulationId + "/accept")
                        .header("Authorization", "Bearer " + login("pharmacy02")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RX_SCOPE_DENIED"));

        mockMvc.perform(post(CIRC + "/" + circulationId + "/accept")
                        .header("Authorization", "Bearer " + login("pharmacist01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(post(CIRC + "/" + circulationId + "/accept")
                        .header("Authorization", "Bearer " + login("pharmacist01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CIRC_STATE_INVALID"));
    }

    @Test
    @DisplayName("拒接后不能再接方，患者能在列表中看到流转单")
    void rejectIsTerminalAndPatientCanList() throws Exception {
        long rxId = approvedPrescription();
        long circulationId = createAsPharmacist(rxId, "pharmacist01");

        mockMvc.perform(get(CIRC)
                        .header("Authorization", "Bearer " + login("patient01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(circulationId))
                .andExpect(jsonPath("$.content[0].prescription.rxNo").isNotEmpty());

        mockMvc.perform(post(CIRC + "/" + circulationId + "/reject")
                        .header("Authorization", "Bearer " + login("pharmacist01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"药品库存不足\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value("药品库存不足"));

        mockMvc.perform(post(CIRC + "/" + circulationId + "/accept")
                        .header("Authorization", "Bearer " + login("pharmacist01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CIRC_STATE_INVALID"));
    }

    @Test
    @DisplayName("未审核通过的处方不能流转；患者不能创建流转单")
    void onlyApprovedPrescriptionCanCirculate() throws Exception {
        long rxId = prescription("DRAFT");
        mockMvc.perform(post(CIRC)
                        .header("Authorization", "Bearer " + login("pharmacist01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rxId\":" + rxId + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CIRC_STATE_INVALID"));

        mockMvc.perform(post(CIRC)
                        .header("Authorization", "Bearer " + login("patient01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rxId\":" + rxId + "}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCOPE_DENIED"));
    }

    private long createAsPharmacist(long rxId, String username) throws Exception {
        MvcResult result = mockMvc.perform(post(CIRC)
                        .header("Authorization", "Bearer " + login(username))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rxId\":" + rxId + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("id").asLong();
    }

    private long approvedPrescription() {
        return prescription("APPROVED");
    }

    private long prescription(String status) {
        String rxNo = "RX-T-" + System.nanoTime();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("INSERT INTO prescription "
                        + "(rx_no, session_id, patient_id, doctor_id, diagnosis, status, signed_hash, "
                        + "reviewer_id, reviewed_at, valid_until, version, created_at, updated_at) "
                        + "VALUES (?, 1, 1, 2, ?, ?, ?, 3, ?, ?, 2, ?, ?)",
                rxNo, "流转测试处方", status,
                "SIG-TEST", now, now.plusHours(72), now, now);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM prescription WHERE rx_no = ?", Long.class, rxNo);
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
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

    private void insertPharmacist(Long userId, String realName, Long pharmacyId) {
        jdbcTemplate.update("INSERT INTO pharmacist_profile "
                        + "(user_id, real_name, pharmacy_id, license_no, review_right, status) "
                        + "VALUES (?, ?, ?, ?, 1, 1)",
                userId, realName, pharmacyId, "LIC-" + userId);
    }
}
