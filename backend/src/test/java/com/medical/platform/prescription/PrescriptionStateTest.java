package com.medical.platform.prescription;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 处方状态机测试：8 个状态的正向迁移与非法迁移。
 *
 * <p>状态与迁移以 {@code docs/design/domain-model.md} §8.1 与《实验报告》表 2-10 为准，
 * 用例编号与前置条件见 {@code docs/test/prescription-state-cases.md}。
 *
 * <p><b>当前状态</b>：R4-A（处方聚合与状态迁移）与 R4-B（药师审核）已合入 main，
 * 因此通过接口真实驱动状态迁移的用例已全部启用；`circulate` / `redeem` / `expire`
 * 三个事件属于第 5 轮流转链路，这里只做迁移表自检，运行期覆盖留到 R5。
 *
 * <p>覆盖三类检查：
 * <ol>
 *   <li>状态机自检：8 个状态、10 条合法迁移、终态无出边、全部状态可达，且与设计文档 §8.1 逐条一致；</li>
 *   <li>安全边界：处方/审核的全部端点未认证一律 401；</li>
 *   <li>正向与非法迁移：经真实接口驱动，非法迁移统一 409 {@code RX_STATE_INVALID}。</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("处方状态机：8 状态的正向与非法迁移")
class PrescriptionStateTest {

    private static final String RX = "/api/v1/prescriptions";
    private static final String PASSWORD = "Passw0rd!";

    /** 开方请求体，字段与 {@code openapi.yaml} 的 {@code PrescriptionSaveRequest} 一致。 */
    private static final String RX_BODY = """
            {
              "diagnosis": "2 型糖尿病",
              "items": [
                {"drugCode": "DRUG001", "drugName": "盐酸二甲双胍片", "specification": "0.5g*20 片",
                 "dosage": "0.5g", "frequency": "每日 2 次", "duration": "30 天",
                 "quantity": 2, "unitPrice": 15.00}
              ]
            }
            """;

    /** 8 个状态，与 domain-model §8.1 一致。 */
    private static final Set<String> STATES = Set.of(
            "DRAFT", "PENDING_REVIEW", "REJECTED", "APPROVED",
            "CIRCULATED", "REDEEMED", "CANCELLED", "EXPIRED");

    /** 终态：不再接受任何事件。 */
    private static final Set<String> TERMINAL_STATES = Set.of("CANCELLED", "REDEEMED", "EXPIRED");

    /** 合法迁移表：(当前状态, 事件) → 目标状态。 */
    private static final Map<String, String> LEGAL_TRANSITIONS = Map.ofEntries(
            Map.entry("DRAFT:submit", "PENDING_REVIEW"),
            Map.entry("PENDING_REVIEW:approve", "APPROVED"),
            Map.entry("PENDING_REVIEW:reject", "REJECTED"),
            Map.entry("REJECTED:revise", "DRAFT"),
            Map.entry("DRAFT:cancel", "CANCELLED"),
            Map.entry("PENDING_REVIEW:cancel", "CANCELLED"),
            Map.entry("APPROVED:circulate", "CIRCULATED"),
            Map.entry("CIRCULATED:redeem", "REDEEMED"),
            Map.entry("APPROVED:expire", "EXPIRED"),
            Map.entry("CIRCULATED:expire", "EXPIRED"));

    /** 每个处方端点都必须先通过认证（未登录时一律 401）。 */
    private static final List<String[]> PROTECTED_ENDPOINTS = List.of(
            new String[]{"POST", "/api/v1/consultations/1/prescriptions"},
            new String[]{"GET", RX},
            new String[]{"GET", RX + "/1"},
            new String[]{"PUT", RX + "/1"},
            new String[]{"POST", RX + "/1/submit"},
            new String[]{"POST", RX + "/1/cancel"},
            new String[]{"GET", RX + "/review-queue"},
            new String[]{"POST", RX + "/1/reviews/approve"},
            new String[]{"POST", RX + "/1/reviews/reject"},
            new String[]{"GET", RX + "/1/reviews"});

    private static final Pattern MERMAID_TRANSITION =
            Pattern.compile("^(\\w+|\\[\\*])\\s*-->\\s*(\\w+|\\[\\*])\\s*:\\s*(\\w+)$");

    private static final Path DESIGN_DOC = Path.of("..", "docs", "design", "domain-model.md");

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
        jdbcTemplate.update("DELETE FROM prescription_review");
        jdbcTemplate.update("DELETE FROM prescription_item");
        jdbcTemplate.update("DELETE FROM prescription");
        jdbcTemplate.update("DELETE FROM consultation_message");
        jdbcTemplate.update("DELETE FROM consultation_session");
        jdbcTemplate.update("DELETE FROM sys_user");

        String hash = passwordEncoder.encode(PASSWORD);
        insertUser(1L, "patient01", hash, "PATIENT", 1);
        insertUser(2L, "doctor01", hash, "DOCTOR", 1);
        insertUser(3L, "doctor02", hash, "DOCTOR", 1);
        insertUser(4L, "pharmacist01", hash, "PHARMACIST", 1);
    }

    // ------------------------------------------------------------------
    // 可执行部分一：状态机自检（不依赖处方实现）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("状态机：8 个状态、10 条合法迁移，且没有指向未知状态的迁移")
    void stateTableIsWellFormed() {
        assertThat(STATES).hasSize(8);
        assertThat(LEGAL_TRANSITIONS).hasSize(10);

        // 迁移的起点、终点都必须是已知状态
        assertThat(LEGAL_TRANSITIONS.keySet())
                .allSatisfy(key -> assertThat(STATES).contains(key.split(":")[0]));
        assertThat(LEGAL_TRANSITIONS.values())
                .allSatisfy(target -> assertThat(STATES).contains(target));

        // 同一 (状态, 事件) 只能有一个目标状态：map 的 key 天然唯一，这里断言事件取值合法
        assertThat(LEGAL_TRANSITIONS.keySet())
                .allSatisfy(key -> assertThat(key).contains(":"));
    }

    @Test
    @DisplayName("状态机：终态没有任何出边")
    void terminalStatesHaveNoOutgoingTransition() {
        for (String terminal : TERMINAL_STATES) {
            assertThat(LEGAL_TRANSITIONS.keySet())
                    .as("终态 %s 不应存在出边", terminal)
                    .noneMatch(key -> key.startsWith(terminal + ":"));
        }
    }

    @Test
    @DisplayName("状态机：8 个状态都能从 DRAFT 经合法迁移到达")
    void everyStateIsReachableFromDraft() {
        Set<String> reached = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add("DRAFT");
        reached.add("DRAFT");

        while (!queue.isEmpty()) {
            String current = queue.poll();
            LEGAL_TRANSITIONS.forEach((key, target) -> {
                if (key.startsWith(current + ":") && reached.add(target)) {
                    queue.add(target);
                }
            });
        }

        assertThat(reached).containsExactlyInAnyOrderElementsOf(STATES);
    }

    @Test
    @DisplayName("状态机：与设计文档 §8.1 的 Mermaid 迁移逐条一致（防文档漂移）")
    void stateTableMatchesDesignDocument() throws Exception {
        assumeTrue(Files.isRegularFile(DESIGN_DOC), "未找到设计文档，跳过文档一致性检查");

        Map<String, String> documented = new LinkedHashMap<>();
        boolean inBlock = false;
        for (String raw : Files.readAllLines(DESIGN_DOC, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.startsWith("stateDiagram-v2")) {
                inBlock = true;
                continue;
            }
            if (inBlock && line.equals("```")) {
                break;
            }
            if (!inBlock) {
                continue;
            }
            Matcher matcher = MERMAID_TRANSITION.matcher(line);
            if (matcher.matches() && !"[*]".equals(matcher.group(1))) {
                // 形如：DRAFT --> PENDING_REVIEW : submit
                documented.put(matcher.group(1) + ":" + matcher.group(3), matcher.group(2));
            }
        }

        assertThat(documented)
                .as("测试中的迁移表必须与 docs/design/domain-model.md §8.1 完全一致")
                .isEqualTo(LEGAL_TRANSITIONS);
    }

    // ------------------------------------------------------------------
    // 可执行部分二：安全边界
    // ------------------------------------------------------------------

    @Test
    @DisplayName("处方与审核端点：未认证一律 401，不进入业务逻辑")
    void prescriptionEndpointsRequireAuthentication() throws Exception {
        for (String[] endpoint : PROTECTED_ENDPOINTS) {
            var request = switch (endpoint[0]) {
                case "GET" -> get(endpoint[1]);
                case "PUT" -> put(endpoint[1]).contentType(MediaType.APPLICATION_JSON).content("{}");
                case "POST" -> post(endpoint[1]).contentType(MediaType.APPLICATION_JSON).content("{}");
                default -> throw new IllegalStateException("未覆盖的方法：" + endpoint[0]);
            };
            mockMvc.perform(request)
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("令牌无效时提交处方：401 而不是 409")
    void invalidTokenCannotSubmit() throws Exception {
        mockMvc.perform(post(RX + "/1/submit")
                        .header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // 正向迁移（经真实接口驱动）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("正向：DRAFT --submit--> PENDING_REVIEW，版本号 +1")
    void submitFromDraft() throws Exception {
        long rxId = givenPrescriptionIn("DRAFT");

        mockMvc.perform(post(RX + "/" + rxId + "/submit")
                        .header("Authorization", bearer("doctor01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    @DisplayName("正向：PENDING_REVIEW --approve--> APPROVED，写入签名摘要")
    void approveFromPendingReview() throws Exception {
        long rxId = givenPrescriptionIn("PENDING_REVIEW");

        mockMvc.perform(post(RX + "/" + rxId + "/reviews/approve")
                        .header("Authorization", bearer("pharmacist01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"用法用量与诊断相符，同意调配\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("APPROVED"))
                .andExpect(jsonPath("$.signature").isNotEmpty());

        assertThat(statusOf(rxId)).isEqualTo("APPROVED");
        assertThat(signedHashOf(rxId)).isNotBlank();
    }

    @Test
    @DisplayName("正向：PENDING_REVIEW --reject--> REJECTED，驳回意见落库")
    void rejectFromPendingReview() throws Exception {
        long rxId = givenPrescriptionIn("PENDING_REVIEW");

        mockMvc.perform(post(RX + "/" + rxId + "/reviews/reject")
                        .header("Authorization", bearer("pharmacist01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"用法用量与说明书不符，请修改后重提\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("REJECTED"));

        assertThat(statusOf(rxId)).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("正向：DRAFT --cancel--> CANCELLED（终态）")
    void cancelFromDraft() throws Exception {
        long rxId = givenPrescriptionIn("DRAFT");

        mockMvc.perform(post(RX + "/" + rxId + "/cancel")
                        .header("Authorization", bearer("doctor01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // ------------------------------------------------------------------
    // 非法迁移（统一 409 RX_STATE_INVALID）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("非法：DRAFT 直接审核通过 --> 409 RX_STATE_INVALID")
    void approveFromDraftIsRejected() throws Exception {
        long rxId = givenPrescriptionIn("DRAFT");

        mockMvc.perform(post(RX + "/" + rxId + "/reviews/approve")
                        .header("Authorization", bearer("pharmacist01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"跳过提交直接通过\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RX_STATE_INVALID"));

        assertThat(statusOf(rxId)).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("非法：重复提交（已 PENDING_REVIEW 再 submit）--> 409 RX_STATE_INVALID")
    void submitTwiceIsRejected() throws Exception {
        long rxId = givenPrescriptionIn("PENDING_REVIEW");

        mockMvc.perform(post(RX + "/" + rxId + "/submit")
                        .header("Authorization", bearer("doctor01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RX_STATE_INVALID"));

        assertThat(statusOf(rxId)).isEqualTo("PENDING_REVIEW");
    }

    @Test
    @DisplayName("非法：APPROVED 后撤销 --> 409 RX_STATE_INVALID（只能作废重开）")
    void cancelFromApprovedIsRejected() throws Exception {
        long rxId = givenPrescriptionIn("APPROVED");

        mockMvc.perform(post(RX + "/" + rxId + "/cancel")
                        .header("Authorization", bearer("doctor01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RX_STATE_INVALID"));

        assertThat(statusOf(rxId)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("非法：待审核状态直接改内容（PUT）--> 409 RX_STATE_INVALID")
    void updateFromPendingReviewIsRejected() throws Exception {
        long rxId = givenPrescriptionIn("PENDING_REVIEW");

        mockMvc.perform(put(RX + "/" + rxId)
                        .header("Authorization", bearer("doctor01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RX_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RX_STATE_INVALID"));

        assertThat(statusOf(rxId)).isEqualTo("PENDING_REVIEW");
    }

    @ParameterizedTest(name = "终态 {0} 再提交 --> 409 RX_STATE_INVALID")
    @ValueSource(strings = {"CANCELLED", "REDEEMED", "EXPIRED"})
    @DisplayName("非法：三个终态都拒绝 submit")
    void terminalStatesRejectSubmit(String terminalState) throws Exception {
        long rxId = givenPrescriptionIn(terminalState);

        mockMvc.perform(post(RX + "/" + rxId + "/submit")
                        .header("Authorization", bearer("doctor01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RX_STATE_INVALID"));

        assertThat(statusOf(rxId)).isEqualTo(terminalState);
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    /**
     * 造一张处于指定状态的处方。
     *
     * <p>DRAFT 直接开方；PENDING_REVIEW 为开方 + 提交；APPROVED / REJECTED 再经药师审核；
     * CANCELLED 用撤销；REDEEMED / EXPIRED 由第 5 轮流转与定时任务产生，这里直接落库，
     * 只用于验证终态拒绝后续事件的行为。
     */
    private long givenPrescriptionIn(String state) throws Exception {
        long rxId = createDraft();
        switch (state) {
            case "DRAFT" -> {
                // 开方即 DRAFT，无需额外操作
            }
            case "PENDING_REVIEW" -> submit(rxId);
            case "APPROVED" -> {
                submit(rxId);
                review(rxId, "approve");
            }
            case "REJECTED" -> {
                submit(rxId);
                review(rxId, "reject");
            }
            case "CANCELLED" -> cancel(rxId);
            case "CIRCULATED", "REDEEMED", "EXPIRED" ->
                    jdbcTemplate.update("UPDATE prescription SET status = ? WHERE id = ?", state, rxId);
            default -> throw new IllegalArgumentException("未知状态：" + state);
        }
        return rxId;
    }

    private String acceptedSession() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/consultations")
                        .header("Authorization", bearer("patient01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"department\":\"内科\",\"diseaseName\":\"2 型糖尿病\",\"chiefComplaint\":\"复诊开药\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String sessionId = objectMapper
                .readTree(created.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("id").asText();

        mockMvc.perform(post("/api/v1/consultations/" + sessionId + "/accept")
                        .header("Authorization", bearer("doctor01")))
                .andExpect(status().isOk());
        return sessionId;
    }

    private long createDraft() throws Exception {
        String sessionId = acceptedSession();
        MvcResult result = mockMvc.perform(post("/api/v1/consultations/" + sessionId + "/prescriptions")
                        .header("Authorization", bearer("doctor01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RX_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("id").asLong();
    }

    private void submit(long rxId) throws Exception {
        mockMvc.perform(post(RX + "/" + rxId + "/submit")
                        .header("Authorization", bearer("doctor01")))
                .andExpect(status().isOk());
    }

    private void review(long rxId, String action) throws Exception {
        mockMvc.perform(post(RX + "/" + rxId + "/reviews/" + action)
                        .header("Authorization", bearer("pharmacist01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"用法用量与诊断相符，同意调配\"}"))
                .andExpect(status().isOk());
    }

    private void cancel(long rxId) throws Exception {
        mockMvc.perform(post(RX + "/" + rxId + "/cancel")
                        .header("Authorization", bearer("doctor01")))
                .andExpect(status().isOk());
    }

    private String statusOf(long rxId) throws Exception {
        MvcResult result = mockMvc.perform(get(RX + "/" + rxId)
                        .header("Authorization", bearer("doctor01")))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("status").asText();
    }

    private String signedHashOf(long rxId) {
        return jdbcTemplate.queryForObject("SELECT signed_hash FROM prescription WHERE id = ?",
                String.class, rxId);
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

    private void insertUser(Long id, String username, String passwordHash, String roleCode, int status) {
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, username, password_hash, role_code, status) VALUES (?, ?, ?, ?, ?)",
                id, username, passwordHash, roleCode, status);
    }
}
