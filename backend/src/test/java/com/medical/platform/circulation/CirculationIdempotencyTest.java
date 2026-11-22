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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 处方流转与核销测试：重放、并发、越权。
 *
 * <p>用例编号与判定标准见 {@code docs/test/circulation-idempotency-cases.md}
 * （CI-01 ~ CI-11 幂等与并发、ST-12 ~ ST-16 越权）。
 *
 * <p>R5-B（李梓语）合入核销与幂等实现后，本类业务用例已全部启用：
 * 前置数据由 {@link #givenCirculatedOrder()} 直接写入 H2（等价的接口链路
 * “问诊 → 开方 → 提交 → 审核 → 流转”分别由 R3/R4/R5-A 的用例覆盖），
 * 测试聚焦核销自身的幂等、并发与越权边界。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("处方流转：核销幂等、重放、并发与越权")
class CirculationIdempotencyTest {

    private static final String CIRC = "/api/v1/circulations";
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
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
        jdbcTemplate.update("DELETE FROM idempotency_record");
        jdbcTemplate.update("DELETE FROM dispense_record");
        jdbcTemplate.update("DELETE FROM circulation_order");
        jdbcTemplate.update("DELETE FROM prescription_review");
        jdbcTemplate.update("DELETE FROM prescription_item");
        jdbcTemplate.update("DELETE FROM prescription");
        jdbcTemplate.update("DELETE FROM consultation_message");
        jdbcTemplate.update("DELETE FROM consultation_session");
        jdbcTemplate.update("DELETE FROM pharmacist_profile");
        jdbcTemplate.update("DELETE FROM sys_user");
        String hash = passwordEncoder.encode(PASSWORD);
        insertUser(1L, "patient01", hash, "PATIENT");
        insertUser(2L, "patient02", hash, "PATIENT");
        insertUser(3L, "doctor01", hash, "DOCTOR");
        insertUser(4L, "pharmacist01", hash, "PHARMACIST");
        insertUser(5L, "pharmacy02", hash, "PHARMACIST");
        // 药师与药房绑定：pharmacist01 属于药房 1，pharmacy02 属于药房 2。
        insertPharmacist(4L, "李药师", 1L, "LIC-TEST-001");
        insertPharmacist(5L, "王药师", 2L, "LIC-TEST-002");
    }

    // ------------------------------------------------------------------
    // 已生效：先认证，再谈幂等
    // ------------------------------------------------------------------

    @Test
    @DisplayName("未认证调用接方/核销/追溯：一律 401，不进入幂等逻辑")
    void circulationEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(post(CIRC + "/1/accept"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(CIRC + "/1/redeem")
                        .header(IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"evidence\":\"PICKUP-123456\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/prescriptions/1/trace"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("无效令牌核销：401，而不是 400 或 409")
    void invalidTokenCannotRedeem() throws Exception {
        mockMvc.perform(post(CIRC + "/1/redeem")
                        .header("Authorization", "Bearer not-a-real-token")
                        .header(IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"evidence\":\"PICKUP-123456\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // 重放：同一幂等键只生效一次
    // ------------------------------------------------------------------

    @Test
    @DisplayName("正向：接方 --redeem--> REDEEMED，生成一条发药记录")
    void redeemAfterAccept() throws Exception {
        long circulationId = givenCirculatedOrder();
        String key = UUID.randomUUID().toString();

        mockMvc.perform(redeem(circulationId, key, "pharmacist01", "PICKUP-123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REDEEMED"))
                .andExpect(jsonPath("$.dispenseRecord.id").isNumber());

        assertThat(dispenseRecordsOf(circulationId)).isEqualTo(1);
        assertThat(prescriptionStatusOf(prescriptionIdOf(circulationId))).isEqualTo("REDEEMED");
    }

    @Test
    @DisplayName("重放：同一幂等键重复核销返回首次结果，不重复写发药记录")
    void replayWithSameKeyReturnsFirstResult() throws Exception {
        long circulationId = givenCirculatedOrder();
        String key = UUID.randomUUID().toString();

        String firstBody = mockMvc.perform(redeem(circulationId, key, "pharmacist01", "PICKUP-123456"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        String replayBody = mockMvc.perform(redeem(circulationId, key, "pharmacist01", "PICKUP-123456"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(objectMapper.readTree(replayBody).get("dispenseRecord").get("id").asLong())
                .as("重放必须返回首次发药记录，而不是新建记录")
                .isEqualTo(objectMapper.readTree(firstBody).get("dispenseRecord").get("id").asLong());
        assertThat(dispenseRecordsOf(circulationId)).isEqualTo(1);
    }

    @Test
    @DisplayName("同键换参数：409 IDEMPOTENCY_CONFLICT，不重复发药")
    void sameKeyWithDifferentPayloadIsRejected() throws Exception {
        long circulationId = givenCirculatedOrder();
        String key = UUID.randomUUID().toString();

        mockMvc.perform(redeem(circulationId, key, "pharmacist01", "PICKUP-123456"))
                .andExpect(status().isOk());

        mockMvc.perform(redeem(circulationId, key, "pharmacist01", "PICKUP-999999"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

        assertThat(dispenseRecordsOf(circulationId)).isEqualTo(1);
    }

    @Test
    @DisplayName("重放：换一个幂等键再核销 --> 409 RX_ALREADY_REDEEMED")
    void secondRedeemWithAnotherKeyIsRejected() throws Exception {
        long circulationId = givenCirculatedOrder();
        mockMvc.perform(redeem(circulationId, UUID.randomUUID().toString(), "pharmacist01", "PICKUP-123456"))
                .andExpect(status().isOk());
        assertThat(statusOf(circulationId)).isEqualTo("REDEEMED");

        mockMvc.perform(redeem(circulationId, UUID.randomUUID().toString(), "pharmacist01", "PICKUP-123456"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RX_ALREADY_REDEEMED"));

        assertThat(dispenseRecordsOf(circulationId)).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // 并发：同一流转单只允许一次成功核销
    // ------------------------------------------------------------------

    @Test
    @DisplayName("并发：两个线程用同一幂等键同时核销，只落一条发药记录")
    void concurrentSameKeyCreatesSingleDispenseRecord() throws Exception {
        long circulationId = givenCirculatedOrder();
        String key = UUID.randomUUID().toString();

        List<Integer> statuses = redeemConcurrently(circulationId, key, key);

        assertThat(statuses).allMatch(s -> s == 200);
        assertThat(dispenseRecordsOf(circulationId)).isEqualTo(1);
    }

    @Test
    @DisplayName("并发：两个线程用不同幂等键同时核销，一个成功、一个 409")
    void concurrentDifferentKeysOnlyOneWins() throws Exception {
        long circulationId = givenCirculatedOrder();

        List<Integer> statuses = redeemConcurrently(circulationId,
                UUID.randomUUID().toString(), UUID.randomUUID().toString());

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(dispenseRecordsOf(circulationId)).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // 参数与状态边界
    // ------------------------------------------------------------------

    @Test
    @DisplayName("缺少 Idempotency-Key：400 IDEMPOTENCY_KEY_REQUIRED")
    void missingIdempotencyKeyIsRejected() throws Exception {
        long circulationId = givenCirculatedOrder();

        mockMvc.perform(post(CIRC + "/" + circulationId + "/redeem")
                        .header("Authorization", bearer("pharmacist01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"evidence\":\"PICKUP-123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        assertThat(dispenseRecordsOf(circulationId)).isZero();
    }

    @Test
    @DisplayName("未接方直接核销：409 CIRC_STATE_INVALID")
    void redeemBeforeAcceptIsRejected() throws Exception {
        long circulationId = givenOrder("CREATED");

        mockMvc.perform(redeem(circulationId, UUID.randomUUID().toString(), "pharmacist01", "PICKUP-123456"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CIRC_STATE_INVALID"));
    }

    // ------------------------------------------------------------------
    // 越权
    // ------------------------------------------------------------------

    @Test
    @DisplayName("越权：患者调用核销 --> 403")
    void patientCannotRedeem() throws Exception {
        long circulationId = givenCirculatedOrder();

        mockMvc.perform(redeem(circulationId, UUID.randomUUID().toString(), "patient01", "PICKUP-123456"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCOPE_DENIED"));

        assertThat(dispenseRecordsOf(circulationId)).isZero();
    }

    @Test
    @DisplayName("越权：其他药房核销他人流转单 --> 403 RX_SCOPE_DENIED")
    void anotherPharmacyCannotRedeem() throws Exception {
        long circulationId = givenCirculatedOrder();

        mockMvc.perform(redeem(circulationId, UUID.randomUUID().toString(), "pharmacy02", "PICKUP-123456"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RX_SCOPE_DENIED"));

        assertThat(dispenseRecordsOf(circulationId)).isZero();
    }

    @Test
    @DisplayName("越权：患者查看他人处方流转轨迹 --> 403")
    void patientCannotTraceOthersPrescription() throws Exception {
        long circulationId = givenCirculatedOrder();
        long rxId = prescriptionIdOf(circulationId);

        mockMvc.perform(get("/api/v1/prescriptions/" + rxId + "/trace")
                        .header("Authorization", bearer("patient02")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RX_SCOPE_DENIED"));
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    /** 造一张已流转（APPROVED → CIRCULATED）的处方及已接方的流转单，返回流转单 ID。 */
    private long givenCirculatedOrder() {
        return givenOrder("ACCEPTED");
    }

    /**
     * 直接落库构造前置数据：处方状态 CIRCULATED，流转单状态由参数决定。
     * 只服务于核销用例，不重复验证 R3/R4/R5-A 的业务链路。
     */
    private long givenOrder(String circulationStatus) {
        LocalDateTime now = LocalDateTime.now();
        String suffix = String.valueOf(System.nanoTime());
        String rxNo = "RX-T-" + suffix;
        String circulationNo = "C-T-" + suffix;

        jdbcTemplate.update("INSERT INTO prescription "
                        + "(rx_no, session_id, patient_id, doctor_id, diagnosis, status, signed_hash, "
                        + "reviewer_id, reviewed_at, version, created_at, updated_at) "
                        + "VALUES (?, 1, 1, 3, ?, 'CIRCULATED', 'SIG-TEST', 4, ?, 3, ?, ?)",
                rxNo, "核销用例处方", now, now, now);
        Long rxId = jdbcTemplate.queryForObject(
                "SELECT id FROM prescription WHERE rx_no = ?", Long.class, rxNo);

        boolean accepted = !"CREATED".equals(circulationStatus);
        jdbcTemplate.update("INSERT INTO circulation_order "
                        + "(circulation_no, rx_id, pharmacy_id, status, idempotency_key, "
                        + "circulated_at, accepted_at, version, created_at, updated_at) "
                        + "VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, ?)",
                circulationNo, rxId, circulationStatus, "CIRC-CREATE-" + suffix,
                now, accepted ? now : null, accepted ? 2 : 1, now, now);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM circulation_order WHERE circulation_no = ?", Long.class, circulationNo);
    }

    private long prescriptionIdOf(long circulationId) {
        return jdbcTemplate.queryForObject(
                "SELECT rx_id FROM circulation_order WHERE id = ?", Long.class, circulationId);
    }

    private String statusOf(long circulationId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM circulation_order WHERE id = ?", String.class, circulationId);
    }

    private String prescriptionStatusOf(long prescriptionId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM prescription WHERE id = ?", String.class, prescriptionId);
    }

    private int dispenseRecordsOf(long circulationId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dispense_record WHERE circulation_id = ?", Integer.class, circulationId);
        return count == null ? 0 : count;
    }

    private org.springframework.test.web.servlet.RequestBuilder redeem(
            long circulationId, String key, String username, String evidence) throws Exception {
        return post(CIRC + "/" + circulationId + "/redeem")
                .header("Authorization", bearer(username))
                .header(IDEMPOTENCY_KEY, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("evidence", evidence)));
    }

    /** 两个线程同时核销，返回各自的 HTTP 状态码。 */
    private List<Integer> redeemConcurrently(long circulationId, String keyA, String keyB) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = new ArrayList<>();

        try {
            List<java.util.concurrent.Future<Integer>> futures = List.of(
                    pool.submit(() -> redeemAndGetStatus(start, circulationId, keyA)),
                    pool.submit(() -> redeemAndGetStatus(start, circulationId, keyB)));
            start.countDown();
            for (var future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        return statuses;
    }

    private Integer redeemAndGetStatus(CountDownLatch start, long circulationId, String key) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        return mockMvc.perform(redeem(circulationId, key, "pharmacist01", "PICKUP-123456"))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private String bearer(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + objectMapper
                .readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("accessToken")
                .asText();
    }

    private void insertUser(Long id, String username, String passwordHash, String roleCode) {
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, username, password_hash, role_code, status) VALUES (?, ?, ?, ?, 1)",
                id, username, passwordHash, roleCode);
    }

    private void insertPharmacist(Long userId, String realName, Long pharmacyId, String licenseNo) {
        jdbcTemplate.update("INSERT INTO pharmacist_profile "
                        + "(user_id, real_name, pharmacy_id, license_no, review_right, status) "
                        + "VALUES (?, ?, ?, ?, 1, 1)",
                userId, realName, pharmacyId, licenseNo);
    }
}
