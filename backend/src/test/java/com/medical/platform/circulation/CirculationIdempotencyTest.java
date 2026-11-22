package com.medical.platform.circulation;

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
 * （CI-01 ~ CI-12 幂等与并发、ST-12 ~ ST-15 越权）。
 *
 * <p><b>当前状态</b>：流转上下文（R5-A 流转单与药房接方、R5-B 核销与幂等键）尚未合入 main，
 * {@code backend/.../circulation} 目前只有 {@code .gitkeep}。因此业务用例暂时
 * {@link Disabled}，只运行已经生效的认证边界：三个流转端点未登录一律 401。
 * 实现合入后：在 {@code schema-test.sql} 补 {@code circulation_order} / {@code dispense_record} /
 * {@code idempotency_record} 三张表，实现 {@link #givenCirculatedOrder()}，去掉注解即可。
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
        jdbcTemplate.update("DELETE FROM sys_user");
        String hash = passwordEncoder.encode(PASSWORD);
        insertUser(1L, "patient01", hash, "PATIENT");
        insertUser(2L, "patient02", hash, "PATIENT");
        insertUser(3L, "doctor01", hash, "DOCTOR");
        insertUser(4L, "pharmacist01", hash, "PHARMACIST");
        insertUser(5L, "pharmacy02", hash, "PHARMACIST");
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
    @Disabled("等待 R5-A 流转单与 R5-B 核销合入 main 后启用")
    @DisplayName("正向：接方 --redeem--> REDEEMED，生成一条发药记录")
    void redeemAfterAccept() throws Exception {
        long circulationId = givenCirculatedOrder();
        String key = UUID.randomUUID().toString();

        mockMvc.perform(redeem(circulationId, key, "pharmacist01", "PICKUP-123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REDEEMED"));

        assertThat(dispenseRecordsOf(circulationId)).isEqualTo(1);
    }

    @Test
    @Disabled("等待 R5-A 流转单与 R5-B 核销合入 main 后启用")
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

        assertThat(objectMapper.readTree(replayBody).get("id").asLong())
                .as("重放必须返回首次结果，而不是新建记录")
                .isEqualTo(objectMapper.readTree(firstBody).get("id").asLong());
        assertThat(dispenseRecordsOf(circulationId)).isEqualTo(1);
    }

    @Test
    @Disabled("等待 R5-A 流转单与 R5-B 核销合入 main 后启用")
    @DisplayName("重放：换一个幂等键再核销 --> 409 RX_ALREADY_REDEEMED")
    void secondRedeemWithAnotherKeyIsRejected() throws Exception {
        long circulationId = givenCirculatedOrder();
        redeem(circulationId, UUID.randomUUID().toString(), "pharmacist01", "PICKUP-123456");

        mockMvc.perform(redeem(circulationId, UUID.randomUUID().toString(), "pharmacist01", "PICKUP-123456"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RX_ALREADY_REDEEMED"));

        assertThat(dispenseRecordsOf(circulationId)).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // 并发：同一流转单只允许一次成功核销
    // ------------------------------------------------------------------

    @Test
    @Disabled("等待 R5-A 流转单与 R5-B 核销合入 main 后启用")
    @DisplayName("并发：两个线程用同一幂等键同时核销，只落一条发药记录")
    void concurrentSameKeyCreatesSingleDispenseRecord() throws Exception {
        long circulationId = givenCirculatedOrder();
        String key = UUID.randomUUID().toString();

        List<Integer> statuses = redeemConcurrently(circulationId, key, key);

        assertThat(statuses).allMatch(s -> s == 200);
        assertThat(dispenseRecordsOf(circulationId)).isEqualTo(1);
    }

    @Test
    @Disabled("等待 R5-A 流转单与 R5-B 核销合入 main 后启用")
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
    @Disabled("等待 R5-A 流转单与 R5-B 核销合入 main 后启用")
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
    @Disabled("等待 R5-A 流转单与 R5-B 核销合入 main 后启用")
    @DisplayName("未接方直接核销：409 CIRC_STATE_INVALID")
    void redeemBeforeAcceptIsRejected() throws Exception {
        long circulationId = givenCirculatedOrder();

        mockMvc.perform(redeem(circulationId, UUID.randomUUID().toString(), "pharmacist01", "PICKUP-123456"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CIRC_STATE_INVALID"));
    }

    // ------------------------------------------------------------------
    // 越权
    // ------------------------------------------------------------------

    @Test
    @Disabled("等待 R5-A 流转单与 R5-B 核销合入 main 后启用")
    @DisplayName("越权：患者调用核销 --> 403")
    void patientCannotRedeem() throws Exception {
        long circulationId = givenCirculatedOrder();

        mockMvc.perform(redeem(circulationId, UUID.randomUUID().toString(), "patient01", "PICKUP-123456"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCOPE_DENIED"));

        assertThat(dispenseRecordsOf(circulationId)).isZero();
    }

    @Test
    @Disabled("等待 R5-A 流转单与 R5-B 核销合入 main 后启用")
    @DisplayName("越权：其他药房核销他人流转单 --> 403 RX_SCOPE_DENIED")
    void anotherPharmacyCannotRedeem() throws Exception {
        long circulationId = givenCirculatedOrder();

        mockMvc.perform(redeem(circulationId, UUID.randomUUID().toString(), "pharmacy02", "PICKUP-123456"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RX_SCOPE_DENIED"));

        assertThat(dispenseRecordsOf(circulationId)).isZero();
    }

    @Test
    @Disabled("等待 R5-A 流转单与 R5-B 核销合入 main 后启用")
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

    /** 造一张已流转（APPROVED → CIRCULATED）的处方及其流转单，返回流转单 ID。 */
    private long givenCirculatedOrder() {
        throw new UnsupportedOperationException(
                "待 R5-A/R5-B 合入后实现：开方 → 提交 → 审核通过 → 流转，取得流转单 ID"
                        + "（schema-test.sql 需补 circulation_order / dispense_record / idempotency_record）");
    }

    private long prescriptionIdOf(long circulationId) {
        return jdbcTemplate.queryForObject(
                "SELECT rx_id FROM circulation_order WHERE id = ?", Long.class, circulationId);
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
}
